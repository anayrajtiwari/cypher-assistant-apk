package ai.cypher.assistant

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val GITHUB_API = "https://api.github.com/repos/anayrajtiwari/cypher-assistant-apk/releases/latest"
        private const val APK_FILE = "cypher-update.apk"
    }

    data class ReleaseInfo(
        val version: String,
        val apkUrl: String,
        val publishedAt: String
    )

    fun checkForUpdate(): ReleaseInfo? {
        return try {
            val url = URL(GITHUB_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/json")

            val json = conn.inputStream.bufferedReader().readText()
            val root = org.json.JSONObject(json)
            val tag = root.optString("tag_name", "unknown")
            val published = root.optString("published_at", "")
            val assets = root.optJSONArray("assets")

            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            if (apkUrl == null) {
                Log.w(TAG, "No APK found in latest release")
                return null
            }

            ReleaseInfo(tag, apkUrl, published)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for update", e)
            null
        }
    }

    suspend fun downloadUpdate(url: String): File? = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(context.cacheDir, APK_FILE)
            if (apkFile.exists()) apkFile.delete()

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = true

            val totalBytes = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                    }
                    output.flush()
                }
            }

            if (totalBytes > 0 && apkFile.length() != totalBytes) {
                Log.e(TAG, "Download incomplete: expected $totalBytes, got ${apkFile.length()}")
                apkFile.delete()
                return@withContext null
            }

            Log.i(TAG, "APK downloaded to ${apkFile.absolutePath} (${apkFile.length() / 1024}KB)")
            apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            null
        }
    }

    fun installApk(apkFile: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                installSession(apkFile)
            } else {
                installLegacy(apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            false
        }
    }

    private fun installSession(apkFile: File): Boolean {
        val pm = context.packageManager
        val installer = pm.packageInstaller

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            context.contentResolver.openInputStream(uri)?.use { input ->
                session.openWrite("cypher", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                action = "android.intent.action.VIEW"
                putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            session.commit(pendingIntent.intentSender)
            Log.i(TAG, "Install session $sessionId committed")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Session install failed", e)
            session.abandon()
            return false
        } finally {
            session.close()
        }
    }

    @Suppress("DEPRECATION")
    private fun installLegacy(apkFile: File): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile),
                "application/vnd.android.package-archive"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        return true
    }

    fun getCurrentVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    fun cleanup() {
        try {
            val apkFile = File(context.cacheDir, APK_FILE)
            if (apkFile.exists()) apkFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }
}
