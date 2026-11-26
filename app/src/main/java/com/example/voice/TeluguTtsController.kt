package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "TeluguTtsController"
private const val GOOGLE_TTS_ENGINE = "com.google.android.tts"

enum class TtsReadiness {
    INITIALIZING,
    TELUGU_READY,
    FALLBACK_LOCALE,
    UNAVAILABLE
}

class TeluguTtsController(private val context: Context) {

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<Long?>(null)
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId.asStateFlow()

    private val _readiness = MutableStateFlow(TtsReadiness.INITIALIZING)
    val readiness: StateFlow<TtsReadiness> = _readiness.asStateFlow()

    private val _availableTeluguVoices = MutableStateFlow<List<TeluguVoiceOption>>(emptyList())
    val availableTeluguVoices: StateFlow<List<TeluguVoiceOption>> = _availableTeluguVoices.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceCounter = AtomicLong(0L)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            mainHandler.post { stop() }
    }
    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(focusListener, mainHandler).build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }
    @Suppress("DEPRECATION")
    private fun releaseFocus() {
        if (!hasFocus) return
        hasFocus = false
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        else audioManager.abandonAudioFocus(focusListener)
        focusRequest = null
    }

    private var tts: TextToSpeech? = null
    private var initialized = false
    private var released = false
    private var currentConfig = TeluguTtsConfig()
    private var cachedVoices: List<Voice> = emptyList()

    @Volatile
    private var activeGroupPrefix: String? = null

    @Volatile
    private var activeFinalUtteranceId: String? = null

    @Volatile
    private var activeMessageId: Long? = null

    init {
        initEngine(preferGoogleEngine = true)
    }

    private fun initEngine(preferGoogleEngine: Boolean) {
        if (released) return
        try {
            val probeEngine = if (preferGoogleEngine) {
                TextToSpeech(context.applicationContext, { status ->
                    mainHandler.post {
                        if (released) return@post
                        if (status == TextToSpeech.SUCCESS) {
                            onEngineReady()
                        } else {
                            Log.w(TAG, "Preferred Google TTS init failed ($status); falling back to default engine")
                            releaseEngineOnly()
                            initEngine(preferGoogleEngine = false)
                        }
                    }
                }, GOOGLE_TTS_ENGINE)
            } else {
                TextToSpeech(context.applicationContext) { status ->
                    mainHandler.post {
                        if (released) return@post
                        if (status == TextToSpeech.SUCCESS) {
                            onEngineReady()
                        } else {
                            Log.w(TAG, "Default TextToSpeech init failed with status=$status")
                            initialized = false
                            _readiness.value = TtsReadiness.UNAVAILABLE
                        }
                    }
                }
            }
            tts = probeEngine
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech creation threw exception", e)
            if (preferGoogleEngine) {
                initEngine(preferGoogleEngine = false)
            } else {
                _readiness.value = TtsReadiness.UNAVAILABLE
            }
        }
    }

    private fun onEngineReady() {
        val engine = tts ?: return
        initialized = true

        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )

        configureTeluguLocaleAndVoices(engine)
        applyConfigInternal(engine, currentConfig)
        attachProgressListener(engine)
    }

    private fun configureTeluguLocaleAndVoices(engine: TextToSpeech) {
        val teluguLocale = Locale.forLanguageTag("te-IN")
        val langResult = engine.setLanguage(teluguLocale)

        discoverTeluguVoices(engine)

        if (langResult == TextToSpeech.LANG_MISSING_DATA ||
            langResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.w(TAG, "te-IN TTS voice missing or unsupported (code=$langResult), checking en-IN fallback")
            val fallbackResult = engine.setLanguage(Locale.forLanguageTag("en-IN"))
            _readiness.value = if (
                fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
                fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                TtsReadiness.UNAVAILABLE
            } else {
                TtsReadiness.FALLBACK_LOCALE
            }
        } else {
            _readiness.value = TtsReadiness.TELUGU_READY
        }
    }

    private fun discoverTeluguVoices(engine: TextToSpeech) {
        val voices = try {
            engine.voices?.filter { voice ->
                voice.locale?.language.equals("te", ignoreCase = true)
            }.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query TTS voices", e)
            emptyList()
        }

        val sorted = voices.sortedWith(
            compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
                .thenBy { it.name }
        )

        cachedVoices = sorted
        _availableTeluguVoices.value = sorted.mapIndexed { index, voice ->
            val qualityBadge = when {
                voice.quality >= Voice.QUALITY_VERY_HIGH -> "Neural HD"
                voice.quality >= Voice.QUALITY_HIGH -> "High"
                else -> "Standard"
            }
            val modeBadge = if (voice.isNetworkConnectionRequired) "Online" else "Offline"
            TeluguVoiceOption(
                name = voice.name,
                displayLabel = "Telugu Voice ${index + 1} ($qualityBadge • $modeBadge)",
                isNetworkRequired = voice.isNetworkConnectionRequired,
                qualityScore = voice.quality
            )
        }
    }

    private fun attachProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val prefix = activeGroupPrefix ?: return
                if (utteranceId != null && utteranceId.startsWith(prefix)) {
                    _isSpeaking.value = true
                    _speakingMessageId.value = activeMessageId
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null && utteranceId == activeFinalUtteranceId) {
                    _isSpeaking.value = false
                    _speakingMessageId.value = null
                    activeGroupPrefix = null
                    activeFinalUtteranceId = null
                    activeMessageId = null
                    mainHandler.post { if (activeGroupPrefix == null) releaseFocus() }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val prefix = activeGroupPrefix
                if (prefix != null && utteranceId != null && utteranceId.startsWith(prefix)) {
                    _isSpeaking.value = false
                    _speakingMessageId.value = null
                    activeGroupPrefix = null
                    activeFinalUtteranceId = null
                    activeMessageId = null
                    mainHandler.post { if (activeGroupPrefix == null) releaseFocus() }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "TTS utterance error code=$errorCode")
                val prefix = activeGroupPrefix
                if (prefix != null && utteranceId != null && utteranceId.startsWith(prefix)) {
                    _isSpeaking.value = false
                    _speakingMessageId.value = null
                    activeGroupPrefix = null
                    activeFinalUtteranceId = null
                    activeMessageId = null
                    mainHandler.post { if (activeGroupPrefix == null) releaseFocus() }
                }
            }
        })
    }

    fun applyConfig(config: TeluguTtsConfig) {
        currentConfig = config
        val engine = tts
        if (initialized && engine != null) {
            applyConfigInternal(engine, config)
        }
    }

    private fun applyConfigInternal(engine: TextToSpeech, config: TeluguTtsConfig) {
        val desiredVoice = config.selectedVoiceName?.let { name ->
            cachedVoices.firstOrNull { it.name == name }
        } ?: cachedVoices.firstOrNull()

        if (desiredVoice != null) {
            try {
                engine.voice = desiredVoice
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply Telugu voice ${desiredVoice.name}", e)
            }
        }

        // Apply pitch and rate after selecting voice so engine voice switch does not reset prosody
        engine.setPitch(config.pitch.coerceIn(0.75f, 1.35f))
        engine.setSpeechRate(config.speechRate.coerceIn(0.75f, 1.40f))
    }

    fun refreshVoices() {
        val engine = tts
        if (initialized && engine != null) {
            configureTeluguLocaleAndVoices(engine)
            applyConfigInternal(engine, currentConfig)
        }
    }

    fun speak(text: String, messageId: Long? = null): Boolean {
        val engine = tts
        if (!initialized || engine == null || _readiness.value == TtsReadiness.UNAVAILABLE) {
            Log.w(TAG, "TTS unavailable; skipping speech output")
            return false
        }

        val formatted = TeluguSpeechFormatter.formatForTeluguSpeech(text)
        if (formatted.isBlank()) return false

        applyConfigInternal(engine, currentConfig)
        val wanted = if (formatted.any { it in '\u0C00'..'\u0C7F' }) "te-IN" else "en-IN"
        if (engine.isLanguageAvailable(Locale.forLanguageTag(wanted)) >= 0) {
            engine.language = Locale.forLanguageTag(wanted)
            if (wanted == "te-IN") applyConfigInternal(engine, currentConfig)
            engine.setPitch(currentConfig.pitch.coerceIn(0.75f, 1.35f))
            engine.setSpeechRate(currentConfig.speechRate.coerceIn(0.75f, 1.40f))
        }

        val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceIn(500, 3800)
        val chunks = splitForSpeech(formatted, maxLen)
        if (chunks.isEmpty()) return false
        if (!runCatching { requestFocus() }.getOrDefault(false)) { Log.w(TAG, "Audio focus denied"); return false }

        val groupPrefix = "grp_${utteranceCounter.incrementAndGet()}_"
        activeGroupPrefix = groupPrefix
        activeFinalUtteranceId = "$groupPrefix${chunks.lastIndex}"
        activeMessageId = messageId

        _speakingMessageId.value = messageId
        _isSpeaking.value = true

        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = "$groupPrefix$index"
            val result = engine.speak(chunk, mode, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                Log.w(TAG, "TTS speak() returned ERROR on chunk $index")
                _isSpeaking.value = false
                _speakingMessageId.value = null
                activeGroupPrefix = null
                activeFinalUtteranceId = null
                activeMessageId = null
                engine.stop()
                releaseFocus()
                return false
            }
        }
        return true
    }

    private fun splitForSpeech(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + maxLen).coerceAtMost(text.length)
            if (end == text.length) {
                chunks += text.substring(start).trim()
                break
            }
            val splitAt = text.lastIndexOfAny(charArrayOf('.', '!', '?', ',', ' '), end - 1)
            val cut = if (splitAt > start + maxLen / 2) splitAt + 1 else end
            val piece = text.substring(start, cut).trim()
            if (piece.isNotEmpty()) {
                chunks += piece
            }
            start = cut
        }
        return chunks
    }

    fun stop() {
        activeGroupPrefix = null
        activeFinalUtteranceId = null
        activeMessageId = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        } finally {
            _isSpeaking.value = false
            _speakingMessageId.value = null
            releaseFocus()
        }
    }

    fun createInstallVoiceDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun releaseEngineOnly() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down intermediate TTS", e)
        } finally {
            tts = null
            initialized = false
        }
    }

    fun release() {
        released = true
        mainHandler.removeCallbacksAndMessages(null)
        stop()
        releaseEngineOnly()
    }
}
