package ai.cypher.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordDetector(
    private val context: Context,
    private val onWake: () -> Unit
) {
    companion object {
        private const val TAG = "WakeWordDetector"
    }

    private var recognizer: SpeechRecognizer? = null
    private val isActive = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val wakePhrases = setOf(
        "zed one eight", "zee one eight",
        "zed 18", "zee 18", "zed18", "zee18",
    )

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (texts.isNullOrEmpty()) {
                restartListening()
                return
            }
            for (text in texts) {
                val lower = text.lowercase().trim()
                for (ww in wakePhrases) {
                    if (lower.contains(ww)) {
                        Log.i(TAG, "Wake word detected in: \"$text\"")
                        onWake()
                        return
                    }
                }
            }
            if (isActive.get() && !isPaused.get()) {
                restartListening()
            }
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
                SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                else -> "ERROR_UNKNOWN($error)"
            }
            Log.w(TAG, "SpeechRecognizer error: $errorMsg")
            if (isActive.get() && !isPaused.get()) {
                restartListening()
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (!isActive.compareAndSet(false, true)) {
            Log.i(TAG, "Already listening")
            return
        }
        isPaused.set(false)

        checkPermissionAndStart()
    }

    fun pauseListening() {
        Log.i(TAG, "Pausing wake word detection")
        isPaused.set(true)
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing recognizer", e)
        }
    }

    fun resumeListening() {
        if (!isActive.get()) {
            Log.i(TAG, "Not active, cannot resume")
            return
        }
        Log.i(TAG, "Resuming wake word detection")
        isPaused.set(false)
        restartListening()
    }

    fun stopListening() {
        Log.i(TAG, "Stopping wake word detection")
        isActive.set(false)
        isPaused.set(false)
        mainHandler.post {
            try {
                recognizer?.stopListening()
                recognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognizer", e)
            }
            recognizer = null
        }
    }

    private fun checkPermissionAndStart() {
        val permission = android.Manifest.permission.RECORD_AUDIO
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            isActive.set(false)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on device")
            isActive.set(false)
            return
        }
        mainHandler.post {
            createAndStartRecognizer()
        }
    }

    private fun createAndStartRecognizer() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(listener)
            startCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create speech recognizer", e)
            if (isActive.get() && !isPaused.get()) {
                mainHandler.postDelayed({ restartListening() }, 2000)
            }
        }
    }

    private fun startCapture() {
        if (!isActive.get() || isPaused.get()) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start capture", e)
            if (isActive.get() && !isPaused.get()) {
                mainHandler.postDelayed({ restartListening() }, 1000)
            }
        }
    }

    private fun restartListening() {
        if (!isActive.get() || isPaused.get()) return
        mainHandler.post {
            try {
                recognizer?.destroy()
            } catch (_: Exception) {}
            recognizer = null
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                    it.setRecognitionListener(listener)
                }
                startCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recreate recognizer", e)
                mainHandler.postDelayed({ restartListening() }, 2000)
            }
        }
    }
}
