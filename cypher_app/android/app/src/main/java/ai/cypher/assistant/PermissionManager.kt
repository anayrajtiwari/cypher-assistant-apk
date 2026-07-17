package ai.cypher.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    companion object {
        private const val TAG = "PermissionManager"

        val corePermissions = listOf(
            Manifest.permission.RECORD_AUDIO,
        )

        val notificationsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else emptyList()

        val optionalPermissions = mapOf(
            "read_contacts" to Manifest.permission.READ_CONTACTS,
            "get_location" to listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            "send_sms" to Manifest.permission.SEND_SMS,
            "make_phone_call" to Manifest.permission.CALL_PHONE,
            "take_photo" to Manifest.permission.CAMERA,
            "answer_call" to Manifest.permission.ANSWER_PHONE_CALLS,
            "read_phone_state" to Manifest.permission.READ_PHONE_STATE,
        )

        private val autoTools = setOf(
            "get_battery_status", "get_device_time", "get_storage_status",
            "list_installed_apps", "vibrate_device", "launch_app",
            "clipboard_read", "tts_speak",
        )

        private val toolPermissionMap = mapOf(
            "read_contacts" to "read_contacts",
            "get_location" to "get_location",
            "send_sms" to "send_sms",
            "make_phone_call" to "make_phone_call",
            "take_photo" to "take_photo",
            "answer_call" to "answer_call",
        )
    }

    private val sessionGranted = mutableSetOf<String>()

    fun checkExplicit(toolName: String): Boolean {
        if (toolName in autoTools) return true
        if (toolName in toolPermissionMap) {
            val permKey = toolPermissionMap[toolName]!!
            return checkRuntimePermission(permKey, toolName)
        }
        return true
    }

    fun checkToolPermission(toolName: String, args: Map<String, Any?>): Boolean {
        if (toolName in autoTools) return true
        if (toolName in sessionGranted) return true
        if (toolName in toolPermissionMap) {
            val permKey = toolPermissionMap[toolName]!!
            return checkRuntimePermission(permKey, toolName)
        }
        sessionGranted.add(toolName)
        return true
    }

    private fun checkRuntimePermission(permissionKey: String, toolName: String): Boolean {
        val perms = optionalPermissions[permissionKey] ?: return false
        val permList = if (perms is List<*>) perms as List<String> else listOf(perms as String)
        val allGranted = permList.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            Log.w(TAG, "Permission denied for $toolName: $permissionKey")
        } else {
            sessionGranted.add(toolName)
        }
        return allGranted
    }

    fun hasCorePermissions(): Boolean {
        val all = corePermissions + notificationsPermission
        return all.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
