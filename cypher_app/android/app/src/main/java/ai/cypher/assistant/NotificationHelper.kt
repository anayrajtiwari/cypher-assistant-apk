package ai.cypher.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "cypher_events", "Cypher Events",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Cypher action notifications" }
            manager.createNotificationChannel(channel)
        }
    }

    fun update(text: String) {
        val notification = NotificationCompat.Builder(context, "cypher_events")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Cypher")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(1002, notification)
    }

    fun send(title: String, content: String) {
        val notification = NotificationCompat.Builder(context, "cypher_events")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
