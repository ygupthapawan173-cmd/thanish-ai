package com.example.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import android.util.Base64
import com.example.control.AgentHudBus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/** One utterance, one microphone lease, one provider. No cloud audio before consent. */
internal class CloudTranscribeClient(
    private val engine: StreamingVoiceEngineKind,
    private val key: String,
    private val allowed: () -> Boolean,
    private val onFinal: (String) -> Unit
) {
    private val gemini = engine == StreamingVoiceEngineKind.GEMINI_TRANSCRIBE_LIVE
    private val rate = if (gemini) 16000 else 24000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()
    private val _state = MutableStateFlow(StreamSttState(engineName = engine.label))
    val state = _state.asStateFlow()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var finished = false
    @Volatile private var captureStarted = false
    @Volatile private var record: AudioRecord? = null
    private var capture: Job? = null
    private var watchdog: Job? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private val partial = StringBuilder()
    private var startMs = 0L
    private var lease = false

    @Synchronized
    fun start(languageTag: String) {
        if (finished) return
        if (key.isBlank() || !allowed()) { fail("Cloud transcription unavailable"); return }
        if (!VoiceMic.lease.acquire(this)) { fail("Microphone busy"); return }
        lease = true
        AgentHudBus.setMainMicBusy(true, this)
        startMs = SystemClock.elapsedRealtime()
        _state.value = StreamSttState(isListening = true, engineName = engine.label, diagnosticsNote = "Connecting")
        val req = if (gemini) Request.Builder().url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + key).build()
            else Request.Builder().url("wss://api.openai.com/v1/realtime?intent=transcription").header("Authorization", "Bearer " + key).build()
        socket = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (finished || !allowed()) { fail("Cloud consent changed"); return }
                val setup = if (gemini) JSONObject().put("setup", JSONObject()
                    .put("model", "models/gemini-3.5-transcribe-live")
                    .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT")))
                    .put("inputAudioTranscription", JSONObject().put("languageCodes",
                        if (languageTag == "auto") JSONArray() else JSONArray().put(languageTag))))
                else JSONObject().put("type", "session.update").put("session", JSONObject().put("type", "transcription")
                    .put("audio", JSONObject().put("input", JSONObject()
                        .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24000))
                        .put("transcription", JSONObject().put("model", "gpt-live-transcribe").put("delay", "low"))
                        .put("turn_detection", JSONObject.NULL))))
                if (!ws.send(setup.toString())) fail("Transcription setup failed")
            }
            override fun onMessage(ws: WebSocket, text: String) = receive(ws, text)
            override fun onMessage(ws: WebSocket, bytes: ByteString) = receive(ws, bytes.utf8())
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { fail("Transcription connection failed" + (response?.let { " HTTP " + it.code } ?: "")) }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) { if (!finished) fail("Transcription closed ($code)") }
        })
        watchdog = scope.launch {
            while (isActive && !finished) {
                delay(200)
                if (!allowed()) { fail("Cloud audio stopped for privacy"); break }
                if (SystemClock.elapsedRealtime() - startMs > 35000L) { fail("Transcription timed out"); break }
            }
        }
    }

    @Synchronized
    private fun receive(ws: WebSocket, text: String) {
        if (finished || socket !== ws) return
        if (!allowed()) { fail("Cloud audio stopped for privacy"); return }
        val j = runCatching { JSONObject(text) }.getOrNull() ?: return
        val type = j.optString("type")
        if (j.has("error") || type == "error" || type.endsWith("transcription.failed")) { fail("Transcription provider rejected session"); return }
        if ((gemini && j.has("setupComplete")) || (!gemini && type == "session.updated")) {
            if (!captureStarted) {
                captureStarted = true
                _state.value = _state.value.copy(measuredConnectLatencyMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L), diagnosticsNote = "Listening")
                capture = scope.launch { recordAudio(ws) }
            }
            return
        }
        if (gemini) {
            val content = j.optJSONObject("serverContent") ?: return
            val interim = content.optJSONObject("interimInputTranscription")?.optString("text").orEmpty()
            if (interim.isNotBlank()) _state.value = _state.value.copy(partialTranscript = interim)
            val final = content.optJSONObject("inputTranscription")?.optString("text").orEmpty()
            if (final.isNotBlank()) complete(final)
        } else {
            if (type == "conversation.item.input_audio_transcription.delta") {
                partial.append(j.optString("delta"))
                _state.value = _state.value.copy(partialTranscript = partial.toString())
            } else if (type == "conversation.item.input_audio_transcription.completed") {
                val final = j.optString("transcript")
                if (final.isBlank()) fail("No speech recognized") else complete(final)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordAudio(ws: WebSocket) {
        try {
            val rec = synchronized(this) {
                if (finished || !allowed()) return
                val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(min > 0)
                val r = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate / 5))
                record = r
                check(r.state == AudioRecord.STATE_INITIALIZED)
                aec = runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(r.audioSessionId)?.apply { enabled = true } else null }.getOrNull()
                ns = runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(r.audioSessionId)?.apply { enabled = true } else null }.getOrNull()
                r.startRecording()
                check(r.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                r
            }
            val pcm = ByteArray(rate / 25 * 2)
            val start = SystemClock.elapsedRealtime()
            var lastSpeech = start
            var voiced = 0
            var empty = 0
            var floor = 120.0
            while (!finished && allowed()) {
                val n = rec.read(pcm, 0, pcm.size)
                if (finished) return
                if (n <= 0) {
                    if (n < 0 || ++empty > 60) error("Microphone read failed")
                    delay(15); continue
                }
                empty = 0
                if (n % 2 != 0) error("Invalid PCM frame")
                var sum = 0.0
                for (i in 0 until n step 2) {
                    val v = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 255)).toShort().toDouble()
                    sum += v * v
                }
                val rms = sqrt(sum / (n / 2.0))
                val now = SystemClock.elapsedRealtime()
                val speech = rms > maxOf(180.0, floor * 2.6)
                if (speech) { voiced++; lastSpeech = now } else if (voiced < 3) floor = (floor * 0.95 + rms * 0.05).coerceIn(40.0, 900.0)
                _state.value = _state.value.copy(amplitude = (rms / 3000.0).toFloat().coerceIn(0f, 1f))
                AudioSpectrumBus.feed(pcm, n, rate, false)
                val b64 = Base64.encodeToString(pcm, 0, n, Base64.NO_WRAP)
                val event = if (gemini) JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject().put("mimeType", "audio/pcm;rate=16000").put("data", b64)))
                    else JSONObject().put("type", "input_audio_buffer.append").put("audio", b64)
                if (!allowed() || finished) { fail("Cloud audio stopped"); return }
                if (ws.queueSize() > 256 * 1024L || !ws.send(event.toString())) error("Transcription socket stalled")
                if (voiced >= 3 && (now - lastSpeech > 850L || now - start > 22000L)) {
                    val end = if (gemini) JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true))
                        else JSONObject().put("type", "input_audio_buffer.commit")
                    if (!allowed() || finished || !ws.send(end.toString())) error("Transcription commit failed")
                    synchronized(this) { releaseRecorder() }
                    _state.value = _state.value.copy(amplitude = 0f, diagnosticsNote = "Finalizing transcript")
                    return
                }
                if (voiced < 3 && now - start > 10000L) error("No speech detected")
            }
            if (!finished) fail("Cloud audio stopped")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (!finished) fail(e.message ?: "Capture failed") }
        catch (_: LinkageError) { if (!finished) fail("Native capture unavailable") }
    }

    @Synchronized private fun complete(text: String) {
        if (finished || !allowed()) { release(); return }
        release()
        onFinal(text.trim())
    }
    @Synchronized private fun fail(message: String) {
        if (finished) return
        release()
        _state.value = StreamSttState(engineName = engine.label, error = message, diagnosticsNote = message)
    }
    private fun releaseRecorder() {
        runCatching { aec?.release() }; aec = null
        runCatching { ns?.release() }; ns = null
        runCatching { record?.stop() }; runCatching { record?.release() }; record = null
    }
    @Synchronized fun release() {
        if (finished) return
        finished = true
        capture?.cancel(); watchdog?.cancel()
        releaseRecorder()
        val old = socket; socket = null; old?.cancel()
        if (lease) { VoiceMic.lease.release(this); AgentHudBus.setMainMicBusy(false, this); lease = false }
        scope.cancel()
        _state.value = _state.value.copy(isListening = false, amplitude = 0f)
    }
}
