package com.vzlpr.controller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vzlpr.controller.vm.DiscoveryViewModel

@Composable
fun DiscoveryScreen(vm: DiscoveryViewModel = viewModel()) {
    val devices by vm.devices.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val progress by vm.progress.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("局域网设备搜索", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "先用 UDP 广播，再自动扫描本机所在网段的 80 端口。请确保手机与相机在同一 WiFi/局域网。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Button(onClick = { vm.scan() }, enabled = !scanning) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text(if (scanning) "  搜索中…" else "  开始搜索")
        }

        if (scanning) {
            val (done, total) = progress
            LinearProgressIndicator(
                progress = if (total == 0) 0f else done.toFloat() / total,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            Text("扫描进度 $done / $total", style = MaterialTheme.typography.bodySmall)
        }

        Text(
            "共发现 ${devices.size} 台设备",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices) { d ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(d.ip, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(d.model.ifEmpty { "HTTP设备" }, style = MaterialTheme.typography.bodySmall)
                        }
                        if (d.mac.isNotEmpty()) Text("MAC: ${d.mac}", style = MaterialTheme.typography.bodySmall)
                        if (d.serial.isNotEmpty()) Text("SN: ${d.serial}", style = MaterialTheme.typography.bodySmall)
                        Text("将此 IP 填入「预览 / 配置」页即可连接", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
