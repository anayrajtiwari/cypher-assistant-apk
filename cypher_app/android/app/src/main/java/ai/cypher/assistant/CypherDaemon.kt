package ai.cypher.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class CypherDaemon(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wakeWord: WakeWordDetector? = null
    private var isRunning = false

    val brain = CypherBrain(context)
    val permissions = PermissionManager(context)
    val bridge = AndroidCapabilities(context, permissions)

    fun start() {
        if (isRunning) return
        isRunning = true

        brain.load()

        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.US
                speak("Hello Boss. All systems operational and standing by.")
            }
        }

        try {
            wakeWord = WakeWordDetector(context) {
                speak("At your service, Boss.")
                try {
                    val stt = STTEngine(context)
                    val text = stt.transcribe(timeoutMs = 7000)
                    if (text.isNotBlank()) {
                        handleCommand(text)
                    }
                } catch (_: Exception) {
                    speak("I had trouble hearing you, Boss.")
                }
            }
            wakeWord?.startListening()
        } catch (_: Exception) {
            speak("Wake word system unavailable, Boss.")
        }
    }

    fun stop() {
        isRunning = false
        wakeWord?.stopListening()
        tts?.stop()
        tts?.shutdown()
    }

    fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text)
        }
    }

    private fun handleCommand(userText: String) {
        val tools = bridge.getToolDefinitions()
        val response = brain.generate(userText, tools)

        val (toolName, toolArgs) = parseToolCall(response)
        if (toolName != null) {
            if (!permissions.checkToolPermission(toolName, toolArgs)) {
                speak("Permission denied, Boss.")
                return
            }
            val result = bridge.executeTool(toolName, toolArgs)
            speak(result)
        } else {
            speak(response)
        }
    }

    private fun parseToolCall(text: String): Pair<String?, Map<String, Any?>> {
        val pattern = Regex("<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>", RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(text) ?: return null to emptyMap()
        return try {
            val json = org.json.JSONObject(match.groupValues[1])
            json.optString("name") to (json.optJSONObject("arguments")?.toMap() ?: emptyMap())
        } catch (_: Exception) {
            null to emptyMap()
        }
    }

    private fun org.json.JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        keys().forEach { key ->
            map[key] = when (val v = get(key)) {
                is org.json.JSONObject -> v.toMap()
                is org.json.JSONArray -> (0 until v.length()).map { i ->
                    when (val e = v[i]) {
                        is org.json.JSONObject -> e.toMap()
                        else -> e
                    }
                }
                else -> v
            }
        }
        return map
    }
}
