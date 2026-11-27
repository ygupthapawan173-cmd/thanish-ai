package com.example.omega

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.ai.PanduAiRepository
import com.example.control.AgentHudBus
import com.example.control.AgentHudState
import com.example.control.DeviceActionExecutor
import com.example.data.AppDatabase
import com.example.data.OmegaPreferences
import com.example.voice.AudioCapturePolicy
import com.example.voice.BackgroundVoicePolicy
import com.example.voice.HandsFreeWakePolicy
import com.example.voice.OwnerModelRecoveryTracker
import com.example.voice.OwnerSpeaker
import com.example.voice.SpeakerProfilePolicy
import com.example.voice.TeluguTtsController
import com.example.voice.TeluguVoiceReplies
import com.example.voice.VoiceInputController
import com.example.voice.VoiceMic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/** Explicitly started, bounded online conversation; no boot restart, local keyword only while locked, no autonomous tools. */
class OnlineBackgroundVoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: OmegaPreferences
    private lateinit var db: AppDatabase
    private lateinit var repository: PanduAiRepository
    private lateinit var input: VoiceInputController
    private lateinit var tts: TeluguTtsController
    private lateinit var ownerSpeaker: OwnerSpeaker
    private val ownerModelRecovery = OwnerModelRecoveryTracker()
    private var started = false
    private var wakeMode = false
    private var closed = false
    private val main = Handler(Looper.getMainLooper())
    private val wakeLease = Any()
    private val fastWake by lazy {
        FastWakeDetector(
            lease = VoiceMic.lease,
            leaseOwner = wakeLease,
            sensitivityProvider = { prefs.settings.value.wakeSensitivity },
            ownerFilterEnabled = { ::ownerSpeaker.isInitialized && ownerSpeaker.enabled && ownerSpeaker.enrolled },
            ownerVerify = { json -> ownerSpeaker.verify(json) },
            minOwnerFrames = { SpeakerProfilePolicy.minFrames }
        )
    }
    private var wakeModel: Model? = null
    private var wakeRecognizer: Recognizer? = null
    private var wakeSpeech: SpeechService? = null
    private var wakeGeneration = 0L
    private var retryAt = 0L
    private var wakeFailures = 0
    private var sessionDeadline = 0L
    private var ackDeadline = 0L

    private fun foregroundBusy() = HandsFreeWakePolicy.foregroundBusy(
        AgentHudBus.isAppInForeground.value, AgentHudBus.isMainMicBusy.value, AgentHudBus.isTtsBusy.value,
        (AgentHudBus.state.value as? AgentHudState.Active)?.isThinking == true
    )

    private fun screenReady() = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isDeviceLocked

    private fun wakeAllowed() = started && !closed && !prefs.settings.value.micPrivacyMuted &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
        getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    private fun lockedWakeCue() {
        val tone = runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 60) }.getOrNull() ?: return
        if (!runCatching { tone.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 180) }.getOrDefault(false)) tone.release()
        else main.postDelayed({ runCatching { tone.release() } }, 250L)
    }

    private fun resumeAfterForeground() {
        if (!started || !wakeMode || phase != "Wake idle") return
        retryAt = SystemClock.elapsedRealtime() + 150L
    }

    private fun releaseWake() {
        ++wakeGeneration
        val old = wakeSpeech; wakeSpeech = null
        val oldRec = wakeRecognizer; wakeRecognizer = null
        val cleanedBound = fastWake.stopAndReleaseMic {
            VoiceMic.lease.release(wakeLease)
        }
        if (!cleanedBound) {
            runCatching { old?.cancel() }; runCatching { old?.shutdown() }
            runCatching { oldRec?.close() }
            if (VoiceMic.lease.owns(wakeLease)) VoiceMic.lease.release(wakeLease)
        }
    }

    private fun returnToWake(message: String) {
        phase = "Wake idle"; sessionDeadline = 0L; ackDeadline = 0L
        responseJob?.cancel(); responseJob = null
        input.stopListening(); tts.stop(); releaseWake()
        turns = 0; history.clear()
        retryAt = SystemClock.elapsedRealtime() + 450L
        publish(message)
    }

    private fun endTurnSession(message: String) {
        if (wakeMode && wakeAllowed()) returnToWake(message + " — rearming Hey JARVIS") else finish(message)
    }

    private fun yieldForeground() {
        if (!started || !wakeMode) return
        if (phase == "Wake loading") return
        returnToWake("Wake paused for foreground voice")
        retryAt = SystemClock.elapsedRealtime() + 1200L
    }

    private fun ensureOwnerSpeakerModelLoaded() {
        if (!::ownerSpeaker.isInitialized || !ownerSpeaker.enabled || !ownerSpeaker.enrolled) return
        if (ownerSpeaker.model != null) {
            ownerModelRecovery.onLoadSuccess()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!ownerModelRecovery.beginLoad(now)) return
        scope.launch(Dispatchers.IO) {
            val loaded = runCatching { ownerSpeaker.load(); ownerSpeaker.model != null }.getOrDefault(false)
            main.post {
                if (closed || !started) return@post
                if (loaded) {
                    ownerModelRecovery.onLoadSuccess()
                    retryAt = 0L
                } else {
                    val failState = ownerModelRecovery.onLoadFailure(SystemClock.elapsedRealtime())
                    if (failState == OwnerModelRecoveryTracker.LoadState.FAILED_BOUNDED) {
                        retryAt = Long.MAX_VALUE
                        publish("Owner speaker model unavailable — wake blocked")
                    } else {
                        retryAt = ownerModelRecovery.nextRetryAtMs
                        publish("Owner speaker model retry ${ownerModelRecovery.attempts}/${OwnerModelRecoveryTracker.MAX_LOAD_ATTEMPTS}")
                    }
                }
            }
        }
    }

    private fun loadWake() {
        ownerModelRecovery.reset()
        publish("Loading Hey JARVIS detector")
        ensureOwnerSpeakerModelLoaded()
        StorageService.unpack(this, "model-en-us", "omega-vosk-en", { model ->
            main.post { if (closed || !started) model.close() else { wakeModel = model; phase = "Wake idle"; retryAt = 0L } }
        }, { _ -> main.post { if (started && !closed) finish("Hey JARVIS model unavailable") } })
    }

    private fun armWake() {
        val model = wakeModel ?: return
        if (!wakeAllowed() || foregroundBusy()) return
        if (!VoiceMic.lease.acquire(wakeLease)) {
            retryAt = SystemClock.elapsedRealtime() + 200L
            if (_status.value != "Wake waiting for microphone release") publish("Wake waiting for microphone release")
            return
        }
        try {
            val ownerFilterActive = ::ownerSpeaker.isInitialized && ownerSpeaker.enabled && ownerSpeaker.enrolled
            if (!ownerModelRecovery.canArmWake(ownerFilterActive, ownerSpeaker.model != null)) {
                releaseWake()
                if (ownerModelRecovery.state == OwnerModelRecoveryTracker.LoadState.FAILED_BOUNDED) {
                    retryAt = Long.MAX_VALUE
                    publish("Owner speaker model unavailable — wake blocked")
                } else {
                    ensureOwnerSpeakerModelLoaded()
                    retryAt = maxOf(ownerModelRecovery.nextRetryAtMs, SystemClock.elapsedRealtime() + 750L)
                    publish("Owner speaker model loading — retrying wake")
                }
                return
            }
            val recognizer = Recognizer(model, 16000f, JSONArray(HandsFreeWakePolicy.phrases + "[unk]").toString())
            wakeRecognizer = recognizer; recognizer.setWords(true)
            if (ownerFilterActive) {
                recognizer.setSpeakerModel(ownerSpeaker.model ?: run { releaseWake(); return })
            }
            val speech = SpeechService(recognizer, 16000f); wakeSpeech = speech
            fastWake.bindSession(object : FastWakeDetector.Session {
                override fun cancel() { speech.cancel() }
                override fun shutdown() { speech.shutdown() }
                override fun closeRecognizer() { recognizer.close() }
            })
            val token = ++wakeGeneration
            fun current() = started && !closed && wakeMode && token == wakeGeneration && phase == "Wake idle"
            fun failed() {
                if (!current()) return
                releaseWake(); wakeFailures++
                retryAt = SystemClock.elapsedRealtime() + HandsFreeWakePolicy.retryDelay(wakeFailures)
                publish("Wake microphone retry — keep permission enabled")
            }
            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) = Unit
                override fun onResult(hypothesis: String?) {
                    if (!current() || !wakeAllowed() || foregroundBusy()) return
                    val json = runCatching { JSONObject(hypothesis.orEmpty()) }.getOrNull() ?: return
                    val words = json.optJSONArray("result") ?: return
                    val confidence = (0 until words.length()).map { words.optJSONObject(it)?.optDouble("conf", Double.NaN) ?: Double.NaN }
                    if (!HandsFreeWakePolicy.accept(json.optString("text"), confidence, AudioCapturePolicy.wakeThreshold(prefs.settings.value.wakeSensitivity))) return
                    if (!fastWake.evaluateResult(hypothesis.orEmpty(), isPartial = false)) return
                    releaseWake(); wakeFailures = 0
                    if (!screenReady()) {
                        // Local keyword only while locked. No cloud audio, AI requests or device actions.
                        retryAt = SystemClock.elapsedRealtime() + 2500L
                        publish("Hey JARVIS detected — unlock to speak")
                        lockedWakeCue()
                    } else if (AgentHudBus.isAppInForeground.value) {
                        retryAt = SystemClock.elapsedRealtime() + 5000L
                        publish("Hey JARVIS — foreground voice")
                        AgentHudBus.emitWakeTrigger("")
                    } else {
                        turns = 0; history.clear(); sessionDeadline = SystemClock.elapsedRealtime() + 180_000L
                        if (prefs.settings.value.autoSpeak) {
                            phase = "Acknowledging"; ackDeadline = SystemClock.elapsedRealtime() + 4000L
                            publish("Hey JARVIS — ready")
                            val s = prefs.settings.value
                            val ackText = s.customWakeAck.trim().ifEmpty { TeluguVoiceReplies.wakeAck(s.languageTag) }
                            tts.configure(TeluguVoiceReplies.wakeAckLocale(s.languageTag), s.ttsSpeed, s.ttsPitch, s.ttsStyle)
                            tts.speak(ackText)
                        } else listen()
                    }
                }
                override fun onFinalResult(hypothesis: String?) = onResult(hypothesis)
                override fun onError(exception: Exception?) = failed()
                override fun onTimeout() = failed()
            }
            if (!speech.startListening(listener)) { failed(); return }
            publish(if (screenReady()) "Listening for Hey JARVIS — offline detector" else "Listening for Hey JARVIS — lock screen, local only")
        } catch (_: Exception) {
            releaseWake(); wakeFailures++; retryAt = SystemClock.elapsedRealtime() + HandsFreeWakePolicy.retryDelay(wakeFailures)
            publish("Wake microphone retry")
        } catch (_: LinkageError) { finish("Wake detector unavailable") }
    }

    private var phase = "Idle"
    private var turns = 0
    private var responseJob: Job? = null
    private val history = mutableListOf<Pair<String,String>>()
    override fun onBind(intent: Intent?): IBinder? = null
    private fun allowed(): Boolean = wakeAllowed() && screenReady()

    override fun onCreate() {
        super.onCreate(); instance = this
        prefs = OmegaPreferences(this)
        db = AppDatabase(this)
        repository = PanduAiRepository({ prefs.settings.value }, DeviceActionExecutor(this, db, prefs), db)
        input = VoiceInputController(this, owner = "background-capture") { text -> accept(text) }
        ownerSpeaker = OwnerSpeaker(this)
        tts = TeluguTtsController(this, backgroundOwned = true, cloudSettings = { prefs.settings.value }, backgroundCloudAllowed = { phase == "Speaking" && allowed() || phase == "Acknowledging" && allowed() })
        tts.setOnSpeechFinished {
            if (phase == "Acknowledging" && allowed()) { ackDeadline = 0L; listen() }
            else if (phase == "Speaking" && allowed() && tts.lastSpeechSucceeded && BackgroundVoicePolicy.followUp(prefs.settings.value.continuousConversation, turns)) listen()
            else if (phase == "Speaking") endTurnSession("Session complete")
        }
        scope.launch { input.state.collect { if (started && phase == "Listening" && it.errorMessage != null) endTurnSession("Voice ended: ${it.errorMessage}") } }
        scope.launch { AgentHudBus.abortSignal.collect { if (started) finish("Stopped") } }
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Online voice session", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != START && intent?.action != START_WAKE) { finish("Stopped"); return START_NOT_STICKY }
        if (started) return START_NOT_STICKY
        if (!AgentHudBus.isAppInForeground.value || AgentHudBus.isMainMicBusy.value || AgentHudBus.isTtsBusy.value) { finish("Start from JARVIS when the microphone is idle"); return START_NOT_STICKY }
        wakeMode = intent?.action == START_WAKE
        started = true
        if (!allowed()) { finish("Microphone, notification permission and unlocked screen required"); return START_NOT_STICKY }
        try {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, ID, notification("Starting online voice"), fgsType)
        } catch (_: Exception) { finish("Android rejected microphone foreground service"); return START_NOT_STICKY }
        scope.launch {
            while (started) {
                delay(100)
                if (!wakeAllowed()) { finish("Stopped for microphone or notification privacy"); break }
                if (!screenReady() && (!wakeMode || phase !in listOf("Wake idle", "Wake loading"))) {
                    if (!wakeMode) { finish("Stopped for screen privacy"); break }
                    returnToWake("Screen locked — local wake only")
                }
                if (wakeMode) {
                    val now = SystemClock.elapsedRealtime()
                    if (sessionDeadline != 0L && now >= sessionDeadline) endTurnSession("Session time limit reached")
                    if (phase == "Acknowledging" && ackDeadline != 0L && now >= ackDeadline) { ackDeadline = 0L; tts.stop(); listen() }
                    if (phase == "Wake idle") {
                        if (foregroundBusy() && wakeSpeech != null) { releaseWake(); retryAt = now + 1000L; publish("Wake waiting for voice session") }
                        else if (!foregroundBusy() && wakeSpeech == null && now >= retryAt) armWake()
                        else if (foregroundBusy() && wakeSpeech == null && _status.value != "Wake waiting for voice session") publish("Wake waiting for voice session")
                    }
                }
            }
        }
        if (wakeMode) { phase = "Wake loading"; loadWake() }
        else { scope.launch { delay(180_000); if (started) finish("Session time limit reached") }; listen() }
        return START_NOT_STICKY
    }

    private fun listen() {
        if (!allowed()) { finish("Session unavailable"); return }
        releaseWake(); phase = "Listening"; publish("Listening online — speak now")
        val s = prefs.settings.value
        input.startListening(s.languageTag, preferOffline = false, timeoutMs = 30000L, bluetoothEnabled = s.bluetoothVoiceRouting)
    }

    private fun accept(text: String) {
        if (!allowed() || phase != "Listening") return
        if (BackgroundVoicePolicy.classify(text) == BackgroundVoicePolicy.Action.STOP) { finish("Stopped by voice"); return }
        phase = "Thinking"; turns++; publish("Thinking online")
        responseJob = scope.launch {
            val reply = withTimeoutOrNull(45000L) { repository.answerBackgroundQuestion(text.take(1000), history.toList()).getOrNull() }
            coroutineContext.ensureActive()
            if (!allowed()) { finish("Session stopped"); return@launch }
            if (reply.isNullOrBlank()) { endTurnSession("Online AI unavailable — check connection"); return@launch }
            history.add(text.take(500) to reply.take(700)); while(history.size > 2) history.removeAt(0)
            if (!prefs.settings.value.autoSpeak) { endTurnSession("Auto speak is disabled"); return@launch }
            val s = prefs.settings.value
            phase = "Speaking"; publish("Speaking — online AI reply")
            tts.configure(TeluguVoiceReplies.effectiveLocale(s.languageTag, utterance = reply), s.ttsSpeed, s.ttsPitch, s.ttsStyle)
            tts.speak(reply.take(1000))
        }
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 1071, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1072, Intent(this, OnlineBackgroundVoiceService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("JARVIS • Online voice")
            .setContentText(message).setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
    }

    private fun publish(message: String) { _status.value = message; runCatching { getSystemService(NotificationManager::class.java).notify(ID, notification(message)) } }
    private fun finish(message: String) {
        started = false; phase = "Idle"; _status.value = message; releaseWake()
        responseJob?.cancel(); input.stopListening(); tts.stop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() {
        started = false; closed = true; releaseWake(); if (::ownerSpeaker.isInitialized) runCatching { ownerSpeaker.close() }; wakeModel?.let { runCatching { it.close() } }; wakeModel = null; if (instance === this) instance = null; scope.cancel(); input.release(); tts.release(); prefs.close(); db.close(); stopForeground(STOP_FOREGROUND_REMOVE)
        if (phase != "Idle") _status.value = "Stopped"
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "jarvis_online_voice"
        private const val ID = 10701
        private var instance: OnlineBackgroundVoiceService? = null
        private const val START_WAKE = "com.example.action.ONLINE_WAKE_START"
        private const val START = "com.example.action.ONLINE_VOICE_START"
        private const val STOP = "com.example.action.ONLINE_VOICE_STOP"
        private val _status = MutableStateFlow("Idle")
        val status = _status.asStateFlow()
        fun start(context: Context): Boolean {
            if (!AgentHudBus.isAppInForeground.value) return false
            return runCatching { ContextCompat.startForegroundService(context, Intent(context, OnlineBackgroundVoiceService::class.java).setAction(START)); true }.getOrDefault(false)
        }
        fun startWake(context: Context): Boolean {
            if (!AgentHudBus.isAppInForeground.value || instance?.started == true) return false
            return runCatching { ContextCompat.startForegroundService(context, Intent(context, OnlineBackgroundVoiceService::class.java).setAction(START_WAKE)); true }.getOrDefault(false)
        }
        fun yieldToForeground(context: Context) {
            val running = instance
            if (running?.wakeMode == true) running.yieldForeground() else stop(context)
        }
        fun foregroundReleased() { instance?.resumeAfterForeground() }
        fun stop(context: Context) { context.stopService(Intent(context, OnlineBackgroundVoiceService::class.java)) }
    }
}
