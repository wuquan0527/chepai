package com.vzlpr.controller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vzlpr.controller.vm.ConfigViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(vm: ConfigViewModel = viewModel()) {
    val ip by vm.ip.collectAsState()
    val user by vm.user.collectAsState()
    val pwd by vm.password.collectAsState()
    val output by vm.output.collectAsState()
    val busy by vm.busy.collectAsState()
    val gateMode by vm.gateMode.collectAsState()
    val serialHex by vm.serialHex.collectAsState()
    val serialChannel by vm.serialChannel.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("相机配置与控制", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = ip, onValueChange = { vm.ip.value = it },
            label = { Text("相机 IP") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = user, onValueChange = { vm.user.value = it },
                label = { Text("用户名") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = pwd, onValueChange = { vm.password.value = it },
                label = { Text("密码") }, singleLine = true, modifier = Modifier.weight(1f)
            )
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { vm.testConnection() }, enabled = !busy) { Text("连接测试") }
            Button(onClick = { vm.openGate() }) { Text("开闸") }
            Button(onClick = { vm.forceTrigger() }) { Text("软触发识别") }
            OutlinedButton(onClick = { vm.setGateBinding() }) { Text("设为开闸设备") }
            OutlinedButton(
                onClick = { vm.restoreFactory() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("恢复出厂") }
        }

        // ---------- 开闸方式（覆盖不同型号/接线） ----------
        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("开闸方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vm.gateModes.forEach { m ->
                        FilterChip(
                            selected = m == gateMode,
                            onClick = { vm.selectGateMode(m) },
                            label = { Text(m.label) }
                        )
                    }
                }
                Text(
                    gateMode.desc,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // 串口透传开闸命令（AUTO / 串口透传 模式相关）
                OutlinedTextField(
                    value = serialHex,
                    onValueChange = { vm.serialHex.value = it },
                    label = { Text("串口开闸命令(十六进制，按现场道闸控制器)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("串口通道")
                    FilterChip(selected = serialChannel == 0, onClick = { vm.serialChannel.value = 0 }, label = { Text("0") })
                    FilterChip(selected = serialChannel == 1, onClick = { vm.serialChannel.value = 1 }, label = { Text("1") })
                    OutlinedButton(onClick = { vm.saveSerial() }) { Text("保存") }
                }
            }
        }

        Text(
            "开闸兼容说明：AUTO 会「应答里带 is_pay + 串口开闸命令」并「后台并发尝试 HTTP 继电器接口」，" +
                "覆盖大多数接线。道闸接相机 RS485/232→选串口透传并填控制器命令；接相机板载继电器→选 HTTP·继电器；" +
                "要脱机自动开→选下发名单。若某接口失败，多为该固件路径不同，可在 VzProtocol.Gate 里增删候选。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp)
        )

        Text("返回信息", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                if (output.isEmpty()) Text("暂无", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                output.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
