package com.lyro.app.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lyro.app.MainActivity
import com.lyro.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service to keep the Lyro Link local web server alive and reliable
 * even when the Android device screen turns off or the app is backgrounded.
 */
class LyroLinkService : Service() {

    companion object {
        const val ACTION_START = "com.lyro.app.link.ACTION_START"
        const val ACTION_STOP = "com.lyro.app.link.ACTION_STOP"
        private const val CHANNEL_ID = "lyro_link_foreground_channel"
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, LyroLinkService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LyroLinkService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                com.lyro.app.LyroApplication.instance.lyroLinkManager.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val notification = buildNotification(
                    address = com.lyro.app.LyroApplication.instance.lyroLinkManager.state.value.fullAddress ?: "Starting...",
                    clients = com.lyro.app.LyroApplication.instance.lyroLinkManager.state.value.connectedClients
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                // Collect state changes to update notification dynamically
                serviceScope.launch {
                    com.lyro.app.LyroApplication.instance.lyroLinkManager.state.collectLatest { state ->
                        if (!state.running) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            val updatedNotif = buildNotification(
                                address = state.fullAddress ?: "Active",
                                clients = state.connectedClients
                            )
                            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            manager.notify(NOTIFICATION_ID, updatedNotif)
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(address: String, clients: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LyroLinkService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clientText = if (clients == 1) "1 device connected" else "$clients devices connected"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lyro Link active")
            .setContentText("$address • $clientText")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lyro Link",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lyro Link local Wi-Fi music streaming service"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
