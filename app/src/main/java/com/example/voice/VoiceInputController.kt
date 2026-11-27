package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.util.Log
import com.example.control.AgentHudBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceInputState(
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val audioAmplitude: Float = 0f,
    val lastErrorCode: Int = 0,
    val errorMessage: String? = null,
    val activeLocale: String = "en-IN",
    val diagnosticsNote: String = "Ready"
)

/** One in-process recognizer session; stale callbacks cannot release a newer session. */
class VoiceInputController(private val context: Context, private val owner: String = "manual", private val onFinalTranscript: (String) -> Unit) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var released = false
    private var generation = 0L
    private var attempts = 0
    private var language = "auto"
    private var offline = false
    private var lastLocale = AutoLanguageSession.DEFAULT_AUTO_LOCALE
    private var sessionTimeoutMs = 30000L
    private val _state = MutableStateFlow(VoiceInputState())
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()
    fun startListening(languageTag: String = "auto", preferOffline: Boolean = false, timeoutMs: Long = 45000L) {
        handler.post {
            if(released) return@post
            stopInternal(); _state.value = VoiceInputState(); language = languageTag; offline = VoiceSessionPolicy.localOnly(preferOffline, privacyRestricted()); attempts = 0; sessionTimeoutMs = timeoutMs.coerceIn(5000L, 60000L)
            if (androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                _state.value = VoiceInputState(errorMessage = "Allow microphone permission first", diagnosticsNote = "Permission denied"); return@post
            }
            AgentHudBus.setMainMicBusy(true, this)
            if (owner != "background-capture") com.example.omega.OmegaWakeService.pauseForManual()
            if (!VoiceMic.lease.acquire(this)) {
                val pending = generation
                _state.value = VoiceInputState(isListening=true, diagnosticsNote="Waiting for microphone handoff")
                fun waitForLease(attempt: Int) {
                    handler.postDelayed({
                        if (pending == generation && !released) {
                            if (VoiceMic.lease.acquire(this)) begin()
                            else if (attempt < 6) waitForLease(attempt + 1)
                            else finishError(8,"Microphone occupied by another voice engine")
                        }
                    }, 150L)
                }
                waitForLease(1)
                return@post
            }
            val token = generation
            _state.value = VoiceInputState(isListening = true, diagnosticsNote = "Preparing microphone")
            handler.postDelayed({ if (token == generation) begin() }, 120)
        }
    }
    private fun privacyRestricted() = appContext.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked || !appContext.getSystemService(android.os.PowerManager::class.java).isInteractive
    private fun begin() {
        destroyRecognizer()
        val token = ++generation
        if (!VoiceMic.lease.owns(this)) { finishError(8, "Microphone lease lost"); return }
        if (privacyRestricted()) { finishError(8, "Unlock the screen to use online recognition"); return }
        val cm = appContext.getSystemService(android.net.ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val validated = network != null && cm?.getNetworkCapabilities(network)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        if (!validated) { finishError(2, "Internet connection required for online recognition"); return }
        offline = VoiceSessionPolicy.localOnly(offline, privacyRestricted())
        val locale = if (language == "auto") AutoLanguageSession.resolveEffectiveLocale("auto", lastLocale) else language
        lastLocale = locale
        val localAvailable = android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        val systemAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        val useLocal = OnlineSpeechPolicy.useLocal(offline, systemAvailable, localAvailable, attempts, onlineOnly = true)
            ?: run { finishError(12, "Online system speech provider unavailable. Check speech services and Internet."); return }
        val sr = runCatching { if (useLocal && android.os.Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext) else SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrElse { retry(5); return }
        recognizer = sr
        fun current() = token == generation && recognizer === sr
        fun privacyBlocked() = !useLocal && privacyRestricted()
        fun enforcePrivacy(): Boolean {
            if (!current() || !privacyBlocked()) return false
            finishError(8, "Online recognition stopped for screen privacy; unlock the screen")
            return true
        }
        val privacyMonitor = object : Runnable {
            override fun run() { if (current() && !enforcePrivacy()) handler.postDelayed(this, 150L) }
        }
        if (!useLocal) handler.post(privacyMonitor)
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if (current()) { Log.i("OmegaVoice", "ASR_READY $locale local=$useLocal"); _state.value = VoiceInputState(isListening = true, activeLocale = locale, diagnosticsNote = "Listening (${if (useLocal) "on-device" else "system"}, $locale)") } }
            override fun onBeginningOfSpeech() { if (current()) _state.value = _state.value.copy(isListening = true) }
            override fun onRmsChanged(rmsdB: Float) { if (current()) _state.value = _state.value.copy(audioAmplitude = if (rmsdB.isFinite()) ((rmsdB + 2f)/12f).coerceIn(0f,1f) else 0f) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { if (current()) _state.value = _state.value.copy(audioAmplitude = 0f, diagnosticsNote = "Processing speech") }
            override fun onError(error: Int) { if (current() && !enforcePrivacy()) retry(error) }
            override fun onResults(results: Bundle?) {
                if (!current() || enforcePrivacy()) return
                val final = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (final.isBlank()) { retry(7); return }
                if (language == "auto") lastLocale = AutoLanguageSession.recordRecognizedText(final)
                Log.i("OmegaVoice", "ASR_RESULT length=${final.length}")
                stopInternal()
                _state.value = VoiceInputState(partialTranscript = final, activeLocale = lastLocale, diagnosticsNote = "Captured (${final.length} chars)", errorMessage = if(final.isBlank()) "No speech recognized" else null)
                if (final.isNotEmpty()) onFinalTranscript(final)
            }
            override fun onPartialResults(partialResults: Bundle?) { if (current() && !enforcePrivacy()) _state.value = _state.value.copy(partialTranscript = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()) }
            override fun onLanguageDetection(results: Bundle) {
                if (!current() || enforcePrivacy() || language != "auto" || android.os.Build.VERSION.SDK_INT < 34) return
                val detected = results.getString(SpeechRecognizer.DETECTED_LANGUAGE).orEmpty()
                if (detected.substringBefore('-').equals("te", true)) lastLocale = AutoLanguageSession.recordRecognizedText("", "te-IN") else if (detected.substringBefore('-').equals("en", true)) lastLocale = AutoLanguageSession.recordRecognizedText("", "en-IN")
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
            if (language == "auto" && android.os.Build.VERSION.SDK_INT >= 34 && attempts == 0) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, arrayListOf("te-IN", "en-IN"))
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf("te-IN", "en-IN"))
            }
        }
        var launched = false
        fun launchCapture() {
            if (!current() || launched) return
            if (enforcePrivacy()) return
            launched = true
            Log.i("OmegaVoice", "ASR_START $locale local=$useLocal")
            runCatching { sr.startListening(intent) }.onFailure { retry(5) }
            handler.postDelayed({ if(current()) retry(6) }, sessionTimeoutMs)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
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
                        Log.i("OmegaVoice", "ASR_SUPPORT installed=$installed online=$online downloadable=${support.supportedOnDeviceLanguages}")
                        if (useLocal && installed.none { it.equals(locale, true) || it.substringBefore('-') == locale.substringBefore('-') }) { retry(13); return }
                        if (language == "auto" && android.os.Build.VERSION.SDK_INT >= 34 && !EliteRoutingPolicy.enableSwitch(installed, online, useLocal)) fallback() else launchCapture()
                    }
                    override fun onError(error: Int) { if (current() && !launched) { Log.i("OmegaVoice", "ASR_SUPPORT_ERROR $error"); fallback() } }
                })
            }.onFailure { fallback() }
            handler.postDelayed({ if (current() && !launched) fallback() }, 500)
        } else launchCapture()
    }
    private fun retry(code: Int) {
        Log.w("OmegaVoice", "ASR_ERROR $code attempt=$attempts")
        ++generation; destroyRecognizer(); handler.removeCallbacksAndMessages(null)
        if (VoiceRecoveryPolicy.retryable(code, language == "auto", attempts)) {
            attempts++
            if(language == "auto" && code in setOf(6,7,12,13)) lastLocale = VoiceRecoveryPolicy.nextLocale(lastLocale)
            val token = generation
            _state.value = _state.value.copy(isListening = true, audioAmplitude = 0f, lastErrorCode = code, diagnosticsNote = "Recovery $attempts/2 (error $code); speak again")
            handler.postDelayed({ if(token == generation) begin() }, if(attempts == 1) 650 else 1400)
        } else finishError(code, when(code) { 9 -> "Microphone permission denied"; 12,13 -> "Language model unavailable; install speech language pack"; 6,7 -> if(owner == "background-capture") "No speech recognized — wake listening will resume" else "No speech recognized. Tap mic again."; else -> "Speech recognition error $code. Check speech service and microphone." })
    }
    private fun finishError(code: Int, message: String) { stopInternal(); _state.value = VoiceInputState(lastErrorCode = code, errorMessage = message, diagnosticsNote = "Error $code") }
    fun stopListening() { if (Looper.myLooper() == Looper.getMainLooper()) stopInternal() else handler.post { stopInternal() } }
    fun release() { released=true; stopListening() }
    private fun stopInternal() { ++generation; handler.removeCallbacksAndMessages(null); destroyRecognizer()
        VoiceMic.lease.release(this); AgentHudBus.setMainMicBusy(false, this); _state.value = _state.value.copy(isListening = false, audioAmplitude = 0f) }
    private fun destroyRecognizer() { val old = recognizer; recognizer = null; runCatching { old?.cancel() }; runCatching { old?.destroy() } }
}
