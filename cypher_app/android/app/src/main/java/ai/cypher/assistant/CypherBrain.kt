package ai.cypher.assistant

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class CypherBrain(private val context: Context) {

    companion object {
        init {
            try {
                System.loadLibrary("cypher_brain")
            } catch (_: UnsatisfiedLinkError) {}
        }
    }

    private external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long
    private external fun nativeGenerate(ptr: Long, prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeUnload(ptr: Long)

    private var nativePtr: Long = 0
    private var isLoaded = false
    private var modelPath: String? = null
    private val modelUrl = "https://github.com/anayrajtiwari/cypher-assistant-apk/releases/download/v1.0/cypher-1.5b-q4_0.gguf"
    private val modelFilename = "cypher-1.5b-q4_0.gguf"

    fun load(): Boolean {
        if (isLoaded) return true

        val file = findModel()
        if (file != null) {
            modelPath = file.absolutePath
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            nativePtr = nativeLoad(file.absolutePath, 2048, threads)
            isLoaded = true
        }
        return isLoaded
    }

    fun downloadModel(): Boolean {
        val dir = File(context.filesDir, "models")
        dir.mkdirs()
        val file = File(dir, modelFilename)
        return try {
            URL(modelUrl).openStream().use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            modelPath = file.absolutePath
            if (nativePtr == 0L) {
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
                nativePtr = nativeLoad(file.absolutePath, 2048, threads)
            }
            isLoaded = true
            true
        } catch (_: Exception) { false }
    }

    private fun findModel(): File? {
        val locations = listOf(
            File(context.filesDir, "models/$modelFilename"),
            File(context.getExternalFilesDir(null), "$modelFilename"),
            File("/sdcard/Cypher/$modelFilename"),
            File("/storage/emulated/0/Cypher/$modelFilename"),
        )
        return locations.firstOrNull { it.exists() }
    }

    fun generate(input: String, tools: List<Map<String, Any>> = emptyList()): String {
        val lower = input.lowercase().trim()

        if ((lower.contains("download model") || lower.contains("install model")) && !isLoaded) {
            return "I'll start downloading the model from GitHub now, Boss."
        }

        if (lower.contains("model") || lower.contains("download")) {
            if (isLoaded && modelPath != null) {
                val size = File(modelPath!!).length() / (1024L * 1024L)
                return "Model loaded successfully, Boss. $size MB GGUF ready."
            }
            return "Model not loaded. Place cypher-1.5b-q4_0.gguf in /sdcard/Cypher/ and restart, or say 'download model'."
        }

        if (nativePtr != 0L && modelPath != null) {
            val toolsBlock = tools.joinToString("\n") { "  - ${it["name"]}: ${it["description"]}" }
            val prompt = """
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
            return try {
                nativeGenerate(nativePtr, prompt, 256, 0.7f)
            } catch (_: Exception) {
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

        val intentMap = mapOf(
            "battery" to "get_battery_status", "time" to "get_device_time", "date" to "get_device_time",
            "storage" to "get_storage_status", "contact" to "read_contacts",
            "location" to "get_location", "where" to "get_location",
            "call" to "make_phone_call", "sms" to "send_sms", "message" to "send_sms",
            "photo" to "take_photo", "camera" to "take_photo",
            "flash" to "toggle_flashlight",
            "volume" to "set_volume", "clipboard" to "clipboard_read",
            "vibrate" to "vibrate_device",
            "url" to "open_url", "browser" to "open_url",
            "launch" to "launch_app",
        )
        for ((keyword, tool) in intentMap) {
            if (lower.contains(keyword) && tools.any { it["name"] == tool }) {
                return "<tool_call>{\"name\":\"$tool\",\"arguments\":{}}</tool_call>"
            }
        }

        return if (isLoaded.not()) "Say 'download model' to install the AI model, Boss." else "Say 'What can you do', Boss."
    }

    fun getModelPath(): String? = modelPath
}
