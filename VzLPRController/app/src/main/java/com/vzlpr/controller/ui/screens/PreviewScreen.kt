package com.vzlpr.controller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.vzlpr.controller.data.net.VzProtocol
import com.vzlpr.controller.vm.PreviewViewModel

@Composable
fun PreviewScreen(vm: PreviewViewModel = viewModel()) {
    val ip by vm.ip.collectAsState()
    val user by vm.user.collectAsState()
    val pwd by vm.password.collectAsState()
    val stream by vm.streamType.collectAsState()
    var playingUrl by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("实时视频预览 (RTSP)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

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

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = stream == 0, onClick = { vm.streamType.value = 0 }, label = { Text("主码流") })
            FilterChip(selected = stream == 1, onClick = { vm.streamType.value = 1 }, label = { Text("子码流") })
        }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (ip.isNotBlank()) {
                        playingUrl = if (stream == 0) VzProtocol.rtspMain(ip, user, pwd)
                        else VzProtocol.rtspSub(ip, user, pwd)
                    }
                }
            ) { Text("播放") }
            Button(onClick = { playingUrl = null }) { Text("停止") }
        }

        val url = playingUrl
        if (url != null) {
            Text(url, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            RtspPlayer(
                url = url,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).aspectRatio(16f / 9f)
            )
        } else {
            Text(
                "输入相机 IP 与账号后点击「播放」。子码流更省流量、更适合手机预览。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun RtspPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            val source = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true) // 走 TCP，穿透性更好
                .createMediaSource(MediaItem.fromUri(url))
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(url) {
        onDispose { player.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        }
    )
}
