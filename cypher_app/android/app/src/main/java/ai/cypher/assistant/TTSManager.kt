package ai.cypher.assistant

import android.speech.tts.TextToSpeech
import android.util.Log

class TTSManager(private val tts: TextToSpeech?) {

    companion object {
        private const val TAG = "TTSManager"
    }

    private val utteranceList = mutableListOf<String>()
    private var isSpeaking = false
    private var progressListenerSet = false

    fun speak(text: String) {
        if (tts == null) {
            Log.w(TAG, "TTS not available, cannot speak")
            return
        }
        utteranceList.add(text)
        if (!isSpeaking) {
            speakNext()
        }
    }

    private fun speakNext() {
        if (utteranceList.isEmpty()) {
            isSpeaking = false
            return
        }
        isSpeaking = true
        val next = utteranceList.removeAt(0)

        if (!progressListenerSet) {
            try {
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(uttId: String?) {}
                    override fun onDone(uttId: String?) {
                        Log.i(TAG, "Utterance done: $uttId")
                        speakNext()
                    }
                    override fun onError(uttId: String?) {
                        Log.e(TAG, "Utterance error: $uttId")
                        speakNext()
                    }
                })
                progressListenerSet = true
            } catch (e: Exception) {
                Log.e(TAG, "Error setting utterance listener", e)
            }
        }
        try {
            tts?.speak(next, TextToSpeech.QUEUE_FLUSH, null, next)
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak failed", e)
            speakNext()
        }
    }
}
