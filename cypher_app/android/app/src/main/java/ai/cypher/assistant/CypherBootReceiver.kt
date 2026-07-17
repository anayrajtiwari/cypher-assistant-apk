package ai.cypher.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

class CypherBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CypherBootReceiver"
        private const val PREFS_NAME = "cypher_prefs"
        private const val KEY_ALWAYS_ON = "alwaysOnCypherEnabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Boot receiver triggered: $action")

        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.i(TAG, "Locked boot completed - deferring until user unlock")
            return
        }

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (isAlwaysOnEnabled(context)) {
                    startService(context)
                } else {
                    Log.i(TAG, "Always-on mode not enabled, skipping auto-start")
                }
            }
        }
    }

    private fun isAlwaysOnEnabled(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_ALWAYS_ON, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read preferences", e)
            false
        }
    }

    private fun startService(context: Context) {
        try {
            val intent = Intent(context, CypherBackgroundService::class.java).apply {
                action = "START"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "Cypher auto-started after boot/update")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service from boot receiver", e)
        }
    }
}
