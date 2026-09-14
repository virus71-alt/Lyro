package com.lyro.app.link

import android.app.ForegroundServiceStartNotAllowedException
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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lyro.app.LyroApplication
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
 *
 * Implements defensive foreground promotion to protect the application from
 * crashing on Android 14+ permission or background execution constraints.
 */
class LyroLinkService : Service() {

    companion object {
        private const val TAG = "LyroLinkService"
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
            context.stopService(Intent(context, LyroLinkService::class.java))
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
                Log.d(TAG, "Received ACTION_STOP intent, cleaning up service and server")
                try {
                    LyroApplication.instance.lyroLinkManager.stopServerOnly()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping server during service stop: ${e.message}")
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                promoteToForeground()
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        Log.d(TAG, "Attempting foreground service promotion...")
        val notification = buildNotification(
            address = LyroApplication.instance.lyroLinkManager.state.value.fullAddress ?: "Starting...",
            clients = LyroApplication.instance.lyroLinkManager.state.value.connectedClients
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.i(TAG, "Foreground service promoted successfully with type connectedDevice")
            LyroApplication.instance.lyroLinkManager.onForegroundServiceStarted()

            // Observe state changes and update notification without crashing
            serviceScope.launch {
                LyroApplication.instance.lyroLinkManager.state.collectLatest { state ->
                    if (!state.running && state.status != LyroLinkStatus.STARTING) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if (state.running) {
                        try {
                            val updatedNotif = buildNotification(
                                address = state.fullAddress ?: "Active",
                                clients = state.connectedClients
                            )
                            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            manager?.notify(NOTIFICATION_ID, updatedNotif)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update notification: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Foreground service promotion failed (SecurityException): ${e.message}", e)
            handlePromotionFailure("Security permission missing for connectedDevice: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Foreground service promotion failed (IllegalStateException): ${e.message}", e)
            handlePromotionFailure("Foreground service start not allowed in current state: ${e.message}")
        } catch (e: Exception) {
            val isStartNotAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException
            if (isStartNotAllowed) {
                Log.e(TAG, "Foreground service start not allowed from background: ${e.message}", e)
                handlePromotionFailure("Cannot start foreground service from background")
            } else {
                Log.e(TAG, "Foreground service promotion failed (${e.javaClass.simpleName}): ${e.message}", e)
                handlePromotionFailure(e.message ?: "Failed to start foreground service")
            }
        }
    }

    private fun handlePromotionFailure(reason: String) {
        try {
            LyroApplication.instance.lyroLinkManager.onForegroundServiceFailed(reason)
        } catch (e: Exception) {
            Log.w(TAG, "Error notifying manager of failure: ${e.message}")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Lyro Link",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Lyro Link local Wi-Fi music streaming service"
                    setShowBadge(false)
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create notification channel: ${e.message}")
            }
        }
    }
}
