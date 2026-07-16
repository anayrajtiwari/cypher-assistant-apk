package ai.cypher.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class PermissionManager(private val context: Context) {

    private val sessionGranted = mutableSetOf<String>()

    private val autoTools = setOf(
        "get_battery_status", "get_device_time", "get_storage_status",
        "list_installed_apps", "vibrate_device", "launch_app",
        "clipboard_read", "tts_speak",
    )

    private val sessionTools = setOf(
        "read_contacts", "get_location",
    )

    private val explicitTools = setOf(
        "send_sms", "make_phone_call", "take_photo", "toggle_flashlight",
        "clipboard_write", "open_url", "send_notification", "open_gallery",
        "delete_file", "install_package",
    )

    fun checkToolPermission(toolName: String, args: Map<String, Any?>): Boolean {
        if (toolName in autoTools) return true
        if (toolName in sessionGranted) return true

        val isExplicit = toolName in explicitTools

        return if (isExplicit) {
            checkExplicit(toolName)
        } else {
            sessionGranted.add(toolName)
            true
        }
    }

    fun checkExplicit(toolName: String): Boolean {
        return true
    }
}
