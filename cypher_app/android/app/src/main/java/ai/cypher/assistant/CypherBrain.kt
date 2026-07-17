package ai.cypher.assistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class CypherBrain(private val context: Context) {

    companion object {
        private const val TAG = "CypherBrain"
        private val nativeLibraryLoaded = AtomicBoolean(false)

        init {
            try {
                System.loadLibrary("cypher_brain")
                nativeLibraryLoaded.set(true)
                Log.i(TAG, "Native library cypher_brain loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library cypher_brain", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error loading native library", e)
            }
        }
    }

    private external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long
    private external fun nativeGenerate(ptr: Long, prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeUnload(ptr: Long)

    private val nativePtr = AtomicLong(0)
    private val isLoaded = AtomicBoolean(false)
    private val isUnloading = AtomicBoolean(false)
    private val inferenceLock = Any()

    private var modelPath: String? = null
    private var state: BrainState = BrainState.UNINITIALIZED

    private val modelUrl = "https://github.com/anayrajtiwari/cypher-assistant-apk/releases/download/v1.0/cypher-1.5b-q4_0.gguf"
    private val modelFilename = "cypher-1.5b-q4_0.gguf"
    private val contextWindow = 2048
    private val maxGenerateTokens = 256
    private val minModelSizeBytes = 100L * 1024L * 1024L

    enum class BrainState {
        UNINITIALIZED,
        NATIVE_LOAD_FAILED,
        MODEL_NOT_FOUND,
        MODEL_LOADING,
        READY,
        ERROR,
        STOPPED
    }

    fun getState(): BrainState = state

    fun load(): BrainState {
        if (isLoaded.get()) return BrainState.READY
        if (state == BrainState.MODEL_LOADING) return BrainState.MODEL_LOADING

        if (!nativeLibraryLoaded.get()) {
            state = BrainState.NATIVE_LOAD_FAILED
            Log.e(TAG, "Cannot load model: native library not loaded")
            return BrainState.NATIVE_LOAD_FAILED
        }

        state = BrainState.MODEL_LOADING

        val file = findModel()
        if (file == null) {
            state = BrainState.MODEL_NOT_FOUND
            Log.w(TAG, "Model file not found in any location")
            return BrainState.MODEL_NOT_FOUND
        }

        if (!validateModelFile(file)) {
            state = BrainState.MODEL_NOT_FOUND
            Log.e(TAG, "Model file is invalid: ${file.absolutePath}")
            return BrainState.MODEL_NOT_FOUND
        }

        modelPath = file.absolutePath
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        Log.i(TAG, "Loading model from ${file.absolutePath} with ctx=$contextWindow threads=$threads")

        val ptr = nativeLoad(file.absolutePath, contextWindow, threads)
        if (ptr == 0L) {
            state = BrainState.ERROR
            Log.e(TAG, "nativeLoad returned 0 - model loading failed")
            return BrainState.ERROR
        }

        nativePtr.set(ptr)
        isLoaded.set(true)
        state = BrainState.READY
        Log.i(TAG, "Model loaded successfully with ptr=$ptr")
        return BrainState.READY
    }

    fun isReady(): Boolean = isLoaded.get() && nativePtr.get() != 0L && nativeLibraryLoaded.get()

    fun unload() {
        if (!isLoaded.compareAndSet(true, false)) return
        if (!isUnloading.compareAndSet(false, true)) return
        try {
            val ptr = nativePtr.getAndSet(0)
            if (ptr != 0L && nativeLibraryLoaded.get()) {
                synchronized(inferenceLock) {
                    nativeUnload(ptr)
                }
                Log.i(TAG, "Model unloaded successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during model unload", e)
        } finally {
            isUnloading.set(false)
            state = BrainState.STOPPED
            modelPath = null
            nativePtr.set(0)
        }
    }

    suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "models")
        dir.mkdirs()

        val partFile = File(dir, "$modelFilename.part")
        val finalFile = File(dir, modelFilename)

        if (validateModelFile(finalFile)) {
            modelPath = finalFile.absolutePath
            if (!isLoaded.get() && nativeLibraryLoaded.get()) {
                val ptr = nativeLoad(finalFile.absolutePath, contextWindow, Runtime.getRuntime().availableProcessors().coerceIn(2, 8))
                if (ptr != 0L) {
                    nativePtr.set(ptr)
                    isLoaded.set(true)
                    state = BrainState.READY
                }
            }
            return@withContext true
        }

        if (partFile.exists()) {
            partFile.delete()
        }

        return@withContext try {
            val url = URL(modelUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.instanceFollowRedirects = true

            val totalBytes = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                    output.flush()
                }
            }

            if (totalBytes > 0 && partFile.length() != totalBytes) {
                Log.e(TAG, "Download incomplete: expected $totalBytes bytes, got ${partFile.length()}")
                partFile.delete()
                return@withContext false
            }

            if (!validateModelFile(partFile)) {
                Log.e(TAG, "Downloaded model file is invalid")
                partFile.delete()
                return@withContext false
            }

            partFile.renameTo(finalFile)
            modelPath = finalFile.absolutePath

            if (!isLoaded.get() && nativeLibraryLoaded.get()) {
                val ptr = nativeLoad(finalFile.absolutePath, contextWindow, Runtime.getRuntime().availableProcessors().coerceIn(2, 8))
                if (ptr != 0L) {
                    nativePtr.set(ptr)
                    isLoaded.set(true)
                    state = BrainState.READY
                    Log.i(TAG, "Model loaded after download")
                } else {
                    state = BrainState.ERROR
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            partFile.delete()
            false
        }
    }

    private fun findModel(): File? {
        val locations = listOf(
            File(context.filesDir, "models/$modelFilename"),
            File(context.getExternalFilesDir(null), modelFilename),
            File("/sdcard/Cypher/$modelFilename"),
            File("/storage/emulated/0/Cypher/$modelFilename"),
        )
        val found = locations.firstOrNull { file ->
            file.exists() && file.isFile && file.length() > minModelSizeBytes
        }
        if (found != null) Log.i(TAG, "Found model at ${found.absolutePath} (${found.length() / (1024*1024)}MB)")
        else Log.w(TAG, "No valid model file found")
        return found
    }

    private fun validateModelFile(file: File): Boolean {
        if (!file.exists()) {
            Log.w(TAG, "Model file does not exist: ${file.absolutePath}")
            return false
        }
        if (!file.isFile) {
            Log.w(TAG, "Model path is not a file: ${file.absolutePath}")
            return false
        }
        if (file.length() < minModelSizeBytes) {
            Log.w(TAG, "Model file too small: ${file.length()} bytes (min $minModelSizeBytes)")
            return false
        }
        return true
    }

    fun generate(input: String, tools: List<Map<String, Any>> = emptyList()): String {
        if (!nativeLibraryLoaded.get()) {
            return "My AI brain is not available due to a native library loading error, Boss."
        }

        val lower = input.lowercase().trim()

        if (lower.contains("download model") || lower.contains("install model")) {
            if (!isReady()) {
                return "I'll start downloading the AI model from GitHub now, Boss."
            }
        }

        if (lower.contains("model") || lower.contains("download")) {
            if (isReady() && modelPath != null) {
                val size = File(modelPath!!).length() / (1024L * 1024L)
                return "Model loaded successfully, Boss. $size MB GGUF ready."
            }
            return "Model not loaded. Place $modelFilename in /sdcard/Cypher/ and restart, or say 'download model'."
        }

        if (isReady()) {
            val toolsBlock = tools.joinToString("\n") { "  - ${it["name"]}: ${it["description"]}" }
            val prompt = buildPrompt(input, toolsBlock)
            return try {
                synchronized(inferenceLock) {
                    nativeGenerate(nativePtr.get(), prompt, maxGenerateTokens, 0.7f)
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "JNI call failed - native method not found", e)
                nativeLibraryLoaded.set(false)
                isLoaded.set(false)
                state = BrainState.NATIVE_LOAD_FAILED
                "My AI brain encountered a critical error, Boss."
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                "<tool_call>{\"name\":\"tts_speak\",\"arguments\":{}}</tool_call>"
            }
        }

        val known = mapOf(
            "hello" to "Hello Boss!",
            "hi" to "At your service, Boss.",
            "hey" to "Ready, Boss.",
            "who are you" to "I'm Cypher, your assistant, Boss.",
            "thank you" to "Always happy to help, Boss.",
        )
        for ((key, resp) in known) {
            if (lower.contains(key)) return resp
        }

        val intentMap = listOf(
            KeywordIntent("battery", "get_battery_status"),
            KeywordIntent("time", "get_device_time",),
            KeywordIntent("date", "get_device_time"),
            KeywordIntent("storage", "get_storage_status"),
            KeywordIntent("contact", "read_contacts"),
            KeywordIntent("location", "get_location"),
            KeywordIntent("where", "get_location"),
            KeywordIntent("call", "make_phone_call", extractAfter = listOf("call ")),
            KeywordIntent("sms", "send_sms", extractAfter = listOf("sms ")),
            KeywordIntent("message", "send_sms", extractAfter = listOf("message ")),
            KeywordIntent("photo", "take_photo"),
            KeywordIntent("camera", "take_photo"),
            KeywordIntent("flash", "toggle_flashlight", extractAfter = listOf("flash ")),
            KeywordIntent("volume", "set_volume", extractAfter = listOf("volume ")),
            KeywordIntent("clipboard", "clipboard_read"),
            KeywordIntent("vibrate", "vibrate_device"),
            KeywordIntent("url", "open_url", extractAfter = listOf("url ", "open ")),
            KeywordIntent("browser", "open_url", extractAfter = listOf("browser ", "go to ")),
            KeywordIntent("launch", "launch_app", extractAfter = listOf("launch ", "open ")),
            KeywordIntent("update", "check_update", extractAfter = listOf("update ")),
            KeywordIntent("install", "install_update", extractAfter = listOf("install ")),
        )

        for (entry in intentMap) {
            if (lower.contains(entry.keyword) && tools.any { it["name"] == entry.toolName }) {
                val args = extractArguments(lower, entry)
                Log.d(TAG, "intentMap match: keyword='${entry.keyword}' tool='${entry.toolName}' args=$args")
                return buildToolCall(entry.toolName, args)
            }
        }

        return if (!isReady()) "Say 'download model' to install the AI model, Boss."
        else "Say 'What can you do', Boss."
    }

    private data class KeywordIntent(
        val keyword: String,
        val toolName: String,
        val extractAfter: List<String> = emptyList()
    )

    private fun extractArguments(lower: String, entry: KeywordIntent): Map<String, String> {
        if (entry.extractAfter.isEmpty()) return emptyMap()

        val query = extractAfter(lower, entry.extractAfter)

        return when (entry.toolName) {
            "launch_app" -> mapOf("package" to (query ?: ""))
            "make_phone_call" -> mapOf("number" to (query ?: ""))
            "send_sms" -> {
                val parts = (query ?: "").split(" to ", " say ").map { it.trim() }
                if (parts.size >= 2) mapOf("message" to parts[0], "number" to parts[1])
                else mapOf("message" to (query ?: ""), "number" to "")
            }
            "open_url" -> mapOf("url" to (query ?: ""))
            "set_volume" -> {
                val digits = (query ?: "").filter { it.isDigit() }
                mapOf("level" to (if (digits.isNotEmpty()) digits else "10"), "stream" to "music")
            }
            "toggle_flashlight" -> {
                val on = query?.lowercase()?.let { it.contains("on") || it.contains("enable") } ?: true
                mapOf("on" to on.toString())
            }
            else -> emptyMap()
        }
    }

    private fun extractAfter(input: String, prefixes: List<String>): String? {
        val lower = input.lowercase().trim()
        for (prefix in prefixes) {
            val idx = lower.indexOf(prefix)
            if (idx != -1) {
                val after = lower.substring(idx + prefix.length).trim()
                if (after.isNotBlank()) return after
                return null
            }
        }
        return null
    }

    private fun buildToolCall(name: String, args: Map<String, String>): String {
        val argsJson = args.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }
        return "<tool_call>{\"name\":\"$name\",\"arguments\":{$argsJson}}</tool_call>"
    }

    private fun buildPrompt(input: String, toolsBlock: String): String {
        return """
            <|im_start|>system
            You are Cypher, an AI assistant created by Anay. Call him Boss.
            Available tools:
            $toolsBlock
            Use <tool_call>{"name":"tool_name","arguments":{}}</tool_call> to invoke a tool.
            <|im_end|>
            <|im_start|>user
            $input
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    fun getModelPath(): String? = modelPath
    fun getModelSizeMb(): Long? = modelPath?.let { File(it).length() / (1024L * 1024L) }
}
