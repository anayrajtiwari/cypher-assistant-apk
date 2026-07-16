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
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cypher Foreground Service (FGS)
 * Targets API 33 (Android 13) to allow microphone recording while running in the background.
 * Optimized for low idle CPU/battery consumption during standby.
 */
class CypherBackgroundService : Service() {

    private val isRunning = AtomicBoolean(false)
    private var recordingThread: Thread? = null

    companion object {
        private const val CHANNEL_ID = "cypher_channel"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 16000
    }

    private var llamaHelper: org.nehuatl.llamacpp.LlamaHelper? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        initLlama()
        startHotwordDetection()
    }

    private fun initLlama() {
        try {
            val sharedFlow = kotlinx.coroutines.flow.MutableSharedFlow<org.nehuatl.llamacpp.LlamaHelper.LLMEvent>(extraBufferCapacity = 10)
            llamaHelper = org.nehuatl.llamacpp.LlamaHelper(
                contentResolver = contentResolver,
                sharedFlow = sharedFlow
            )
            
            // Start collecting events in a background coroutine
            kotlinx.coroutines.GlobalScope.launch {
                sharedFlow.collect { event ->
                    when (event) {
                        is org.nehuatl.llamacpp.LlamaHelper.LLMEvent.Loaded -> 
                            android.util.Log.i("CypherLLM", "Model Loaded: ${event.path}")
                        is org.nehuatl.llamacpp.LlamaHelper.LLMEvent.Error -> 
                            android.util.Log.e("CypherLLM", "Model Error: ${event.message}")
                        is org.nehuatl.llamacpp.LlamaHelper.LLMEvent.Ongoing -> 
                            android.util.Log.i("CypherLLM", "Token: ${event.word}")
                        else -> {}
                    }
                }
            }

            // Must use file URI
            llamaHelper?.load(
                path = "file:///sdcard/Download/cypher-1.5b-q4_0.gguf",
                contextLength = 2048
            ) { contextId ->
                android.util.Log.i("CypherLLM", "GGUF model loaded successfully. Context ID: $contextId")
            }
        } catch (e: Exception) {
            android.util.Log.e("CypherLLM", "Failed to init GGUF model", e)
        }
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cypher OS Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Cypher background audio listener & agent service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        // Open app when clicking the notification
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cypher OS Active")
            .setContentText("Listening for 'zed_one_eight' wake command...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
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
                        // Low battery drain idle loop: Read audio blocks
                        val read = record.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            // [Placeholder] Feed PCM buffer into low-power wake-word engine (e.g. OpenWakeWord / Porcupine)
                            // Detect phrase: "zed_one_eight" / "zee_one_eight"
                            
                            // Sleep briefly to prevent CPU thread thrashing and conserve battery
                            Thread.sleep(100)
                        }
                    }
                    record.stop()
                    record.release()
                }
            } catch (e: SecurityException) {
                // Handle permission not granted
            } catch (e: Exception) {
                // Log other exceptions
            }
        }.apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.set(false)
        recordingThread?.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
