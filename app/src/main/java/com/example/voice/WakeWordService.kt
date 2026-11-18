package com.example.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voxrt.sdk.wakeword.VoxrtWakeWordEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * User-started, on-device wake phrase listener.
 * Android requires this microphone foreground service to be started while the app is visible
 * and RECORD_AUDIO permission is already granted.
 */
class WakeWordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenJob: Job? = null
    @Volatile private var stopping = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopping = true
                listenJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                if (listenJob?.isActive != true) {
                    stopping = false
                    listenJob = serviceScope.launch { listenForWakeWord() }
                }
            }
        }
        return START_STICKY
    }

    private suspend fun listenForWakeWord() {
        var recorder: AudioRecord? = null
        var engine: VoxrtWakeWordEngine? = null
        try {
            val model = ensureModel()
            engine = VoxrtWakeWordEngine.fromBytes(model)
            engine.setThreshold(0.90f)
            engine.setCooldownFrames(200)

            val sampleRate = 16_000
            val blockSamples = 1_600
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBuffer > 0) { "AudioRecord buffer unavailable" }

            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, blockSamples * 4)
            )
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed" }
            recorder.startRecording()
            val pcm = ShortArray(blockSamples)

            while (!stopping && !Thread.currentThread().isInterrupted) {
                val count = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) continue
                val input = if (count == pcm.size) pcm else pcm.copyOf(count)
                val detections = engine.processPcm(input)
                if (detections.isNotEmpty()) {
                    sendBroadcast(Intent(ACTION_WAKE_DETECTED).setPackage(packageName))
                    Log.i(TAG, "Wake phrase detected: score=${detections.last().score}")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Wake word listener stopped", error)
            sendBroadcast(Intent(ACTION_WAKE_ERROR).setPackage(packageName).putExtra(EXTRA_ERROR, error.message))
        } finally {
            try { recorder?.stop() } catch (_: Exception) { }
            recorder?.release()
            engine?.close()
            if (!stopping) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun ensureModel(): ByteArray {
        val file = java.io.File(filesDir, MODEL_FILE)
        if (file.exists()) {
            val cached = file.readBytes()
            if (sha256(cached) == MODEL_SHA256) return cached
            file.delete()
        }
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        val bytes = try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
        check(sha256(bytes) == MODEL_SHA256) { "Wake-word model checksum mismatch" }
        file.writeBytes(bytes)
        return bytes
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZENO wake word",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Microphone is active for the configured wake phrase" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ZENO is listening")
            .setContentText("Wake phrase engine currently listens for “Hey Assistant”")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopping = true
        listenJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ZenoWakeWord"
        private const val CHANNEL_ID = "zeno_wake_word"
        private const val NOTIFICATION_ID = 7401
        private const val MODEL_FILE = "voxrt_wake_word.vxrt"
        private const val MODEL_URL =
            "https://github.com/VoxRT/voxrt-wake-word-models/releases/download/v0.1.0/voxrt_wake_word.vxrt"
        private const val MODEL_SHA256 =
            "9d40bdc132a2ad8e85bd8a28bb49b77c51a7c62f60567222a037e44418510e8f"

        const val ACTION_START = "com.example.voice.action.START_WAKE_WORD"
        const val ACTION_STOP = "com.example.voice.action.STOP_WAKE_WORD"
        const val ACTION_WAKE_DETECTED = "com.example.voice.action.WAKE_DETECTED"
        const val ACTION_WAKE_ERROR = "com.example.voice.action.WAKE_ERROR"
        const val EXTRA_ERROR = "error"
    }
}
