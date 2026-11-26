package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import com.example.omega.OmegaRecoveryPolicy
import com.example.omega.OmegaVoiceMode
import com.example.omega.OmegaVoicePreferences
import com.example.omega.OmegaWakeService
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.control.PhoneCommandParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "VoiceInputController"

sealed interface VoiceInputState {
    data object Idle : VoiceInputState
    data class Listening(
        val partialText: String = "",
        val amplitude: Float = 0.15f
    ) : VoiceInputState
}

enum class SpeechLocaleOption(val tag: String, val shortLabel: String) {
    AUTO("te-IN", "TE+EN"),
    TELUGU("te-IN", "తెలుగు"),
    ENGLISH_INDIA("en-IN", "ENG")
}

class VoiceInputController(
    private val context: Context,
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        val engineStatus = MutableStateFlow("Idle — no speech session")
        val detectedLanguage = MutableStateFlow("Not reported")
        val lastSpeechError = MutableStateFlow("None")
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context) ||
            (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

    fun startListening(locale: SpeechLocaleOption = SpeechLocaleOption.TELUGU, forcePlatform: Boolean = false) {
        mainHandler.post {
            if (!forcePlatform) { detectedLanguage.value = "Not reported"; lastSpeechError.value = "None" }
            if (OmegaWakeService.active.value) {
                engineStatus.value = "Wake microphone owns recording"
                onError("Stop OMEGA offline listening before using chat microphone.")
                return@post
            }
            val mode = OmegaVoicePreferences.get(context)
            val availableLocal = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            val local = !forcePlatform && availableLocal && mode != OmegaVoiceMode.PLATFORM
            if (mode == OmegaVoiceMode.LOCAL && !local) {
                engineStatus.value = "On-device service unavailable"
                lastSpeechError.value = "Local recognition unavailable"
                onError("On-device recognition unavailable. Check language packs.")
                return@post
            }
            if (!local && !SpeechRecognizer.isRecognitionAvailable(context)) {
                engineStatus.value = "Platform speech service unavailable"
                _state.value = VoiceInputState.Idle
                onError(
                    "బాస్, ఈ డివైజ్‌లో వాయిస్ రికగ్నిషన్ సర్వీస్ అందుబాటులో లేదు. కింద టైప్ చేసి మాట్లాడండి!"
                )
                return@post
            }

            try {
                destroyRecognizerInternal()

                val recognizer = if (local && Build.VERSION.SDK_INT >= 31)
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext)
                else SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                speechRecognizer = recognizer
                engineStatus.value = (if (local) "On-device" else "Android platform") + " · " + locale.shortLabel + " · starting"

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (speechRecognizer !== recognizer) return
                        engineStatus.value = (if (local) "On-device" else "Android platform") + " · listening"
                        _state.value = VoiceInputState.Listening(partialText = "", amplitude = 0f)
                    }

                    override fun onBeginningOfSpeech() {
                        if (speechRecognizer !== recognizer) return
                        _state.value = VoiceInputState.Listening(
                            partialText = (_state.value as? VoiceInputState.Listening)?.partialText.orEmpty(),
                            amplitude = 0.45f
                        )
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (speechRecognizer !== recognizer) return
                        val current = _state.value
                        if (current is VoiceInputState.Listening) {
                            // Map typical SpeechRecognizer dB range [-2f..10f] into 0.15f..1.0f
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.15f, 1f)
                            _state.value = current.copy(amplitude = normalized)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        if (speechRecognizer !== recognizer) return
                        val current = _state.value
                        if (current is VoiceInputState.Listening) {
                            _state.value = current.copy(amplitude = 0.15f)
                        }
                    }

                    override fun onError(error: Int) {
                        if (speechRecognizer !== recognizer) return
                        Log.w(TAG, "SpeechRecognizer error code: $error")
                        lastSpeechError.value = "SpeechRecognizer error " + error
                        engineStatus.value = "Speech recognition failed"
                        _state.value = VoiceInputState.Idle
                        destroyRecognizerInternal()
                        // HYBRID permits one platform fallback. LOCAL mode never uploads speech.
                        if (OmegaRecoveryPolicy.platformFallback(mode, local, forcePlatform,
                                SpeechRecognizer.isRecognitionAvailable(context), error)) {
                            engineStatus.value = "Hybrid retry via Android platform"
                            startListening(locale, forcePlatform = true)
                            return
                        }
                        val message = mapErrorToMessage(error)
                        if (message != null) {
                            onError(message)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        if (speechRecognizer !== recognizer) return
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() }
                            .orEmpty()
                        val transcript = matches.firstOrNull { PhoneCommandParser.parse(it) != null }
                            ?: matches.firstOrNull().orEmpty()
                        _state.value = VoiceInputState.Idle
                        destroyRecognizerInternal()

                        if (transcript.isNotBlank()) {
                            engineStatus.value = "Speech result received"
                            onFinalResult(transcript)
                        } else {
                            onError("అయ్యో బాస్, సరిగ్గా వినిపించలేదు! మళ్ళీ చెప్పండి.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (speechRecognizer !== recognizer) return
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim().orEmpty()
                        val current = _state.value
                        if (current is VoiceInputState.Listening && partial.isNotBlank()) {
                            _state.value = current.copy(partialText = partial)
                        }
                    }

                    override fun onLanguageDetection(results: Bundle) {
                        if (speechRecognizer !== recognizer) return
                        if (Build.VERSION.SDK_INT >= 34) {
                            detectedLanguage.value = results.getString(SpeechRecognizer.DETECTED_LANGUAGE)
                                ?.take(35) ?: "Not reported by speech service"
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.tag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.tag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, local)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    if (Build.VERSION.SDK_INT >= 34 && locale == SpeechLocaleOption.AUTO) {
                        val supported = arrayListOf("te-IN", "en-IN")
                        putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                        putStringArrayListExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, supported
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                            RecognizerIntent.LANGUAGE_SWITCH_BALANCED
                        )
                        putStringArrayListExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, supported
                        )
                    }
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                _state.value = VoiceInputState.Listening(partialText = "", amplitude = 0.2f)
                recognizer.startListening(intent)
                mainHandler.postDelayed({
                    if (speechRecognizer === recognizer) {
                        destroyRecognizerInternal()
                        _state.value = VoiceInputState.Idle
                        engineStatus.value = "Speech timeout"
                        lastSpeechError.value = "35-second session timeout"
                        onError("Voice session timed out. Tap the microphone to try again.")
                    }
                }, 35000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                engineStatus.value = "Speech service start failed"
                lastSpeechError.value = e.javaClass.simpleName
                _state.value = VoiceInputState.Idle
                destroyRecognizerInternal()
                onError("మైక్ స్టార్ట్ చేయడంలో సమస్య వచ్చింది బాస్. కింద టైప్ చేయొచ్చు!")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            val recognizer = speechRecognizer
            if (recognizer == null) {
                _state.value = VoiceInputState.Idle
                return@post
            }
            try {
                recognizer.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer", e)
                _state.value = VoiceInputState.Idle
                destroyRecognizerInternal()
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            _state.value = VoiceInputState.Idle
            destroyRecognizerInternal()
        }
    }

    fun release() {
        cancel()
    }

    private fun destroyRecognizerInternal() {
        val old = speechRecognizer
        speechRecognizer = null
        try {
            old?.cancel()
            old?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying SpeechRecognizer", e)
        } finally {
            speechRecognizer = null
        }
    }

    private fun mapErrorToMessage(error: Int): String? {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO ->
                "బాస్, ఆడియో రికార్డింగ్‌లో చిన్న సమస్య వచ్చింది. మళ్ళీ ట్రై చేయండి."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "మైక్రోఫోన్ పర్మిషన్ అవసరం బాస్!"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "వాయిస్ గుర్తించడానికి ఇంటర్నెట్ కనెక్షన్ కావాలి బాస్. నెట్ చెక్ చేసి మళ్ళీ ట్రై చేయండి."
            SpeechRecognizer.ERROR_NO_MATCH ->
                "అయ్యో బాస్, మీ మాట సరిగ్గా అర్థం కాలేదు! ఇంకొంచెం స్పష్టంగా చెప్పండి."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "బాస్, మీరు ఇంకా మాట్లాడలేదు అనుకుంటా! మైక్ మళ్ళీ నొక్కి చెప్పండి."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "మైక్ కాస్త బిజీగా ఉంది బాస్, ఒక సెకను ఆగి మళ్ళీ నొక్కండి."
            SpeechRecognizer.ERROR_CLIENT -> "Speech service client error (5). Check engine and try again."
            else ->
                "వాయిస్ వినడంలో సమస్య వచ్చింది (కోడ్: $error). కింద టైప్ చేయండి బాస్!"
        }
    }
}
