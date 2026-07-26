package com.vzlpr.controller.data.repo

import android.content.Context
import android.util.Base64
import com.vzlpr.controller.data.local.AppDatabase
import com.vzlpr.controller.data.local.PassRecordEntity
import com.vzlpr.controller.data.local.VzDao
import com.vzlpr.controller.data.local.WhitelistEntity
import com.vzlpr.controller.data.model.CameraConfig
import com.vzlpr.controller.data.model.GateMode
import com.vzlpr.controller.data.model.PlateDecision
import com.vzlpr.controller.data.model.PlateEvent
import com.vzlpr.controller.data.model.SerialFrame
import com.vzlpr.controller.data.model.VzDevice
import com.vzlpr.controller.data.net.ConfigApi
import com.vzlpr.controller.data.net.DeviceDiscovery
import com.vzlpr.controller.data.net.PlatePushHandler
import com.vzlpr.controller.data.net.VzProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 应用数据仓储。集中：白名单/记录读写、设备搜索、相机配置、推送处理。
 * 同时实现 [PlatePushHandler]，被内置 HTTP 服务器在收到车牌时同步回调。
 */
class AppRepository private constructor(
    context: Context,
    private val dao: VzDao
) : PlatePushHandler {

    val configApi = ConfigApi()
    val discovery = DeviceDiscovery()

    /** 用于异步开闸等后台任务，不阻塞给相机的推送应答 */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs = context.getSharedPreferences("vz_settings", Context.MODE_PRIVATE)

    // ---------- 可观察状态 ----------
    val lastEvent = MutableStateFlow<PlateEvent?>(null)
    val serverLog = MutableStateFlow<List<String>>(emptyList())
    val serverRunning = MutableStateFlow(false)

    // ---------- 设置 ----------
    var pushPort: Int
        get() = prefs.getInt("push_port", com.vzlpr.controller.data.net.VzProtocol.DEFAULT_PUSH_LISTEN_PORT)
        set(v) = prefs.edit().putInt("push_port", v).apply()

    var autoOpenGate: Boolean
        get() = prefs.getBoolean("auto_open", true)
        set(v) = prefs.edit().putBoolean("auto_open", v).apply()

    var gateUser: String
        get() = prefs.getString("gate_user", "admin") ?: "admin"
        set(v) = prefs.edit().putString("gate_user", v).apply()

    var gatePassword: String
        get() = prefs.getString("gate_pwd", "admin") ?: "admin"
        set(v) = prefs.edit().putString("gate_pwd", v).apply()

    /** 手动指定开闸相机 IP（为空则用推送来源 IP） */
    var gateIp: String
        get() = prefs.getString("gate_ip", "") ?: ""
        set(v) = prefs.edit().putString("gate_ip", v).apply()

    /** 开闸方式 */
    var gateMode: GateMode
        get() = runCatching { GateMode.valueOf(prefs.getString("gate_mode", GateMode.AUTO.name)!!) }
            .getOrDefault(GateMode.AUTO)
        set(v) = prefs.edit().putString("gate_mode", v.name).apply()

    /** 串口透传开闸命令（十六进制，取决于现场道闸控制器） */
    var serialOpenHex: String
        get() = prefs.getString("serial_hex", VzProtocol.Gate.DEFAULT_OPEN_HEX) ?: VzProtocol.Gate.DEFAULT_OPEN_HEX
        set(v) = prefs.edit().putString("serial_hex", v).apply()

    /** 串口通道 0/1 */
    var serialChannel: Int
        get() = prefs.getInt("serial_ch", VzProtocol.Gate.DEFAULT_SERIAL_CHANNEL)
        set(v) = prefs.edit().putInt("serial_ch", v).apply()

    /** 继电器保持时长(ms) */
    var ioHoldMs: Int
        get() = prefs.getInt("io_hold", 800)
        set(v) = prefs.edit().putInt("io_hold", v).apply()

    // ---------- 白名单 ----------
    fun observeWhitelist(): Flow<List<WhitelistEntity>> = dao.observeWhitelist()
    suspend fun anyWhitelistPlate(): String? = dao.getAllWhitelist().firstOrNull()?.plate
    suspend fun upsertWhitelist(item: WhitelistEntity) = dao.upsert(item)
    suspend fun deleteWhitelist(plate: String) = dao.deletePlate(plate)
    suspend fun whitelistCount(): Int = dao.whitelistCount()

    // ---------- 通行记录 ----------
    fun observeRecords(): Flow<List<PassRecordEntity>> = dao.observeRecords()
    suspend fun clearRecords() = dao.clearRecords()

    // ---------- 设备搜索 ----------
    suspend fun searchUdp(): List<VzDevice> = discovery.discoverUdp()
    suspend fun searchSweep(onProgress: (Int, Int) -> Unit): List<VzDevice> =
        discovery.discoverSweep(onProgress = onProgress)

    // ---------- 推送处理（PlatePushHandler） ----------
    override fun onPlate(event: PlateEvent): PlateDecision {
        // 白名单比对与写记录走本地 SQLite，很快，同步完成以便立即应答相机
        val allow = runBlocking {
            val norm = normalize(event.plate)
            val hit = dao.findPlate(event.plate) ?: findByNormalized(norm)
            val now = System.currentTimeMillis()
            val ok = hit != null && hit.enabled && (hit.expireAt == 0L || hit.expireAt > now)
            dao.insertRecord(
                PassRecordEntity(
                    plate = event.plate,
                    colorName = event.colorName,
                    confidence = event.confidence,
                    deviceIp = event.deviceIp,
                    allowed = ok,
                    time = event.timeMillis
                )
            )
            lastEvent.value = event.copy(allowed = ok)
            log("车牌 ${event.plate} ${if (ok) "✔放行" else "✘拒绝"}")
            ok
        }

        if (!allow || !autoOpenGate) {
            return PlateDecision(allow = allow, isPay = false, ledText = if (allow) "欢迎通行" else "非白名单")
        }

        val mode = gateMode
        // 1) 应答内开闸：串口透传命令（AUTO / RESPONSE_SERIAL）
        val frames = if (mode == GateMode.AUTO || mode == GateMode.RESPONSE_SERIAL) {
            val bytes = VzProtocol.Gate.hexToBytes(serialOpenHex)
            if (bytes.isNotEmpty())
                listOf(SerialFrame(serialChannel, Base64.encodeToString(bytes, Base64.NO_WRAP), bytes.size))
            else emptyList()
        } else emptyList()

        // 2) 主动式开闸：直连相机继电器 / 下发名单（AUTO / HTTP_IO / CAMERA_WHITELIST）后台执行
        if (mode == GateMode.AUTO || mode == GateMode.HTTP_IO || mode == GateMode.CAMERA_WHITELIST) {
            val ip = gateIp.ifEmpty { event.deviceIp }
            if (ip.isNotEmpty()) {
                val cfg = CameraConfig(ip = ip, user = gateUser, password = gatePassword)
                ioScope.launch {
                    if (mode == GateMode.CAMERA_WHITELIST) {
                        val r = configApi.addCameraWhitelist(cfg, event.plate)
                        log("下发名单 -> $ip : ${if (r.ok) "成功" else "失败(${r.code})"}")
                    } else {
                        val r = configApi.openGate(cfg, holdMs = ioHoldMs)
                        log("HTTP开闸 -> $ip : ${if (r.ok) "成功" else "失败(${r.code})"}")
                    }
                }
            }
        }

        return PlateDecision(allow = true, isPay = true, ledText = "欢迎通行", serialFrames = frames)
    }

    @Volatile private var pendingSnap = false

    /** 请求在下一次心跳时让相机抓拍一次（软触发的另一种途径） */
    fun requestSnapshotViaHeartbeat() { pendingSnap = true }

    override fun onHeartbeat(): Boolean {
        val snap = pendingSnap
        pendingSnap = false
        log(if (snap) "♥ 心跳(触发抓拍)" else "♥ 心跳")
        return snap
    }

    override fun onLog(message: String) = log(message)

    private suspend fun findByNormalized(norm: String): WhitelistEntity? {
        // 简单容错：忽略大小写/空格后比对（车牌量不大时可接受）
        return dao.getAllWhitelist().firstOrNull { normalize(it.plate) == norm }
    }

    private fun normalize(s: String): String =
        s.uppercase().replace(" ", "").replace("-", "")

    private fun log(msg: String) {
        val ts = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        val line = "[$ts] $msg"
        serverLog.value = (listOf(line) + serverLog.value).take(200)
    }

    companion object {
        @Volatile private var INSTANCE: AppRepository? = null
        fun get(context: Context): AppRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository(
                    context.applicationContext,
                    AppDatabase.get(context).dao()
                ).also { INSTANCE = it }
            }
    }
}
