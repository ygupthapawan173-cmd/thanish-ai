package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
    TELUGU("te-IN", "తెలుగు"),
    ENGLISH_INDIA("en-IN", "ENG")
}

class VoiceInputController(
    private val context: Context,
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(locale: SpeechLocaleOption = SpeechLocaleOption.TELUGU) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _state.value = VoiceInputState.Idle
                onError(
                    "బాస్, ఈ డివైజ్‌లో వాయిస్ రికగ్నిషన్ సర్వీస్ అందుబాటులో లేదు. కింద టైప్ చేసి మాట్లాడండి!"
                )
                return@post
            }

            try {
                destroyRecognizerInternal()

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = VoiceInputState.Listening(partialText = "", amplitude = 0.2f)
                    }

                    override fun onBeginningOfSpeech() {
                        _state.value = VoiceInputState.Listening(
                            partialText = (_state.value as? VoiceInputState.Listening)?.partialText.orEmpty(),
                            amplitude = 0.45f
                        )
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val current = _state.value
                        if (current is VoiceInputState.Listening) {
                            // Map typical SpeechRecognizer dB range [-2f..10f] into 0.15f..1.0f
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.15f, 1f)
                            _state.value = current.copy(amplitude = normalized)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        val current = _state.value
                        if (current is VoiceInputState.Listening) {
                            _state.value = current.copy(amplitude = 0.15f)
                        }
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "SpeechRecognizer error code: $error")
                        _state.value = VoiceInputState.Idle
                        destroyRecognizerInternal()
                        val message = mapErrorToMessage(error)
                        if (message != null) {
                            onError(message)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() }
                            .orEmpty()
                        val transcript = matches.firstOrNull { PhoneCommandParser.parse(it) != null }
                            ?: matches.firstOrNull().orEmpty()
                        _state.value = VoiceInputState.Idle
                        destroyRecognizerInternal()

                        if (transcript.isNotBlank()) {
                            onFinalResult(transcript)
                        } else {
                            onError("అయ్యో బాస్, సరిగ్గా వినిపించలేదు! మళ్ళీ చెప్పండి.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim().orEmpty()
                        val current = _state.value
                        if (current is VoiceInputState.Listening && partial.isNotBlank()) {
                            _state.value = current.copy(partialText = partial)
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
                    // Do not force an offline model: many Android devices need Google's
                    // online speech service for Telugu (te-IN) recognition.
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    // Android 13+ can use phrase hints to improve recognition of app-specific
                    // names and common Telugu/English phone commands.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        putStringArrayListExtra(
                            RecognizerIntent.EXTRA_BIASING_STRINGS,
                            arrayListOf(
                                "ZENO", "Hey Assistant", "హాయ్", "నమస్కారం",
                                "వాట్సాప్ ఓపెన్ చేయి", "యూట్యూబ్ ఓపెన్ చేయి",
                                "కెమెరా ఓపెన్ చేయి", "సెట్టింగ్స్ ఓపెన్ చేయి",
                                "వైఫై ఆన్ చేయి", "బ్లూటూత్ ఆన్ చేయి",
                                "వాల్యూమ్ పెంచు", "బ్రైట్నెస్ తగ్గించు",
                                "బ్యాటరీ ఎంత ఉంది", "ఫోన్ కాల్ చేయి",
                                "open WhatsApp", "open YouTube", "open camera",
                                "open settings", "turn on Wi-Fi", "turn on Bluetooth",
                                "increase volume", "battery status"
                            )
                        )
                    }
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_800L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200L)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                _state.value = VoiceInputState.Listening(partialText = "", amplitude = 0.2f)
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
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
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
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
                "మైక్రోఫోన్ పర్మిషన్ అవసరం. Settings → Apps → ZENO AI → Permissions లో Microphone Allow చేయండి."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                "ఈ Speech service లో తెలుగు భాష అందుబాటులో లేదు. Google app / Speech Services update చేసి Telugu language support చెక్ చేయండి."
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "తెలుగు speech pack అందుబాటులో లేదు. Internet on చేసి మళ్ళీ ప్రయత్నించండి."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER ->
                "తెలుగు వాయిస్ సర్వీస్ కనెక్ట్ కాలేదు. ఇంటర్నెట్ చెక్ చేసి, Google app / Speech Services by Google update చేసి మళ్ళీ ప్రయత్నించండి."
            SpeechRecognizer.ERROR_NO_MATCH ->
                "అయ్యో బాస్, మీ మాట సరిగ్గా అర్థం కాలేదు! ఇంకొంచెం స్పష్టంగా చెప్పండి."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "మీ మాట వినిపించలేదు. మైక్ నొక్కిన వెంటనే 1–2 సెకన్లు ఆగి స్పష్టంగా మాట్లాడండి."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "మైక్ కాస్త బిజీగా ఉంది. ఒక సెకను ఆగి మళ్ళీ ప్రయత్నించండి."
            SpeechRecognizer.ERROR_CLIENT -> null
            else ->
                "వాయిస్ వినడంలో సమస్య వచ్చింది (కోడ్: $error). కింద టైప్ చేయండి బాస్!"
        }
    }
}
