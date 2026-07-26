package com.vzlpr.controller.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.vzlpr.controller.data.local.PassRecordEntity
import com.vzlpr.controller.data.local.WhitelistEntity
import com.vzlpr.controller.data.model.CameraConfig
import com.vzlpr.controller.data.model.GateMode
import com.vzlpr.controller.data.model.PlateEvent
import com.vzlpr.controller.data.model.VzDevice
import com.vzlpr.controller.data.net.PushService
import com.vzlpr.controller.data.repo.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------- 设备搜索 ----------------
class DiscoveryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    val devices = MutableStateFlow<List<VzDevice>>(emptyList())
    val scanning = MutableStateFlow(false)
    val progress = MutableStateFlow(0 to 254)

    fun scan() {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            devices.value = emptyList()
            val merged = LinkedHashMap<String, VzDevice>()
            // 1) UDP 广播
            repo.searchUdp().forEach { merged[it.ip] = it }
            devices.value = merged.values.toList()
            // 2) 子网扫描兜底
            val sweep = repo.searchSweep { done, total -> progress.value = done to total }
            sweep.forEach { d -> merged[d.ip] = merged[d.ip] ?: d }
            devices.value = merged.values.sortedBy { ipToLong(it.ip) }
            scanning.value = false
        }
    }

    private fun ipToLong(ip: String): Long =
        ip.split(".").fold(0L) { acc, p -> acc * 256 + (p.toLongOrNull() ?: 0) }
}

// ---------------- 白名单 ----------------
class WhitelistViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    val items: StateFlow<List<WhitelistEntity>> =
        repo.observeWhitelist().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(plate: String, owner: String, expireAt: Long) {
        val p = plate.trim()
        if (p.isEmpty()) return
        viewModelScope.launch {
            repo.upsertWhitelist(WhitelistEntity(plate = p, owner = owner.trim(), expireAt = expireAt))
        }
    }

    fun toggle(item: WhitelistEntity) {
        viewModelScope.launch { repo.upsertWhitelist(item.copy(enabled = !item.enabled)) }
    }

    fun delete(plate: String) {
        viewModelScope.launch { repo.deleteWhitelist(plate) }
    }

    /** 批量导入：每行一个「车牌,车主」 */
    fun importText(text: String) {
        viewModelScope.launch {
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                val parts = line.split(",", "，", "\t")
                val plate = parts.getOrNull(0)?.trim() ?: return@forEach
                val owner = parts.getOrNull(1)?.trim() ?: ""
                if (plate.isNotEmpty()) repo.upsertWhitelist(WhitelistEntity(plate = plate, owner = owner))
            }
        }
    }
}

// ---------------- 监控（推送/记录） ----------------
class MonitorViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    val records: StateFlow<List<PassRecordEntity>> =
        repo.observeRecords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val log: StateFlow<List<String>> = repo.serverLog
    val running: StateFlow<Boolean> = repo.serverRunning
    val lastEvent: StateFlow<PlateEvent?> = repo.lastEvent

    val pushPort get() = repo.pushPort
    var autoOpen: Boolean
        get() = repo.autoOpenGate
        set(v) { repo.autoOpenGate = v }

    fun setPushPort(p: Int) { repo.pushPort = p }

    fun startServer() = PushService.start(getApplication())
    fun stopServer() = PushService.stop(getApplication())
    fun clearRecords() { viewModelScope.launch { repo.clearRecords() } }

    /** 自测：向本机推送服务器发一条模拟车牌（优先用白名单里的车牌，便于看到「放行」）。需先启动服务。 */
    fun simulatePush() {
        viewModelScope.launch(Dispatchers.IO) {
            val plate = repo.anyWhitelistPlate() ?: "京A12345"
            val json = """{"AlarmInfoPlate":{"channel":0,"deviceName":"self-test","ipaddr":"127.0.0.1",""" +
                """"result":{"PlateResult":{"license":"$plate","colorType":0,"colorValue":0,"confidence":99,""" +
                """"timeStamp":{"Timeval":{"sec":0,"usec":0}},"triggerType":1}},"serialno":"selftest"}}"""
            runCatching {
                val client = OkHttpClient()
                val req = Request.Builder()
                    .url("http://127.0.0.1:${repo.pushPort}/")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { }
            }
        }
    }
}

// ---------------- 实时预览 ----------------
class PreviewViewModel(app: Application) : AndroidViewModel(app) {
    // 0=主码流 1=子码流
    val ip = MutableStateFlow("")
    val user = MutableStateFlow("admin")
    val password = MutableStateFlow("admin")
    val streamType = MutableStateFlow(1)
}

// ---------------- 相机配置/控制 ----------------
class ConfigViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)

    val ip = MutableStateFlow("")
    val user = MutableStateFlow("admin")
    val password = MutableStateFlow("admin")
    val output = MutableStateFlow<List<String>>(emptyList())
    val busy = MutableStateFlow(false)

    // 开闸方式设置
    val gateMode = MutableStateFlow(repo.gateMode)
    val serialHex = MutableStateFlow(repo.serialOpenHex)
    val serialChannel = MutableStateFlow(repo.serialChannel)
    val gateModes: List<GateMode> = GateMode.entries

    fun selectGateMode(m: GateMode) {
        gateMode.value = m
        repo.gateMode = m
        push("开闸方式：${m.label}")
    }

    fun saveSerial() {
        repo.serialOpenHex = serialHex.value.trim()
        repo.serialChannel = serialChannel.value
        push("已保存串口开闸命令")
    }

    private fun cfg() = CameraConfig(ip = ip.value.trim(), user = user.value, password = password.value)

    private fun push(line: String) { output.value = (listOf(line) + output.value).take(50) }

    fun testConnection() {
        if (ip.value.isBlank()) return
        busy.value = true
        viewModelScope.launch {
            val r = repo.configApi.ping(cfg())
            push(if (r.ok) "连接成功 (HTTP ${r.code})" else "连接失败 (${r.code})")
            busy.value = false
        }
    }

    fun openGate() {
        if (ip.value.isBlank()) return
        viewModelScope.launch {
            val r = repo.configApi.openGate(cfg())
            push(if (r.ok) "开闸命令已发送" else "开闸失败 (${r.code}) — 请核对固件 IO 接口")
        }
    }

    fun forceTrigger() {
        if (ip.value.isBlank()) return
        viewModelScope.launch {
            val r = repo.configApi.forceTrigger(cfg())
            push(if (r.ok) "软触发已发送" else "软触发失败 (${r.code})")
        }
    }

    fun restoreFactory() {
        if (ip.value.isBlank()) return
        viewModelScope.launch {
            val r = repo.configApi.restoreFactory(cfg())
            push(if (r.ok) "已发送恢复出厂" else "失败 (${r.code})")
        }
    }

    fun setGateBinding() {
        repo.gateIp = ip.value.trim()
        repo.gateUser = user.value
        repo.gatePassword = password.value
        push("已将该相机设为开闸设备")
    }
}
