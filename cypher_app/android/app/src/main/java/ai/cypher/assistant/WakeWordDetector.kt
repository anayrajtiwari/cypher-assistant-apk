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
    private var restartOnError = false

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (texts.isNullOrEmpty()) {
                Log.w(TAG, "onResults: null or empty results, restarting")
                startCapture()
                return
            }

            Log.i(TAG, "onResults received ${texts.size} candidates")
            for ((i, text) in texts.withIndex()) {
                val normalized = normalize(text)
                Log.i(TAG, "  [$i] raw=\"$text\" normalized=\"$normalized\"")

                if (isWakeWordMatch(normalized)) {
                    Log.i(TAG, "*** WAKE WORD DETECTED in \"$text\" (normalized=\"$normalized\") ***")
                    isPaused.set(true)
                    onWake()
                    return
                }
            }

            if (isActive.get() && !isPaused.get()) {
                Log.d(TAG, "No wake word match in any candidate, restarting")
                startCapture()
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
            Log.w(TAG, "SpeechRecognizer error: $errorMsg (code=$error)")
            if (isActive.get() && !isPaused.get()) {
                if (restartOnError || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    recreateAndStart()
                } else {
                    restartOnError = true
                    startCapture()
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
        }
        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!texts.isNullOrEmpty()) {
                Log.d(TAG, "onPartialResults: \"${texts[0]}\"")
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (!isActive.compareAndSet(false, true)) {
            Log.i(TAG, "startListening: already active, skipping")
            return
        }
        isPaused.set(false)
        restartOnError = false
        Log.i(TAG, "startListening: beginning wake-word detection")
        checkPermissionAndStart()
    }

    fun pauseListening() {
        Log.i(TAG, "pauseListening: pausing wake-word detection")
        isPaused.set(true)
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "pauseListening: error stopping recognizer", e)
        }
    }

    fun resumeListening() {
        if (!isActive.get()) {
            Log.i(TAG, "resumeListening: not active, ignoring")
            return
        }
        Log.i(TAG, "resumeListening: resuming wake-word detection")
        isPaused.set(false)
        startCapture()
    }

    fun stopListening() {
        Log.i(TAG, "stopListening: stopping wake-word detection")
        isActive.set(false)
        isPaused.set(false)
        mainHandler.post {
            try {
                recognizer?.stopListening()
                recognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "stopListening: error destroying recognizer", e)
            }
            recognizer = null
            Log.i(TAG, "stopListening: recognizer destroyed")
        }
    }

    private fun checkPermissionAndStart() {
        val permission = android.Manifest.permission.RECORD_AUDIO
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "checkPermissionAndStart: RECORD_AUDIO not granted")
            isActive.set(false)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "checkPermissionAndStart: speech recognition not available")
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
            Log.d(TAG, "createAndStartRecognizer: recognizer created")
            startCapture()
        } catch (e: Exception) {
            Log.e(TAG, "createAndStartRecognizer: failed to create recognizer", e)
            if (isActive.get() && !isPaused.get()) {
                mainHandler.postDelayed({ recreateAndStart() }, 2000)
            }
        }
    }

    private fun startCapture() {
        if (!isActive.get() || isPaused.get()) {
            Log.d(TAG, "startCapture: skipping (active=$isActive paused=$isPaused)")
            return
        }
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.startListening(intent)
            Log.i(TAG, "startCapture: listening started")
        } catch (e: Exception) {
            Log.e(TAG, "startCapture: failed to start listening", e)
            if (isActive.get() && !isPaused.get()) {
                mainHandler.postDelayed({ recreateAndStart() }, 1000)
            }
        }
    }

    private fun recreateAndStart() {
        if (!isActive.get() || isPaused.get()) {
            Log.d(TAG, "recreateAndStart: skipping (active=$isActive paused=$isPaused)")
            return
        }
        Log.d(TAG, "recreateAndStart: recreating recognizer after error")
        mainHandler.post {
            createAndStartRecognizer()
        }
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .trim()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace("eighteen", "18")
            .replace("one eight", "1 8")
            .replace("eight", "8")
            .replace("one", "1")
            .replace("zed", "z")
            .replace("zee", "z")
    }

    private fun isWakeWordMatch(normalized: String): Boolean {
        return normalized.contains("z 18") ||
               normalized.contains("z 1 8") ||
               normalized.contains("z18")
    }
}
