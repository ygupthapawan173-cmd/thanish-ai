package com.example.omega

import ai.picovoice.porcupine.PorcupineManager
import com.example.voice.PorcupineWakeConfig
import com.example.voice.SherpaWakeSession
import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.ai.PanduAiRepository
import com.example.control.AgentHudBus
import com.example.control.AgentHudState
import com.example.control.DeviceActionExecutor
import com.example.data.ApiSlotManager
import com.example.data.AppDatabase
import com.example.data.OmegaPreferences
import com.example.voice.AudioCapturePolicy
import com.example.voice.HandsFreeWakePolicy
import com.example.voice.PreloadedAckPlayer
import com.example.voice.SarvamRealtimeSttClient
import com.example.voice.StreamingVoiceEngineKind
import com.example.voice.TeluguTtsController
import com.example.voice.TeluguVoiceReplies
import com.example.voice.VoiceEngineSelector
import com.example.voice.VoiceInputController
import com.example.voice.VoiceMic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * V110 Unified Native Microphone Foreground Service (Sections 6, 7, 8, 9, 11):
 * - Online Wake Priority (`Sarvam Saaras V4`) when unlocked, internet-connected & consented
 * - Offline Wake Fallback (`Porcupine or Sherpa INT8 Hey JARVIS` + `Silero VAD`) when offline or lock-screen active
 * - Preloaded instant "Yes Boss" response measuring actual device latency (`PreloadedAckPlayer`)
 * - Screen-lock wake recognition uses LOCAL detector ONLY (zero cloud audio while locked)
 * - Stops microphone capture immediately when listening is disabled or privacy muted
 */
class OnlineBackgroundVoiceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())

    private lateinit var prefs: OmegaPreferences
    private lateinit var slotManager: ApiSlotManager
    private lateinit var db: AppDatabase
    private lateinit var repository: PanduAiRepository
    private lateinit var input: VoiceInputController
    private lateinit var tts: TeluguTtsController

    private var onlineWakeClient: SarvamRealtimeSttClient? = null
    private var onlineWakeJob: Job? = null
    private var porcupine: PorcupineManager? = null
    private var sherpaWake: SherpaWakeSession? = null
    private var sherpaRetryAt = 0L
    private var porcupineRetryAt = 0L

    private var started = false
    private var wakeMode = false
    private var closed = false
    private var wakeGeneration = 0L
    private var retryAt = 0L
    private var wakeFailures = 0
    private var ackDeadline = 0L
    private var phase = "Idle"
    private var responseJob: Job? = null

    private val wakeLease = Any()

    private fun foregroundBusy(): Boolean = HandsFreeWakePolicy.foregroundBusy(
        AgentHudBus.isAppInForeground.value,
        AgentHudBus.isMainMicBusy.value,
        AgentHudBus.isTtsBusy.value,
        (AgentHudBus.state.value as? AgentHudState.Active)?.isThinking == true
    )

    private fun screenReady(): Boolean {
        val interactive = runCatching { getSystemService(PowerManager::class.java).isInteractive }.getOrDefault(true)
        val unlocked = runCatching { !getSystemService(KeyguardManager::class.java).isDeviceLocked }.getOrDefault(false)
        return interactive && unlocked
    }

    private fun hasValidatedInternet(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(net)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private fun effectiveSarvamKey(): String {
        val s = prefs.settings.value
        return s.sarvamApiKey.trim().ifBlank { slotManager.getActiveSarvamKeyAndUrl().first }
    }

    private fun wakeAllowed(): Boolean {
        val s = prefs.settings.value
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasNotif = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        val lockAllowed = screenReady() || s.screenLockWakeEnabled
        return started && !closed && !s.micPrivacyMuted && hasMic && hasNotif && lockAllowed
    }

    private fun allowedOnlineAction(): Boolean = wakeAllowed() && screenReady()

    private fun resumeAfterForeground() {
        if (!started || !wakeMode || phase != "Wake idle") return
        retryAt = SystemClock.elapsedRealtime() + 180L
    }

    private fun releaseWake() {
        ++wakeGeneration
        onlineWakeJob?.cancel()
        onlineWakeJob = null
        onlineWakeClient?.release()
        onlineWakeClient = null
        sherpaWake?.stop()
        sherpaWake = null
        val local = porcupine
        porcupine = null
        runCatching { local?.stop() }
        runCatching { local?.delete() }

        VoiceMic.lease.release(wakeLease)
    }

    private fun returnToWake(message: String) {
        phase = "Wake idle"
        ackDeadline = 0L
        responseJob?.cancel()
        responseJob = null
        input.stopListening()
        tts.stop()
        releaseWake()
        retryAt = SystemClock.elapsedRealtime() + 350L
        publish(message)
    }

    private fun yieldForeground() {
        if (!started || !wakeMode || phase == "Wake loading") return
        returnToWake("Wake standby — foreground active")
        retryAt = SystemClock.elapsedRealtime() + 1_000L
    }

    private fun armWake() {
        if (!wakeAllowed() || foregroundBusy()) return
        val s = prefs.settings.value
        val benchmark = VoiceEngineSelector.selectBest(
            hasInternet = hasValidatedInternet(),
            isLocked = !screenReady(),
            cloudConsentGranted = s.cloudAudioConsentGranted && s.onlineWakePriority,
            hasSarvamKey = effectiveSarvamKey().isNotBlank(),
            hasGeminiKey = s.effectiveGeminiKey().isNotBlank()
        )
        if (benchmark.selectedEngine == StreamingVoiceEngineKind.SARVAM_SAARAS_V4 && screenReady()) {
            armOnlineSarvamWake()
        } else {
            armLocalWake()
        }
    }

    private fun armLocalWake() {
        if (SystemClock.elapsedRealtime() < porcupineRetryAt || !PorcupineWakeConfig.configured(this)) {
            armSherpaWake()
            return
        }
        if (!VoiceMic.lease.acquire(wakeLease)) {
            retryAt = SystemClock.elapsedRealtime() + 250L
            return
        }
        val token = ++wakeGeneration
        fun current() = started && !closed && wakeMode && token == wakeGeneration && phase == "Wake idle"
        try {
            val manager = PorcupineManager.Builder()
                .setAccessKey(PorcupineWakeConfig.accessKey(this))
                .setKeywordPath(PorcupineWakeConfig.modelFile(this).absolutePath)
                .setSensitivity(0.55f)
                .setErrorCallback { _ -> main.post {
                    if (current()) {
                        releaseWake()
                        wakeFailures++
                        retryAt = SystemClock.elapsedRealtime() + HandsFreeWakePolicy.retryDelay(wakeFailures)
                        porcupineRetryAt = SystemClock.elapsedRealtime() + 60_000L
                        publish("Porcupine failed — trying Sherpa")
                        if (wakeAllowed() && !foregroundBusy()) armSherpaWake()
                    }
                } }
                .build(this) { index -> main.post {
                    if (index == 0 && current() && wakeAllowed() && !foregroundBusy()) {
                        val detected = SystemClock.elapsedRealtime()
                        releaseWake()
                        wakeFailures = 0
                        handleWakeTriggered(detected, "")
                    }
                } }
            porcupine = manager
            manager.start()
            publish("Listening • Porcupine local wake")
        } catch (_: Exception) {
            releaseWake()
            porcupineRetryAt = SystemClock.elapsedRealtime() + 60_000L
            armSherpaWake()
        } catch (_: LinkageError) {
            releaseWake()
            porcupineRetryAt = SystemClock.elapsedRealtime() + 60_000L
            armSherpaWake()
        }
    }

    private fun armSherpaWake() {
        if (SystemClock.elapsedRealtime() < sherpaRetryAt) {
            retryAt = sherpaRetryAt
            publish("Local wake retry scheduled")
            return
        }
        if (!VoiceMic.lease.acquire(wakeLease)) {
            retryAt = SystemClock.elapsedRealtime() + 250L
            return
        }
        val token = ++wakeGeneration
        fun current() = started && !closed && wakeMode && token == wakeGeneration && phase == "Wake idle"
        val session = SherpaWakeSession(this,
            onReady = { main.post { if (current()) publish("Listening • Sherpa INT8 wake") } },
            onWake = { main.post {
                if (current() && wakeAllowed() && !foregroundBusy()) {
                    val detected = SystemClock.elapsedRealtime()
                    releaseWake()
                    wakeFailures = 0
                    handleWakeTriggered(detected, "")
                } else if (current()) releaseWake()
            } },
            onFailure = { main.post {
                if (current()) {
                    releaseWake()
                    sherpaRetryAt = SystemClock.elapsedRealtime() + 60_000L
                    retryAt = sherpaRetryAt
                    publish("Sherpa failed — retry scheduled")
                }
            } })
        sherpaWake = session
        session.start()
        publish("Preparing Sherpa INT8 wake")
    }

    private fun armOnlineSarvamWake() {
        releaseWake()
        val token = ++wakeGeneration
        val client = SarvamRealtimeSttClient(
            context = this,
            apiKeyProvider = { effectiveSarvamKey() },
            wakeMode = true,
            captureAllowed = { wakeAllowed() && screenReady() && prefs.settings.value.cloudAudioConsentGranted && prefs.settings.value.onlineWakePriority },
            onWakeDetected = { inlineCmd ->
                if (token != wakeGeneration || !wakeAllowed() || !screenReady() || !prefs.settings.value.cloudAudioConsentGranted || foregroundBusy() || !wakeMode || phase != "Wake idle") return@SarvamRealtimeSttClient
                val wakeTime = SystemClock.elapsedRealtime()
                releaseWake()
                wakeFailures = 0
                handleWakeTriggered(wakeTime, inlineCmd)
            }
        )
        onlineWakeClient = client
        onlineWakeJob = scope.launch {
            client.state.collect { st ->
                if (token != wakeGeneration || !started || !wakeMode) return@collect
                if (st.measuredConnectLatencyMs > 0L) {
                    VoiceEngineSelector.recordLatency(StreamingVoiceEngineKind.SARVAM_SAARAS_V4, st.measuredConnectLatencyMs)
                }
                if (st.measuredConnectLatencyMs > 0L && st.error == null && st.isListening) publish("Listening • Online Wake (Saaras V4)")
                if (st.error != null) {
                    Log.w("OmegaWake", "Online wake error (${st.error}), falling back to local keyword wake")
                    releaseWake()
                    wakeFailures++
                    if (wakeAllowed() && !foregroundBusy()) armLocalWake()
                    else retryAt = SystemClock.elapsedRealtime() + HandsFreeWakePolicy.retryDelay(wakeFailures)
                }
            }
        }
        client.start(prefs.settings.value.languageTag)
        publish("Connecting • Online Wake (Saaras V4)")
    }

    private fun handleWakeTriggered(wakeDetectedAtMs: Long, inlineCommand: String) {
        val s = prefs.settings.value
        if (!screenReady()) {
            // Lock-screen wake: local only! Never stream mic audio or run cloud AI while locked.
            retryAt = SystemClock.elapsedRealtime() + 2_200L
            publish("Hey JARVIS detected — unlock screen to continue")
            PreloadedAckPlayer.playImmediateAck(this, "en-IN", wakeDetectedAtMs) {}
            return
        }

        if (AgentHudBus.isAppInForeground.value) {
            retryAt = SystemClock.elapsedRealtime() + 3_500L
            publish("Hey JARVIS — handed to active app")
            AgentHudBus.emitWakeTrigger(inlineCommand)
            return
        }

        phase = "Acknowledging"
        ackDeadline = SystemClock.elapsedRealtime() + 1_800L
        publish("Yes Boss!")
        PreloadedAckPlayer.playImmediateAck(this, s.languageTag, wakeDetectedAtMs) {
            main.post {
                if (closed || !started || phase != "Acknowledging") return@post
                ackDeadline = 0L
                if (inlineCommand.isNotBlank()) accept(inlineCommand) else listen()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = OmegaPreferences(this)
        slotManager = ApiSlotManager(this, prefs)
        db = AppDatabase(this)
        repository = PanduAiRepository({ prefs.settings.value }, slotManager, DeviceActionExecutor(this, db, prefs))
        input = VoiceInputController(
            context = this,
            owner = "background-capture",
            sarvamKeyProvider = { effectiveSarvamKey() },
            geminiKeyProvider = { prefs.settings.value.effectiveGeminiKey() },
            openAiKeyProvider = { prefs.settings.value.effectiveOpenAiKey() },
            cloudConsentProvider = { prefs.settings.value.cloudAudioConsentGranted }
        ) { text -> accept(text) }

        tts = TeluguTtsController(
            context = this,
            backgroundOwned = true,
            cloudSettings = { prefs.settings.value },
            slotManager = slotManager,
            backgroundCloudAllowed = { (phase == "Speaking" || phase == "Acknowledging") && allowedOnlineAction() }
        )
        tts.setOnSpeechFinished {
            if (phase == "Speaking" || phase == "Acknowledging") {
                returnToWake("Listening for Hey JARVIS")
            }
        }

        PreloadedAckPlayer.warmUpSarvamAckCache(
            this,
            effectiveSarvamKey(),
            prefs.settings.value.sarvamSpeaker,
            prefs.settings.value.sarvamModel
        )

        scope.launch {
            input.state.collect { st ->
                if (started && phase == "Listening" && st.errorMessage != null) {
                    returnToWake("Ready — ${st.errorMessage}")
                }
            }
        }
        scope.launch {
            AgentHudBus.abortSignal.collect {
                if (started) finish("Stopped")
            }
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "JARVIS Voice Background Service", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != START && intent?.action != START_WAKE) {
            finish("Stopped")
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY

        wakeMode = intent.action == START_WAKE
        started = true
        if (!wakeAllowed()) {
            finish("Grant Microphone & Notification permissions first")
            return START_NOT_STICKY
        }

        try {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, ID, notification("Starting JARVIS Voice Engine"), fgsType)
        } catch (_: Exception) {
            finish("Android foreground microphone start blocked")
            return START_NOT_STICKY
        }

        scope.launch {
            while (started) {
                delay(150L)
                if (!wakeAllowed()) {
                    finish("Stopped by privacy / permission setting")
                    break
                }
                if (!screenReady() && (!wakeMode || phase !in listOf("Wake idle", "Wake loading"))) {
                    if (!wakeMode) {
                        finish("Stopped on screen lock")
                        break
                    }
                    returnToWake("Screen locked — local wake standby")
                }
                if (wakeMode) {
                    val now = SystemClock.elapsedRealtime()
                    if (phase == "Acknowledging" && ackDeadline != 0L && now >= ackDeadline) {
                        ackDeadline = 0L
                        listen()
                    }
                    if (phase == "Wake idle") {
                        if (onlineWakeClient != null && (!screenReady() || !prefs.settings.value.cloudAudioConsentGranted || !prefs.settings.value.onlineWakePriority || !hasValidatedInternet())) {
                            releaseWake()
                            retryAt = now
                        }
                        val hasActiveWake = onlineWakeClient != null || porcupine != null || sherpaWake != null
                        if (foregroundBusy() && hasActiveWake) {
                            releaseWake()
                            retryAt = now + 800L
                            publish("Standby — foreground active")
                        } else if (!foregroundBusy() && !hasActiveWake && now >= retryAt) {
                            armWake()
                        }
                    }
                }
            }
        }

        if (wakeMode) {
            phase = "Wake idle"
            retryAt = 0L
        } else {
            listen()
        }
        return START_NOT_STICKY
    }

    private fun listen() {
        if (!allowedOnlineAction()) {
            returnToWake("Unlock screen for voice query")
            return
        }
        releaseWake()
        phase = "Listening"
        publish("Listening for command...")
        input.startListening(prefs.settings.value.languageTag, preferOffline = false, timeoutMs = 25_000L)
    }

    private fun accept(text: String) {
        if (!allowedOnlineAction()) {
            returnToWake("Screen locked")
            return
        }
        phase = "Thinking"
        publish("Thinking...")
        responseJob = scope.launch {
            val reply = withTimeoutOrNull(45_000L) {
                repository.answerBackgroundQuestion(text.take(1_000)).getOrNull()
            }
            coroutineContext.ensureActive()
            if (!allowedOnlineAction()) {
                returnToWake("Session paused")
                return@launch
            }
            if (reply.isNullOrBlank()) {
                returnToWake("No AI reply — rearming wake")
                return@launch
            }
            if (!prefs.settings.value.autoSpeak) {
                returnToWake("Completed • ${reply.take(64)}")
                return@launch
            }
            val s = prefs.settings.value
            phase = "Speaking"
            publish("Speaking Sarvam Telugu/English reply")
            tts.configure(TeluguVoiceReplies.effectiveLocale(s.languageTag, utterance = reply))
            tts.speak(reply.take(1_500))
        }
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            1071,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1072,
            Intent(this, OnlineBackgroundVoiceService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS INFINITY V111")
            .setContentText(message)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun publish(message: String) {
        _status.value = message
        runCatching { getSystemService(NotificationManager::class.java).notify(ID, notification(message)) }
    }

    private fun finish(message: String) {
        started = false
        phase = "Idle"
        _status.value = message
        releaseWake()
        responseJob?.cancel()
        input.stopListening()
        tts.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        started = false
        closed = true
        releaseWake()
        if (instance === this) instance = null
        scope.cancel()
        input.release()
        tts.release()
        prefs.close()
        db.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
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
            return runCatching {
                ContextCompat.startForegroundService(context, Intent(context, OnlineBackgroundVoiceService::class.java).setAction(START))
                true
            }.getOrDefault(false)
        }

        fun startWake(context: Context): Boolean {
            if (!AgentHudBus.isAppInForeground.value || instance?.started == true) return false
            return runCatching {
                ContextCompat.startForegroundService(context, Intent(context, OnlineBackgroundVoiceService::class.java).setAction(START_WAKE))
                true
            }.getOrDefault(false)
        }

        fun yieldToForeground(context: Context) {
            val running = instance
            if (running?.wakeMode == true) running.yieldForeground() else stop(context)
        }

        fun foregroundReleased() {
            instance?.resumeAfterForeground()
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OnlineBackgroundVoiceService::class.java))
        }
    }
}
