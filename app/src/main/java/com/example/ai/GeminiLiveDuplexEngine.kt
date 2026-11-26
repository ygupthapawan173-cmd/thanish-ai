package com.example.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.content.ContextCompat
import com.example.omega.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/** Foreground, user-started Gemini Live session. No tools, background recording or hidden capture. */
class GeminiLiveDuplexEngine(private val context: Context) {
    data class Telemetry(val connected:Boolean=false,val stateLabel:String="Standby",
        val micAmplitude:Float=0f,val speakerAmplitude:Float=0f,val lastTranscript:String="")
    private val _telemetry=MutableStateFlow(Telemetry())
    val telemetry=_telemetry.asStateFlow()
    private val handler=Handler(Looper.getMainLooper())
    private val client=OkHttpClient.Builder().pingInterval(20,TimeUnit.SECONDS)
        .readTimeout(0,TimeUnit.MILLISECONDS).followRedirects(false).build()
    private var generation=0L
    private var ws:WebSocket?=null
    private var lease=false
    private var scope:CoroutineScope?=null
    private var record:AudioRecord?=null
    private var track:AudioTrack?=null
    private var aec:AcousticEchoCanceler?=null
    private var ns:NoiseSuppressor?=null
    private var audioQueue:Channel<ByteArray>?=null
    private val manager=context.getSystemService(AudioManager::class.java)
    private var focus:AudioFocusRequest?=null
    private val focusListener=AudioManager.OnAudioFocusChangeListener { change ->
        if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            handler.post { disconnect("Audio focus lost") }
    }
    private fun state(label:String) { _telemetry.value=_telemetry.value.copy(stateLabel=label) }
    fun connect(model:String) {
        disconnect()
        if (OmegaVoicePreferences.get(context)==OmegaVoiceMode.LOCAL) { state("On-device-only mode: Live disabled");return }
        if (ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { state("Microphone permission required");return }
        val key=EncryptedApiKeyStore(context).getApiKey(AiProvider.GEMINI)
        if(key.isNullOrBlank()) { state("Configure Gemini key in Settings");return }
        if(!validModel(model)) { state("Enter a valid Live model ID");return }
        if(!OmegaAssistantCore.sessions.acquireMic(MicOwner.LIVE)) { state("Stop current voice/AI session first");return }
        lease=true
        val id=++generation
        state("Connecting")
        val request=Request.Builder().url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent")
            .header("x-goog-api-key",key).build()
        ws=client.newWebSocket(request,object:WebSocketListener() {
            override fun onOpen(socket:WebSocket,response:Response) {
                handler.post { if(id==generation) {
                    val setup=JSONObject().put("setup",JSONObject().put("model","models/"+model.removePrefix("models/"))
                        .put("generationConfig",JSONObject().put("responseModalities",JSONArray().put("AUDIO")))
                        .put("inputAudioTranscription",JSONObject()).put("outputAudioTranscription",JSONObject())
                        .put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text","You are JARVIS OMEGA. Reply in Telugu or English matching the user. Do not claim to control devices.")))))
                    if(!socket.send(setup.toString())) disconnect("Setup send failed") else state("Awaiting server setup")
                } }
            }
            override fun onMessage(socket:WebSocket,text:String) { handler.post { if(id==generation) receive(text,id) } }
            override fun onMessage(socket:WebSocket,bytes:ByteString) = onMessage(socket,bytes.utf8())
            override fun onFailure(socket:WebSocket,t:Throwable,response:Response?) {
                handler.post { if(id==generation) disconnect("Live connection failed"+(response?.code?.let{" (HTTP "+it+")"}?:"")) }
            }
            override fun onClosing(socket:WebSocket,code:Int,reason:String) { handler.post { if(id==generation) disconnect("Server closed: "+code) } }
            override fun onClosed(socket:WebSocket,code:Int,reason:String) { handler.post { if(id==generation) disconnect("Session closed: "+code) } }
        })
        handler.postDelayed({if(id==generation && !_telemetry.value.connected) disconnect("Setup timed out")},15000)
        handler.postDelayed({if(id==generation) disconnect("Session time limit reached; start again if needed")},600000)
    }
    @SuppressLint("MissingPermission")
    private fun startAudio(id:Long) {
        try {
            if(android.os.Build.VERSION.SDK_INT>=26) {
                val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setOnAudioFocusChangeListener(focusListener,handler).build()
                focus=request
                check(manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            }
            val rb=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
            val tb=AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
            check(rb>0 && tb>0)
            val mic=AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,rb.coerceAtLeast(3200))
            record=mic;check(mic.state==AudioRecord.STATE_INITIALIZED)
            if(AcousticEchoCanceler.isAvailable()) aec=runCatching{AcousticEchoCanceler.create(mic.audioSessionId)?.apply{enabled=true}}.getOrNull()
            if(NoiseSuppressor.isAvailable()) ns=runCatching{NoiseSuppressor.create(mic.audioSessionId)?.apply{enabled=true}}.getOrNull()
            val speaker=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(tb.coerceAtLeast(4800)).setTransferMode(AudioTrack.MODE_STREAM).build()
            track=speaker;check(speaker.state==AudioTrack.STATE_INITIALIZED)
            speaker.play();mic.startRecording();check(mic.recordingState==AudioRecord.RECORDSTATE_RECORDING)
            val session=CoroutineScope(SupervisorJob()+Dispatchers.IO);scope=session
            val queue=Channel<ByteArray>(32);audioQueue=queue
            val socket=ws?:error("Socket lost")
            _telemetry.value=Telemetry(true,"Live • AEC "+(aec?.enabled==true)+" • NS "+(ns?.enabled==true))
            session.launch {
                val buf=ByteArray(1600)
                while(isActive) {
                    val n=mic.read(buf,0,buf.size)
                    if(n<=0) { handler.post{if(id==generation) disconnect("Microphone read failed")};break }
                    val amp=pcmRms(buf,n)
                    val data=Base64.encodeToString(buf,0,n,Base64.NO_WRAP)
                    val msg=JSONObject().put("realtimeInput",JSONObject().put("audio",JSONObject().put("mimeType","audio/pcm;rate=16000").put("data",data)))
                    if(socket.queueSize()>256000 || !socket.send(msg.toString())) { handler.post{if(id==generation) disconnect("Network too slow")};break }
                    handler.post { if(id==generation) _telemetry.value=_telemetry.value.copy(micAmplitude=amp) }
                }
            }
            session.launch {
                for(pcm in queue) {
                    if(!isActive) break
                    val n=speaker.write(pcm,0,pcm.size)
                    if(n<0) { handler.post{if(id==generation) disconnect("Playback failed")};break }
                    handler.post { if(id==generation) _telemetry.value=_telemetry.value.copy(speakerAmplitude=pcmRms(pcm,pcm.size)) }
                }
            }
        } catch(_:Exception) { disconnect("Microphone or playback unavailable") }
    }
    private fun receive(raw:String,id:Long) {
        try {
            val json=JSONObject(raw)
            if(json.has("setupComplete") && !_telemetry.value.connected) {startAudio(id);return}
            if(!_telemetry.value.connected) return
            val content=json.optJSONObject("serverContent")?:return
            if(content.optBoolean("interrupted")) {
                val queue=audioQueue
                while(queue?.tryReceive()?.isSuccess==true) {}
                runCatching { track?.pause();track?.flush();track?.play() }
                _telemetry.value=_telemetry.value.copy(speakerAmplitude=0f,stateLabel="Interrupted")
                return
            }
            val transcript=content.optJSONObject("outputTranscription")?.optString("text").orEmpty()
            if(transcript.isNotBlank()) _telemetry.value=_telemetry.value.copy(lastTranscript=(_telemetry.value.lastTranscript+" "+transcript).takeLast(1000))
            val parts=content.optJSONObject("modelTurn")?.optJSONArray("parts")
            if(parts!=null) for(i in 0 until parts.length()) {
                val item=parts.optJSONObject(i)?.optJSONObject("inlineData")?:continue
                if(item.optString("mimeType").startsWith("audio/pcm")) {
                    val data=Base64.decode(item.getString("data"),Base64.DEFAULT)
                    if(data.size>256000 || audioQueue?.trySend(data)?.isSuccess!=true) {disconnect("Playback buffer overflow");return}
                }
            }
            if(content.optBoolean("turnComplete")) _telemetry.value=_telemetry.value.copy(stateLabel="Live • listening",speakerAmplitude=0f)
        } catch(_:Exception) {disconnect("Invalid server payload")}
    }
    fun sendTextTurn(text:String) {
        if(_telemetry.value.connected && text.isNotBlank()) {
            if(ws?.send(JSONObject().put("realtimeInput",JSONObject().put("text",text.take(2000))).toString())!=true) disconnect("Text send failed")
        }
    }
    /** Explicit user-selected JPEG only, not background screen capture. */
    fun sendVisionFrame(jpeg:ByteArray):Boolean {
        if(!_telemetry.value.connected || jpeg.isEmpty() || jpeg.size>500000 || (ws?.queueSize() ?: Long.MAX_VALUE)>256000L) return false
        return ws?.send(JSONObject().put("realtimeInput",JSONObject().put("video",JSONObject().put("mimeType","image/jpeg")
            .put("data",Base64.encodeToString(jpeg,Base64.NO_WRAP)))).toString())==true
    }
    fun disconnect(label:String="Standby") {
        ++generation;ws?.cancel();ws=null
        scope?.cancel();scope=null;audioQueue?.close();audioQueue=null
        runCatching{record?.stop()};runCatching{track?.pause();track?.flush();track?.stop()}
        runCatching{aec?.release()};runCatching{ns?.release()};aec=null;ns=null
        runCatching{record?.release()};record=null;runCatching{track?.release()};track=null
        if(android.os.Build.VERSION.SDK_INT>=26) focus?.let{runCatching{manager.abandonAudioFocusRequest(it)}}
        focus=null
        if(lease){lease=false;OmegaAssistantCore.sessions.releaseMic(MicOwner.LIVE)}
        _telemetry.value=Telemetry(stateLabel=label)
    }
    fun release(){disconnect();client.dispatcher.executorService.shutdown();client.connectionPool.evictAll()}
    companion object {
        fun validModel(model:String)=model.removePrefix("models/").matches(Regex("[A-Za-z0-9._-]{3,100}"))
        fun pcmRms(pcm:ByteArray,length:Int):Float {
            val end=length.coerceIn(0,pcm.size);var sum=0.0;var count=0;var i=0
            while(i+1<end){val s=((pcm[i+1].toInt() shl 8) or (pcm[i].toInt() and 255)).toShort().toDouble();sum+=s*s;count++;i+=2}
            return if(count==0) 0f else (sqrt(sum/count)/32768.0).toFloat().coerceIn(0f,1f)
        }
    }
}
