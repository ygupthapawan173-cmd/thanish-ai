package com.example.omega

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
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
        val recognitionReady = MutableStateFlow(false)
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
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    // Recovery is bounded and only active while the user-started foreground service remains alive.
    private var recognitionRestarts = 0
    private var reconnectPending = false
    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext) { result ->
            if (!closed && result == TextToSpeech.SUCCESS) {
                ttsReady = true
                runCatching { tts?.language = Locale.US }
            }
        }
    }
    private fun acknowledgeWake() {
        val style = OmegaVoicePreferences.acknowledgement(this)
        val text = OmegaWakeAck.spokenText(style, OmegaVoicePreferences.customAcknowledgement(this))
        if (style == WakeAckStyle.CHIME) {
            runCatching {
                val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 65)
                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                handler.postDelayed({ tone.release() }, 300)
            }
        } else if (ttsReady && text != null) {
            runCatching {
                val wanted = if (OmegaWakeAck.isTelugu(text)) Locale("te", "IN") else Locale.US
                val supported = (tts?.isLanguageAvailable(wanted) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= 0
                val selected = if (supported) wanted else Locale.US
                if (!supported) lastError.value = "Telugu TTS voice unavailable; English acknowledgement used"
                tts?.language = selected
                val line = if (supported) text else "Yes, Sir!"
                val spoken = tts?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "omega_wake_ack")
                if (spoken == TextToSpeech.ERROR) lastError.value = "Wake TTS failed; microphone still active"
            }
        }
    }
    /** One gate for BOTH partial recognition and combined wake+command finals. */
    private fun armWake(speakAck: Boolean = true): Boolean {
        if (closed || armed || !wakeGate.allow(SystemClock.elapsedRealtime())) return false
        lastWakeAt.value = System.currentTimeMillis()
        armed = true
        lastError.value = ""
        status.value = "Wake detected — say one English command"
        refresh()
        if (speakAck) acknowledgeWake()
        handler.removeCallbacks(expiry)
        handler.postDelayed(expiry, 15000L)
        return true
    }
    private val expiry = Runnable {
        armed = false
        if (!closed && active.value) {
            status.value = "Listening for " + OmegaVoicePreferences.wakePhrase(this).label
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
                        recognitionReady.value = true
                        status.value = "Listening for " + OmegaVoicePreferences.wakePhrase(this).label
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
        recognitionReady.value = false
        status.value = message
        stopSelf()
    }
    override fun onPartialResult(hypothesis: String?) {
        if (closed || !active.value) return
        val partial = runCatching { JSONObject(hypothesis.orEmpty()).optString("partial") }
            .getOrDefault("").trim().take(300)
        if (partial.isEmpty()) return
        lastPartial.value = partial
        recognitionRestarts = 0
        // A partial hypothesis may wake the assistant, but only a final result commits a command.
        if (!armed && OmegaWakeEngine.match(partial, OmegaVoicePreferences.activeWakePhrases(this),
                OmegaVoicePreferences.sensitivity(this)) != null) armWake()
    }
    override fun onFinalResult(hypothesis: String?) = onResult(hypothesis)
    private fun recoverOfflineRecognition(reason: String) {
        handler.post {
            if (closed || !active.value || reconnectPending) return@post
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                fail("Microphone permission revoked; stop offline listening")
                return@post
            }
            if (recognitionRestarts >= 2) {
                fail("Offline recognition failed after two reconnect attempts: " + reason)
                return@post
            }
            recognitionRestarts++
            reconnectPending = true
            recognitionReady.value = false
            status.value = "Reconnecting offline recognition (" + recognitionRestarts + "/2)"
            refresh()
            runCatching { speech?.stop() }
            handler.postDelayed({
                if (closed || !active.value) return@postDelayed
                reconnectPending = false
                val resumed = runCatching { speech?.startListening(this) == true }.getOrDefault(false)
                if (resumed) {
                    recognitionReady.value = true
                    status.value = "Listening for " + OmegaVoicePreferences.wakePhrase(this).label
                    refresh()
                } else {
                    fail("Offline voice engine could not restart")
                }
            }, 350L)
        }
    }
    override fun onTimeout() {
        if (!closed) recoverOfflineRecognition("timeout")
    }
    override fun onError(exception: Exception?) {
        if (!closed) recoverOfflineRecognition("recognizer error")
    }
    override fun onResult(hypothesis: String?) {
        if (closed || !active.value) return
        val text = runCatching { JSONObject(hypothesis.orEmpty()).optString("text") }.getOrDefault("").trim()
        if (text.isEmpty()) return
        if (armed && OmegaWakeAck.isEcho(text, OmegaWakeAck.spokenText(
                OmegaVoicePreferences.acknowledgement(this),
                OmegaVoicePreferences.customAcknowledgement(this)))) return
        val match = OmegaWakeEngine.match(text, OmegaVoicePreferences.activeWakePhrases(this),
            OmegaVoicePreferences.sensitivity(this))
        if (match != null) {
            if (match.tail.isNotBlank()) {
                if (armed || armWake(speakAck = false)) deliver(match.tail)
            } else if (!armed) armWake()
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
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ttsReady = false
        runCatching { speech?.stop() }
        runCatching { speech?.shutdown() }
        speech = null
        runCatching { recognizer?.close() }; recognizer = null
        runCatching { model?.close() }; model = null
        active.value = false
        recognitionReady.value = false
        reconnectPending = false
        if (status.value.startsWith("Listening") || status.value.startsWith("Loading") || status.value.startsWith("Wake detected")) status.value = "Stopped"
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

