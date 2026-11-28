package com.example.voice

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.control.AgentHudBus
import com.example.data.ApiSlotManager
import com.example.data.OmegaSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * V110 Pure Sarvam AI Premium Telugu + Mixed English Voice Controller (Sections 8 & 16):
 * - NO Android TTS (System TextToSpeech engine removed)
 * - NO OpenAI Onyx or Gemini 30 Voices
 * - NO custom TTS speed/pitch/emotion overrides
 * - Synthesizes natural Telugu & TE/EN mixed speech via Sarvam `bulbul:v3` / `bulbul:v4-flash`,
 *   manages AudioFocus + MediaPlayer lifecycle cleanly, and supports network backoff recovery.
 */
class TeluguTtsController(
    context: Context,
    private val backgroundOwned: Boolean = false,
    private val cloudSettings: (() -> OmegaSettings)? = null,
    private val slotManager: ApiSlotManager? = null,
    private val backgroundCloudAllowed: () -> Boolean = { false }
) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var focus: AudioFocusRequest? = null
    private var mediaPlayer: MediaPlayer? = null
    private var spectrumTap: Visualizer? = null
    private var activeJob: Job? = null
    private var playbackWatchdog: Runnable? = null
    private var privacyMonitor: Runnable? = null
    private var generation = 0L
    private var cooldownUntil = 0L
    private var localOnly = false
    private var configuredLocale = AutoLanguageSession.DEFAULT_AUTO_LOCALE

    @Volatile
    private var released = false

    @Volatile
    var lastSpeechSucceeded: Boolean = false
        private set

    private var onSpeechFinishedCallback: (() -> Unit)? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsStatus = MutableStateFlow("Gemini / optional Sarvam voice")
    val ttsStatus: StateFlow<String> = _ttsStatus.asStateFlow()

    fun setOnSpeechFinished(callback: (() -> Unit)?) {
        onSpeechFinishedCallback = callback
    }

    fun configure(localeTag: String, localOnly: Boolean = false) {
        this.localOnly = localOnly
        this.configuredLocale = AutoLanguageSession.resolveEffectiveLocale(localeTag)
        val s = cloudSettings?.invoke()
        val key = effectiveSarvamKey(s)
        if (s != null && key.isNotBlank()) {
            PreloadedAckPlayer.warmUpSarvamAckCache(appContext, key, s.sarvamSpeaker, s.sarvamModel)
        }
    }

    private fun effectiveSarvamKey(s: OmegaSettings?): String {
        val direct = s?.sarvamApiKey?.trim().orEmpty()
        if (direct.isNotBlank()) return direct
        return slotManager?.getActiveSarvamKeyAndUrl()?.first.orEmpty()
    }

    private fun effectiveGeminiKey(s: OmegaSettings): String = s.effectiveGeminiKey().ifBlank {
        slotManager?.slots?.value?.firstOrNull { it.provider == com.example.data.SlotProvider.GEMINI && it.enabled && it.apiKey.isNotBlank() }?.apiKey.orEmpty()
    }

    private fun privacyBlocked(s: OmegaSettings): Boolean {
        val locked = runCatching {
            appContext.getSystemService(KeyguardManager::class.java).isDeviceLocked
        }.getOrDefault(true)
        if (localOnly || s.micPrivacyMuted || locked) return true
        val ownerAllowed = (!backgroundOwned && AgentHudBus.isAppInForeground.value) ||
            (backgroundOwned && backgroundCloudAllowed())
        return !ownerAllowed || AgentHudBus.isMainMicBusy.value
    }

    fun speak(rawText: String) {
        stop()
        if (released) return
        val s = cloudSettings?.invoke() ?: OmegaSettings()
        val clean = SarvamVoiceClient.normalizeForSarvamSpeech(
            rawText,
            SarvamVoiceClient.detectLanguageCode(rawText, s.languageTag) == "te-IN"
        )
        if (clean.isBlank()) {
            onSpeechFinishedCallback?.invoke()
            return
        }
        val geminiKey = effectiveGeminiKey(s)
        val sarvamKey = effectiveSarvamKey(s)
        val useGemini = geminiKey.isNotBlank()
        val key = if (useGemini) geminiKey else sarvamKey
        if (key.isBlank()) {
            lastSpeechSucceeded = false
            _ttsStatus.value = "Add Gemini API key for speech (Sarvam optional)"
            onSpeechFinishedCallback?.invoke()
            return
        }
        if (privacyBlocked(s)) {
            lastSpeechSucceeded = false
            _ttsStatus.value = "Speech paused for privacy / lock-screen policy"
            onSpeechFinishedCallback?.invoke()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now < cooldownUntil) {
            _ttsStatus.value = "Voice reconnecting in ${(cooldownUntil - now) / 1000L + 1L}s"
            onSpeechFinishedCallback?.invoke()
            return
        }

        val token = ++generation
        lastSpeechSucceeded = false
        _isSpeaking.value = true
        AgentHudBus.setTtsBusy(this, true)
        if (!backgroundOwned) {
            com.example.omega.OnlineBackgroundVoiceService.yieldToForeground(appContext)
        }
        val langCode = SarvamVoiceClient.detectLanguageCode(clean, s.languageTag)
        val speaker = if (useGemini) "Charon" else s.sarvamSpeaker.ifBlank { "shubh" }
        val voiceProvider = if (useGemini) "Gemini" else "Sarvam"
        val model = s.sarvamModel.ifBlank { "bulbul:v3" }
        _ttsStatus.value = "$voiceProvider ($speaker) synthesizing $langCode..."

        val monitor = object : Runnable {
            override fun run() {
                if (token != generation) return
                val curSettings = cloudSettings?.invoke() ?: s
                if (privacyBlocked(curSettings)) {
                    complete(token, success = false, status = "Voice stopped for privacy")
                } else {
                    main.postDelayed(this, 250L)
                }
            }
        }
        privacyMonitor = monitor
        main.postDelayed(monitor, 250L)

        activeJob = scope.launch {
            try {
                val timeoutMs = 24_000L
                val result = withTimeoutOrNull(timeoutMs) {
                    if (useGemini) GeminiSpeechOutput.synthesize(key, clean) else SarvamVoiceClient.synthesize(
                        apiKey = key,
                        rawText = clean,
                        languageTag = langCode,
                        speakerId = speaker,
                        modelOverride = model
                    )
                } ?: Result.failure(java.io.IOException("Voice synthesis timed out"))

                if (token != generation) return@launch
                val curSettings = cloudSettings?.invoke() ?: s
                if (privacyBlocked(curSettings)) {
                    complete(token, success = false, status = "Voice stopped for privacy")
                    return@launch
                }

                val bytes = result.getOrElse { err ->
                    val msg = err.message.orEmpty()
                    if ("HTTP 429" in msg || "timed out" in msg.lowercase()) {
                        cooldownUntil = SystemClock.elapsedRealtime() + 8_000L
                    }
                    complete(token, success = false, status = msg.ifBlank { "Voice synthesis failed" })
                    return@launch
                }

                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                if (!requestSpeechFocus(attrs) { complete(token, false, "Audio focus interrupted") }) {
                    complete(token, success = false, status = "Audio focus denied")
                    return@launch
                }

                val mp = MediaPlayer()
                mediaPlayer = mp
                mp.setAudioAttributes(attrs)
                mp.setDataSource(object : MediaDataSource() {
                    override fun getSize(): Long = bytes.size.toLong()
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        if (position < 0 || position >= bytes.size) return -1
                        val n = minOf(size, bytes.size - position.toInt())
                        bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + n)
                        return n
                    }
                    override fun close() = Unit
                })

                mp.setOnPreparedListener { player ->
                    if (token != generation) return@setOnPreparedListener
                    runCatching {
                        player.start()
                        spectrumTap = runCatching {
                            val effect = Visualizer(player.audioSessionId)
                            try {
                                effect.captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(128).coerceAtMost(1024)
                                val status = effect.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                                        if (fft != null && token == generation) AudioSpectrumBus.feedVisualizer(fft)
                                    }
                                }, minOf(Visualizer.getMaxCaptureRate(), 20000), false, true)
                                check(status == Visualizer.SUCCESS)
                                effect.enabled = true
                                effect
                            } catch (e: Exception) { effect.release(); throw e }
                        }.getOrNull()
                        _ttsStatus.value = "$voiceProvider ($speaker • $langCode) playing"
                    }.onFailure {
                        complete(token, success = false, status = "Voice playback start failed")
                    }
                }
                mp.setOnCompletionListener {
                    complete(token, success = true, status = "$voiceProvider ($speaker • $langCode) complete")
                }
                mp.setOnErrorListener { _, _, _ ->
                    complete(token, success = false, status = "Voice audio playback error")
                    true
                }

                val watchdog = Runnable {
                    if (token == generation) {
                        complete(token, success = false, status = "Voice playback timed out")
                    }
                }
                playbackWatchdog = watchdog
                main.postDelayed(watchdog, VoiceSessionPolicy.ttsTimeoutMs(clean.length))
                mp.prepareAsync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SarvamTtsController", "Speech failure: ${e.message}")
                complete(token, success = false, status = e.message ?: "Voice error")
            }
        }
    }

    private fun complete(token: Long, success: Boolean, status: String) {
        if (token != generation) return
        cleanupPlayback()
        lastSpeechSucceeded = success
        _isSpeaking.value = false
        _ttsStatus.value = status
        AgentHudBus.setTtsBusy(this, false)
        onSpeechFinishedCallback?.invoke()
    }

    fun stop() {
        ++generation
        cleanupPlayback()
        _isSpeaking.value = false
        AgentHudBus.setTtsBusy(this, false)
    }

    fun release() {
        released = true
        stop()
        scope.cancel()
    }

    private fun cleanupPlayback() {
        playbackWatchdog?.let { main.removeCallbacks(it) }
        playbackWatchdog = null
        privacyMonitor?.let { main.removeCallbacks(it) }
        privacyMonitor = null
        activeJob?.cancel()
        activeJob = null
        runCatching { spectrumTap?.enabled = false; spectrumTap?.release() }
        spectrumTap = null
        mediaPlayer?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        mediaPlayer = null
        abandonFocus()
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focus?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        } else {
            runCatching { audio.abandonAudioFocus(null) }
        }
        focus = null
    }

    @Suppress("DEPRECATION")
    private fun requestSpeechFocus(attrs: AudioAttributes, onLoss: () -> Unit): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener({ change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        onLoss()
                    }
                }, main)
                .build()
            focus = request
            runCatching { audio.requestAudioFocus(request) }
                .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            runCatching {
                audio.requestAudioFocus(
                    { change ->
                        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            main.post { onLoss() }
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
}
