package ai.cypher.assistant

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class CypherBrain(private val context: Context) {

    private var modelPath: String? = null
    private var isLoaded = false

    fun load(): Boolean {
        if (isLoaded) return true

        val modelFile = File(context.filesDir, "models/cypher-1.5b-q4_0.gguf")
        if (modelFile.exists()) {
            modelPath = modelFile.absolutePath
            isLoaded = true
            return true
        }

        modelFile.parentFile?.mkdirs()
        try {
            context.assets.open("models/cypher-1.5b-q4_0.gguf").use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            modelPath = modelFile.absolutePath
            isLoaded = true
            return true
        } catch (e: Exception) {
            modelPath = null
            return false
        }
    }

    fun generate(input: String, tools: List<Map<String, Any>> = emptyList()): String {
        val lower = input.lowercase().trim()

        val modelAvailable = modelPath != null

        if (lower.contains("model") && modelAvailable) {
            return "My model is loaded and ready, Boss. ${File(modelPath!!).length() / (1024*1024)}MB GGUF standing by."
        }

        val known = mapOf(
            "hello" to "Hello Boss! All systems online.",
            "hi" to "At your service, Boss.",
            "hey" to "Ready when you are, Boss.",
            "who are you" to "I'm Cypher, your personal assistant, Boss.",
            "what can you do" to "Battery, time, SMS, calls, camera, flashlight, contacts, location, clipboard, volume, notifications, apps, and more.",
            "thank you" to "Always happy to help, Boss.",
            "good morning" to "Good morning, Boss. How can I assist today?",
            "good evening" to "Good evening, Boss. Standing by.",
        )
        for ((key, resp) in known) {
            if (lower.contains(key)) return resp
        }

        val intentMap = mapOf(
            "battery" to "get_battery_status",
            "time" to "get_device_time", "date" to "get_device_time",
            "storage" to "get_storage_status", "space" to "get_storage_status",
            "app" to "list_installed_apps", "installed" to "list_installed_apps",
            "contact" to "read_contacts",
            "location" to "get_location", "where" to "get_location",
            "call" to "make_phone_call", "phone" to "make_phone_call",
            "sms" to "send_sms", "message" to "send_sms", "text" to "send_sms",
            "photo" to "take_photo", "camera" to "take_photo", "picture" to "take_photo",
            "flash" to "toggle_flashlight", "torch" to "toggle_flashlight", "flashlight" to "toggle_flashlight",
            "volume" to "set_volume", "quiet" to "set_volume", "loud" to "set_volume",
            "clipboard" to "clipboard_read", "copy" to "clipboard_write",
            "notification" to "send_notification",
            "vibrate" to "vibrate_device", "vibration" to "vibrate_device",
            "url" to "open_url", "browser" to "open_url", "website" to "open_url",
            "launch" to "launch_app", "open app" to "launch_app",
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
