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
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.Channel
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import com.example.control.AgentHudBus
import com.example.voice.BluetoothAudioRouter
import com.example.voice.AudioCapturePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.os.Handler
import android.os.Looper
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
    val amplitude: Float = 0f,
    val statusText: String = "Live Voice Idle",
    val error: String? = null
)

class GeminiLiveDuplexEngine(
    context: Context,
    private val apiKeyProvider: () -> String,
    private val liveModelProvider: () -> String,
    private val bluetoothRoutingEnabledProvider: () -> Boolean,
    private val backgroundOwned: Boolean = false,
    private val allowedProvider: () -> Boolean = { true },
    private val liveSpeakerProvider: () -> String = { "" }
) {
    private val appContext = context.applicationContext
    private val btRouter = BluetoothAudioRouter(context)
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
    private var captureJob: Job? = null
    private var watchdog: Job? = null
    private var reconnectJob: Job? = null
    private var agc: AutomaticGainControl? = null
    private var playbackJob: Job? = null
    private var playbackQueue: Channel<Pair<Long,ByteArray>>? = null
    @Volatile private var playbackEpoch=0L
    @Volatile private var lastFrameAt=0L
    @Volatile private var lastVoiceAt=0L
    @Volatile private var lastNonzeroAt=0L
    @Volatile private var wanted=false
    private var retryCount=0
    private fun isAllowed()=runCatching { allowedProvider() }.getOrDefault(false)
    private val fallbackModel="gemini-2.5-flash-native-audio-preview-12-2025"

    private val _state = MutableStateFlow(LiveDuplexState())
    val state: StateFlow<LiveDuplexState> = _state.asStateFlow()

    @Synchronized fun connect() {
        disconnect(); wanted=true; retryCount=0; openSession()
    }

    @Synchronized private fun openSession() {
        if(!wanted || !isAllowed()) { disconnect(); _state.value=LiveDuplexState(error="Live session not allowed"); return }
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            wanted=false
            _state.value = LiveDuplexState(error = "Add your Gemini API key in Settings for Live Voice.")
            return
        }

        AgentHudBus.setMainMicBusy(true, this)
        if(!backgroundOwned) com.example.omega.OmegaWakeService.pauseForManual()
        if (!com.example.voice.VoiceMic.lease.acquire(this)) {
            AgentHudBus.setMainMicBusy(false, this)
            wanted=false
            _state.value = LiveDuplexState(error = "Microphone occupied; retry after current session")
            return
        }
        val token = ++session
        btRouter.routeToBluetoothIfAvailable(bluetoothRoutingEnabledProvider())
        val rawModel = if(retryCount==0) liveModelProvider().trim().ifEmpty { fallbackModel } else fallbackModel
        val speaker = com.example.voice.LiveVoiceChoices.normalize(liveSpeakerProvider())
        val modelPath = if (rawModel.startsWith("models/")) rawModel else "models/$rawModel"
        _state.value = LiveDuplexState(isConnecting = true, statusText = "Connecting Live Voice...")

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        try {
            webSocket = httpClient.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    synchronized(this@GeminiLiveDuplexEngine) {
                        if (token != session || this@GeminiLiveDuplexEngine.webSocket !== webSocket) return
                        if(!isAllowed()) { fail(token,"Live session not allowed"); return }
                        val generation = JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
                        if (speaker.isNotEmpty()) generation.put("speechConfig", JSONObject().put("voiceConfig",
                            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", speaker))))
                        val setup = JSONObject().put("setup", JSONObject().put("model", modelPath)
                            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", com.example.ai.CreatorIdentity.SYSTEM_CONTEXT))))
                            .put("generationConfig", generation))
                        if (!webSocket.send(setup.toString())) fail(token,"Live setup send failed")
                    }
                }
                override fun onMessage(webSocket: WebSocket, text: String) = receive(token,webSocket,text)
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(token,webSocket,bytes.utf8())
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    fail(token, "Live connection failed${response?.let { " (HTTP ${it.code})" }.orEmpty()}")
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { fail(token,"Live session closed ($code)") }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { fail(token,"Live server closed session ($code)") }
            })
            setupTimeout = scope.launch { delay(12000); synchronized(this@GeminiLiveDuplexEngine) { if(token==session && !_state.value.isConnected) fail(token,"Live setup timeout") } }
        } catch(_:Exception) { fail(token,"Unable to start Live connection") }
    }

    @Synchronized private fun receive(token:Long, ws:WebSocket, text:String) {
        if(token!=session || webSocket!==ws) return
        if(!isAllowed()) { disconnect(); _state.value=LiveDuplexState(error="Live session paused by privacy policy"); return }
        val json=runCatching { JSONObject(text) }.getOrNull() ?: return
        if(json.has("error")) {
            val e = json.optJSONObject("error")
            val code = e?.optInt("code", 0) ?: 0
            val status = e?.optString("status").orEmpty().takeIf { it.matches(Regex("[A-Z_]{1,48}")) }.orEmpty()
            val hint = when (code) { 400 -> "Check model and session configuration"; 401,403 -> "Check API key and access"; 404 -> "Live model not available"; 429 -> "Quota or rate limit"; else -> "Check provider status" }
            fail(token, "Live API rejected session (HTTP $code $status): $hint"); return
        }
        if(json.has("setupComplete") && !_state.value.isConnected) {
            setupTimeout?.cancel(); setupTimeout=null
            // Wait for the offline wake recorder to release before opening Live mic.
            main.postDelayed({ synchronized(this@GeminiLiveDuplexEngine) {
                if(token==session && webSocket===ws && !_state.value.isConnected && isAllowed()) {
                    runCatching { initPlayback(); startMicStream(ws,token); _state.value=LiveDuplexState(isConnected=true,statusText="Live Voice Active") }
                        .onFailure { fail(token,"Live microphone or audio initialization failed") }
                } else if(token==session && !isAllowed()) { disconnect(); _state.value=LiveDuplexState(error="Live session paused by privacy policy") }
            } },450)
        } else if(_state.value.isConnected) handleMessage(text)
    }

    @Synchronized private fun fail(token:Long, message:String) {
        if(token!=session) return
        closeSession()
        val retryable = LiveRetryPolicy.mayRetry(message, retryCount, liveModelProvider().trim(), fallbackModel)
        if(wanted && isAllowed() && retryable && retryCount<2) {
            retryCount++
            val attempt=retryCount
            _state.value=LiveDuplexState(isConnecting=true,statusText="Reconnecting Live Voice ($attempt/2)")
            reconnectJob=scope.launch(Dispatchers.Main.immediate) { delay(LiveRetryPolicy.delayMs(message, attempt)); synchronized(this@GeminiLiveDuplexEngine) { if(wanted && isAllowed()) openSession() else { disconnect(); _state.value=LiveDuplexState(error="Live session not allowed") } } }
        } else { wanted=false; _state.value=LiveDuplexState(error=message,statusText="Disconnected") }
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
                        if(playbackQueue?.trySend(playbackEpoch to pcm)?.isSuccess!=true) { interruptPlayback(); Log.w("OmegaVoice","LIVE_PLAYBACK_QUEUE_FULL") }
                    }
                }
            }
            if (sc.optBoolean("turnComplete", false)) {
                _state.value = _state.value.copy(isAiSpeaking = false, statusText = "Listening...")
            }
        } catch (_: Exception) {}
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
        val queue=Channel<Pair<Long,ByteArray>>(32); playbackQueue=queue
        val track=audioTrack ?: error("AudioTrack unavailable")
        playbackJob=scope.launch {
            for((epoch,pcm) in queue) {
                var offset=0
                while(isActive && epoch==playbackEpoch && offset<pcm.size) {
                    val n=runCatching { track.write(pcm,offset,pcm.size-offset,AudioTrack.WRITE_NON_BLOCKING) }.getOrDefault(-1)
                    if(n<0) break
                    if(n==0) delay(10) else offset+=n
                }
            }
        }
    }

    private fun interruptPlayback() {
        ++playbackEpoch
        val q=playbackQueue; if(q!=null) while(q.tryReceive().isSuccess) { }
        runCatching { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.play() }
    }

    @SuppressLint("MissingPermission")
    private fun startMicStream(ws: WebSocket, token: Long) {
        val rate = 16000
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Unsupported live microphone configuration" }
        val minBuf = minimum.coerceAtLeast(3200)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf)
        audioRecord = rec
        check(rec.state == AudioRecord.STATE_INITIALIZED)
        val effects = BluetoothAudioRouter.attachAudioEffects(rec.audioSessionId)
        aec = effects.first; ns = effects.second
        agc=runCatching { if(AutomaticGainControl.isAvailable()) AutomaticGainControl.create(rec.audioSessionId)?.apply { enabled=true } else null }.getOrNull()
        rec.startRecording()
        check(rec.recordingState == AudioRecord.RECORDSTATE_RECORDING)

        lastFrameAt=SystemClock.elapsedRealtime(); lastVoiceAt=lastFrameAt; lastNonzeroAt=lastFrameAt
        watchdog?.cancel(); watchdog=scope.launch {
            while(isActive) {
                delay(500)
                if(!isAllowed()) { main.post { synchronized(this@GeminiLiveDuplexEngine) { if(token==session) { disconnect(); _state.value=LiveDuplexState(error="Live session paused by privacy policy") } } }; break }
                val now=SystemClock.elapsedRealtime()
                if(com.example.voice.VoiceHealthPolicy.stalled(now,lastFrameAt,lastNonzeroAt)) { main.post { fail(token,"Live microphone stalled") }; break }
                if(backgroundOwned && now-lastVoiceAt>30000 && !_state.value.isAiSpeaking) { main.post { synchronized(this@GeminiLiveDuplexEngine) { if(token==session) { disconnect(); _state.value=LiveDuplexState(error="Live silence timeout") } } }; break }
            }
        }
        captureJob?.cancel()
        captureJob = scope.launch {
            val vad=com.example.voice.LiveSilencePolicy()
            var neural = com.example.voice.SileroLiveVad.open(appContext)
            try {
            val buf = ByteArray(3200)
            var emptyReads = 0
            while (isActive && token==session && isAllowed() && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = rec.read(buf, 0, buf.size)
                if (AudioCapturePolicy.validPcmRead(read, buf.size)) {
                    emptyReads = 0
                    if(token!=session || !isAllowed()) break
                    lastFrameAt=SystemClock.elapsedRealtime()
                    val level=computeRms(buf, read)
                    if(level>0.0001f) lastNonzeroAt=lastFrameAt
                    val rmsSpeech = vad.update(level)
                    val speech = if (neural == null) rmsSpeech else try {
                        neural!!.update(buf, read)
                    } catch (e: Exception) {
                        Log.w("InfinityVAD", "Neural inference failed; adaptive RMS fallback")
                        runCatching { neural?.close() }; neural = null
                        rmsSpeech
                    }
                    if(speech) lastVoiceAt=lastFrameAt
                    _state.value = _state.value.copy(amplitude = level)
                    val b64 = Base64.encodeToString(buf, 0, read, Base64.NO_WRAP)
                    val payload = JSONObject().put(
                        "realtimeInput",
                        JSONObject().put("audio", JSONObject().put("mimeType", "audio/pcm;rate=16000").put("data", b64))
                    )
                    if(!ws.send(payload.toString())) { main.post { fail(token,"Live audio send failed") }; break }
                } else if (read == 0) {
                    if (AudioCapturePolicy.emptyReadLimit(++emptyReads)) { main.post { fail(token,"Live microphone returned no audio") }; break }
                    delay(AudioCapturePolicy.EMPTY_READ_DELAY_MS)
                } else { main.post { fail(token,"Live microphone read failed ($read)") }; break }
            }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { main.post { fail(token,"Live microphone capture failed") } }
            finally { runCatching { neural?.close() } }
        }
    }

    private fun computeRms(pcm: ByteArray, len: Int): Float {
        var sum = 0.0; var count = 0; var i = 0
        while (i + 1 < len) {
            val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toDouble()
            sum += s * s; count++; i += 2
        }
        return if (count == 0) 0f else (sqrt(sum / count) / 8000.0).toFloat().coerceIn(0f, 1f)
    }

    @Synchronized fun disconnect() {
        wanted=false; reconnectJob?.cancel(); reconnectJob=null
        closeSession()
        _state.value=LiveDuplexState()
    }
    private fun closeSession() {
        ++session; setupTimeout?.cancel(); setupTimeout=null
        val owned=com.example.voice.VoiceMic.lease.owns(this)
        val old=webSocket; webSocket=null; old?.cancel()
        cleanupAudio()
        if(owned) btRouter.clearRouting()
        com.example.voice.VoiceMic.lease.release(this)
        AgentHudBus.setMainMicBusy(false, this)
    }

    private fun cleanupAudio() {
        captureJob?.cancel(); captureJob = null; watchdog?.cancel(); watchdog=null
        ++playbackEpoch; playbackQueue?.cancel(); playbackQueue=null; playbackJob?.cancel(); playbackJob=null
        runCatching { agc?.release() }; agc=null
        runCatching { aec?.release() }; runCatching { ns?.release() }; aec = null; ns = null
        runCatching { audioRecord?.stop() }; runCatching { audioRecord?.release() }; audioRecord = null
        runCatching { audioTrack?.stop() }; runCatching { audioTrack?.release() }; audioTrack = null
    }

    fun release() { disconnect(); scope.cancel() }
}
