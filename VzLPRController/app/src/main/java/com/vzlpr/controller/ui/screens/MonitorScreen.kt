package com.vzlpr.controller.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vzlpr.controller.vm.MonitorViewModel
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreen(vm: MonitorViewModel = viewModel()) {
    val running by vm.running.collectAsState()
    val records by vm.records.collectAsState()
    val log by vm.log.collectAsState()
    val last by vm.lastEvent.collectAsState()
    var autoOpen by remember { mutableStateOf(vm.autoOpen) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val localIp = remember { localIpv4() }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("识别监控", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // ---- 服务状态卡 ----
        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.padding(end = 8.dp)
                            .background(if (running) Color(0xFF2E7D32) else Color(0xFFB71C1C), MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (running) "运行中" else "已停止", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    Text("推送接收服务器", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "在相机后台「HTTP 推送」里，把服务器地址填为：",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "http://${localIp}:${vm.pushPort}/",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.startServer() }, enabled = !running
                    ) { Text("启动服务") }
                    Button(
                        onClick = { vm.stopServer() }, enabled = running,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("停止") }
                    TextButton(onClick = { vm.simulatePush() }, enabled = running) { Text("模拟推送自测") }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("命中白名单时自动开闸")
                    Switch(checked = autoOpen, onCheckedChange = { autoOpen = it; vm.autoOpen = it })
                }
            }
        }

        // ---- 最近一次识别 ----
        last?.let { e ->
            Card(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (e.allowed) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("最近识别", style = MaterialTheme.typography.labelMedium)
                    Text(
                        e.plate, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (e.allowed) Color(0xFF2E7D32) else Color(0xFFB71C1C)
                    )
                    Text("${e.colorName}牌 · 置信度 ${e.confidence} · ${if (e.allowed) "放行" else "拒绝"}")
                    Text("来源 ${e.deviceIp}", style = MaterialTheme.typography.bodySmall)
                    val bmp = remember(e.imageBase64) { decodePlateImage(e.imageBase64) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "抓拍大图",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }

        // ---- 运行日志 ----
        Text("运行日志", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Box(
            Modifier.fillMaxWidth()
                .heightIn(max = 140.dp)
                .background(Color(0xFF0D1117), MaterialTheme.shapes.small)
                .padding(8.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                log.forEach { line ->
                    Text(line, color = Color(0xFF9CDCFE), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                if (log.isEmpty()) Text("暂无日志", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- 通行记录 ----
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("通行记录（${records.size}）", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { vm.clearRecords() }) { Text("清空") }
        }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(records, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(r.plate, fontWeight = FontWeight.Bold)
                            Text(fmt.format(Date(r.time)), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (r.allowed) "放行" else "拒绝",
                            color = if (r.allowed) Color(0xFF2E7D32) else Color(0xFFB71C1C),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** 取本机第一个非回环 IPv4 地址 */
private fun localIpv4(): String {
    try {
        for (ni in NetworkInterface.getNetworkInterfaces()) {
            if (!ni.isUp || ni.isLoopback) continue
            for (addr in ni.inetAddresses) {
                val h = addr.hostAddress ?: continue
                if (!addr.isLoopbackAddress && h.count { it == '.' } == 3 && !h.contains(":")) return h
            }
        }
    } catch (_: Exception) {}
    return "本机IP"
}

/** 解码相机推送的大图 Base64（兼容带 data:image 前缀的情况） */
private fun decodePlateImage(b64: String?): ImageBitmap? {
    if (b64.isNullOrBlank()) return null
    return runCatching {
        val pure = if (b64.contains(",")) b64.substringAfter(",") else b64
        val bytes = Base64.decode(pure, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
