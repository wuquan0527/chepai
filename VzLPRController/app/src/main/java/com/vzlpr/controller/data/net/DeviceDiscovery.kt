package com.vzlpr.controller.data.net

import com.vzlpr.controller.data.model.VzDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * 局域网相机搜索。提供两种方式：
 *  1) UDP 广播搜索——最快，但需固件支持特定探测包（不同型号略有差异）；
 *  2) 子网 HTTP 扫描——遍历本机所在 /24 网段逐个探测 80 端口，兼容性最好（作为兜底）。
 * 实际使用建议两者都跑，合并结果。
 */
class DeviceDiscovery {

    private val http = OkHttpClient.Builder()
        .connectTimeout(600, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .build()

    /** UDP 广播搜索，收集在超时时间内应答的设备。 */
    suspend fun discoverUdp(timeoutMs: Int = 1500): List<VzDevice> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, VzDevice>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = timeoutMs
            }
            // 通用探测包：部分臻识固件对广播的 "VZLPR_SEARCH" 会回应设备信息。
            // 若你的固件使用其它魔术字，请在此处替换。
            val probe = "VZLPR_SEARCH".toByteArray()
            val bcast = InetAddress.getByName("255.255.255.255")
            socket.send(DatagramPacket(probe, probe.size, bcast, VzProtocol.DISCOVERY_UDP_PORT))

            val buf = ByteArray(2048)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    socket.receive(pkt)
                    val ip = pkt.address.hostAddress ?: continue
                    val text = String(pkt.data, 0, pkt.length)
                    found[ip] = parseUdpReply(ip, text)
                } catch (e: Exception) {
                    break // 超时或无更多应答
                }
            }
        } catch (_: Exception) {
        } finally {
            socket?.close()
        }
        found.values.toList()
    }

    private fun parseUdpReply(ip: String, text: String): VzDevice {
        // 应答通常是一段包含 mac/sn/model 的文本或 JSON；这里做宽松解析。
        fun grab(key: String): String {
            val i = text.indexOf(key, ignoreCase = true)
            if (i < 0) return ""
            val rest = text.substring(i + key.length).trimStart(':', '=', '"', ' ')
            return rest.takeWhile { it != ',' && it != '"' && it != '}' && it != '\n' }.trim()
        }
        return VzDevice(
            ip = ip,
            mac = grab("mac"),
            model = grab("model"),
            serial = grab("sn").ifEmpty { grab("serial") },
            firmware = grab("firmware")
        )
    }

    /** 子网扫描：探测本机 /24 网段内所有主机的 HTTP 端口。 */
    suspend fun discoverSweep(
        httpPort: Int = VzProtocol.DEFAULT_HTTP_PORT,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<VzDevice> = coroutineScope {
        val prefix = localSubnetPrefix() ?: return@coroutineScope emptyList()
        val gate = Semaphore(40)
        var done = 0
        val jobs = (1..254).map { host ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val ip = "$prefix$host"
                    val dev = probeHttp(ip, httpPort)
                    synchronized(this@coroutineScope) { onProgress(++done, 254) }
                    dev
                }
            }
        }
        jobs.awaitAll().filterNotNull()
    }

    /** 探测单个 IP 的 HTTP 端口，识别是否疑似臻识相机。 */
    private suspend fun probeHttp(ip: String, port: Int): VzDevice? = withTimeoutOrNull(1200) {
        // 先快速判断端口是否开放
        val open = runCatching {
            java.net.Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), 500); true
            }
        }.getOrDefault(false)
        if (!open) return@withTimeoutOrNull null

        val req = Request.Builder().url("http://$ip:$port/").get().build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                val server = resp.header("Server") ?: ""
                val body = resp.peekBody(4096).string()
                val looksVz = server.contains("vz", true) ||
                    body.contains("main.htm", true) ||
                    body.contains("VzLPR", true) ||
                    resp.header("WWW-Authenticate")?.contains("IPCamera", true) == true
                VzDevice(
                    ip = ip,
                    model = if (looksVz) "疑似臻识相机" else "HTTP设备",
                    httpPort = port,
                    online = true
                )
            }
        }.getOrNull()
    }

    /** 取本机 WLAN/以太网 IPv4 的 /24 前缀，如 "192.168.1." */
    private fun localSubnetPrefix(): String? {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            for (ni in ifaces) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses) {
                    val h = addr.hostAddress ?: continue
                    if (!addr.isLoopbackAddress && h.count { it == '.' } == 3 && !h.contains(":")) {
                        return h.substringBeforeLast('.') + "."
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }
}
