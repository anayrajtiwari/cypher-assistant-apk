package ai.cypher.assistant

import android.content.Context
import android.media.AudioManager
import android.telecom.TelecomManager
import android.util.Log

class TelephonyManager(
    private val context: Context,
    private val bridge: AndroidCapabilities,
    private val permissions: PermissionManager
) {
    companion object {
        private const val TAG = "TelephonyManager"
    }

    private val telecomManager: TelecomManager? by lazy {
        try { context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager } catch (e: Exception) { null }
    }
    private val audioManager: AudioManager? by lazy {
        try { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager } catch (e: Exception) { null }
    }

    fun answerCall(): String {
        if (!permissions.checkExplicit("answer_call")) {
            return "Permission denied. Cypher needs the Answer Phone Calls permission and the Default Dialer role to answer calls."
        }
        return try {
            telecomManager?.acceptRingingCall()
            "Call answered."
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException answering call", e)
            "Cannot answer call. Cypher needs to be the default dialer app for this feature."
        } catch (e: Exception) {
            Log.e(TAG, "Error answering call", e)
            "Failed to answer call: ${e.message}"
        }
    }

    fun disconnectCall(): String {
        if (!permissions.hasPermission(android.Manifest.permission.ANSWER_PHONE_CALLS)) {
            return "Permission denied for ending calls."
        }
        return try {
            telecomManager?.endCall()
            "Call ended."
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException ending call", e)
            "Cannot end call. Cypher needs to be the default dialer app."
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call", e)
            "Failed to end call: ${e.message}"
        }
    }

    fun muteRingtone(): String {
        return try {
            audioManager?.let {
                it.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                return "Ringtone muted."
            }
            "Failed to mute ringtone."
        } catch (e: Exception) {
            Log.e(TAG, "Error muting ringtone", e)
            "Failed to mute ringtone."
        }
    }
}
