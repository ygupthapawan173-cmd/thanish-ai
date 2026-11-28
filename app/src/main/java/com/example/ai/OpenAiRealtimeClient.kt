package com.example.ai

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.AutomaticGainControl
import android.util.Base64
import com.example.control.AgentHudBus
import com.example.voice.VoiceMic
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * OpenAI Realtime Voice Client (Section 6 — GPT-Live 1.1 / gpt-realtime-2.1):
 * Connects authorized OpenAI credentials to `/v1/realtime` with hardware AEC/NS and exclusive mic lease.
 */
class OpenAiRealtimeClient(
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String = { "gpt-live-1.1" }
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var webSocket: WebSocket? = null
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var fallbackTried = false

    private val _state = MutableStateFlow(LiveDuplexState())
    val state: StateFlow<LiveDuplexState> = _state.asStateFlow()

    @Synchronized
    fun connect() {
        disconnect()
        fallbackTried = false
        openSocket(modelProvider().ifBlank { "gpt-live-1.1" })
    }

    @Synchronized
    private fun openSocket(modelId: String) {
        val key = apiKeyProvider().trim()
        if (key.isBlank()) {
            _state.value = LiveDuplexState(error = "Configure OpenAI API key for GPT-Live")
            return
        }
        AgentHudBus.setMainMicBusy(true, this)
        if (!VoiceMic.lease.acquire(this)) {
            AgentHudBus.setMainMicBusy(false, this)
            _state.value = LiveDuplexState(error = "Microphone busy")
            return
        }
        _state.value = LiveDuplexState(isConnecting = true, activeModel = modelId, statusText = "Connecting $modelId...")
        val url = "wss://api.openai.com/v1/realtime?model=$modelId"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .build()

        webSocket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (this@OpenAiRealtimeClient.webSocket !== webSocket) return
                val cfg = JSONObject()
                    .put("type", "session.update")
                    .put(
                        "session",
                        JSONObject()
                            .put("type", "realtime")
                            .put("output_modalities", JSONArray().put("audio"))
                            .put("instructions", CreatorIdentity.SYSTEM_CONTEXT)
                            .put("audio", JSONObject().put("input", JSONObject()
                                .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24000))
                                .put("noise_reduction", JSONObject().put("type", "near_field")))
                                .put("output", JSONObject().put("format", JSONObject().put("type", "audio/pcm").put("rate", 24000))))
                    )
                if (!webSocket.send(cfg.toString())) { closeResources(); _state.value = LiveDuplexState(error = "Session setup send failed") }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (this@OpenAiRealtimeClient.webSocket !== webSocket) return
                val j = runCatching { JSONObject(text) }.getOrNull() ?: return
                val type = j.optString("type")
                if (type == "session.updated" && !_state.value.isConnected) {
                runCatching {
                    initPlayback()
                    startCapture(webSocket)
                    _state.value = LiveDuplexState(isConnected = true, activeModel = modelId, statusText = "OpenAI Live Active")
                }.onFailure { err ->
                    closeResources()
                    _state.value = LiveDuplexState(error = err.message ?: "Audio capture error")
                }
                } else if (type == "response.output_audio.delta" || type == "response.audio.delta") {
                    val delta = j.optString("delta")
                    if (delta.isNotBlank()) {
                        val pcm = Base64.decode(delta, Base64.DEFAULT)
                        _state.value = _state.value.copy(isAiSpeaking = true, statusText = "Speaking...")
                        runCatching { audioTrack?.write(pcm, 0, pcm.size) }
                    }
                } else if (type == "response.output_audio.done" || type == "response.audio.done" || type == "response.done") {
                    _state.value = _state.value.copy(isAiSpeaking = false, statusText = "Listening...")
                } else if (type == "error") {
                    val msg = j.optJSONObject("error")?.optString("message").orEmpty()
                    if (!fallbackTried && modelId != "gpt-realtime-2.1" && msg.contains("model", true)) {
                        fallbackTried = true
                        closeResources()
                        openSocket("gpt-realtime-2.1")
                    } else {
                        closeResources()
                        _state.value = LiveDuplexState(error = msg.ifBlank { "OpenAI Realtime error" })
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (this@OpenAiRealtimeClient.webSocket !== webSocket) return
                val code = response?.code ?: 0
                closeResources()
                if (!fallbackTried && code == 404 && modelId != "gpt-realtime-2.1") {
                    fallbackTried = true
                    openSocket("gpt-realtime-2.1")
                } else {
                    _state.value = LiveDuplexState(error = "OpenAI Realtime failed${if (code > 0) " (HTTP $code)" else ""}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (this@OpenAiRealtimeClient.webSocket !== webSocket) return
                closeResources()
                _state.value = LiveDuplexState(statusText = "OpenAI Live Idle")
            }
        })
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
    }

    @SuppressLint("MissingPermission")
    private fun startCapture(ws: WebSocket) {
        val rate = 24000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(3200)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min)
        audioRecord = rec
        check(rec.state == AudioRecord.STATE_INITIALIZED)
        aec = runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true } else null }.getOrNull()
        ns = runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } else null }.getOrNull()
        agc = runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(rec.audioSessionId)?.apply { enabled = true } else null }.getOrNull()
        rec.startRecording()
        check(rec.recordingState == AudioRecord.RECORDSTATE_RECORDING)

        captureJob = scope.launch {
            val buf = ByteArray(1024)
            while (isActive && webSocket === ws) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0 && n % 2 == 0) {
                    var sum = 0.0
                    var i = 0
                    while (i < n - 1) {
                        val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 255)).toShort()
                        sum += s * s
                        i += 2
                    }
                    val rms = sqrt(sum / (n / 2.0)).toFloat()
                    _state.value = _state.value.copy(amplitude = (rms / 3000f).coerceIn(0f, 1f))
                    val payload = JSONObject()
                        .put("type", "input_audio_buffer.append")
                        .put("audio", Base64.encodeToString(buf.copyOf(n), Base64.NO_WRAP))
                    if (!ws.send(payload.toString())) break
                } else {
                    delay(15L)
                }
            }
        }
    }

    @Synchronized
    private fun closeResources() {
        captureJob?.cancel(); captureJob = null
        runCatching { aec?.release() }; aec = null
        runCatching { ns?.release() }; ns = null
        runCatching { agc?.release() }; agc = null
        runCatching { audioRecord?.stop(); audioRecord?.release() }; audioRecord = null
        runCatching { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.release() }; audioTrack = null
        val oldSocket = webSocket; webSocket = null
        runCatching { oldSocket?.close(1000, "Done") }
        VoiceMic.lease.release(this)
        AgentHudBus.setMainMicBusy(false, this)
    }

    @Synchronized
    fun disconnect() {
        closeResources()
        _state.value = LiveDuplexState(statusText = "OpenAI Live Idle")
    }

    fun release() {
        disconnect()
        scope.cancel()
    }
}
