package ai.cypher.assistant

import android.content.Context
import android.media.AudioManager
import android.telecom.TelecomManager

class TelephonyManager(
    private val context: Context,
    private val bridge: AndroidCapabilities
) {

    private val telecomManager: TelecomManager? by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    }
    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    fun answerCall(): String {
        return try {
            telecomManager?.acceptRingingCall()?.let { return "Call answered." }
            "Failed to answer call."
        } catch (e: SecurityException) {
            "Missing CALL_PHONE permission."
        }
    }

    fun disconnectCall(): String {
        return try {
            telecomManager?.endCall()?.let { return "Call ended." }
            "Failed to end call."
        } catch (e: SecurityException) {
            "Missing CALL_PHONE permission."
        }
    }

    fun muteRingtone(): String {
        audioManager?.let {
            it.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
            return "Ringtone muted."
        }
        return "Failed to mute ringtone."
    }
}
