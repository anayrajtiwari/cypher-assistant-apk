package ai.cypher.assistant

import android.content.Context
import kotlin.random.Random

class CypherBrain(private val context: Context) {

    private val greetings = listOf(
        "At your service, Boss.",
        "Yes, Boss. What can I do for you?",
        "Standing by, Boss. What's the task?",
        "Ready when you are, Boss.",
    )

    private val confirmations = listOf(
        "Done, Boss.",
        "Task completed, Boss.",
        "Consider it handled, Boss.",
        "Executed successfully, Boss.",
    )

    private val unknown = listOf(
        "I understand the request, but I need more specific instructions, Boss.",
        "I'm not equipped to handle that directly, Boss. Would you like me to search for it?",
        "That's beyond my current capabilities, Boss. Is there something else I can help with?",
    )

    private val statusResponses = mapOf(
        "hello" to "Hello Boss! All systems are operational and standing by.",
        "hi" to "Hi Boss! Ready to assist.",
        "hey" to "Hey Boss! What can I do for you?",
        "who are you" to "I'm Cypher, your personal AI assistant, created by you, Boss.",
        "what can you do" to "I can check device status, send messages, make calls, take photos, control settings, and more. Just tell me what you need, Boss.",
        "thank you" to "Always happy to help, Boss.",
        "thanks" to "My pleasure, Boss.",
    )

    private val greetings_list = listOf(
        "hello", "hi", "hey", "good morning", "good evening", "good afternoon", "cypher"
    )

    fun load(): Boolean = true

    fun generate(input: String, tools: List<Map<String, Any>> = emptyList()): String {
        val lower = input.lowercase().trim()

        for ((key, response) in statusResponses) {
            if (lower == key || lower.startsWith(key)) {
                return response
            }
        }

        for (greeting in greetings_list) {
            if (lower.contains(greeting)) {
                return greetings.random()
            }
        }

        if (lower.contains("battery")) {
            return "Checking battery status for you, Boss."
        }
        if (lower.contains("time") || lower.contains("date")) {
            return "Let me check the current time and date for you, Boss."
        }
        if (lower.contains("storage") || lower.contains("space")) {
            return "One moment, let me check your storage, Boss."
        }
        if (lower.contains("app") && (lower.contains("list") || lower.contains("installed"))) {
            return "Fetching your installed applications, Boss."
        }
        if (lower.contains("contact")) {
            return "Reading your contacts, Boss."
        }
        if (lower.contains("location") || lower.contains("where am i")) {
            return "Getting your current location, Boss."
        }
        if (lower.contains("call") || lower.contains("phone")) {
            return "Let me handle that call for you, Boss."
        }
        if (lower.contains("sms") || lower.contains("message") || lower.contains("text")) {
            return "I'll send that message for you, Boss."
        }
        if (lower.contains("photo") || lower.contains("camera") || lower.contains("picture")) {
            return "Opening the camera for you, Boss."
        }
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("flash")) {
            return "Toggling the flashlight, Boss."
        }
        if (lower.contains("volume") || lower.contains("quiet") || lower.contains("loud")) {
            return "Adjusting the volume, Boss."
        }
        if (lower.contains("clipboard") || lower.contains("copy")) {
            return "Accessing your clipboard, Boss."
        }
        if (lower.contains("notification")) {
            return "Managing notifications for you, Boss."
        }
        if (lower.contains("vibrate")) {
            return "Vibrating the device as requested, Boss."
        }

        return unknown.random()
    }
}
