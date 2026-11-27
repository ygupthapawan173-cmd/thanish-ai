package com.example.voice

import android.content.Context
import com.example.control.AgentHudBus
import android.os.Handler
import android.os.Looper
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.util.Log
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.TtsEmotionStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import android.media.MediaPlayer
import android.media.MediaDataSource
import com.example.data.OmegaSettings
import com.example.data.AiProvider
import com.example.ai.SmartModelPolicy
import kotlinx.coroutines.*

class TeluguTtsController(context: Context, private val backgroundOwned: Boolean = false, private val cloudSettings: (() -> OmegaSettings)? = null, private val backgroundCloudAllowed: () -> Boolean = { false }) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var localOnly = false
    private val _voices = MutableStateFlow<List<TtsVoiceChoice>>(emptyList())
    val voices: StateFlow<List<TtsVoiceChoice>> = _voices.asStateFlow()
    fun refreshVoices() { if(isReady) tts?.let { _voices.value=VoicePackCatalog.choices(it, engineRequest) } }
    private var initFailed = false
    private var released = false
    var lastSpeechSucceeded = false
        private set
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null
    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            focus?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        } else {
            runCatching { audio.abandonAudioFocus(null) }
        }
        focus = null
    }
    @Suppress("DEPRECATION")
    private fun requestSpeechFocus(attrs: AudioAttributes, onLoss: () -> Unit): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener({ change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onLoss()
                }, main)
                .build()
            focus = request
            runCatching { audio.requestAudioFocus(request) }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            runCatching {
                audio.requestAudioFocus(
                    { change -> if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) main.post { onLoss() } },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
    private fun complete(id: String?, error: String? = null) {
        if (id == null || id !in utteranceIds) return
        if (error == null && id != finalUtteranceId) { utteranceIds.remove(id); return }
        val canRetry = utteranceIds.size == initialUtteranceCount
        utteranceIds.clear(); finalUtteranceId = null
        if (error != null) runCatching { tts?.stop() }
        cancelWatchdog()
        val retryText=activeText
        val failedVoice=activeVoice
        activeId=null; activeText=null; activeVoice=null; lastSpeechSucceeded=error==null; _isSpeaking.value=false; abandonFocus(); AgentHudBus.setTtsBusy(this,false)
        if (error != null) { _ttsStatus.value=error; Log.w("OmegaVoice", "TTS_ERROR $error") }
        else Log.i("OmegaVoice", "TTS_DONE")
        if(canRetry && error != null && (error.startsWith("TTS error") || error == "TTS playback failed") && excludedVoice==null && failedVoice!=null && retryText!=null) {
            excludedVoice=failedVoice
            speakInternal(retryText)
            return
        }
        excludedVoice=null
        onSpeechFinishedCallback?.invoke()
    }
    private var pendingUtterance: String? = null
    private var pendingConfig: (() -> Unit)? = null
    private var lastConfig: (() -> Unit)? = null
    private var engineRequest = ""
    private var engineGeneration = 0L
    private var activeId: String? = null
    private val utteranceIds = linkedSetOf<String>()
    private var initialUtteranceCount = 0
    private var finalUtteranceId: String? = null
    private var playbackWatchdog: Runnable? = null
    private fun cancelWatchdog() { playbackWatchdog?.let { main.removeCallbacks(it) }; playbackWatchdog = null }
    private var activeText: String? = null
    private var activeVoice: String? = null
    private var excludedVoice: String? = null
    private val main = Handler(Looper.getMainLooper())
    private var onSpeechFinishedCallback: (() -> Unit)? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsStatus = MutableStateFlow("Initializing TTS...")
    val ttsStatus: StateFlow<String> = _ttsStatus.asStateFlow()

    init { initializeEngine("") }

    private fun initializeEngine(requested: String) {
        val token = ++engineGeneration
        tts?.shutdown(); tts = null
        engineRequest = requested
        isReady = false; initFailed = false
        _voices.value = emptyList()
        _ttsStatus.value = "Loading voice engine: ${requested.ifBlank { "Android default" }}"
        pendingConfig = lastConfig
        val listener = TextToSpeech.OnInitListener { status -> main.post {
            if (released || token != engineGeneration) return@post
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val res = tts?.setLanguage(Locale.forLanguageTag("en-IN"))
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.US
                }
                _ttsStatus.value = "Speaker TTS Ready • ${engineRequest.ifBlank { "Android default" }}"
                refreshVoices()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { main.post { if (utteranceId != null && utteranceId in utteranceIds) _isSpeaking.value = true } }
                    override fun onDone(utteranceId: String?) {
                        main.post { complete(utteranceId) }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { main.post { complete(utteranceId, "TTS playback failed") } }
                    override fun onError(utteranceId: String?, errorCode: Int) { main.post { complete(utteranceId, "TTS error $errorCode") } }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { main.post { complete(utteranceId, "TTS interrupted") } }
                })
                pendingConfig?.invoke(); pendingConfig=null
                pendingUtterance?.let { queued ->
                    pendingUtterance = null
                    speakInternal(queued)
                }
            } else {
                initFailed=true
                _ttsStatus.value = "TTS init failed (code $status). Check Android Text-to-Speech settings."
                if (pendingUtterance != null) { pendingUtterance=null; onSpeechFinishedCallback?.invoke() }
            }
        } }
        try {
            tts = if (requested.isBlank()) TextToSpeech(appContext, listener) else TextToSpeech(appContext, listener, requested)
        } catch (_: Exception) {
            initFailed = true
            _ttsStatus.value = "Voice engine could not be loaded. Check Android voice settings."
        }
    }

    fun setOnSpeechFinished(callback: (() -> Unit)?) { onSpeechFinishedCallback = callback }

    fun configure(localeTag: String, speed: Float, pitch: Float, style: TtsEmotionStyle, localOnly: Boolean = false) {
        this.localOnly=localOnly
        lastConfig = { configure(localeTag,speed,pitch,style,localOnly) }
        if (!isReady) { pendingConfig={ configure(localeTag,speed,pitch,style,localOnly) }; return }
        val tag = if (localeTag == "auto") "en-IN" else localeTag
        val res = tts?.setLanguage(Locale.forLanguageTag(tag))
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.language = Locale.getDefault()
        }
        tts?.setSpeechRate(style.speed(speed))
        tts?.setPitch(style.pitch(pitch))
    }

    private val cloudScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cloudJob: Job? = null
    private var cloudPlayer: MediaPlayer? = null
    private var cloudGeneration = 0L
    private var cloudCooldownUntil = 0L
    private var cloudPrivacyCheck: Runnable? = null
    private fun cloudAllowed(s: OmegaSettings, length: Int): Boolean = CloudVoicePolicy.allowed(
        s.gatewayBaseUrl, CloudVoicePreferences.enabled(appContext), s.activeProvider == AiProvider.OPENROUTER,
        s.smartMode == SmartModelPolicy.Mode.FREE, s.providerBudgetUsd,
        localOnly || s.micPrivacyMuted || appContext.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked,
        ((!backgroundOwned && AgentHudBus.isAppInForeground.value) || (backgroundOwned && backgroundCloudAllowed())) && !AgentHudBus.isMainMicBusy.value,
        s.effectiveOpenRouterKey().isNotBlank(), length)
    private fun geminiAllowed(s: OmegaSettings, length: Int): Boolean = GeminiSpeechPolicy.allowed(
        GeminiSpeechPreferences.enabled(appContext), s.activeProvider == AiProvider.OPENROUTER, s.useGeminiLiveAudio,
        localOnly || s.micPrivacyMuted || appContext.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked,
        ((!backgroundOwned && AgentHudBus.isAppInForeground.value) || (backgroundOwned && backgroundCloudAllowed())) && !AgentHudBus.isMainMicBusy.value,
        s.geminiApiKey.isNotBlank(), length)
    private fun stopCloud() {
        ++cloudGeneration
        cloudPrivacyCheck?.let { main.removeCallbacks(it) }; cloudPrivacyCheck = null
        cloudJob?.cancel(); cloudJob = null
        cloudPlayer?.let { runCatching { it.release() } }; cloudPlayer = null
    }
    private fun startCloud(raw: String): Boolean {
        val s = cloudSettings?.invoke() ?: return false
        val clean = raw.replace("*", "").replace("#", "").replace("`", "").trim()
        val gemini = GeminiSpeechPreferences.enabled(appContext)
        if (released || android.os.SystemClock.elapsedRealtime() < cloudCooldownUntil || !(if (gemini) geminiAllowed(s, clean.length) else cloudAllowed(s, clean.length))) return false
        val generation = ++cloudGeneration
        val choice = CloudVoicePolicy.select(clean, CloudVoicePreferences.voice(appContext, "en"), CloudVoicePreferences.voice(appContext, "te"), CloudVoicePreferences.hd(appContext))
        val speaker = if (gemini) GeminiSpeechPreferences.voice(appContext, InstalledSpeechPolicy.language(clean)) else choice.voice
        val provider = if (gemini) "Gemini" else "AICredits"
        lastSpeechSucceeded = false
        _isSpeaking.value = true; AgentHudBus.setTtsBusy(this,true)
        _ttsStatus.value = "$provider voice: requesting $speaker"
        var started = false
        var activeModel = if (gemini) GeminiSpeechPreferences.lastActiveModel(appContext) else choice.model
        var lastCloudError: Exception? = null
        fun stillAllowed(): Boolean = cloudSettings?.invoke()?.let { if (gemini) geminiAllowed(it, clean.length) && it.geminiApiKey == s.geminiApiKey else !GeminiSpeechPreferences.enabled(appContext) && cloudAllowed(it, clean.length) && it.effectiveOpenRouterKey() == s.effectiveOpenRouterKey() } == true
        fun finish(success: Boolean, fallback: Boolean) {
            if (generation != cloudGeneration) return
            stopCloud(); cancelWatchdog(); abandonFocus()
            _isSpeaking.value = false; AgentHudBus.setTtsBusy(this,false)
            lastSpeechSucceeded = success
            if (success) {
                com.example.data.ApiQuotaTracker.recordTtsUsage(activeModel, clean.length, fallbackUsed = false)
                _ttsStatus.value = "$provider ($activeModel): $speaker • completed"; onSpeechFinishedCallback?.invoke()
            } else {
                com.example.data.ApiQuotaTracker.recordTtsUsage(activeModel, clean.length, fallbackUsed = fallback)
                val err = lastCloudError
                val isQuota = err is GeminiSpeechHttpException && GeminiSpeechPolicy.isQuotaError(err.httpCode, err.message)
                val cooldownMs = if (isQuota) GeminiSpeechPolicy.QUOTA_COOLDOWN_MS else GeminiSpeechPolicy.TRANSIENT_COOLDOWN_MS
                _ttsStatus.value = if (isQuota) "Gemini TTS quota reached — offline voice fallback active (auto-recovery in ${cooldownMs / 1000}s)" else "Cloud speech unavailable — offline voice fallback"
                if (fallback && !started && stillAllowed()) { cloudCooldownUntil = android.os.SystemClock.elapsedRealtime() + cooldownMs; speakInternal(clean) }
                else onSpeechFinishedCallback?.invoke()
            }
        }
        val privacyCheck = object : Runnable {
            override fun run() {
                if (generation != cloudGeneration) return
                if (!stillAllowed()) finish(false,false) else main.postDelayed(this,250L)
            }
        }
        cloudPrivacyCheck = privacyCheck; main.postDelayed(privacyCheck,250L)
        cloudJob = cloudScope.launch {
            try {
                val bytes = if (gemini) {
                    val candidates = GeminiSpeechPolicy.candidateModels(
                        GeminiSpeechPreferences.discoveredModels(appContext),
                        GeminiSpeechPreferences.lastActiveModel(appContext)
                    )
                    val res = GeminiSpeechClient.synthesizeWithAutoSwitch(s.geminiApiKey.trim(), clean, speaker, candidates)
                    activeModel = res.usedModel
                    GeminiSpeechPreferences.lastActiveModel(appContext, res.usedModel)
                    res.wavBytes
                } else CloudVoiceClient.synthesize(s.effectiveOpenRouterKey(), clean, choice)
                if (generation != cloudGeneration) return@launch
                if (!stillAllowed()) { finish(false,false); return@launch }
                val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                if (!requestSpeechFocus(attrs) { finish(false,false) }) { finish(false,false); return@launch }
                val player = MediaPlayer(); cloudPlayer = player
                player.setAudioAttributes(attrs)
                player.setDataSource(object : MediaDataSource() {
                    override fun getSize(): Long = bytes.size.toLong()
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        if (position < 0 || position >= bytes.size) return -1
                        val n = minOf(size, bytes.size - position.toInt())
                        bytes.copyInto(buffer, offset, position.toInt(), position.toInt()+n)
                        return n
                    }
                    override fun close() { }
                })
                player.setOnPreparedListener {
                    if (generation == cloudGeneration) {
                        if (!stillAllowed()) finish(false,false)
                        else runCatching { it.start(); started = true; runCatching { it.playbackParams = it.playbackParams.setSpeed(s.ttsSpeed.coerceIn(0.75f,1.5f)) }; _ttsStatus.value = "$provider voice: $speaker • playing" }.onFailure { finish(false,true) }
                    }
                }
                player.setOnCompletionListener { finish(true,false) }
                player.setOnErrorListener { _, _, _ -> finish(false,true); true }
                val watchdog = Runnable { finish(false, !started) }
                playbackWatchdog = watchdog; main.postDelayed(watchdog, VoiceSessionPolicy.ttsTimeoutMs(clean.length))
                player.prepareAsync()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { lastCloudError = e; finish(false,true) }
        }
        return true
    }
    fun speak(rawText: String) {
        stop()
        excludedVoice=null
        if (!startCloud(rawText)) speakInternal(rawText)
    }

    private fun speakInternal(rawText: String) {
        lastSpeechSucceeded=false
        val clean = rawText.replace("*", "").replace("#", "").replace("`", "").replace("•", ",").trim()
        if (clean.isBlank() || released) { onSpeechFinishedCallback?.invoke(); return }
        val language = InstalledSpeechPolicy.language(clean)
        val hasTeluguChars = language == "te"
        val requested = VoicePackCatalog.engine(appContext, language)
        if (requested.isNotBlank() && VoicePackCatalog.engines(appContext).none { it.id == requested }) {
            _ttsStatus.value = "Selected $language voice engine missing; install it or select Android default."
            onSpeechFinishedCallback?.invoke(); return
        }
        if (requested != engineRequest || initFailed) initializeEngine(requested)
        if (initFailed) { onSpeechFinishedCallback?.invoke(); return }
        if (!isReady) {
            pendingUtterance = clean
            val token = engineGeneration
            main.postDelayed({ if (!isReady && token == engineGeneration && pendingUtterance == clean) { pendingUtterance=null; _ttsStatus.value="TTS initialization timed out"; onSpeechFinishedCallback?.invoke() } }, 15000L)
            return
        }
        val locked = appContext.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked
        val allowNetwork=InstalledSpeechPolicy.networkVoiceAllowed(CloudVoicePreferences.enabled(appContext),localOnly,locked)
        val engine=tts ?: run { onSpeechFinishedCallback?.invoke(); return }
        val preferred=VoicePackCatalog.selected(appContext,language)
        val candidates=runCatching { engine.voices.orEmpty().filter { it.name!=excludedVoice && InstalledSpeechPolicy.voiceEligible(language,it.locale.language,VoicePackCatalog.needsData(it),it.isNetworkConnectionRequired,allowNetwork) } }.getOrDefault(emptyList())
            .sortedWith(compareByDescending<android.speech.tts.Voice> { it.name==preferred }.thenBy { it.isNetworkConnectionRequired }.thenByDescending { it.locale.country=="IN" }.thenByDescending { it.quality })
        val chosen=candidates.firstOrNull { runCatching { engine.setVoice(it)==TextToSpeech.SUCCESS }.getOrDefault(false) } ?: if(excludedVoice==null && candidates.isEmpty()) {
            runCatching {
                engine.setLanguage(Locale.forLanguageTag(if(hasTeluguChars) "te-IN" else "en-IN"))
                engine.voice?.takeIf { it.locale.language==language && !VoicePackCatalog.needsData(it) && (allowNetwork || !it.isNetworkConnectionRequired) }
            }.getOrNull()
        } else null
        if(chosen==null) {
            _ttsStatus.value="${if(hasTeluguChars) "Telugu" else "English"} voice unavailable; install voice data in Voice Packs"
            onSpeechFinishedCallback?.invoke()
            return
        }
        _ttsStatus.value="${chosen.locale.toLanguageTag()} • ${chosen.name} • ${if(chosen.isNetworkConnectionRequired) "Online" else "Offline"}"
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val chunks=TtsChunkPolicy.split(clean, TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(2))
        val ids=chunks.map { UUID.randomUUID().toString() }
        val id=ids.last(); activeId=id; activeText=clean; activeVoice=chosen.name
        utteranceIds.addAll(ids); initialUtteranceCount=ids.size; finalUtteranceId=id
        AgentHudBus.setTtsBusy(this,true)
        if (!backgroundOwned) com.example.omega.OmegaWakeService.pauseForManual()
        abandonFocus()
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        if (!requestSpeechFocus(attrs) { main.post { if (activeId==id) { tts?.stop(); complete(id,"TTS audio focus lost") } } }) { complete(id,"TTS audio focus denied"); return }
        val watchdog = Runnable { if (activeId == id) { runCatching { tts?.stop() }; complete(id, "TTS playback timed out") } }
        playbackWatchdog = watchdog
        main.postDelayed(watchdog, VoiceSessionPolicy.ttsTimeoutMs(clean.length))
        main.postDelayed({ if(activeId==id) {
            Log.i("OmegaVoice", "TTS_START")
            for ((index, chunk) in chunks.withIndex()) {
                if ((!CloudVoicePreferences.enabled(appContext) || !VoicePackPolicy.allowNetworkVoice(localOnly, appContext.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked)) && chosen.isNetworkConnectionRequired) { complete(id,"TTS privacy policy blocked network voice"); break }
                if(runCatching { tts?.speak(chunk, if(index==0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, ids[index]) }.getOrNull()!=TextToSpeech.SUCCESS) { complete(id,"TTS playback failed"); break }
            }
        } }, 150)
    }

    fun stop() {
        stopCloud()
        cancelWatchdog()
        pendingUtterance = null
        utteranceIds.clear(); finalUtteranceId=null; activeId=null; activeText=null; activeVoice=null; excludedVoice=null; abandonFocus(); AgentHudBus.setTtsBusy(this,false)
        tts?.stop()
        _isSpeaking.value = false
    }

    fun release() {
        ++engineGeneration; released=true; stop(); cloudScope.cancel(); tts?.shutdown(); tts = null
    }
}
