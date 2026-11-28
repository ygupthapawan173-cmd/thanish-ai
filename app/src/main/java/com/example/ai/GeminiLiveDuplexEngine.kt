package com.example.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.example.control.AgentHudBus
import com.example.voice.AudioCapturePolicy
import com.example.voice.VoiceMic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
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
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

data class LiveDuplexState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isAiSpeaking: Boolean = false,
    val activeModel: String = "",
    val amplitude: Float = 0f,
    val statusText: String = "Live Voice Idle",
    val error: String? = null
)

/**
 * Gemini 3.8 Live / Extended Thinking Bidirectional Duplex Voice Engine (Sections 6, 8, 9):
 * - Hardware AEC, Noise Suppressor & AGC
 * - Mic stall recovery & Exclusive MicLease ownership
 * - Automatic model fallback (`gemini-3.8-live` -> `gemini-2.5-flash-native-audio-preview-12-2025`)
 */
class GeminiLiveDuplexEngine(
    context: Context,
    private val apiKeyProvider: () -> String,
    private val liveModelProvider: () -> String,
    private val backgroundOwned: Boolean = false,
    private val allowedProvider: () -> Boolean = { true }
) {
    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var webSocket: WebSocket? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var session = 0L
    private var setupTimeout: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var captureJob: Job? = null
    private var watchdog: Job? = null
    private var reconnectJob: Job? = null
    private var playbackJob: Job? = null
    private var playbackQueue: Channel<Pair<Long, ByteArray>>? = null
    @Volatile private var playbackEpoch = 0L
    @Volatile private var lastFrameAt = 0L
    @Volatile private var lastVoiceAt = 0L
    @Volatile private var lastNonzeroAt = 0L
    @Volatile private var wanted = false
    private var retryCount = 0

    private val fallbackModel = "gemini-2.5-flash-native-audio-preview-12-2025"
    private val _state = MutableStateFlow(LiveDuplexState())
    val state: StateFlow<LiveDuplexState> = _state.asStateFlow()

    private fun isAllowed() = runCatching { allowedProvider() }.getOrDefault(false)

    @Synchronized
    fun connect() {
        disconnect()
        wanted = true
        retryCount = 0
        openSession()
    }

    @Synchronized
    private fun openSession() {
        if (!wanted || !isAllowed()) {
            disconnect()
            _state.value = LiveDuplexState(error = "Live session not permitted right now")
            return
        }
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            wanted = false
            _state.value = LiveDuplexState(error = "Configure your Gemini API key for Live Voice.")
            return
        }
        AgentHudBus.setMainMicBusy(true, this)
        if (!VoiceMic.lease.acquire(this)) {
            AgentHudBus.setMainMicBusy(false, this)
            wanted = false
            _state.value = LiveDuplexState(error = "Microphone busy; retry after current capture finishes")
            return
        }
        val token = ++session
        val rawModel = if (retryCount == 0) liveModelProvider().trim().ifEmpty { "gemini-3.8-live" } else fallbackModel
        val modelPath = if (rawModel.startsWith("models/")) rawModel else "models/$rawModel"
        _state.value = LiveDuplexState(isConnecting = true, activeModel = rawModel, statusText = "Connecting $rawModel...")

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        try {
            webSocket = httpClient.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        synchronized(this@GeminiLiveDuplexEngine) {
                            if (token != session || this@GeminiLiveDuplexEngine.webSocket !== webSocket) return
                            if (!isAllowed()) { fail(token, "Live session paused"); return }
                            val generation = JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
                            val setup = JSONObject().put(
                                "setup",
                                JSONObject()
                                    .put("model", modelPath)
                                    .put(
                                        "systemInstruction",
                                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", CreatorIdentity.SYSTEM_CONTEXT)))
                                    )
                                    .put("generationConfig", generation)
                            )
                            if (!webSocket.send(setup.toString())) fail(token, "Live setup send failed")
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) = receive(token, webSocket, text)
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(token, webSocket, bytes.utf8())
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        fail(token, "Live connection error${response?.let { " (HTTP ${it.code})" }.orEmpty()}")
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = fail(token, "Live session closed ($code)")
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = fail(token, "Live server closing ($code)")
                }
            )
            setupTimeout = scope.launch {
                delay(12_000L)
                synchronized(this@GeminiLiveDuplexEngine) {
                    if (token == session && !_state.value.isConnected) fail(token, "Live setup timeout")
                }
            }
        } catch (_: Exception) {
            fail(token, "Unable to open Live WebSocket")
        }
    }

    @Synchronized
    private fun receive(token: Long, ws: WebSocket, text: String) {
        if (token != session || webSocket !== ws) return
        if (!isAllowed()) {
            disconnect()
            _state.value = LiveDuplexState(error = "Live session paused")
            return
        }
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (json.has("error")) {
            val e = json.optJSONObject("error")
            val code = e?.optInt("code", 0) ?: 0
            val msg = e?.optString("message").orEmpty()
            fail(token, "Live API error HTTP $code: ${msg.ifBlank { "check model & key" }}")
            return
        }
        if (json.has("setupComplete") && !_state.value.isConnected) {
            setupTimeout?.cancel()
            setupTimeout = null
            main.postDelayed(
                {
                    synchronized(this@GeminiLiveDuplexEngine) {
                        if (token == session && webSocket === ws && !_state.value.isConnected && isAllowed()) {
                            runCatching {
                                initPlayback()
                                startMicStream(ws, token)
                                _state.value = _state.value.copy(isConnecting = false, isConnected = true, statusText = "Gemini Live Active")
                            }.onFailure {
                                fail(token, "Live audio initialization failed")
                            }
                        }
                    }
                },
                320L
            )
        } else if (_state.value.isConnected) {
            handleMessage(text)
        }
    }

    @Synchronized
    private fun fail(token: Long, message: String) {
        if (token != session) return
        closeSession()
        val retryable = LiveRetryPolicy.mayRetry(message, retryCount, liveModelProvider().trim(), fallbackModel)
        if (wanted && isAllowed() && retryable && retryCount < 2) {
            retryCount++
            val attempt = retryCount
            _state.value = LiveDuplexState(isConnecting = true, statusText = "Reconnecting Live ($attempt/2)...")
            reconnectJob = scope.launch(Dispatchers.Main.immediate) {
                delay(LiveRetryPolicy.delayMs(message, attempt))
                synchronized(this@GeminiLiveDuplexEngine) {
                    if (wanted && isAllowed()) openSession() else disconnect()
                }
            }
        } else {
            wanted = false
            _state.value = LiveDuplexState(error = message, statusText = "Live Disconnected")
        }
    }

    private fun handleMessage(raw: String) {
        try {
            val sc = JSONObject(raw).optJSONObject("serverContent") ?: return
            if (sc.optBoolean("interrupted", false)) {
                interruptPlayback()
                _state.value = _state.value.copy(isAiSpeaking = false, statusText = "Listening...")
                return
            }
            val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val inline = parts.getJSONObject(i).optJSONObject("inlineData") ?: continue
                    if (inline.optString("mimeType", "").startsWith("audio/pcm")) {
                        val pcm = Base64.decode(inline.optString("data"), Base64.DEFAULT)
                        _state.value = _state.value.copy(isAiSpeaking = true, statusText = "Speaking...")
                        if (playbackQueue?.trySend(playbackEpoch to pcm)?.isSuccess != true) {
                            interruptPlayback()
                        }
                    }
                }
            }
            if (sc.optBoolean("turnComplete", false)) {
                _state.value = _state.value.copy(isAiSpeaking = false, statusText = "Listening...")
            }
        } catch (_: Exception) {
        }
    }

    private fun initPlayback() {
        val rate = 24000
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
        val queue = Channel<Pair<Long, ByteArray>>(32)
        playbackQueue = queue
        val track = audioTrack ?: error("AudioTrack unavailable")
        playbackJob = scope.launch {
            for ((epoch, pcm) in queue) {
                var offset = 0
                while (isActive && epoch == playbackEpoch && offset < pcm.size) {
                    val n = runCatching { track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_NON_BLOCKING) }.getOrDefault(-1)
                    if (n < 0) break
                    if (n == 0) delay(10) else offset += n
                }
            }
        }
    }

    private fun interruptPlayback() {
        ++playbackEpoch
        val q = playbackQueue
        if (q != null) while (q.tryReceive().isSuccess) {}
        runCatching { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.play() }
    }

    @SuppressLint("MissingPermission")
    private fun startMicStream(ws: WebSocket, token: Long) {
        val rate = 16000
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Unsupported microphone format" }
        val minBuf = minimum.coerceAtLeast(3200)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf)
        audioRecord = rec
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "Microphone busy or unavailable" }

        val sid = rec.audioSessionId
        aec = runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sid)?.apply { enabled = true } else null }.getOrNull()
        ns = runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sid)?.apply { enabled = true } else null }.getOrNull()
        agc = runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sid)?.apply { enabled = true } else null }.getOrNull()

        rec.startRecording()
        check(rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "AudioRecord failed to start" }
        val now = SystemClock.elapsedRealtime()
        lastFrameAt = now
        lastVoiceAt = now
        lastNonzeroAt = now

        watchdog = scope.launch {
            while (isActive && token == session && _state.value.isConnected) {
                delay(500L)
                if (!isAllowed()) { fail(token, "Live session paused by privacy policy"); break }
                val cur = SystemClock.elapsedRealtime()
                if (cur - lastFrameAt > 4_000L || cur - lastNonzeroAt > 10_000L) {
                    fail(token, "Microphone stall detected")
                    break
                }
                if (_state.value.isAiSpeaking) {
                    lastVoiceAt = cur
                } else if (cur - lastVoiceAt > 45_000L) {
                    fail(token, "Live silence timeout")
                    break
                }
            }
        }

        captureJob = scope.launch {
            val buf = ByteArray(1024)
            var emptyReads = 0
            while (isActive && webSocket === ws && token == session) {
                val read = rec.read(buf, 0, buf.size)
                if (AudioCapturePolicy.validPcmRead(read, buf.size)) {
                    emptyReads = 0
                    val sampleNow = SystemClock.elapsedRealtime()
                    lastFrameAt = sampleNow
                    var sum = 0.0
                    var i = 0
                    while (i < read - 1) {
                        val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 255)).toShort()
                        sum += s * s
                        i += 2
                    }
                    val rms = sqrt(sum / (read / 2.0)).toFloat()
                    if (rms > 25f) lastNonzeroAt = sampleNow
                    if (rms > 150f) lastVoiceAt = sampleNow
                    val amp = (rms / 3000f).coerceIn(0f, 1f)
                    _state.value = _state.value.copy(amplitude = amp)

                    val b64 = Base64.encodeToString(buf.copyOf(read), Base64.NO_WRAP)
                    val frame = JSONObject().put(
                        "realtimeInput",
                        JSONObject().put(
                            "mediaChunks",
                            JSONArray().put(
                                JSONObject().put("mimeType", "audio/pcm;rate=16000").put("data", b64)
                            )
                        )
                    )
                    if (!ws.send(frame.toString())) break
                } else if (read == 0) {
                    if (AudioCapturePolicy.emptyReadLimit(++emptyReads)) {
                        Log.w("GeminiLive", "Empty reads exceeded")
                        break
                    }
                    delay(AudioCapturePolicy.EMPTY_READ_DELAY_MS)
                } else {
                    break
                }
            }
        }
    }

    @Synchronized
    private fun closeSession() {
        setupTimeout?.cancel(); setupTimeout = null
        watchdog?.cancel(); watchdog = null
        captureJob?.cancel(); captureJob = null
        playbackJob?.cancel(); playbackJob = null
        playbackQueue?.close(); playbackQueue = null
        runCatching { aec?.release() }; aec = null
        runCatching { ns?.release() }; ns = null
        runCatching { agc?.release() }; agc = null
        runCatching { audioRecord?.stop(); audioRecord?.release() }; audioRecord = null
        runCatching { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.release() }; audioTrack = null
        runCatching { webSocket?.close(1000, "Done") }; webSocket = null
        VoiceMic.lease.release(this)
        AgentHudBus.setMainMicBusy(false, this)
    }

    @Synchronized
    fun disconnect() {
        wanted = false
        reconnectJob?.cancel(); reconnectJob = null
        ++session
        closeSession()
        _state.value = LiveDuplexState(statusText = "Live Voice Idle")
    }

    fun release() {
        disconnect()
        scope.cancel()
    }
}
