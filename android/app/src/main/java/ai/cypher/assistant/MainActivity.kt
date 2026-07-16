package ai.cypher.assistant

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request all essential permissions on first start (Mic, Calls, Ring Listener, Notifications)
        val permissions = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_PHONE_STATE
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                101
            )
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            // Bind JavaScript interface to allow web UI to call native download
            addJavascriptInterface(WebAppInterface(this@MainActivity), "AndroidInterface")
        }
        
        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }

    class WebAppInterface(private val context: Context) {
        
        @JavascriptInterface
        fun downloadModel(url: String) {
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle("Downloading Cypher AI Model")
                    setDescription("Fetching cypher-1.5b-q4_0.gguf (1.15 GB)")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    // Save to secure app-specific files directory to avoid Scoped Storage/MTP write errors
                    setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_DOWNLOADS,
                        "cypher-1.5b-q4_0.gguf"
                    )
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }

                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.enqueue(request)

                (context as AppCompatActivity).runOnUiThread {
                    Toast.makeText(context, "Model download started in background...", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                (context as AppCompatActivity).runOnUiThread {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
