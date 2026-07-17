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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class STTEngine(private val context: Context) {

    companion object {
        private const val TAG = "STTEngine"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun transcribe(timeoutMs: Long = 7000): String {
        return withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                if (cont.isCancelled) return@suspendCancellableCoroutine

                mainHandler.post {
                    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

                    cont.invokeOnCancellation {
                        Log.i(TAG, "STT cancelled")
                        mainHandler.post {
                            try {
                                recognizer.stopListening()
                                recognizer.destroy()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error cleaning up cancelled STT", e)
                            }
                        }
                    }

                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onResults(bundle: Bundle?) {
                            val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val result = texts?.firstOrNull() ?: ""
                            Log.i(TAG, "STT result: \"$result\"")
                            mainHandler.post {
                                try { recognizer.destroy() } catch (_: Exception) {}
                            }
                            if (!cont.isCancelled) cont.resume(result)
                        }

                        override fun onError(error: Int) {
                            val errorMsg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "no speech detected"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
                                SpeechRecognizer.ERROR_AUDIO -> "audio error"
                                else -> "error code $error"
                            }
                            Log.w(TAG, "STT error: $errorMsg")
                            mainHandler.post {
                                try { recognizer.destroy() } catch (_: Exception) {}
                            }
                            if (!cont.isCancelled) cont.resume("")
                        }

                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000)
                    }
                    try {
                        recognizer.startListening(intent)
                        Log.i(TAG, "STT started listening")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start STT listening", e)
                        try { recognizer.destroy() } catch (_: Exception) {}
                        if (!cont.isCancelled) cont.resume("")
                    }
                }
            }
        }
    }
}
