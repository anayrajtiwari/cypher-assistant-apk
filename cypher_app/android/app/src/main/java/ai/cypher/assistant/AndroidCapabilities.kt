package ai.cypher.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidCapabilities(
    private val context: Context,
    private val permissions: PermissionManager
) {
    companion object {
        private const val TAG = "AndroidCapabilities"
    }

    private val smsManager: SmsManager? by lazy {
        try { SmsManager.getDefault() } catch (e: Exception) { null }
    }

    private val locationManager: LocationManager? by lazy {
        try { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager } catch (e: Exception) { null }
    }

    fun getBatteryStatus(): String {
        return try {
            val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val charging = plugged != 0
            val pct = level * 100 / scale
            "Battery: $pct% ${if (charging) "CHARGING" else "DISCHARGING"}"
        } catch (e: Exception) {
            Log.e(TAG, "getBatteryStatus failed", e)
            "Battery status unavailable."
        }
    }

    fun getDeviceTime(): String {
        return SimpleDateFormat("EEEE, MMMM dd, yyyy - hh:mm:ss a", Locale.US).format(Date())
    }

    fun getStorageStatus(): String {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val total = stat.totalBytes / (1024L * 1024L * 1024L)
            val free = stat.availableBytes / (1024L * 1024L * 1024L)
            "Storage: ${total}GB total, ${free}GB free"
        } catch (e: Exception) {
            Log.e(TAG, "getStorageStatus failed", e)
            "Storage status unavailable."
        }
    }

    fun listInstalledApps(): String {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            apps.take(50).joinToString("\n") { "${it.loadLabel(pm)} (${it.packageName})" }
        } catch (e: Exception) {
            Log.e(TAG, "listInstalledApps failed", e)
            "Unable to list installed apps."
        }
    }

    fun readContacts(): String {
        if (!permissions.checkExplicit("read_contacts")) return "Permission denied. Grant contacts access in Settings."
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ), null, null, null
            )
            val contacts = mutableListOf<String>()
            cursor?.use {
                while (it.moveToNext()) {
                    val name = it.getString(0)
                    val num = it.getString(1)
                    contacts.add("$name: $num")
                }
            }
            contacts.take(50).joinToString("\n").ifEmpty { "No contacts found." }
        } catch (e: SecurityException) {
            "Permission denied for contacts."
        } catch (e: Exception) {
            Log.e(TAG, "readContacts failed", e)
            "Failed to read contacts."
        }
    }

    fun getLocation(): String {
        if (!permissions.checkExplicit("get_location")) return "Permission denied. Grant location access in Settings."
        return try {
            val lm = locationManager ?: return "Location service unavailable."
            val providers = lm.getProviders(true)
            for (provider in providers) {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) {
                    return "Location: ${loc.latitude}, ${loc.longitude} (accuracy: ${loc.accuracy}m)"
                }
            }
            "Location unavailable. Try turning on GPS."
        } catch (e: SecurityException) {
            "Location permission denied."
        } catch (e: Exception) {
            Log.e(TAG, "getLocation failed", e)
            "Failed to get location."
        }
    }

    fun sendSms(number: String, message: String): String {
        if (!permissions.checkExplicit("send_sms")) return "Permission denied. Grant SMS access in Settings."
        if (number.isBlank()) return "No phone number provided."
        if (message.isBlank()) return "No message provided."
        return try {
            val sms = smsManager ?: return "SMS service unavailable."
            sms.sendTextMessage(number, null, message, null, null)
            "SMS sent to $number."
        } catch (e: SecurityException) {
            "SMS permission denied."
        } catch (e: Exception) {
            Log.e(TAG, "sendSms failed", e)
            "SMS failed: ${e.message}"
        }
    }

    fun makePhoneCall(number: String): String {
        if (!permissions.checkExplicit("make_phone_call")) return "Permission denied. Grant phone access in Settings."
        if (number.isBlank()) return "No phone number provided."
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Calling $number..."
        } catch (e: SecurityException) {
            "Call permission denied."
        } catch (e: Exception) {
            Log.e(TAG, "makePhoneCall failed", e)
            "Failed to make call: ${e.message}"
        }
    }

    fun vibrateDevice(durationMs: Long = 500): String {
        return try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            "Vibrated for ${durationMs}ms."
        } catch (e: Exception) {
            Log.e(TAG, "vibrateDevice failed", e)
            "Vibration failed."
        }
    }

    fun launchApp(packageName: String): String {
        if (packageName.isBlank()) return "No package name provided."
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return "App $packageName not found."
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Launched $packageName."
        } catch (e: Exception) {
            Log.e(TAG, "launchApp failed", e)
            "Failed to launch $packageName."
        }
    }

    fun takePhoto(): String {
        if (!permissions.checkExplicit("take_photo")) return "Permission denied. Grant camera access in Settings."
        return try {
            val dir = File(context.getExternalFilesDir(null), "Cypher/photos")
            dir.mkdirs()
            val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                ))
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Camera opened. Photo saved to ${file.absolutePath}."
        } catch (e: Exception) {
            Log.e(TAG, "takePhoto failed", e)
            "Failed to open camera: ${e.message}"
        }
    }

    fun setVolume(stream: String = "music", level: Int = 10): String {
        return try {
            val streamId = when (stream.lowercase()) {
                "music", "media" -> android.media.AudioManager.STREAM_MUSIC
                "ring" -> android.media.AudioManager.STREAM_RING
                "notification" -> android.media.AudioManager.STREAM_NOTIFICATION
                "alarm" -> android.media.AudioManager.STREAM_ALARM
                else -> android.media.AudioManager.STREAM_MUSIC
            }
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val max = audio.getStreamMaxVolume(streamId)
            val safeLevel = level.coerceIn(0, max)
            audio.setStreamVolume(streamId, safeLevel, 0)
            "Volume set to $safeLevel/$max for $stream."
        } catch (e: Exception) {
            Log.e(TAG, "setVolume failed", e)
            "Failed to set volume."
        }
    }

    fun toggleFlashlight(on: Boolean): String {
        return try {
            val camera = try {
                @Suppress("DEPRECATION")
                android.hardware.Camera.open()
            } catch (e: Exception) {
                return "Flashlight unavailable: ${e.message}"
            }
            val params = camera.parameters
            params.flashMode = if (on) android.hardware.Camera.Parameters.FLASH_MODE_TORCH
                else android.hardware.Camera.Parameters.FLASH_MODE_OFF
            camera.parameters = params
            camera.startPreview()
            if (!on) camera.stopPreview()
            camera.release()
            "Flashlight turned ${if (on) "on" else "off"}"
        } catch (e: Exception) {
            Log.e(TAG, "toggleFlashlight failed", e)
            "Failed to toggle flashlight."
        }
    }

    fun clipboardRead(): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() ?: "Empty"
            else "Clipboard is empty."
        } catch (e: Exception) {
            Log.e(TAG, "clipboardRead failed", e)
            "Failed to read clipboard."
        }
    }

    fun clipboardWrite(text: String): String {
        if (!permissions.checkExplicit("clipboard_write")) return "Permission denied."
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("cypher", text))
            "Copied to clipboard."
        } catch (e: Exception) {
            Log.e(TAG, "clipboardWrite failed", e)
            "Failed to write to clipboard."
        }
    }

    fun openUrl(url: String): String {
        if (!permissions.checkExplicit("open_url")) return "Permission denied."
        if (url.isBlank()) return "No URL provided."
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened $url."
        } catch (e: Exception) {
            Log.e(TAG, "openUrl failed", e)
            "Failed to open URL."
        }
    }

    fun sendNotification(title: String, content: String): String {
        if (!permissions.checkExplicit("send_notification")) return "Permission denied."
        return try {
            NotificationHelper(context).send(title, content)
            "Notification sent: $title"
        } catch (e: Exception) {
            Log.e(TAG, "sendNotification failed", e)
            "Failed to send notification."
        }
    }

    fun getToolDefinitions(): List<Map<String, Any>> {
        return listOf(
            tool("get_battery_status", "Get battery level and charging status."),
            tool("get_device_time", "Get current date and time."),
            tool("get_storage_status", "Get internal storage space info."),
            tool("list_installed_apps", "List installed applications."),
            tool("read_contacts", "Read phone contacts.", explicit = true),
            tool("get_location", "Get current GPS location.", explicit = true),
            tool("send_sms", "Send an SMS message.", explicit = true, params = mapOf(
                "number" to "string", "message" to "string"
            ), required = listOf("number", "message")),
            tool("make_phone_call", "Make a phone call.", explicit = true, params = mapOf(
                "number" to "string"
            ), required = listOf("number")),
            tool("vibrate_device", "Vibrate the device.", params = mapOf("duration_ms" to "integer")),
            tool("launch_app", "Launch an app by package name.", params = mapOf("package" to "string"), required = listOf("package")),
            tool("take_photo", "Take a photo with the camera.", explicit = true),
            tool("set_volume", "Set volume level.", params = mapOf("stream" to "string", "level" to "integer")),
            tool("toggle_flashlight", "Turn flashlight on/off.", explicit = true, params = mapOf("on" to "boolean")),
            tool("clipboard_read", "Read clipboard content."),
            tool("clipboard_write", "Write to clipboard.", explicit = true, params = mapOf("text" to "string"), required = listOf("text")),
            tool("open_url", "Open a URL in the browser.", explicit = true, params = mapOf("url" to "string"), required = listOf("url")),
            tool("send_notification", "Send a system notification.", explicit = true, params = mapOf("title" to "string", "content" to "string"), required = listOf("title", "content")),
            tool("tts_speak", "Speak text out loud via TTS.", params = mapOf("text" to "string"), required = listOf("text")),
        )
    }

    private fun tool(
        name: String, description: String, explicit: Boolean = false,
        params: Map<String, String> = emptyMap(), required: List<String> = emptyList()
    ): Map<String, Any> {
        val properties = params.entries.associate { (k, v) ->
            k to mapOf("type" to v, "description" to k.replace("_", " "))
        }
        return mapOf(
            "name" to name,
            "description" to description,
            "explicit" to explicit,
            "parameters" to mapOf("type" to "object", "properties" to properties, "required" to required)
        )
    }

    fun executeTool(name: String, args: Map<String, Any?>): String {
        return try {
            when (name) {
                "get_battery_status" -> getBatteryStatus()
                "get_device_time" -> getDeviceTime()
                "get_storage_status" -> getStorageStatus()
                "list_installed_apps" -> listInstalledApps()
                "read_contacts" -> readContacts()
                "get_location" -> getLocation()
                "send_sms" -> sendSms(args["number"]?.toString() ?: "", args["message"]?.toString() ?: "")
                "make_phone_call" -> makePhoneCall(args["number"]?.toString() ?: "")
                "vibrate_device" -> vibrateDevice((args["duration_ms"] as? Number)?.toLong() ?: 500)
                "launch_app" -> launchApp(args["package"]?.toString() ?: "")
                "take_photo" -> takePhoto()
                "set_volume" -> setVolume(args["stream"]?.toString() ?: "music", (args["level"] as? Number)?.toInt() ?: 10)
                "toggle_flashlight" -> toggleFlashlight(args["on"]?.toString()?.toBooleanStrictOrNull() ?: true)
                "clipboard_read" -> clipboardRead()
                "clipboard_write" -> clipboardWrite(args["text"]?.toString() ?: "")
                "open_url" -> openUrl(args["url"]?.toString() ?: "")
                "send_notification" -> sendNotification(args["title"]?.toString() ?: "", args["content"]?.toString() ?: "")
                "tts_speak" -> "OK"
                else -> "Unknown tool: $name"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool $name", e)
            "Failed to execute $name: ${e.message}"
        }
    }
}
