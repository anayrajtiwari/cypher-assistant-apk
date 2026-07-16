package ai.cypher.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.nehuatl.llamacpp.LlamaAndroid
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class CypherBackgroundService : Service() {

    private val isRunning = AtomicBoolean(false)
    private var recordingThread: Thread? = null
    private var llamaAndroid: LlamaAndroid? = null
    private var llamaContextId: Int = -1
    private var isModelLoaded = false

    companion object {
        private const val CHANNEL_ID = "cypher_channel"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 16000
        var instance: CypherBackgroundService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundService()
        initLlamaEngine()
        startHotwordDetection()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cypher OS Agent Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Cypher local AI engine active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cypher OS AI Engaged")
            .setContentText("Offline GGUF Engine Active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun findModelFile(): File? {
        val candidates = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "cypher-1.5b-q4_0.gguf"),
            File("/sdcard/Download/cypher-1.5b-q4_0.gguf"),
            File("/storage/emulated/0/Download/cypher-1.5b-q4_0.gguf"),
            File(getExternalFilesDir(null), "cypher-1.5b-q4_0.gguf"),
            File(filesDir, "cypher-1.5b-q4_0.gguf")
        )
        for (f in candidates) {
            if (f.exists() && f.length() > 50000000L) { // >50MB check
                Log.i("CypherLLM", "Found GGUF model file at: ${f.absolutePath} (${f.length()} bytes)")
                return f
            }
        }
        return null
    }

    fun initLlamaEngine() {
        if (isModelLoaded) return

        GlobalScope.launch(Dispatchers.IO) {
            val modelFile = findModelFile()
            if (modelFile == null) {
                Log.e("CypherLLM", "No valid GGUF model file found in Download directories")
                notifyUI("STATUS_UPDATE", "Model file missing in Downloads!")
                return@launch
            }

            try {
                notifyUI("STATUS_UPDATE", "Loading local GGUF model into memory...")
                val pfd = ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val fd = pfd.detachFd()

                val engine = LlamaAndroid(contentResolver)
                llamaAndroid = engine

                val config = mapOf<String, Any>(
                    "model" to modelFile.absolutePath,
                    "model_fd" to fd,
                    "use_mmap" to true,
                    "use_mlock" to false,
                    "n_ctx" to 2048,
                    "n_batch" to 512,
                    "n_threads" to 4,
                    "n_gpu_layers" to 0
                )

                val res = engine.startEngine(config) { token ->
                    notifyUI("STREAM_TOKEN", token)
                }

                if (res != null && res.containsKey("contextId")) {
                    llamaContextId = (res["contextId"] as Number).toInt()
                    isModelLoaded = true
                    Log.i("CypherLLM", "GGUF Model initialized successfully! Context ID: $llamaContextId")
                    notifyUI("STATUS_UPDATE", "SYSTEM ONLINE // MODEL LOADED")
                } else {
                    Log.e("CypherLLM", "startEngine returned null context")
                    notifyUI("STATUS_UPDATE", "Engine Initialization Failed")
                }
            } catch (e: Exception) {
                Log.e("CypherLLM", "Error initializing GGUF engine", e)
                notifyUI("STATUS_UPDATE", "LLM Error: ${e.message}")
            }
        }
    }

    fun generateResponse(prompt: String) {
        if (!isModelLoaded || llamaContextId == -1) {
            notifyUI("STATUS_UPDATE", "Model not loaded. Attempting load...")
            initLlamaEngine()
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                notifyUI("STREAM_START", "")
                val formattedPrompt = "<|user|>\n$prompt<|end|>\n<|assistant|>\n"
                val params = mapOf<String, Any>(
                    "prompt" to formattedPrompt,
                    "emit_partial_completion" to true,
                    "temperature" to 0.7,
                    "n_predict" to 256
                )
                llamaAndroid?.launchCompletion(llamaContextId, params)
                notifyUI("STREAM_END", "")
            } catch (e: Exception) {
                Log.e("CypherLLM", "Completion error", e)
                notifyUI("STREAM_TOKEN", " [Error generating response]")
                notifyUI("STREAM_END", "")
            }
        }
    }

    private fun notifyUI(type: String, data: String) {
        val intent = Intent("ai.cypher.assistant.LLM_EVENT").apply {
            putExtra("type", type)
            putExtra("data", data)
        }
        sendBroadcast(intent)
    }

    private fun startHotwordDetection() {
        if (isRunning.getAndSet(true)) return

        recordingThread = Thread {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            try {
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    record.startRecording()
                    val buffer = ShortArray(bufferSize)

                    while (isRunning.get()) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            Thread.sleep(100)
                        }
                    }
                    record.stop()
                    record.release()
                }
            } catch (e: Exception) {
                Log.e("CypherService", "Hotword error", e)
            }
        }.apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isModelLoaded) {
            initLlamaEngine()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.set(false)
        recordingThread?.interrupt()
        if (llamaContextId != -1) {
            llamaAndroid?.releaseContext(llamaContextId)
        }
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
