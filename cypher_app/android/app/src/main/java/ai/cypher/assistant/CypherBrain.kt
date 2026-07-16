package ai.cypher.assistant

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class CypherBrain(private val context: Context) {

    private var modelPath: String? = null
    private var isLoaded = false
    private var downloadProgress = ""

    private val modelUrl = "https://github.com/anayrajtiwari/cypher-assistant-apk/releases/download/v1.0/cypher-1.5b-q4_0.gguf"
    private val modelFilename = "cypher-1.5b-q4_0.gguf"

    fun load(): Boolean {
        if (isLoaded) return true

        val modelFile = File(context.filesDir, "models/$modelFilename")
        if (modelFile.exists()) {
            modelPath = modelFile.absolutePath
            isLoaded = true
            return true
        }

        val sdcardModel = File(
            Environment.getExternalStorageDirectory(), "Cypher/$modelFilename"
        )
        if (sdcardModel.exists()) {
            modelPath = sdcardModel.absolutePath
            isLoaded = true
            return true
        }

        return false
    }

    fun downloadModel(): Boolean {
        val dir = File(context.filesDir, "models")
        dir.mkdirs()
        val file = File(dir, modelFilename)
        return try {
            val url = URL(modelUrl)
            url.openStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            modelPath = file.absolutePath
            isLoaded = true
            downloadProgress = ""
            true
        } catch (e: Exception) {
            downloadProgress = "Download failed: ${e.message}"
            false
        }
    }

    fun generate(input: String, tools: List<Map<String, Any>> = emptyList()): String {
        val lower = input.lowercase().trim()

        val modelAvailable = modelPath != null

        if (lower.contains("model") || lower.contains("download")) {
            if (modelAvailable) {
                val size = File(modelPath!!).length() / (1024L * 1024L)
                return "Model is ready, Boss. $size MB loaded."
            }
            if (downloadProgress.isNotEmpty()) {
                return downloadProgress
            }
            return "Model not yet downloaded, Boss. Say 'download model' to fetch it from GitHub."
        }

        if (lower.contains("download model") || lower.contains("install model")) {
            return "Downloading model from GitHub, Boss. This will take a few minutes."
        }

        val known = mapOf(
            "hello" to "Hello Boss! All systems online.",
            "hi" to "At your service, Boss.",
            "hey" to "Ready when you are, Boss.",
            "who are you" to "I'm Cypher, your personal assistant, Boss.",
            "what can you do" to "Battery, time, SMS, calls, camera, flashlight, contacts, location, clipboard, volume, notifications, apps, and more.",
            "thank you" to "Always happy to help, Boss.",
            "good morning" to "Good morning, Boss.",
            "good evening" to "Good evening, Boss.",
        )
        for ((key, resp) in known) {
            if (lower.contains(key)) return resp
        }

        val intentMap = mapOf(
            "battery" to "get_battery_status",
            "time" to "get_device_time", "date" to "get_device_time",
            "storage" to "get_storage_status", "space" to "get_storage_status",
            "app" to "list_installed_apps",
            "contact" to "read_contacts",
            "location" to "get_location", "where" to "get_location",
            "call" to "make_phone_call",
            "sms" to "send_sms", "message" to "send_sms", "text" to "send_sms",
            "photo" to "take_photo", "camera" to "take_photo", "picture" to "take_photo",
            "flash" to "toggle_flashlight", "torch" to "toggle_flashlight",
            "volume" to "set_volume",
            "clipboard" to "clipboard_read",
            "vibrate" to "vibrate_device",
            "url" to "open_url", "browser" to "open_url", "website" to "open_url",
            "launch" to "launch_app",
        )
        for ((keyword, tool) in intentMap) {
            if (lower.contains(keyword)) {
                val toolDef = tools.find { it["name"] == tool }
                if (toolDef != null) {
                    return "<tool_call>{\"name\":\"$tool\",\"arguments\":{}}</tool_call>"
                }
            }
        }

        return "Say 'What can you do' for available commands, Boss."
    }

    fun getModelPath(): String? = modelPath
}
