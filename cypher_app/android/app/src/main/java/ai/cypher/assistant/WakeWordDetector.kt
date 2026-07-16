package ai.cypher.assistant

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class WakeWordDetector(
    private val context: Context,
    private val onWake: () -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    private val wakePhrases = setOf(
        "zed one eight", "zee one eight",
        "zed 18", "zee 18", "zed18", "zee18",
        "zed one 8", "zee one 8"
    )

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
            for (text in texts) {
                val lower = text.lowercase().trim()
                for (ww in wakePhrases) {
                    if (lower.contains(ww)) {
                        onWake()
                        return
                    }
                }
            }
            restartListening()
        }

        override fun onError(error: Int) {
            restartListening()
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
        if (SpeechRecognizer.isRecognitionAvailable(context).not()) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(listener)
        isListening = true
        startCapture()
    }

    private fun startCapture() {
        if (!isListening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(intent)
    }

    private fun restartListening() {
        recognizer?.destroy()
        if (!isListening) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
        }
        startCapture()
    }

    fun stopListening() {
        isListening = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }
}
