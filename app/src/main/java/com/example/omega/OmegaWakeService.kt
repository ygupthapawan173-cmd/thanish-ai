package com.example.omega

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale
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
        val lastPartial = MutableStateFlow("")
        val lastError = MutableStateFlow("")
        val lastWakeAt = MutableStateFlow(0L)
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
    private val wakeGate = WakeGate(1600L)
    private var delivering = false
    private var lastAck: String? = null
    private var tone: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext) { result ->
            if (!closed && result == TextToSpeech.SUCCESS) {
                ttsReady = true
                runCatching { tts?.language = Locale.US }
            }
        }
    }
    private fun matchWake(text: String): WakeMatch? =
        OmegaWakeEngine.match(
            text, OmegaVoicePreferences.activeWakePhrases(this),
            OmegaVoicePreferences.sensitivity(this)
        )

    private fun acknowledgeWake() {
        val style = OmegaVoicePreferences.ackStyle(this)
        val text = OmegaWakeAck.spokenText(style, OmegaVoicePreferences.customAck(this))
        lastAck = text
        if (style == WakeAckStyle.CHIME) {
            runCatching {
                tone?.release()
                val chime = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
                tone = chime
                chime.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                handler.postDelayed({
                    if (tone === chime) tone = null
                    chime.release()
                }, 200L)
            }
        } else if (ttsReady && !text.isNullOrBlank()) {
            runCatching {
                val locale = if (OmegaWakeAck.isTelugu(text)) Locale.forLanguageTag("te-IN")
                    else Locale.US
                val available = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (available >= TextToSpeech.LANG_AVAILABLE) {
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "omega_wake_ack")
                }
            }
        }
    }

    private fun armWake() {
        if (closed || armed || delivering) return
        val now = SystemClock.elapsedRealtime()
        if (!wakeGate.allow(now)) return
        lastWakeAt.value = System.currentTimeMillis()
        armed = true
        lastError.value = ""
        status.value = "Wake detected — say one English command"
        refresh()
        acknowledgeWake()
        handler.removeCallbacks(expiry)
        handler.postDelayed(expiry, 15000)
    }
    private val expiry = Runnable {
        armed = false
        if (!closed && active.value) {
            status.value = "Listening for " + OmegaVoicePreferences.activeWakeLabel(this)
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
        lastError.value = ""
        lastPartial.value = ""
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
                        status.value = "Listening for " + OmegaVoicePreferences.activeWakeLabel(this)
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
    private fun fail(message: String) {
        lastError.value = message
        status.value = message
        stopSelf()
    }
    override fun onPartialResult(hypothesis: String?) {
        if (closed || !active.value) return
        val partial = runCatching { JSONObject(hypothesis.orEmpty()).optString("partial") }
            .getOrDefault("").trim().take(300)
        if (partial.isEmpty()) return
        lastPartial.value = partial
        // A partial hypothesis may wake the assistant, but only a final result commits a command.
        if (!armed && matchWake(partial) != null) armWake()
    }
    override fun onFinalResult(hypothesis: String?) = onResult(hypothesis)
    override fun onTimeout() { if (!closed) fail("Offline recognition timed out; start listening again") }
    override fun onError(exception: Exception?) {
        if (!closed) fail("Offline recognition stopped; check microphone permission and model")
    }
    override fun onResult(hypothesis: String?) {
        if (closed || !active.value) return
        val text = runCatching { JSONObject(hypothesis.orEmpty()).optString("text") }.getOrDefault("").trim()
        if (text.isEmpty()) return
        if (delivering || (armed && OmegaWakeAck.isEcho(text, lastAck))) return
        val matched = matchWake(text)
        if (matched != null) {
            if (matched.tail.isNotEmpty()) {
                // Partial-triggered wake already holds the gate for this utterance.
                // For an unsignalled final wake+command, take the gate once.
                if (!armed && !wakeGate.allow(SystemClock.elapsedRealtime())) return
                lastWakeAt.value = System.currentTimeMillis()
                deliver(matched.tail)
            } else if (!armed) armWake()
        } else if (armed) deliver(text.take(300))
    }
    private fun deliver(text: String) {
        if (closed || delivering || text.isBlank()) return
        delivering = true
        armed = false
        lastAck = null
        handler.removeCallbacks(expiry)
        pendingCommand.value = text
        status.value = "Command captured; review in JARVIS"
        runCatching { getSystemService(NotificationManager::class.java).notify(RESULT, notification("Command captured. Tap to review.", false)) }
        stopSelf()
    }
    override fun onDestroy() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop(); tts?.shutdown() }
        runCatching { tone?.release() }
        tone = null
        tts = null
        ttsReady = false
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

