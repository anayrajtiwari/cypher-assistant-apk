package ai.cypher.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.*
import java.util.Locale

class CypherDaemon(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tts: TextToSpeech? = null
    private var isRunning = false

    private lateinit var brain: CypherBrain
    private lateinit var wakeWord: WakeWordDetector
    private lateinit var bridge: AndroidCapabilities
    private lateinit var telephony: TelephonyManager
    private lateinit var permissions: PermissionManager
    private lateinit var notificationHelper: NotificationHelper

    fun start() {
        if (isRunning) return
        isRunning = true

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                speak("Hello Boss. All systems operational and standing by.")
            }
        }

        brain = CypherBrain(context)
        permissions = PermissionManager(context)
        bridge = AndroidCapabilities(context, permissions)
        telephony = TelephonyManager(context, bridge)
        notificationHelper = NotificationHelper(context)

        wakeWord = WakeWordDetector(
            context = context,
            onWake = { onWakeDetected() }
        )

        brain.load()
        notificationHelper.send("Cypher Online", "Standing by. Wake word: Zed One Eight")
        wakeWord.startListening()
    }

    fun stop() {
        isRunning = false
        scope.cancel()
        wakeWord.stopListening()
        tts?.stop()
        tts?.shutdown()
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text)
    }

    private fun onWakeDetected() {
        speak("At your service, Boss.")
        scope.launch {
            notificationHelper.update("Listening...")

            val stt = STTEngine(context)
            val text = stt.transcribe(timeoutMs = 7000)
            if (text.isBlank()) {
                speak("I didn't catch that, Boss.")
                notificationHelper.update("Standing by")
                return@launch
            }

            handleCommand(text)
            notificationHelper.update("Standing by")
        }
    }

    private suspend fun handleCommand(userText: String) {
        val tools = bridge.getToolDefinitions()
        var response = brain.generate(userText, tools)

        repeat(3) {
            val (toolName, toolArgs) = parseToolCall(response)
            if (toolName == null) {
                speak(response)
                return
            }

            if (!permissions.checkToolPermission(toolName, toolArgs)) {
                speak("Boss declined the $toolName operation.")
                return
            }

            val result = bridge.executeTool(toolName, toolArgs)
            speak(result)
            return
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
