package com.example.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import com.example.control.AgentHudBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

data class StreamSttState(
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val amplitude: Float = 0f,
    val measuredConnectLatencyMs: Long = 0L,
    val engineName: String = "Sarvam Saaras V4",
    val diagnosticsNote: String = "Ready",
    val error: String? = null
)

/**
 * Real-Time Streaming STT & Online Wake Client for Sarvam Saaras V4 (Sections 6, 8, 9):
 * - Endpoint: `wss://api.sarvam.ai/speech-to-text-realtime/ws?model=saaras:v4&language_code=te-IN&mode=codemix`
 * - Integrates Silero VAD + AEC + Noise Suppression + AGC + Mic Stall Recovery
 * - Detects "Hey JARVIS" in partial & final transcripts for online wake priority
 */
class SarvamRealtimeSttClient(
    private val context: Context,
    private val apiKeyProvider: () -> String,
    private val wakeMode: Boolean = false,
    private val captureAllowed: () -> Boolean = { true },
    private val onWakeDetected: (inlineCommand: String) -> Unit = {},
    private val onFinalTranscript: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var sessionToken = 0L
    private var captureJob: Job? = null
    private var watchdogJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var vad: SileroLiveVad? = null
    @Volatile private var lastSpeechAt = 0L
    @Volatile private var lastFrameAt = 0L
    @Volatile private var latestPartial = ""
    @Volatile private var startedAtMs = 0L

    private val _state = MutableStateFlow(StreamSttState())
    val state: StateFlow<StreamSttState> = _state.asStateFlow()

    @Synchronized
    fun start(languageTag: String = "auto") {
        stop()
        if (!captureAllowed()) { _state.value = StreamSttState(error = "Cloud audio not permitted"); return }
        val key = apiKeyProvider().trim()
        if (key.isBlank()) {
            _state.value = StreamSttState(error = "Sarvam API key required for Saaras V4 streaming")
            return
        }
        AgentHudBus.setMainMicBusy(!wakeMode, this)
        if (!VoiceMic.lease.acquire(this)) {
            if (!wakeMode) AgentHudBus.setMainMicBusy(false, this)
            _state.value = StreamSttState(error = "Microphone busy with another session")
            return
        }

        val token = ++sessionToken
        latestPartial = ""
        startedAtMs = SystemClock.elapsedRealtime()
        val lang = if (languageTag == "en-IN") "en-IN" else "te-IN"
        val mode = if (wakeMode) "transcribe" else "codemix"
        val url = "wss://api.sarvam.ai/speech-to-text-realtime/ws?model=saaras:v4&language_code=$lang&mode=$mode&sample_rate=16000&encoding=linear16&endpointing=vad"
        _state.value = StreamSttState(isListening = true, engineName = "Sarvam Saaras V4", diagnosticsNote = "Connecting Saaras V4...")

        val req = Request.Builder()
            .url(url)
            .header("Api-Subscription-Key", key)
            .build()

        webSocket = httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (token != sessionToken || this@SarvamRealtimeSttClient.webSocket !== webSocket) return
                val connectMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(1L)
                runCatching {
                    vad = SileroLiveVad.open(appContext)
                    startMicCapture(webSocket, token, connectMs)
                }.onFailure { err ->
                    fail(token, err.message ?: "Saaras V4 mic start failed")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (token != sessionToken || this@SarvamRealtimeSttClient.webSocket !== webSocket) return
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                val err = json.optString("error").ifBlank { json.optJSONObject("data")?.optString("error").orEmpty() }
                if (err.isNotBlank()) {
                    fail(token, "Saaras V4: $err")
                    return
                }
                val transcript = json.optString("transcript").ifBlank {
                    json.optJSONObject("data")?.optString("transcript").orEmpty()
                }.trim()
                val isFinal = json.optBoolean("is_final", false) ||
                    json.optString("type").equals("transcript", true) ||
                    json.optString("event").equals("speech_end", true)

                if (transcript.isNotBlank()) {
                    lastSpeechAt = SystemClock.elapsedRealtime()
                    latestPartial = transcript
                    _state.value = _state.value.copy(
                        partialTranscript = transcript,
                        diagnosticsNote = "Saaras V4 ($lang): listening"
                    )
                    if (wakeMode && HandsFreeWakePolicy.matchesPartialWake(transcript)) {
                        val inline = HandsFreeWakePolicy.stripWakePrefix(transcript)
                        stop()
                        main.post { onWakeDetected(inline) }
                        return
                    }
                    if (!wakeMode && isFinal) {
                        stop()
                        main.post { onFinalTranscript(transcript) }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code ?: 0
                fail(token, "Saaras V4 failure${if (code > 0) " (HTTP $code)" else ""}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (token == sessionToken) {
                    val tail = latestPartial
                    stopInternal()
                    if (!wakeMode && tail.isNotBlank()) {
                        main.post { onFinalTranscript(tail) }
                    }
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun startMicCapture(ws: WebSocket, token: Long, connectMs: Long) {
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(3200)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min)
        audioRecord = rec
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }

        val sid = rec.audioSessionId
        aec = runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sid)?.apply { enabled = true } else null }.getOrNull()
        ns = runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sid)?.apply { enabled = true } else null }.getOrNull()
        agc = runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sid)?.apply { enabled = true } else null }.getOrNull()

        rec.startRecording()
        check(rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone failed to record" }

        val now = SystemClock.elapsedRealtime()
        lastFrameAt = now
        lastSpeechAt = now
        _state.value = _state.value.copy(
            measuredConnectLatencyMs = connectMs,
            diagnosticsNote = if (wakeMode) "Online wake active (Saaras V4)" else "Listening (Saaras V4 • ${connectMs}ms)"
        )

        watchdogJob = scope.launch {
            while (isActive && token == sessionToken && webSocket === ws) {
                delay(400L)
                if (!captureAllowed()) { fail(token, "Cloud audio stopped for privacy"); break }
                val cur = SystemClock.elapsedRealtime()
                if (cur - lastFrameAt > 3_500L) {
                    fail(token, "Microphone stall detected")
                    break
                }
                if (!wakeMode && cur - lastSpeechAt > 3_200L && latestPartial.isNotBlank()) {
                    val spoken = latestPartial
                    stop()
                    main.post { onFinalTranscript(spoken) }
                    break
                }
                if (!wakeMode && cur - lastSpeechAt > 12_000L && latestPartial.isBlank()) {
                    fail(token, "No speech detected")
                    break
                }
            }
        }

        captureJob = scope.launch {
            val buf = ByteArray(1024)
            var empty = 0
            while (isActive && token == sessionToken && webSocket === ws) {
                val read = rec.read(buf, 0, buf.size)
                if (AudioCapturePolicy.validPcmRead(read, buf.size)) {
                    empty = 0
                    lastFrameAt = SystemClock.elapsedRealtime()
                    var sum = 0.0
                    var i = 0
                    while (i < read - 1) {
                        val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 255)).toShort()
                        sum += s * s
                        i += 2
                    }
                    val rms = sqrt(sum / (read / 2.0)).toFloat()
                    val speechVad = vad?.update(buf, read) ?: (rms > 180f)
                    if (speechVad) lastSpeechAt = SystemClock.elapsedRealtime()
                    _state.value = _state.value.copy(amplitude = (rms / 2800f).coerceIn(0f, 1f))

                    // Preserve the PCM timeline: server-side VAD needs silence as well as speech.
                    // Cloud capture is enabled only by the caller after explicit consent.
                    if (!captureAllowed()) { fail(token, "Cloud audio stopped for privacy"); break }
                    if (!wakeMode) AudioSpectrumBus.feed(buf, read, rate, false)
                    val chunkMsg = JSONObject()
                        .put("event", "audio_input")
                        .put("audio", Base64.encodeToString(buf.copyOf(read), Base64.NO_WRAP))
                    if (ws.queueSize() > 256 * 1024L || !ws.send(chunkMsg.toString())) {
                        fail(token, "Audio streaming connection stalled")
                        break
                    }
                } else if (read == 0) {
                    if (AudioCapturePolicy.emptyReadLimit(++empty)) {
                        fail(token, "Audio capture stall")
                        break
                    }
                    delay(AudioCapturePolicy.EMPTY_READ_DELAY_MS)
                } else {
                    fail(token, "Microphone read failed ($read)")
                    break
                }
            }
        }
    }

    @Synchronized
    private fun fail(token: Long, message: String) {
        if (token != sessionToken) return
        stopInternal()
        _state.value = StreamSttState(isListening = false, error = message, diagnosticsNote = message)
    }

    @Synchronized
    private fun stopInternal() {
        ++sessionToken
        watchdogJob?.cancel(); watchdogJob = null
        captureJob?.cancel(); captureJob = null
        runCatching { vad?.close() }; vad = null
        runCatching { aec?.release() }; aec = null
        runCatching { ns?.release() }; ns = null
        runCatching { agc?.release() }; agc = null
        runCatching { audioRecord?.stop(); audioRecord?.release() }; audioRecord = null
        runCatching { webSocket?.close(1000, "Stopped") }; webSocket = null
        VoiceMic.lease.release(this)
        if (!wakeMode) AgentHudBus.setMainMicBusy(false, this)
    }

    fun stop() {
        stopInternal()
        _state.value = _state.value.copy(isListening = false, amplitude = 0f)
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
