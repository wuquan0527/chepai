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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vzlpr.controller.vm.WhitelistViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WhitelistScreen(vm: WhitelistViewModel = viewModel()) {
    val items by vm.items.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("白名单（${items.size}）", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row {
                OutlinedButton(onClick = { showImport = true }) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null); Text(" 导入")
                }
                OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null); Text(" 添加")
                }
            }
        }

        LazyColumn(
            Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.plate }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.plate, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val sub = buildString {
                                if (item.owner.isNotEmpty()) append(item.owner)
                                if (item.expireAt > 0) {
                                    if (isNotEmpty()) append(" · ")
                                    append("至 ${fmt.format(Date(item.expireAt))}")
                                } else {
                                    if (isNotEmpty()) append(" · ")
                                    append("长期有效")
                                }
                            }
                            Text(sub, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = item.enabled, onCheckedChange = { vm.toggle(item) })
                        IconButton(onClick = { vm.delete(item.plate) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }

    if (showAdd) AddDialog(onDismiss = { showAdd = false }) { plate, owner ->
        vm.add(plate, owner, 0L); showAdd = false
    }
    if (showImport) ImportDialog(onDismiss = { showImport = false }) { text ->
        vm.importText(text); showImport = false
    }
}

@Composable
private fun AddDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var plate by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加白名单车牌") },
        text = {
            Column {
                OutlinedTextField(
                    value = plate, onValueChange = { plate = it },
                    label = { Text("车牌号，如 京A12345") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = owner, onValueChange = { owner = it },
                    label = { Text("车主/备注（可选）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(plate, owner) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量导入") },
        text = {
            Column {
                Text("每行一条，格式：车牌,车主", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("粘贴车牌列表") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("导入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
