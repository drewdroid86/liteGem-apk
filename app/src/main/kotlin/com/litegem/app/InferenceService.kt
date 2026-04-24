package com.litegem.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class InferenceService : Service() {

    companion object {
        const val ACTION_START = "com.litegem.START"
        const val ACTION_STOP  = "com.litegem.STOP"
        private const val CHANNEL_ID = "litegem_service"
        private const val NOTIF_ID = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var engine: InferenceEngine
    private lateinit var server: ApiServer

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startEngine()
            ACTION_STOP  -> stopSelf()
        }
        return START_STICKY
    }

    private fun startEngine() {
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        engine = InferenceEngine(applicationContext)
        server = ApiServer(engine)
        server.start(port = 11434)
    }

    override fun onDestroy() {
        server.stop()
        engine.unload()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "liteGem Inference",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("liteGem")
            .setContentText("AI inference running on localhost:11434")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
}
