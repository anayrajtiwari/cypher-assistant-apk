package ai.cypher.assistant

import android.speech.tts.TextToSpeech

class TTSManager(private val tts: TextToSpeech?) {

    private val utteranceList = mutableListOf<String>()
    private var isSpeaking = false

    fun speak(text: String) {
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
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(uttId: String?) {}
            override fun onDone(uttId: String?) { speakNext() }
            override fun onError(uttId: String?) { speakNext() }
        })
        tts?.speak(next, TextToSpeech.QUEUE_FLUSH, null, next)
    }
}
