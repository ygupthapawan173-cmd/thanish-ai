package com.example.omega

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import com.example.voice.VoiceMic
import com.example.voice.NeuralWakeCapture
import com.example.voice.OwnerSpeaker
import com.example.ai.GeminiLiveDuplexEngine
import com.example.data.AiProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.control.AgentHudBus
import com.example.control.AgentHudState
import com.example.control.PhoneControlManager
import com.example.control.DeviceActionExecutor
import com.example.ai.PanduAiRepository
import com.example.data.AppDatabase
import com.example.voice.BackgroundVoicePolicy
import com.example.voice.VoiceInputController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.OmegaPreferences
import com.example.voice.TeluguTtsController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/** User-enabled microphone foreground service with bounded hands-free background turns. */
class OmegaWakeService: Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var prefs: OmegaPreferences
    private lateinit var speaker: OwnerSpeaker
    private var speakerLoading=false
    private var speakerFailed=false
    private lateinit var backgroundLive: GeminiLiveDuplexEngine
    private var liveAttempted=false
    private var liveActive=false
    private var liveWatch: Job?=null
    private lateinit var smartTriggers: SmartTriggerManager
    private lateinit var tts: TeluguTtsController
    private var model: Model? = null
    private var rec: Recognizer? = null
    private var speech: SpeechService? = null
    private var neuralWake: NeuralWakeCapture? = null
    private var neuralFailed = false
    private var loading = false
    @Volatile private var closed = false
    @Volatile private var started = false
    private var epoch = 0L
    private val rearmPolicy = WakeRearmPolicy()
    private val rearmAttempt = Runnable {
        rearmPolicy.consume()
        if (canListen() && speech == null && neuralWake == null) arm()
    }
    // A cancelled/coalesced StateFlow transition must not strand the idle wake service.
    private val rearmWatchdog = object : Runnable {
        override fun run() {
            if (closed) return
            if (started && !backgroundActive && canListen() && speech == null && neuralWake == null) schedule(0)
            handler.postDelayed(this, 2500L)
        }
    }
    private var retries = 0
    private var waitForTap = false
    private var wakePhrase = ""
    @Volatile private var backgroundActive = false
    private var backgroundToken = 0L
    private var backgroundJob: Job? = null
    private var captureJob: Job? = null
    private var backgroundTurns = 0
    private val backgroundContext=ArrayDeque<Pair<String,String>>()
    private var commandFallback = false
    private var commandCapture = false
    private var commandStartedLocked = false
    private var commandSpeech: SpeechService? = null
    private var commandRecognizer: Recognizer? = null
    private var commandOwnedModel: Model? = null
    private val wakeLease = Any()
    private val fallbackLease = Any()
    private var cue: android.media.ToneGenerator? = null
    private lateinit var voiceInput: VoiceInputController
    private lateinit var database: AppDatabase
    private lateinit var repository: PanduAiRepository
    private lateinit var phone: PhoneControlManager
    private val readyToSpeak = MutableStateFlow(false)
    private fun backgroundAllowed() = started && !closed && prefs.settings.value.wakeWordEnabled && !prefs.settings.value.micPrivacyMuted && ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED

    override fun onCreate() {
        super.onCreate(); instance = this
        prefs = OmegaPreferences(this); smartTriggers = SmartTriggerManager(this); tts = TeluguTtsController(this, backgroundOwned=true)
        database=AppDatabase(this); phone=PhoneControlManager(this)
        speaker=OwnerSpeaker(this)
        backgroundLive=GeminiLiveDuplexEngine(this,{prefs.settings.value.effectiveGeminiKey()}, {prefs.settings.value.geminiLiveVoiceModel}, {prefs.settings.value.bluetoothVoiceRouting}, backgroundOwned=true, liveSpeakerProvider={prefs.settings.value.geminiLiveSpeaker}, allowedProvider={ backgroundAllowed() && backgroundActive && !getSystemService(KeyguardManager::class.java).isDeviceLocked && !speaker.enabled && prefs.settings.value.useGeminiLiveAudio && prefs.settings.value.effectiveGeminiKey().isNotBlank() })
        scope.launch { backgroundLive.state.collect { st ->
            if(liveActive && backgroundActive) {
                if(st.error!=null) {
                    liveActive=false; backgroundLive.disconnect()
                    if(st.error.contains("silence timeout") || st.error.contains("privacy policy") || getSystemService(KeyguardManager::class.java).isDeviceLocked || !backgroundAllowed()) endBackground(st.error)
                    else listenCommand()
                }
                else if(st.isConnected || st.isConnecting) { if(status.value!=st.statusText) publish(st.statusText); AgentHudBus.updateState(AgentHudState.Active(if(st.isAiSpeaking) "Speaking…" else st.statusText,audioAmplitude=st.amplitude)) }
            }
        } }
        speakerLoading=true
        scope.launch {
            val loaded=withContext(Dispatchers.IO) { runCatching { speaker.load(); if(closed) speaker.close(); true }.getOrDefault(false) }
            speakerLoading=false; speakerFailed=!loaded
            if(!closed) { if(!loaded && speaker.enabled) publish("Owner speaker model unavailable — wake blocked") else schedule(100) }
        }
        repository=PanduAiRepository({ prefs.settings.value }, DeviceActionExecutor(this,database,prefs),database)
        voiceInput=VoiceInputController(this,owner="background-capture") { text -> if(backgroundActive && commandCapture) acceptCommand(text) }
        scope.launch { voiceInput.state.collect { v ->
            if(backgroundActive && commandCapture && (!commandFallback || (!speaker.enabled && prefs.settings.value.languageTag == "te-IN"))) {
                if(v.isListening) { if(status.value != v.diagnosticsNote) publish(v.diagnosticsNote); AgentHudBus.updateState(AgentHudState.Active("Listening…", audioAmplitude=v.audioAmplitude)) }
                else if(v.errorMessage!=null) { if(commandFallback) endBackground("Offline Telugu retry failed: ${v.errorMessage}") else fallbackCommand(v.errorMessage) }
            }
        } }
        scope.launch { AgentHudBus.abortSignal.collect { if(backgroundActive) endBackground("Conversation stopped") } }
        smartTriggers.register()
        handler.postDelayed(rearmWatchdog, 2500L)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"JARVIS INFINITY offline wake",NotificationManager.IMPORTANCE_LOW))
        scope.launch { AgentHudBus.isMainMicBusy.collect { if(it) { if(!backgroundActive) { waitForTap=false; pause() } } else schedule(700) } }
        scope.launch { AgentHudBus.isTtsBusy.collect { if(it) { if(!backgroundActive) pause() } else schedule(700) } }
        scope.launch { prefs.settings.collect { s ->
            if(liveActive && (s.effectiveGeminiKey().isBlank() || !s.useGeminiLiveAudio)) endBackground("Live session disabled by settings")
            if (!s.wakeWordEnabled && started) { endBackground("Wake disabled"); stopSelf(); return@collect }
            if (s.micPrivacyMuted) { speaker.cancelTraining(); endBackground("Microphone muted"); pause() } else { if((speech != null || neuralWake != null) && wakePhrase != s.wakeWord.trim().lowercase()) { pause(); retries=0 }; schedule(500) }
        } }
        scope.launch { SmartTriggerBus.events.collect { event ->
            val s=prefs.settings.value
            if(!AgentHudBus.isAppInForeground.value && !backgroundActive && !AgentHudBus.isMainMicBusy.value && !AgentHudBus.isTtsBusy.value && s.autoSpeak) { tts.configure(s.languageTag,s.ttsSpeed,s.ttsPitch,s.ttsStyle); tts.speak(event.announcement) }
        } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == TOGGLE_FILTER && started) {
            if(speaker.enrolled && !getSystemService(KeyguardManager::class.java).isDeviceLocked && !backgroundActive) { pause(); speaker.toggleFilter(); publish(if(speaker.enabled) "Owner filtering enabled (English commands; wake verified)" else "Owner filtering disabled"); schedule(200) }
            return START_STICKY
        }
        if(intent?.action == TRAIN && started) {
            if(!backgroundAllowed() || speaker.enrolled || backgroundActive || getSystemService(KeyguardManager::class.java).isDeviceLocked || AgentHudBus.isMainMicBusy.value || speakerLoading || speakerFailed) return START_STICKY
            pause(); speaker.begin(); publish("Voice training: say $wakePhrase this is my voice profile (0/3)"); schedule(150)
            handler.postDelayed({ if(!closed && speaker.trainingUntil!=0L && !speaker.isTraining) { speaker.cancelTraining(); pause(); schedule(100) } }, 91_000)
            return START_STICKY
        }
        if(intent?.action == CLEAR_PROFILE && started) {
            if(speaker.enrolled && !getSystemService(KeyguardManager::class.java).isDeviceLocked) { endBackground("Voice profile cleared"); pause(); speaker.clear(); schedule(100) }
            return START_STICKY
        }
        if(intent?.action == END_SESSION && started) { if(speaker.isTraining) { speaker.cancelTraining(); pause() }; endBackground("Conversation stopped"); schedule(700); return START_STICKY }
        if(intent?.action == RESTART && started) { endBackground("Restarting wake engine"); pause(); retries=0; schedule(500); return START_STICKY }
        if(intent?.action == STOP) { endBackground("Wake disabled"); prefs.update { it.copy(wakeWordEnabled=false) }; stopSelf(); return START_NOT_STICKY }
        if(!prefs.settings.value.wakeWordEnabled || ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY }
        if (started) { schedule(0); return START_STICKY }
        try { if(Build.VERSION.SDK_INT>=29) startForeground(ID, notification("Loading offline wake model"),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(ID,notification("Loading offline wake model")) }
        catch(e:Exception) { Log.w("OmegaVoice", "FGS_BLOCKED ${e.javaClass.simpleName}"); status.value="Microphone foreground service blocked"; stopSelf(); return START_NOT_STICKY }
        started=true
        if(model!=null) schedule(0) else if(!loading) {
            loading=true; publish("Loading offline Vosk model")
            StorageService.unpack(this,"model-en-us","omega-vosk-en",{m -> loading=false; if(closed) m.close() else {model=m; Log.i("OmegaVoice", "WAKE_MODEL_READY"); if(speaker.enabled && speakerFailed) publish("Owner speaker model unavailable — wake blocked") else schedule(200)}},{_ -> loading=false; if(!closed) fail("Offline wake model unavailable")})
        }
        return START_STICKY
    }
    private fun canListen(): Boolean = WakeRearmPolicy.eligible(
        started, closed, prefs.settings.value.wakeWordEnabled, prefs.settings.value.micPrivacyMuted,
        ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED,
        !speakerLoading && !(speaker.enabled && speakerFailed), waitForTap, backgroundActive,
        AgentHudBus.isMainMicBusy.value, AgentHudBus.isTtsBusy.value)
    private fun schedule(ms:Long) {
        if(!rearmPolicy.reserve(canListen(),model!=null,speech!=null || neuralWake!=null)) return
        if (!handler.postDelayed(rearmAttempt,ms.coerceAtLeast(0L))) rearmPolicy.cancel()
    }
    private fun pause() {
        ++epoch; handler.removeCallbacks(rearmAttempt); rearmPolicy.cancel()
        neuralWake?.stop(); neuralWake=null; val old=speech; speech=null
        runCatching { old?.cancel() }; runCatching { old?.shutdown() }; runCatching { rec?.close() }; rec=null; VoiceMic.lease.release(wakeLease)
        if(started&&!closed&&!waitForTap) publish(when {
            prefs.settings.value.micPrivacyMuted -> "Microphone muted"
            AgentHudBus.isMainMicBusy.value -> "Wake paused — command microphone in use"
            AgentHudBus.isTtsBusy.value -> "Wake paused — voice playback"
            else -> "Wake rearming"
        })
    }
    private fun arm() {
        val m=model?:return
        wakePhrase=prefs.settings.value.wakeWord.lowercase().trim()
        val phrase=wakePhrase.replace(Regex("\\s+")," ")
        if(!phrase.matches(Regex("[a-z]+( [a-z]+){1,5}"))) { fail("Use a 2–6 word English wake phrase"); return }
        // Owner verification and custom phrases remain on the original Vosk route.
        if (phrase == "hey jarvis" && !speaker.enabled && !speaker.isTraining && !neuralFailed && NeuralWakeCapture.available(this)) {
            val token = ++epoch
            val capture = NeuralWakeCapture(this, com.example.voice.AudioCapturePolicy.wakeThreshold(prefs.settings.value.wakeSensitivity).toFloat(),
                onReady = { if (epoch == token && canListen()) publish("Neural offline listening: hey jarvis") },
                onFinished = { hit, failed ->
                    if (epoch == token) {
                        neuralWake = null
                        if (failed) { neuralFailed = true; publish("Neural wake unavailable — Vosk fallback"); schedule(200) }
                        else if (hit && canListen() && !speaker.enabled && !speaker.isTraining) {
                            retries=0
                            if (AgentHudBus.isAppInForeground.value && !getSystemService(KeyguardManager::class.java).isDeviceLocked) {
                                AgentHudBus.emitWakeTrigger(""); schedule(1800)
                            } else startBackground()
                        } else schedule(700)
                    }
                })
            neuralWake = capture
            try { if (capture.start()) return } catch (_: Exception) { neuralFailed = true }
            neuralWake = null; schedule(700); return
        }
        if (!VoiceMic.lease.acquire(wakeLease)) { if(status.value != "Wake waiting for microphone release") publish("Wake waiting for microphone release"); schedule(700); return }
        try {
            val basePhrases=if(phrase=="hey jarvis") listOf(phrase,"hey jervis","hey jar vis","hey jar vice") else listOf(phrase)
            val phrases=if(speaker.enabled) basePhrases+"$phrase this is my voice" else basePhrases
            val training=speaker.isTraining
            val trainingPhrase="$phrase this is my voice profile"
            val grammar=if(training) listOf(trainingPhrase,"[unk]") else BackgroundVoicePolicy.wakeGrammar(phrases)
            val r=Recognizer(m,16000f,JSONArray(grammar).toString()); rec=r; r.setWords(true)
            if(training || speaker.enabled) r.setSpeakerModel(speaker.model ?: error("Speaker model unavailable"))
            val service=SpeechService(r,16000f); speech=service; val token=++epoch
            val threshold=com.example.voice.AudioCapturePolicy.wakeThreshold(prefs.settings.value.wakeSensitivity)
            fun current()=token==epoch && canListen()
            val listener=object:RecognitionListener {
                override fun onPartialResult(hypothesis:String?) {
                    if (!current()) return
                    // Partials are telemetry only: do not trigger sensitive actions from unstable hypotheses.
                    val partial=runCatching { JSONObject(hypothesis.orEmpty()).optString("partial") }.getOrDefault("")
                    if (BackgroundVoicePolicy.matchWake(partial, phrases)!=null) Log.d("OmegaVoice", "WAKE_PARTIAL_MATCH")
                }
                override fun onResult(hypothesis:String?) {
                    if(!current()) return
                    val json=runCatching { JSONObject(hypothesis.orEmpty()) }.getOrNull()?:return
                    val heard=json.optString("text").trim(); val words=json.optJSONArray("result")?:return
                    if(training) {
                        if(!speaker.isTraining || getSystemService(KeyguardManager::class.java).isDeviceLocked) { speaker.cancelTraining(); pause(); schedule(100); return }
                        if(heard!=trainingPhrase) return
                        val vector=speaker.vector(json) ?: run { publish("Training: speak the full phrase slowly"); return }
                        val n=runCatching { speaker.enroll(vector) }.getOrElse { speaker.cancelTraining(); publish("Voice profile save failed"); pause(); schedule(100); return }
                        if(n==3) { pause(); publish("Owner voice profile saved"); schedule(500) }
                        else publish("Voice training: say $trainingPhrase ($n/3)")
                        return
                    }
                    val matched=BackgroundVoicePolicy.matchWake(heard,phrases) ?: return
                    val count=matched.split(" ").size
                    if(words.length()<count || (0 until count).any { !com.example.voice.AudioCapturePolicy.confidenceAccepted(words.optJSONObject(it)?.optDouble("conf",Double.NaN)?:Double.NaN,threshold) }) return
                    val verifiedWake = !speaker.enabled || speaker.verify(json)
                    if(!com.example.voice.AudioCapturePolicy.ownerWakeAllowed(speaker.enabled, verifiedWake)) { Log.i("OmegaVoice","OWNER_WAKE_REJECTED"); publish("Owner voice not verified — repeat clearly"); return }
                    val remainder=heard.removePrefix(matched).trim()
                    val inline=remainder.takeIf { verifiedWake && it in BackgroundVoicePolicy.inlineCommands && (count until words.length()).all { i -> com.example.voice.AudioCapturePolicy.confidenceAccepted(words.optJSONObject(i)?.optDouble("conf",Double.NaN)?:Double.NaN,threshold) } }.orEmpty()
                    Log.i("OmegaVoice", "WAKE_MATCH inline=${inline.isNotBlank()}")
                    waitForTap=true; pause(); retries=0
                    if(!speaker.enabled && AgentHudBus.isAppInForeground.value && !getSystemService(KeyguardManager::class.java).isDeviceLocked) {
                        waitForTap=false; AgentHudBus.emitWakeTrigger(inline); schedule(1800)
                    } else {
                        waitForTap=false; startBackground(inline)
                    }
                }
                override fun onFinalResult(hypothesis:String?)=onResult(hypothesis)
                override fun onError(exception:Exception?) { if(current()) recover() }
                override fun onTimeout() { if(current()) recover() }
            }
            check(service.startListening(listener)); handler.postDelayed({ if(current()) retries=0 }, 30_000L); publish(if(training) "Voice training: say $trainingPhrase (${speaker.training.size}/3)" else if(speaker.enabled) "Owner wake listening: $phrase" else "Offline listening: $phrase"); Log.i("OmegaVoice", "VOSK_ARMED")
        } catch(_:Exception) { recover() } catch(_:LinkageError) { fail("Vosk native library unavailable") }
    }
    private fun startBackground(inline: String = "") {
        if(!backgroundAllowed() || backgroundActive || AgentHudBus.isMainMicBusy.value || AgentHudBus.isTtsBusy.value) { schedule(700); return }
        backgroundActive=true; backgroundTurns=0; backgroundContext.clear(); liveAttempted=false; liveActive=false; val token=++backgroundToken
        AgentHudBus.setMainMicBusy(true,this)
        publish("Hey JARVIS — preparing command microphone")
        AgentHudBus.updateState(AgentHudState.Active("Hey JARVIS — listening"))
        if (inline.isNotBlank() && BackgroundVoicePolicy.classify(inline)!=BackgroundVoicePolicy.Action.QUESTION) {
            commandCapture=true; acceptCommand(inline)
        } else {
            // Audible acknowledgement when user enabled replies. Capture only after TTS completion.
            if(prefs.settings.value.autoSpeak) {
                speakReply("Yes, Boss",false,acknowledgement=true)
                return
            }
            // Otherwise use a short ready cue.
            runCatching {
                cue?.release()
                cue=android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 65)
                cue?.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 120)
            }
            handler.postDelayed({ if(token==backgroundToken && backgroundActive) { runCatching { cue?.release() }; cue=null; listenCommand() } }, 250)
        }
    }
    private fun listenCommand() {
        if(!backgroundAllowed() || !backgroundActive) { endBackground("Wake paused"); return }
        commandCapture=true; commandFallback=false; commandStartedLocked=getSystemService(KeyguardManager::class.java).isDeviceLocked
        val s=prefs.settings.value
        watchCommandPrivacy(backgroundToken)
        if(speaker.enabled) { fallbackCommand("Owner verification required"); return }
        if(!liveAttempted && s.useGeminiLiveAudio && s.effectiveGeminiKey().isNotBlank() && !getSystemService(KeyguardManager::class.java).isDeviceLocked) {
            liveAttempted=true; liveActive=true; commandCapture=false
            backgroundLive.connect()
            val token=backgroundToken
            liveWatch?.cancel(); liveWatch=scope.launch {
                // Bounded cloud session; no new network audio while locked or privacy muted.
                delay(120_000); if(token==backgroundToken && liveActive) endBackground("Live session time limit")
            }
            return
        }
        publish("Speak now — background command")
        voiceInput.startListening(s.languageTag,preferOffline=s.preferOfflineSpeech || getSystemService(KeyguardManager::class.java).isDeviceLocked,timeoutMs=10000L)
    }
    private fun commandPrivacyAllowed() = com.example.voice.VoiceHealthPolicy.commandCaptureAllowed(backgroundAllowed(), commandStartedLocked, getSystemService(KeyguardManager::class.java).isDeviceLocked)
    private fun watchCommandPrivacy(token:Long) {
        captureJob?.cancel(); captureJob=scope.launch { repeat(76) {
                delay(500)
                if(token!=backgroundToken || !commandCapture) return@launch
                if(!commandPrivacyAllowed()) { endBackground("Command paused by privacy policy"); return@launch }
            }; if(token==backgroundToken && commandCapture) endBackground("Command timed out") }
    }
    private fun fallbackCommand(reason:String) {
        if(!backgroundActive || !commandCapture || commandFallback) return
        commandFallback=true; voiceInput.stopListening()
        if (!speaker.enabled) { endBackground("Speech provider unavailable — wake listening resumed"); return }
        if (prefs.settings.value.languageTag == "te-IN") {
            endBackground("Owner-filtered Telugu commands unavailable in Online Edition; select English or unlock and use manual voice")
            return
        }
        val token=backgroundToken
        publish("Owner verified English command — speak now")
        watchCommandPrivacy(token)
        fun startFallback(m:Model, owned:Boolean) {
            if(token!=backgroundToken || !backgroundActive || !commandCapture || !commandPrivacyAllowed()) { if(owned) runCatching { m.close() }; return }
            if(!VoiceMic.lease.acquire(fallbackLease)) { if(owned) runCatching { m.close() }; endBackground("Command microphone occupied"); return }
            if(owned) commandOwnedModel=m
            try {
                val r=Recognizer(m,16000f); commandRecognizer=r; if(speaker.enabled) r.setSpeakerModel(speaker.model ?: error("Speaker model unavailable"))
                val service=SpeechService(r,16000f); commandSpeech=service
                fun current()=token==backgroundToken && backgroundActive && commandCapture && commandPrivacyAllowed() && commandSpeech===service
                fun result(h:String?) { if(!current()) return; val json=runCatching { JSONObject(h.orEmpty()) }.getOrNull() ?: return; val text=json.optString("text").trim(); if(text.isNotBlank()) { if(speaker.enabled && !speaker.verify(json)) endBackground("Owner command not verified") else acceptCommand(text) } }
                check(service.startListening(object:RecognitionListener {
                    override fun onPartialResult(hypothesis:String?)=Unit
                    override fun onResult(hypothesis:String?)=result(hypothesis)
                    override fun onFinalResult(hypothesis:String?)=result(hypothesis)
                    override fun onError(exception:Exception?) { if(current()) endBackground("Offline command microphone failed") }
                    override fun onTimeout() { if(current()) endBackground("No speech — wake listening resumed") }
                },8000))
            } catch(_:Exception) { endBackground("Offline command unavailable") }
        }
        handler.postDelayed({
            if(token==backgroundToken && backgroundActive && commandCapture && commandPrivacyAllowed()) {
                model?.let { startFallback(it,false) } ?: endBackground(reason)
            }
        },450)
    }
    private fun stopCommandCapture() {
        commandCapture=false; captureJob?.cancel(); captureJob=null; voiceInput.stopListening()
        val old=commandSpeech; commandSpeech=null
        runCatching { old?.cancel() }; runCatching { old?.shutdown() }; runCatching { commandRecognizer?.close() }; commandRecognizer=null; runCatching { commandOwnedModel?.close() }; commandOwnedModel=null; VoiceMic.lease.release(fallbackLease)
    }
    private fun acceptCommand(raw:String) {
        if(!backgroundActive || !commandCapture || !commandPrivacyAllowed()) return
        stopCommandCapture(); backgroundTurns++
        val token=backgroundToken
        val action=BackgroundVoicePolicy.classify(raw)
        val direct=com.example.ai.DirectDeviceCommand.parse(raw)
        if(action==BackgroundVoicePolicy.Action.STOP) { endBackground("Conversation stopped"); return }
        publish("Thinking — background voice")
        AgentHudBus.updateState(AgentHudState.Active("Thinking…",isThinking=true))
        backgroundJob=scope.launch {
            val te=raw.any { it in '\u0C00'..'\u0C7F' }
            val localReply = if(direct == null) null else when(direct.tool) {
                "toggle_flashlight" -> phone.setFlashlight(direct.argument as Boolean)
                "set_media_volume" -> phone.setMediaVolume(direct.argument as Int)
                "get_battery_status" -> if(te) "బ్యాటరీ ${phone.getBatteryPercent()} శాతం" else phone.getBatterySummary()
                "open_app", "set_timer" -> if(getSystemService(KeyguardManager::class.java).isDeviceLocked || !AgentHudBus.isAppInForeground.value) "Open JARVIS and unlock your phone to launch apps or timers." else runCatching { if(direct.tool=="open_app") phone.openAppByName(direct.argument as String) else phone.setTimer(direct.argument as Int,"JARVIS INFINITY") }.getOrDefault("Action unavailable")
                else -> null
            }
            val reply=com.example.ai.CreatorIdentity.reply(raw) ?: localReply ?: when(action) {
                BackgroundVoicePolicy.Action.TIME -> (if(te) "ఇప్పుడు సమయం " else "The time is ")+SimpleDateFormat("hh:mm a",Locale.getDefault()).format(Date())
                BackgroundVoicePolicy.Action.DATE -> (if(te) "ఈరోజు తేదీ " else "Today is ")+SimpleDateFormat("dd MMM yyyy",Locale.getDefault()).format(Date())
                BackgroundVoicePolicy.Action.BATTERY -> if(te) "బ్యాటరీ ${phone.getBatteryPercent()} శాతం" else "Battery is ${phone.getBatteryPercent()} percent."
                BackgroundVoicePolicy.Action.TORCH_ON, BackgroundVoicePolicy.Action.TORCH_OFF -> runCatching { phone.setFlashlight(action==BackgroundVoicePolicy.Action.TORCH_ON) }.getOrDefault("Flashlight unavailable")
                BackgroundVoicePolicy.Action.GREETING -> if(te) "నమస్తే, చెప్పండి." else "Hello, how can I help?"
                else -> if (getSystemService(KeyguardManager::class.java).isDeviceLocked) {
                    if(te) "దయచేసి ఫోన్ అన్‌లాక్ చేయండి." else "Please unlock your phone for AI questions."
                } else if (!BackgroundVoicePolicy.canSendCloud(false,false)) {
                    if(te) "ఆఫ్‌లైన్ AI అందుబాటులో లేదు." else "Offline general AI is unavailable."
                } else withTimeoutOrNull(35000) { repository.answerBackgroundQuestion(raw,backgroundContext.toList()).onFailure { Log.w("OmegaVoice", "AI_REPLY_FAILED ${it.javaClass.simpleName}") }.getOrNull() } ?: if(te) "AI సమాధానం అందుబాటులో లేదు. యాప్‌లో సెట్టింగ్స్ చూడండి." else "AI answer unavailable. Check the connection or open the app."
            }
            if(token==backgroundToken && backgroundActive && backgroundAllowed()) {
                if(action==BackgroundVoicePolicy.Action.QUESTION && !getSystemService(KeyguardManager::class.java).isDeviceLocked) {
                    backgroundContext.addLast(raw.take(500) to reply.take(700)); while(backgroundContext.size>2) backgroundContext.removeFirst()
                }
                speakReply(reply,true)
            }
        }
    }
    private fun speakReply(text:String, allowFollowUp:Boolean, acknowledgement:Boolean=false) {
        if(!backgroundActive || !backgroundAllowed()) { endBackground("Wake paused"); return }
        stopCommandCapture()
        val s=prefs.settings.value; val token=backgroundToken
        if(!s.autoSpeak) { endBackground("Voice reply disabled in settings"); return }
        publish("Speaking — background voice")
        AgentHudBus.updateState(AgentHudState.Active("Speaking…"))
        readyToSpeak.value=false
        tts.configure(s.languageTag,s.ttsSpeed,s.ttsPitch,s.ttsStyle)
        tts.setOnSpeechFinished { readyToSpeak.value=true }
        tts.speak(text.take(700))
        scope.launch {
            val finished=withTimeoutOrNull(25000) { readyToSpeak.first { it }; true } ?: false
            if(token!=backgroundToken || !backgroundActive) return@launch
            if(!finished || !tts.lastSpeechSucceeded) { if(acknowledgement) { tts.setOnSpeechFinished(null); tts.stop(); listenCommand() } else endBackground("Speaker unavailable or playback failed"); return@launch }
            delay(550)
            if(token!=backgroundToken || !backgroundActive) return@launch
            if(acknowledgement) listenCommand()
            else if(allowFollowUp && BackgroundVoicePolicy.followUp(prefs.settings.value.continuousConversation,backgroundTurns)) listenCommand()
            else endBackground("Conversation complete")
        }
    }
    private fun endBackground(message:String) {
        if(!backgroundActive) return
        backgroundActive=false; backgroundContext.clear(); liveActive=false; liveWatch?.cancel(); liveWatch=null; backgroundLive.disconnect(); ++backgroundToken; runCatching { cue?.release() }; cue=null; backgroundJob?.cancel(); backgroundJob=null
        stopCommandCapture(); tts.setOnSpeechFinished(null); tts.stop()
        AgentHudBus.setMainMicBusy(false,this); AgentHudBus.updateState(AgentHudState.Hidden)
        if(!closed) { publish(message); Log.i("OmegaVoice", "WAKE_REARM"); schedule(700) }
    }
    private fun recover() { pause(); retries=(retries+1).coerceAtMost(6); val wait = WakeRetryPolicy.delayMs(retries); publish("Wake recovery $retries — retry in ${wait/1000}s"); schedule(wait) }
    private fun fail(message:String) { Log.w("OmegaVoice", "WAKE_FAILED $message"); status.value=message; prefs.update { it.copy(wakeWordEnabled=false) }; stopSelf() }
    private fun publish(message:String,detected:Boolean=false) { Log.i("OmegaVoice", "STATE $message"); status.value=message; runCatching { getSystemService(NotificationManager::class.java).notify(ID,notification(message,detected)) } }
    private fun notification(message:String,detected:Boolean=false):Notification {
        val open=Intent(this,MainActivity::class.java).apply { flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP; putExtra(MainActivity.EXTRA_START_LISTENING,detected) }
        val tap=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end=PendingIntent.getService(this,3,Intent(this,OmegaWakeService::class.java).setAction(END_SESSION),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val profileAction=if(speaker.enrolled) CLEAR_PROFILE else TRAIN
        val profile=PendingIntent.getService(this,if(speaker.enrolled) 6 else 5,Intent(this,OmegaWakeService::class.java).setAction(profileAction),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,2,Intent(this,OmegaWakeService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val filter=PendingIntent.getService(this,7,Intent(this,OmegaWakeService::class.java).setAction(TOGGLE_FILTER),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("JARVIS INFINITY • Offline Wake").setContentText(message).setContentIntent(tap).setOngoing(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Stop microphone",stop).addAction(android.R.drawable.ic_media_pause,"End conversation",end).addAction(android.R.drawable.ic_popup_sync,if(speaker.enrolled) "Delete voice profile" else "Train owner voice",profile).apply { if(speaker.enrolled) addAction(android.R.drawable.ic_menu_manage,if(speaker.enabled) "Owner filter OFF" else "Owner filter ON",filter) }.build()
    }
    override fun onDestroy() { closed=true; endBackground("Stopped"); voiceInput.release(); pause(); handler.removeCallbacksAndMessages(null); scope.cancel(); prefs.close(); backgroundLive.release(); speaker.close(); smartTriggers.unregister(); tts.release(); database.close(); runCatching { model?.close() }; model=null; instance=null; if(!status.value.contains("failed")&&!status.value.contains("unavailable")&&!status.value.contains("blocked")&&!status.value.startsWith("Use a")) status.value="Stopped"; stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
    companion object {
        private const val CHANNEL="omega_offline_wake_v202"; private const val ID=3191; private const val STOP="com.example.omega.STOP_WAKE"
        private const val TOGGLE_FILTER="com.example.omega.TOGGLE_OWNER_FILTER"
        private const val TRAIN="com.example.omega.TRAIN_OWNER"; private const val CLEAR_PROFILE="com.example.omega.CLEAR_OWNER"
        private const val END_SESSION="com.example.omega.END_SESSION"; private const val RESTART="com.example.omega.RESTART_WAKE"
        private var instance:OmegaWakeService?=null
        val status=MutableStateFlow("Stopped")
        fun ownerEnrolled() = instance?.speaker?.enrolled == true
        fun ownerFilterEnabled() = instance?.speaker?.enabled == true
        fun ownerAction(context:Context, action:String) {
            if (!isRunning()) return
            val command = when(action) { "train" -> TRAIN; "clear" -> CLEAR_PROFILE; "filter" -> TOGGLE_FILTER; "restart" -> RESTART; else -> return }
            context.startService(Intent(context,OmegaWakeService::class.java).setAction(command))
        }
        fun isRunning() = instance?.started == true && instance?.closed == false
        fun isBackgroundConversationActive() = instance?.backgroundActive == true
        fun stopBackgroundConversation() { instance?.endBackground("Conversation stopped") }
        fun pauseForManual() { instance?.let { it.endBackground("Manual microphone requested"); it.waitForTap=false; it.pause(); it.schedule(700) } }
        fun requestIdleRearm() { instance?.schedule(700) }
        fun start(context:Context) { ContextCompat.startForegroundService(context,Intent(context,OmegaWakeService::class.java)) }
        fun stop(context:Context) { context.stopService(Intent(context,OmegaWakeService::class.java)) }
    }
}
