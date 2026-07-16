package ai.cypher.assistant

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    private val llmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val type = intent?.getStringExtra("type") ?: return
            val data = intent.getStringExtra("data") ?: ""
            val escapedData = data.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")
            
            webView.post {
                webView.evaluateJavascript("if(window.onNativeLLMEvent) window.onNativeLLMEvent('$type', '$escapedData');", null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestPermissions()
        startCypherService()

        ContextCompat.registerReceiver(
            this,
            llmReceiver,
            IntentFilter("ai.cypher.assistant.LLM_EVENT"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            addJavascriptInterface(WebAppInterface(this@MainActivity), "AndroidInterface")
        }
        
        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }

    private fun startCypherService() {
        val serviceIntent = Intent(this, CypherBackgroundService::class.java)
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                101
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startCypherService()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(llmReceiver)
        } catch (e: Exception) {}
        super.onDestroy()
    }

    class WebAppInterface(private val context: Context) {
        
        @JavascriptInterface
        fun sendPromptToCypher(prompt: String) {
            val service = CypherBackgroundService.instance
            if (service != null) {
                service.generateResponse(prompt)
            } else {
                (context as AppCompatActivity).runOnUiThread {
                    Toast.makeText(context, "Initializing Cypher local engine...", Toast.LENGTH_SHORT).show()
                }
                val serviceIntent = Intent(context, CypherBackgroundService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }

        @JavascriptInterface
        fun retryLoadModel() {
            CypherBackgroundService.instance?.initLlamaEngine()
        }

        @JavascriptInterface
        fun downloadModel(url: String) {
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle("Downloading Cypher AI Model")
                    setDescription("Fetching cypher-1.5b-q4_0.gguf (1.15 GB)")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(
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
