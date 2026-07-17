package ai.cypher.assistant

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class CypherDaemon(private val context: Context, private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "CypherDaemon"
    }

    private var tts: TextToSpeech? = null
    private val ttsReady = AtomicBoolean(false)
    private var wakeWord: WakeWordDetector? = null
    private val isRunning = AtomicBoolean(false)
    private var pipelineJob: Job? = null
    private var daemonState: DaemonState = DaemonState.STOPPED
    private var ttsListenerAttached = false

    val brain = CypherBrain(context)
    val permissions = PermissionManager(context)
    val bridge = AndroidCapabilities(context, permissions)

    enum class DaemonState {
        STOPPED,
        INITIALIZING,
        WAKE_WORD_LISTENING,
        PROCESSING_COMMAND,
        SPEAKING,
        LISTENING,
        ERROR
    }

    fun start() {
        if (!isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Daemon already running, skipping start")
            return
        }

        daemonState = DaemonState.INITIALIZING
        Log.i(TAG, "Daemon starting")

        initializeTts()

        val brainState = brain.load()
        Log.i(TAG, "Brain state after load: $brainState")

        if (brainState == CypherBrain.BrainState.NATIVE_LOAD_FAILED) {
            Log.w(TAG, "Native library failed to load, continuing with rule-based responses")
        }

        if (brainState == CypherBrain.BrainState.MODEL_NOT_FOUND) {
            Log.i(TAG, "Model not found locally, starting background download")
            scope.launch {
                val downloaded = brain.downloadModel()
                if (downloaded) {
                    Log.i(TAG, "Model downloaded and loaded successfully during startup")
                } else {
                    Log.w(TAG, "Background model download failed, will retry on voice command")
                }
            }
        }

        startWakeWord()
        daemonState = DaemonState.WAKE_WORD_LISTENING
        Log.i(TAG, "Daemon started, state=WAKE_WORD_LISTENING")
    }

    private fun initializeTts() {
        try {
            if (tts == null) {
                tts = TextToSpeech(context) { status ->
                    val ready = status == TextToSpeech.SUCCESS
                    ttsReady.set(ready)
                    if (ready) {
                        setMaleVoice()
                        attachUtteranceListener()
                        Log.i(TAG, "TTS initialized successfully")
                    } else {
                        Log.w(TAG, "TTS initialization failed with status: $status")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS initialization error", e)
            ttsReady.set(false)
        }
    }

    private fun attachUtteranceListener() {
        if (ttsListenerAttached) return
        ttsListenerAttached = true
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(uid: String) {
                Log.d(TAG, "TTS started: $uid")
            }
            override fun onDone(uid: String) {
                Log.d(TAG, "TTS done: $uid")
            }
            override fun onError(uid: String) {
                Log.w(TAG, "TTS error: $uid")
            }
            override fun onStop(uid: String, interrupted: Boolean) {
                Log.d(TAG, "TTS stopped: $uid interrupted=$interrupted")
            }
        })
    }

    private fun setMaleVoice() {
        val engine = tts ?: return
        engine.language = Locale.US
        engine.setPitch(0.85f)
        engine.setSpeechRate(0.95f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val maleVoice = engine.voices?.firstOrNull { voice ->
                    voice.locale.language == "en" && (
                        voice.name.contains("male", ignoreCase = true) ||
                        voice.name.contains("en-us-x-tp", ignoreCase = true)
                    )
                }
                if (maleVoice != null) {
                    engine.voice = maleVoice
                    Log.i(TAG, "Set male voice: ${maleVoice.name}")
                } else {
                    engine.setPitch(0.78f)
                    Log.w(TAG, "No male voice found, using lower pitch (0.78)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Voice selection failed, using default with lower pitch", e)
                engine.setPitch(0.78f)
            }
        } else {
            engine.setPitch(0.78f)
        }
    }

    suspend fun speakAndWait(text: String) {
        if (!ttsReady.get() || tts == null) {
            Log.w(TAG, "TTS not ready, cannot speak: $text")
            return
        }
        suspendCancellableCoroutine<Unit> { cont ->
            val utteranceId = "cypher_${System.nanoTime()}"
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(uid: String) {}
                override fun onDone(uid: String) {
                    if (uid != utteranceId) return
                    if (!cont.isCancelled) cont.resume(Unit)
                }
                override fun onError(uid: String) {
                    if (uid != utteranceId) return
                    if (!cont.isCancelled) cont.resume(Unit)
                }
                override fun onStop(uid: String, interrupted: Boolean) {
                    if (uid != utteranceId) return
                    if (!cont.isCancelled) cont.resume(Unit)
                }
            })
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                if (!cont.isCancelled) cont.resume(Unit)
            }
        }
    }

    fun speak(text: String) {
        if (ttsReady.get()) {
            try {
                tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "fire_and_forget_${System.nanoTime()}")
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak error", e)
            }
        } else {
            Log.w(TAG, "TTS not ready, cannot speak: $text")
        }
    }

    private fun startWakeWord() {
        try {
            if (wakeWord != null) {
                Log.w(TAG, "WakeWord already exists, stopping old one first")
                stopWakeWord()
            }
            wakeWord = WakeWordDetector(context) {
                onWakeWordDetected()
            }
            wakeWord?.startListening()
            Log.i(TAG, "Wake word detector started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake word detector", e)
        }
    }

    private fun onWakeWordDetected() {
        if (!isRunning.get()) return
        if (daemonState == DaemonState.PROCESSING_COMMAND || daemonState == DaemonState.LISTENING) {
            Log.i(TAG, "Already processing a command, ignoring wake word")
            return
        }

        Log.i(TAG, "Wake word detected, starting command pipeline")
        daemonState = DaemonState.PROCESSING_COMMAND
        pipelineJob?.cancel()
        pipelineJob = scope.launch {
            try {
                pauseWakeWord()

                daemonState = DaemonState.SPEAKING
                speakAndWait("At your service, Boss.")

                daemonState = DaemonState.LISTENING
                val stt = STTEngine(context)
                val text = stt.transcribe(timeoutMs = 8000)
                if (text.isBlank()) {
                    Log.w(TAG, "No speech detected after TTS")
                    return@launch
                }

                Log.i(TAG, "Heard: \"$text\"")
                daemonState = DaemonState.PROCESSING_COMMAND
                handleCommand(text)

            } catch (e: Exception) {
                Log.e(TAG, "Pipeline error", e)
                speakAndWait("I had trouble hearing you, Boss.")
            } finally {
                resumeWakeWord()
                daemonState = DaemonState.WAKE_WORD_LISTENING
                Log.i(TAG, "Pipeline complete, returning to wake word listening")
            }
        }
    }

    private fun pauseWakeWord() {
        try {
            wakeWord?.pauseListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing wake word", e)
        }
    }

    private fun resumeWakeWord() {
        try {
            wakeWord?.resumeListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming wake word", e)
        }
    }

    private fun stopWakeWord() {
        try {
            wakeWord?.stopListening()
            wakeWord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word", e)
        }
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) {
            Log.i(TAG, "Daemon already stopped, skipping stop")
            return
        }

        Log.i(TAG, "Daemon stopping")
        daemonState = DaemonState.STOPPED

        pipelineJob?.cancel()
        pipelineJob = null

        stopWakeWord()

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
        ttsReady.set(false)

        brain.unload()

        Log.i(TAG, "Daemon stopped")
    }

    private fun handleCommand(userText: String) {
        val tools = bridge.getToolDefinitions()
        val response = brain.generate(userText, tools)

        val (toolName, toolArgs) = parseToolCall(response)
        if (toolName != null) {
            Log.i(TAG, "Tool call detected: $toolName args=$toolArgs")
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
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tool call", e)
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
