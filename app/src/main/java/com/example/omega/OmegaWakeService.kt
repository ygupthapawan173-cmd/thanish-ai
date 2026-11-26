package com.example.omega

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/** Explicit foreground microphone session. No hidden recording or automatic action execution. */
class OmegaWakeService : Service(), RecognitionListener {
    companion object {
        private const val CHANNEL = "omega_wake_v1"
        private const val MIC = 7201
        private const val RESULT = 7202
        private const val STOP = "com.example.omega.STOP_WAKE"
        val status = MutableStateFlow("Stopped")
        val active = MutableStateFlow(false)
        val pendingCommand = MutableStateFlow("")
        fun start(c: Context) {
            ContextCompat.startForegroundService(c, Intent(c, OmegaWakeService::class.java))
        }
        fun stop(c: Context) {
            c.stopService(Intent(c, OmegaWakeService::class.java))
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speech: SpeechService? = null
    private var loading = false
    private var closed = false
    private var armed = false
    private val expiry = Runnable {
        armed = false
        if (!closed && active.value) {
            status.value = "Listening for Hey JARVIS"
            refresh()
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            status.value = "Stopped"
            stopSelf()
            return START_NOT_STICKY
        }
        if (loading || speech != null) return START_NOT_STICKY
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            fail("Microphone and notification permissions required")
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "JARVIS offline listening", NotificationManager.IMPORTANCE_LOW))
            status.value = "Loading bundled English model"
            if (Build.VERSION.SDK_INT >= 29) startForeground(MIC, notification(status.value, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(MIC, notification(status.value, true))
            active.value = true
            loading = true
            StorageService.unpack(this, "model-en-us", "omega-vosk-model",
                { loaded ->
                    loading = false
                    if (closed) loaded.close() else try {
                        model = loaded
                        val local = Recognizer(loaded, 16000f)
                        recognizer = local
                        speech = SpeechService(local, 16000f)
                        check(speech?.startListening(this) == true)
                        status.value = "Listening for Hey JARVIS"
                        refresh()
                    } catch (e: Exception) { fail("Offline microphone could not start") }
                    catch (e: LinkageError) { fail("Native library unavailable") }
                },
                { _ -> loading = false; if (!closed) fail("Bundled model could not load") })
        } catch (e: Exception) { fail("Start wake service from the visible app") }
        catch (e: LinkageError) { fail("Native library unavailable") }
        return START_NOT_STICKY
    }
    private fun notification(message: String, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 7203, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 7204, Intent(this, OmegaWakeService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("JARVIS OMEGA").setContentText(message)
            .setContentIntent(open).setOngoing(ongoing).setAutoCancel(!ongoing).setVisibility(Notification.VISIBILITY_PRIVATE)
        if (ongoing) b.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop microphone", stop).build())
        return b.build()
    }
    private fun refresh() {
        if (!closed) runCatching { getSystemService(NotificationManager::class.java).notify(MIC, notification(status.value, true)) }
    }
    private fun fail(message: String) { status.value = message; stopSelf() }
    override fun onPartialResult(hypothesis: String?) = Unit
    override fun onFinalResult(hypothesis: String?) = Unit
    override fun onTimeout() { if (!closed) fail("Listening session ended") }
    override fun onError(exception: Exception?) { if (!closed) fail("Recognition stopped; check microphone") }
    override fun onResult(hypothesis: String?) {
        if (closed || !active.value) return
        val text = runCatching { JSONObject(hypothesis.orEmpty()).optString("text") }.getOrDefault("").trim()
        if (text.isEmpty()) return
        val tail = OmegaPolicy.wakeTail(text)
        if (tail != null) {
            if (tail.isNotEmpty()) deliver(tail) else {
                armed = true
                status.value = "Wake detected — say one English command"
                refresh()
                handler.removeCallbacks(expiry)
                handler.postDelayed(expiry, 15000)
            }
        } else if (armed) deliver(text.take(300))
    }
    private fun deliver(text: String) {
        armed = false
        handler.removeCallbacks(expiry)
        pendingCommand.value = text
        status.value = "Command captured; review in JARVIS"
        runCatching { getSystemService(NotificationManager::class.java).notify(RESULT, notification("Command captured. Tap to review.", false)) }
        stopSelf()
    }
    override fun onDestroy() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        runCatching { speech?.stop() }
        runCatching { speech?.shutdown() }
        speech = null
        runCatching { recognizer?.close() }; recognizer = null
        runCatching { model?.close() }; model = null
        active.value = false
        if (status.value.startsWith("Listening") || status.value.startsWith("Loading") || status.value.startsWith("Wake detected")) status.value = "Stopped"
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

