package ai.cypher.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class CypherBackgroundService : Service() {

    companion object {
        private const val TAG = "CypherService"

        private const val CHANNEL_ID = "cypher_foreground_silent"
        private const val CHANNEL_NAME = "Cypher"
        private const val CHANNEL_DESC = "Cypher persistent assistant notification"

        const val NOTIFICATION_ID = 1001
    }

    private var daemon: CypherDaemon? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isForeground = false
    private var initializationAttempted = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        createSilentNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: intent=$intent flags=$flags startId=$startId")

        if (!promoteToForeground()) {
            Log.e(TAG, "Failed to promote to foreground service, stopping self")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!initializationAttempted) {
            initializationAttempted = true
            initializeDaemon()
        }

        return START_STICKY
    }

    private fun promoteToForeground(): Boolean {
        return try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
            Log.i(TAG, "Foreground service started successfully")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during startForeground", e)
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException during startForeground", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during startForeground", e)
            false
        }
    }

    private fun initializeDaemon() {
        try {
            if (daemon == null) {
                daemon = CypherDaemon(applicationContext, serviceScope)
            }
            daemon?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize daemon", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        try {
            daemon?.stop()
            daemon = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping daemon", e)
        }
        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling scope", e)
        }
        isForeground = false
        initializationAttempted = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved - restarting service")
        val restartIntent = Intent(applicationContext, CypherBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun createSilentNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = CHANNEL_DESC
                        setShowBadge(false)
                        enableVibration(false)
                        setSound(null, null as AudioAttributes?)
                        setBlockable(false)
                    }
                    manager.createNotificationChannel(channel)
                    Log.i(TAG, "Silent notification channel created (id=$CHANNEL_ID)")
                } else {
                    Log.i(TAG, "Silent notification channel already exists (id=$CHANNEL_ID)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create notification channel", e)
            }
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cypher")
            .setContentText("Standing by")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
