package com.example.voice

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.control.AgentHudBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VoiceInputState(
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val audioAmplitude: Float = 0f,
    val lastErrorCode: Int = 0,
    val errorMessage: String? = null,
    val activeLocale: String = "te-IN",
    val activeEngine: String = "Standby",
    val diagnosticsNote: String = "Ready"
)

/**
 * V110 Streaming + Fallback Speech Input Controller (Sections 6, 7, 8):
 * - Selects best streaming engine via `VoiceEngineSelector`:
 *   1. `Sarvam Saaras V4` (`wss://api.sarvam.ai/speech-to-text-realtime/ws`) with Telugu+English codemix,
 *      Silero VAD, AEC, Noise Suppression, AGC & mic stall recovery when Sarvam key + consent + network exist.
 *   2. Automatic fallback to system inline `SpeechRecognizer` (never opens Google Voice Search popup).
 * - Guarantees exclusive single-mic ownership via `VoiceMic.lease`.
 */
class VoiceInputController(
    private val context: Context,
    private val owner: String = "manual",
    private val sarvamKeyProvider: () -> String = { "" },
    private val geminiKeyProvider: () -> String = { "" },
    private val cloudConsentProvider: () -> Boolean = { true },
    private val onFinalTranscript: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recognizer: SpeechRecognizer? = null
    private var sarvamClient: SarvamRealtimeSttClient? = null
    private var sarvamCollectorJob: Job? = null

    @Volatile
    private var released = false
    private var generation = 0L
    private var attempts = 0
    private var language = "auto"
    private var offline = false
    private var lastLocale = AutoLanguageSession.DEFAULT_AUTO_LOCALE
    private var sessionTimeoutMs = 45_000L
    private var startedAtMs = 0L

    private val _state = MutableStateFlow(VoiceInputState())
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    fun startListening(languageTag: String = "auto", preferOffline: Boolean = false, timeoutMs: Long = 45_000L) {
        main.post {
            if (released) return@post
            stopInternal()
            _state.value = VoiceInputState()
            language = languageTag
            offline = VoiceSessionPolicy.localOnly(preferOffline, privacyRestricted())
            attempts = 0
            sessionTimeoutMs = timeoutMs.coerceIn(5_000L, 60_000L)
            startedAtMs = SystemClock.elapsedRealtime()

            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                _state.value = VoiceInputState(
                    errorMessage = "Allow microphone permission first",
                    diagnosticsNote = "Permission denied"
                )
                return@post
            }

            if (owner != "background-capture") {
                com.example.omega.OnlineBackgroundVoiceService.yieldToForeground(appContext)
            }

            // Try Sarvam Saaras V4 streaming first when selected
            val benchmark = VoiceEngineSelector.selectBest(
                hasInternet = hasValidatedInternet(),
                isLocked = privacyRestricted(),
                cloudConsentGranted = cloudConsentProvider(),
                hasSarvamKey = sarvamKeyProvider().isNotBlank(),
                hasGeminiKey = geminiKeyProvider().isNotBlank()
            )
            if (!offline && benchmark.selectedEngine == StreamingVoiceEngineKind.SARVAM_SAARAS_V4 && sarvamKeyProvider().isNotBlank()) {
                startSarvamStreaming(languageTag)
                return@post
            }

            acquireSystemRecognizerLease()
        }
    }

    private fun startSarvamStreaming(languageTag: String) {
        val token = ++generation
        val stt = SarvamRealtimeSttClient(
            context = appContext,
            apiKeyProvider = sarvamKeyProvider,
            wakeMode = false,
            onFinalTranscript = { transcript ->
                if (token != generation || released) return@SarvamRealtimeSttClient
                val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(1L)
                VoiceEngineSelector.recordLatency(StreamingVoiceEngineKind.SARVAM_SAARAS_V4, elapsed)
                val resolvedLocale = AutoLanguageSession.recordRecognizedText(transcript)
                lastLocale = resolvedLocale
                stopInternal()
                _state.value = VoiceInputState(
                    isListening = false,
                    partialTranscript = transcript,
                    activeLocale = resolvedLocale,
                    activeEngine = "Sarvam Saaras V4",
                    diagnosticsNote = "Saaras V4 captured (${elapsed}ms)"
                )
                onFinalTranscript(transcript)
            }
        )
        sarvamClient = stt
        sarvamCollectorJob = scope.launch {
            stt.state.collect { s ->
                if (token != generation || released) return@collect
                if (s.measuredConnectLatencyMs > 0L) {
                    VoiceEngineSelector.recordLatency(StreamingVoiceEngineKind.SARVAM_SAARAS_V4, s.measuredConnectLatencyMs)
                }
                if (s.error != null) {
                    Log.w("OmegaVoice", "Saaras V4 fallback -> system SpeechRecognizer (${s.error})")
                    sarvamClient?.stop()
                    sarvamClient = null
                    acquireSystemRecognizerLease()
                } else {
                    _state.value = VoiceInputState(
                        isListening = s.isListening,
                        partialTranscript = s.partialTranscript,
                        audioAmplitude = s.amplitude,
                        activeLocale = AutoLanguageSession.resolveEffectiveLocale(languageTag, lastLocale),
                        activeEngine = s.engineName,
                        diagnosticsNote = s.diagnosticsNote
                    )
                }
            }
        }
        stt.start(AutoLanguageSession.resolveEffectiveLocale(languageTag, lastLocale))
    }

    private fun acquireSystemRecognizerLease() {
        AgentHudBus.setMainMicBusy(true, this)
        if (!VoiceMic.lease.acquire(this)) {
            val pending = generation
            _state.value = VoiceInputState(isListening = true, activeEngine = "Waiting Mic", diagnosticsNote = "Waiting for microphone handoff")
            fun waitForLease(attempt: Int) {
                main.postDelayed({
                    if (pending == generation && !released) {
                        if (VoiceMic.lease.acquire(this)) beginSystemRecognizer()
                        else if (attempt < 6) waitForLease(attempt + 1)
                        else finishError(8, "Microphone occupied by another voice engine")
                    }
                }, 140L)
            }
            waitForLease(1)
            return
        }
        val token = generation
        _state.value = VoiceInputState(isListening = true, activeEngine = "System Streaming ASR", diagnosticsNote = "Preparing microphone")
        main.postDelayed({ if (token == generation) beginSystemRecognizer() }, 100L)
    }

    private fun hasValidatedInternet(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private fun privacyRestricted(): Boolean {
        val km = runCatching { appContext.getSystemService(KeyguardManager::class.java).isDeviceLocked }.getOrDefault(false)
        val pm = runCatching { !appContext.getSystemService(PowerManager::class.java).isInteractive }.getOrDefault(false)
        return km || pm
    }

    private fun beginSystemRecognizer() {
        destroyRecognizer()
        val token = ++generation
        if (!VoiceMic.lease.owns(this)) {
            finishError(8, "Microphone lease lost")
            return
        }
        if (privacyRestricted()) {
            finishError(8, "Unlock the screen to use online recognition")
            return
        }
        if (!hasValidatedInternet()) {
            finishError(2, "Internet connection required for online speech recognition")
            return
        }
        offline = VoiceSessionPolicy.localOnly(offline, privacyRestricted())
        val locale = if (language == "auto") AutoLanguageSession.resolveEffectiveLocale("auto", lastLocale) else language
        lastLocale = locale

        val localAvailable = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        val systemAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        val useLocal = OnlineSpeechPolicy.useLocal(offline, systemAvailable, localAvailable, attempts, onlineOnly = true)
            ?: run {
                finishError(12, "System speech recognition service unavailable")
                return
            }

        val sr = runCatching {
            if (useLocal && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            else SpeechRecognizer.createSpeechRecognizer(appContext)
        }.getOrElse {
            retry(5)
            return
        }
        recognizer = sr

        fun current() = token == generation && recognizer === sr
        fun privacyBlocked() = !useLocal && privacyRestricted()
        fun enforcePrivacy(): Boolean {
            if (!current() || !privacyBlocked()) return false
            finishError(8, "Online recognition paused for screen privacy")
            return true
        }

        val privacyMonitor = object : Runnable {
            override fun run() {
                if (current() && !enforcePrivacy()) main.postDelayed(this, 150L)
            }
        }
        if (!useLocal) main.post(privacyMonitor)

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (current()) {
                    _state.value = VoiceInputState(
                        isListening = true,
                        activeLocale = locale,
                        activeEngine = if (useLocal) "On-Device ASR" else "Streaming ASR ($locale)",
                        diagnosticsNote = "Listening ($locale)"
                    )
                }
            }

            override fun onBeginningOfSpeech() {
                if (current()) _state.value = _state.value.copy(isListening = true)
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (current()) {
                    _state.value = _state.value.copy(
                        audioAmplitude = if (rmsdB.isFinite()) ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) else 0f
                    )
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                if (current()) _state.value = _state.value.copy(audioAmplitude = 0f, diagnosticsNote = "Processing speech")
            }

            override fun onError(error: Int) {
                if (current() && !enforcePrivacy()) retry(error)
            }

            override fun onResults(results: Bundle?) {
                if (!current() || enforcePrivacy()) return
                val final = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (final.isBlank()) {
                    retry(7)
                    return
                }
                if (language == "auto") lastLocale = AutoLanguageSession.recordRecognizedText(final)
                stopInternal()
                _state.value = VoiceInputState(
                    partialTranscript = final,
                    activeLocale = lastLocale,
                    activeEngine = "Streaming ASR",
                    diagnosticsNote = "Captured (${final.length} chars)"
                )
                onFinalTranscript(final)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (current() && !enforcePrivacy()) {
                    _state.value = _state.value.copy(
                        partialTranscript = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    )
                }
            }

            override fun onLanguageDetection(results: Bundle) {
                if (!current() || enforcePrivacy() || language != "auto" || Build.VERSION.SDK_INT < 34) return
                val detected = results.getString(SpeechRecognizer.DETECTED_LANGUAGE).orEmpty()
                if (detected.substringBefore('-').equals("te", true)) {
                    lastLocale = AutoLanguageSession.recordRecognizedText("", "te-IN")
                } else if (detected.substringBefore('-').equals("en", true)) {
                    lastLocale = AutoLanguageSession.recordRecognizedText("", "en-IN")
                }
                _state.value = _state.value.copy(activeLocale = lastLocale, diagnosticsNote = "Detected $detected")
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            if (language == "auto" && Build.VERSION.SDK_INT >= 34 && attempts == 0) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, arrayListOf("te-IN", "en-IN"))
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf("te-IN", "en-IN"))
            }
        }

        var launched = false
        fun launchCapture() {
            if (!current() || launched || enforcePrivacy()) return
            launched = true
            runCatching { sr.startListening(intent) }.onFailure { retry(5) }
            main.postDelayed({ if (current()) retry(6) }, sessionTimeoutMs)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            val fallback = {
                intent.removeExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH)
                intent.removeExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION)
                launchCapture()
            }
            runCatching {
                sr.checkRecognitionSupport(intent, appContext.mainExecutor, object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        if (!current() || launched) return
                        val installed = support.installedOnDeviceLanguages
                        val online = support.onlineLanguages
                        if (useLocal && installed.none { it.equals(locale, true) || it.substringBefore('-') == locale.substringBefore('-') }) {
                            retry(13)
                            return
                        }
                        if (language == "auto" && Build.VERSION.SDK_INT >= 34 && !EliteRoutingPolicy.enableSwitch(installed, online, useLocal)) {
                            fallback()
                        } else {
                            launchCapture()
                        }
                    }

                    override fun onError(error: Int) {
                        if (current() && !launched) fallback()
                    }
                })
            }.onFailure { fallback() }
            main.postDelayed({ if (current() && !launched) fallback() }, 500L)
        } else {
            launchCapture()
        }
    }

    private fun retry(code: Int) {
        ++generation
        destroyRecognizer()
        main.removeCallbacksAndMessages(null)
        if (VoiceRecoveryPolicy.retryable(code, language == "auto", attempts)) {
            attempts++
            if (language == "auto" && code in setOf(6, 7, 12, 13)) {
                lastLocale = VoiceRecoveryPolicy.nextLocale(lastLocale)
            }
            val token = generation
            _state.value = _state.value.copy(
                isListening = true,
                audioAmplitude = 0f,
                lastErrorCode = code,
                diagnosticsNote = "Recovering speech ($attempts/2)"
            )
            main.postDelayed({ if (token == generation) beginSystemRecognizer() }, if (attempts == 1) 600L else 1200L)
        } else {
            finishError(
                code,
                when (code) {
                    9 -> "Microphone permission denied"
                    12, 13 -> "Speech language model unavailable"
                    6, 7 -> if (owner == "background-capture") "No speech recognized — rearming wake standby" else "No speech heard — tap mic again"
                    else -> "Speech recognition error $code"
                }
            )
        }
    }

    private fun finishError(code: Int, message: String) {
        stopInternal()
        _state.value = VoiceInputState(
            lastErrorCode = code,
            errorMessage = message,
            activeEngine = "Stopped",
            diagnosticsNote = message
        )
    }

    fun stopListening() {
        if (Looper.myLooper() == Looper.getMainLooper()) stopInternal()
        else main.post { stopInternal() }
    }

    fun release() {
        released = true
        stopListening()
        scope.cancel()
    }

    private fun stopInternal() {
        ++generation
        main.removeCallbacksAndMessages(null)
        sarvamCollectorJob?.cancel()
        sarvamCollectorJob = null
        sarvamClient?.stop()
        sarvamClient = null
        destroyRecognizer()
        VoiceMic.lease.release(this)
        AgentHudBus.setMainMicBusy(false, this)
        _state.value = _state.value.copy(isListening = false, audioAmplitude = 0f)
    }

    private fun destroyRecognizer() {
        val old = recognizer
        recognizer = null
        runCatching { old?.cancel() }
        runCatching { old?.destroy() }
    }
}
