package ai.cypher.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class CypherBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_REBOOT,
            Intent.ACTION_MY_PACKAGE_REPLACED -> startService(context)
        }
    }

    private fun startService(context: Context) {
        val intent = Intent(context, CypherBackgroundService::class.java).apply {
            action = "START"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
