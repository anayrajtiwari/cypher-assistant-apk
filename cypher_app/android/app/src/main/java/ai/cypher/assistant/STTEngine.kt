package ai.cypher.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class STTEngine(private val context: Context) {

    suspend fun transcribe(timeoutMs: Long = 7000): String {
        return withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                if (cont.isCancelled) return@suspendCancellableCoroutine

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

                cont.invokeOnCancellation {
                    recognizer.destroy()
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(bundle: Bundle?) {
                        val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val result = texts?.firstOrNull() ?: ""
                        recognizer.destroy()
                        cont.resume(result)
                    }

                    override fun onError(error: Int) {
                        recognizer.destroy()
                        cont.resume("")
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
                }
                recognizer.startListening(intent)
            }
        }
    }
}
