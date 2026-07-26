package com.vzlpr.controller.data.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vzlpr.controller.R
import com.vzlpr.controller.data.repo.AppRepository

/**
 * 常驻前台服务，承载内置 HTTP 推送服务器，保证 App 退到后台也能持续接收相机车牌推送。
 */
class PushService : Service() {

    private var server: PushServer? = null
    private lateinit var repo: AppRepository

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository.get(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTI_ID, buildNotification())
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        if (server != null) return
        val s = PushServer(repo.pushPort, repo)
        try {
            s.start(NanoTimeoutSockets.SOCKET_READ_TIMEOUT, false)
            server = s
            repo.serverRunning.value = true
            repo.onLog("推送服务器已启动，端口 ${repo.pushPort}")
        } catch (e: Exception) {
            repo.serverRunning.value = false
            repo.onLog("启动失败: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        server = null
        repo.serverRunning.value = false
        repo.onLog("推送服务器已停止")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.push_service_running))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.push_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "vz_push"
        private const val NOTI_ID = 1001

        fun start(ctx: Context) {
            val i = Intent(ctx, PushService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, PushService::class.java))
        }
    }

    /** NanoHTTPD 默认 socket 读超时常量封装（避免直接依赖内部字段名） */
    private object NanoTimeoutSockets {
        const val SOCKET_READ_TIMEOUT = 10000
    }
}
