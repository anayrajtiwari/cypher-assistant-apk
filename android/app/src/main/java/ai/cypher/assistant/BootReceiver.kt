package ai.cypher.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Cypher Native Boot Receiver
 * Auto-starts Cypher Background Daemon on Android 16 OS Boot
 * and greets Boss out loud: "Hello Boss!"
 */
class BootReceiver : BroadcastReceiver() {
    private var tts: TextToSpeech? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            // 1. Launch Cypher Background Service using ContextCompat
            val serviceIntent = Intent(context, CypherBackgroundService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)

            // 2. Out loud TTS Greeting: "Hello Boss!"
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.speak("Hello Boss! All systems operational and standing by.", TextToSpeech.QUEUE_FLUSH, null, "CY_BOOT_GREETING")
                }
            }
        }
    }
}
