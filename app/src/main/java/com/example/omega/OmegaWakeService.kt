package com.example.omega

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.ai.AiStreamEvent
import com.example.ai.PanduAiRepository
import com.example.ai.GeminiLiveDuplexEngine
import com.example.data.AppDatabase
import com.example.data.ChatRepository
import com.example.data.TtsPreferencesRepository
import com.example.voice.TeluguTtsController
import com.example.voice.TtsReadiness
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.json.JSONArray
import com.example.voice.VoiceInputController
import com.example.voice.SpeechLocaleOption
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/** Explicit foreground microphone session. No hidden recording or automatic action execution. */
class OmegaWakeService : Service(), RecognitionListener {
    companion object {
        private const val CHANNEL = "omega_wake_v1"
        private const val MIC = 7201
        private const val RESULT = 7202
        private const val STOP = "com.example.omega.STOP_WAKE"
        private const val ENROLL = "com.example.omega.ENROLL_SPEAKER"
        private const val CANCEL_ENROLL = "com.example.omega.CANCEL_ENROLL_SPEAKER"
        val enrollmentActive=MutableStateFlow(false)
        val enrollmentStatus=MutableStateFlow("Not recording")
        val enrolledSpeaker=MutableStateFlow(false)
        val speakerRejections=MutableStateFlow(0)
        @Volatile private var enrollmentRequested=false
        fun requestEnrollment(c:Context) { if(active.value) {enrollmentRequested=true;runCatching{c.startService(Intent(c,OmegaWakeService::class.java).setAction(ENROLL))}.onFailure{enrollmentRequested=false;enrollmentStatus.value="Could not start enrollment"}} }
        fun cancelEnrollment(c:Context) { val requested=enrollmentRequested || enrollmentActive.value;enrollmentRequested=false;if(active.value && requested)runCatching{c.startService(Intent(c,OmegaWakeService::class.java).setAction(CANCEL_ENROLL))} }
        val status = MutableStateFlow("Stopped")
        val active = MutableStateFlow(false)
        val recognitionReady = MutableStateFlow(false)
        val pendingCommand = MutableStateFlow("")
        private val reviewQueue=mutableListOf<String>()
        val pendingQueueSize=MutableStateFlow(0)
        private fun publishPending(){pendingQueueSize.value=reviewQueue.size;pendingCommand.value=reviewQueue.firstOrNull().orEmpty()}
        @Synchronized fun resolvePending(expected:String){val updated=OmegaEliteConversation.removePending(reviewQueue.toList(),expected);reviewQueue.clear();reviewQueue.addAll(updated);publishPending()}
        @Synchronized private fun offerPending(text:String):Boolean {val updated=OmegaEliteConversation.appendPending(reviewQueue.toList(),text)?:return false;reviewQueue.clear();reviewQueue.addAll(updated);publishPending();return true}
        val lastPartial = MutableStateFlow("")
        val lastError = MutableStateFlow("")
        val lastWakeAt = MutableStateFlow(0L)
        val capturedCount = MutableStateFlow(0)
        val acceptedWakeCount = MutableStateFlow(0)
        val recognitionStage = MutableStateFlow("Stopped")
        val liveTelemetry = MutableStateFlow(GeminiLiveDuplexEngine.Telemetry())
        val rejectedWakeCount = MutableStateFlow(0)
        fun start(c: Context) {
            ContextCompat.startForegroundService(c, Intent(c, OmegaWakeService::class.java))
        }
        fun stop(c: Context) {
            c.stopService(Intent(c, OmegaWakeService::class.java))
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speech: SpeechService? = null
    private var loading = false
    private var closed = false
    private var armed = false
    private val wakeGate = WakeGate(1600L)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    // Recovery is bounded and only active while the user-started foreground service remains alive.
    private var recognitionRestarts = 0
    private var reconnectPending = false
    private var ownsMic = false
    private var processing = false
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var assistantTts: TeluguTtsController? = null
    private var strictGrammar = true
    private var commandPhase = false
    private var offlineEpoch = 0L
    private var captureJob: Job? = null
    private var platformCommand: VoiceInputController? = null
    private var wakeLive:GeminiLiveDuplexEngine?=null
    private var liveCollector:Job?=null
    private var liveGeneration=0L
    private var liveRetries=0
    private var followupTurns=0
    private var liveLock:PowerManager.WakeLock?=null
    private var speakerModel:SpeakerModel?=null
    private var ownerTemplate:List<Double>?=null
    private var ownerGate=false
    private var enrollmentJob:Job?=null
    private var enrollmentEpoch=0L
    private val enrollmentSamples=mutableListOf<List<Double>>()
    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext) { result ->
            if (!closed && result == TextToSpeech.SUCCESS) {
                ttsReady = true
                runCatching { tts?.language = Locale.US }
            }
        }
    }
    private fun acknowledgeWake() {
        val style = OmegaVoicePreferences.acknowledgement(this)
        val text = OmegaWakeAck.spokenText(style, OmegaVoicePreferences.customAcknowledgement(this))
        if (style == WakeAckStyle.CHIME) {
            runCatching {
                val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 65)
                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                handler.postDelayed({ tone.release() }, 300)
            }
        } else if (ttsReady && text != null) {
            runCatching {
                val wanted = if (OmegaWakeAck.isTelugu(text)) Locale("te", "IN") else Locale.US
                val supported = (tts?.isLanguageAvailable(wanted) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= 0
                val selected = if (supported) wanted else Locale.US
                if (!supported) lastError.value = "Telugu TTS voice unavailable; English acknowledgement used"
                tts?.language = selected
                val line = if (supported) text else "Yes, Sir!"
                val spoken = tts?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "omega_wake_ack")
                if (spoken == TextToSpeech.ERROR) lastError.value = "Wake TTS failed; microphone still active"
            }
        }
    }
    /** One gate for BOTH partial recognition and combined wake+command finals. */
    private fun armWake(speakAck: Boolean = true): Boolean {
        if (closed || armed || !OmegaRecoveryPolicy.mayAcceptWake(pendingCommand.value) || !wakeGate.allow(SystemClock.elapsedRealtime())) return false
        lastWakeAt.value = System.currentTimeMillis()
        acceptedWakeCount.value += 1
        armed = true
        lastError.value = ""
        status.value = "Wake detected — say one English command"
        refresh()
        if (speakAck) acknowledgeWake()
        handler.removeCallbacks(expiry)
        handler.postDelayed(expiry, 15000L)
        return true
    }
    private val expiry = Runnable {
        armed = false
        if (!closed && active.value) {
            status.value = "Listening for " + OmegaVoicePreferences.wakePhrase(this).label
            refresh()
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        OmegaAlwaysReady.onServiceStarted()
        if (intent?.action == STOP) {
            OmegaAlwaysReady.setEnabled(this, false)
            status.value = "Stopped"
            stopSelf()
            return START_NOT_STICKY
        }
        if(intent?.action==ENROLL){beginEnrollment();return START_NOT_STICKY}
        if(intent?.action==CANCEL_ENROLL){endEnrollment("Enrollment canceled");return START_NOT_STICKY}
        if (active.value || loading || speech != null) return START_NOT_STICKY
        OmegaAlwaysReady.refresh(this)
        if (!OmegaAlwaysReady.enabled.value) { stopSelf(); return START_NOT_STICKY }
        lastError.value = ""
        lastPartial.value = ""
        capturedCount.value = 0
        acceptedWakeCount.value = 0
        rejectedWakeCount.value=0
        ownerGate=OmegaVoicePreferences.speakerGate(this)
        enrolledSpeaker.value=OmegaSpeakerStore(this).exists()
        speakerRejections.value=0
        strictGrammar = ownerGate || OmegaVoicePreferences.strictWakeGrammar(this)
        recognitionStage.value = "Loading"
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            fail("Microphone and notification permissions required")
            return START_NOT_STICKY
        }
        if (!OmegaAssistantCore.sessions.acquireMic(MicOwner.WAKE)) {
            fail("Manual microphone in use; stop it before starting wake listening")
            return START_NOT_STICKY
        }
        ownsMic = true
        try {
            if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "JARVIS offline listening", NotificationManager.IMPORTANCE_LOW))
            status.value = "Loading bundled English model"
            if (Build.VERSION.SDK_INT >= 29) startForeground(MIC, notification(status.value, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(MIC, notification(status.value, true))
            active.value = true
            loading = true
            StorageService.unpack(this, "model-en-us", "omega-vosk-model",
                { loaded ->
                    loading = false
                    if (closed) loaded.close() else try {
                        model = loaded
                        if(ownerGate) {
                            sessionScope.launch {
                                try {
                                    ownerTemplate=OmegaSpeakerStore(this@OmegaWakeService).load()
                                    check(ownerTemplate!=null)
                                    prepareSpeakerModel()
                                    if(!closed)check(startOfflineStage(false))
                                } catch(e:CancellationException){throw e}
                                catch(_:Exception){if(!closed)fail("Owner voice model/template unavailable; turn off optional filter or enroll again")}
                                catch(_:LinkageError){if(!closed)fail("Speaker native model unavailable")}
                            }
                        } else check(startOfflineStage(false))
                    } catch (e: Exception) { fail("Offline microphone could not start") }
                    catch (e: LinkageError) { fail("Native library unavailable") }
                },
                { _ -> loading = false; if (!closed) fail("Bundled model could not load") })
        } catch (e: Exception) { fail("Start wake service from the visible app") }
        catch (e: LinkageError) { fail("Native library unavailable") }
        return START_NOT_STICKY
    }
    /** Rebuild the recognizer only after the old AudioRecord is released; stale events are ignored. */
    private fun closeOfflineStage() {
        ++offlineEpoch
        recognitionReady.value = false
        val old = speech
        speech = null
        runCatching { old?.cancel() }
        runCatching { old?.shutdown() }
        runCatching { recognizer?.close() }
        recognizer = null
    }
    private fun startOfflineStage(command: Boolean): Boolean {
        if (closed || !active.value || !ownsMic) return false
        closeOfflineStage()
        commandPhase = command
        return try {
            val m = model ?: error("Model not loaded")
            val local = if (strictGrammar && !command) {
                val grammar = JSONArray(OmegaWakeEngine.grammarPhrases(OmegaVoicePreferences.activeWakePhrases(this))).toString()
                Recognizer(m, 16000f, grammar)
            } else Recognizer(m, 16000f)
            if(strictGrammar && !command) local.setWords(true)
            if(enrollmentActive.value || ownerGate && !command) local.setSpeakerModel(requireNotNull(speakerModel))
            recognizer = local
            val recorder = SpeechService(local, 16000f)
            speech = recorder
            val epoch = offlineEpoch
            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) { if (epoch == offlineEpoch) this@OmegaWakeService.onPartialResult(hypothesis) }
                override fun onResult(hypothesis: String?) { if (epoch == offlineEpoch) this@OmegaWakeService.onResult(hypothesis) }
                override fun onFinalResult(hypothesis: String?) { if (epoch == offlineEpoch) this@OmegaWakeService.onFinalResult(hypothesis) }
                override fun onError(exception: Exception?) { if (epoch == offlineEpoch) this@OmegaWakeService.onError(exception) }
                override fun onTimeout() { if (epoch == offlineEpoch) this@OmegaWakeService.onTimeout() }
            }
            check(recorder.startListening(listener))
            recognitionReady.value = true
            recognitionStage.value = if(enrollmentActive.value) "Local speaker enrollment" else if (command) "Vosk English command" else if (strictGrammar) "Vosk strict phrase grammar" else "Vosk legacy open vocabulary"
            status.value = if(enrollmentActive.value) "Speaker enrollment • speak and pause three times" else if (command) "Say one English command" else "Listening for " + OmegaVoicePreferences.wakePhrase(this).label
            refresh()
            true
        } catch (_: Exception) { closeOfflineStage(); false }
          catch (_: LinkageError) { closeOfflineStage(); false }
    }
    private suspend fun prepareSpeakerModel(){
        if(speakerModel!=null)return
        val path=withContext(Dispatchers.IO){StorageService.sync(this@OmegaWakeService,"model-spk","omega-vosk-speaker")}
        if(!closed)speakerModel=SpeakerModel(path)
    }
    private val enrollmentExpiry=Runnable{if(enrollmentActive.value)endEnrollment("Enrollment timed out; previous template unchanged")}
    private fun beginEnrollment(){
        if(!enrollmentRequested)return
        if(closed || !active.value || !recognitionReady.value || processing || commandPhase || armed || reconnectPending || enrollmentActive.value || pendingCommand.value.isNotBlank()){
            enrollmentRequested=false;enrollmentStatus.value="Wait for wake standby; review pending commands first";return
        }
        ++enrollmentEpoch
        val epoch=enrollmentEpoch
        enrollmentActive.value=true
        enrollmentSamples.clear()
        enrollmentStatus.value="Preparing local speaker model"
        closeOfflineStage()
        enrollmentJob=sessionScope.launch {
            try {
                prepareSpeakerModel()
                if(closed || epoch!=enrollmentEpoch || !enrollmentActive.value)return@launch
                check(startOfflineStage(true))
                enrollmentStatus.value="Speak three long phrases in your own voice; pause after each (0/3)"
                handler.postDelayed(enrollmentExpiry,45000L)
            }catch(e:CancellationException){throw e}
            catch(_:Exception){if(!closed && epoch==enrollmentEpoch)endEnrollment("Speaker enrollment unavailable")}
            catch(_:LinkageError){if(!closed && epoch==enrollmentEpoch)endEnrollment("Speaker native library unavailable")}
        }
    }
    private fun endEnrollment(message:String){
        enrollmentRequested=false
        if(!enrollmentActive.value)return
        ++enrollmentEpoch
        enrollmentJob?.cancel();enrollmentJob=null
        handler.removeCallbacks(enrollmentExpiry)
        enrollmentActive.value=false;enrollmentSamples.clear()
        enrollmentStatus.value=message
        enrolledSpeaker.value=OmegaSpeakerStore(this).exists()
        restoreWake()
    }
    private fun enrollmentResult(json:JSONObject){
        val vector=OmegaSpeakerPolicy.fromResult(json,150)?:return
        enrollmentSamples.add(vector)
        enrollmentStatus.value="Speaker samples: "+enrollmentSamples.size+"/3"
        if(enrollmentSamples.size==3){
            try {
                val centroid=requireNotNull(OmegaSpeakerPolicy.centroid(enrollmentSamples))
                check(enrollmentSamples.all{OmegaSpeakerPolicy.match(centroid,it)})
                OmegaSpeakerStore(this).save(centroid)
                if(ownerGate)ownerTemplate=centroid
                endEnrollment("Local template saved. No audio stored.")
            } catch(_:Exception){endEnrollment("Enrollment failed; speak clearly in a quiet room")}
        }
    }
    private fun restoreWake() {
        captureJob?.cancel(); captureJob = null
        platformCommand?.release(); platformCommand = null
        handler.removeCallbacks(expiry)
        handler.removeCallbacks(commandExpiry)
        armed = false
        commandPhase = false
        if (!closed && active.value && !startOfflineStage(false)) fail("Strict wake recognizer could not resume")
    }
    private val commandExpiry = Runnable {
        if (!closed && commandPhase && !processing) {
            lastError.value = "No command received; wake listening resumed"
            restoreWake()
        }
    }
    private fun hasReviewRoom()=pendingQueueSize.value<OmegaEliteConversation.MAX_PENDING
    private fun startStrictCommand() {
        if (closed || commandPhase || !hasReviewRoom() || !wakeGate.allow(SystemClock.elapsedRealtime())) return
        followupTurns=0
        liveRetries=0
        acceptedWakeCount.value += 1
        lastWakeAt.value = System.currentTimeMillis()
        lastError.value = ""
        armed = true
        commandPhase = true
        closeOfflineStage()
        recognitionStage.value = "Wake confirmed • microphone paused"
        status.value = OmegaVoicePreferences.wakePhrase(this).label + " detected — preparing command"
        refresh()
        if(OmegaVoicePreferences.liveAfterWake(this) && OmegaVoicePreferences.get(this)!=OmegaVoiceMode.LOCAL && GeminiLiveDuplexEngine.validModel(OmegaVoicePreferences.liveModel(this)) && OmegaAssistantCore.sessions.beginBackground()) {
            beginWakeLive();return
        }
        acknowledgeWake()
        startCommandCapture(true)
    }
    private fun startCommandCapture(waitForAck:Boolean) {
        captureJob?.cancel()
        captureJob = sessionScope.launch {
            if(waitForAck) {
            delay(600)
            var ticks = 0
            while (tts?.isSpeaking == true && ticks++ < 20) delay(200)
            if (tts?.isSpeaking == true) tts?.stop()
            delay(200)
            }
            if (closed || !active.value || !commandPhase) return@launch
            if (OmegaVoicePreferences.bilingualWakeCommand(this@OmegaWakeService)) {
                recognitionStage.value = "Android SpeechRecognizer • AUTO TE+EN"
                status.value = "Say a Telugu or English command"
                refresh()
                platformCommand = VoiceInputController(applicationContext,
                    onFinalResult = { transcript -> if (!closed && commandPhase) deliver(transcript) },
                    onError = { message ->
                        if (!closed && commandPhase) {
                            platformCommand?.release(); platformCommand = null
                            lastError.value = message + " — English offline fallback"
                            handler.removeCallbacks(commandExpiry)
                            if (!startOfflineStage(true)) restoreWake()
                            else handler.postDelayed(commandExpiry, 15000L)
                        }
                    }, wakeOwned = true)
                platformCommand?.startListening(SpeechLocaleOption.AUTO)
                handler.postDelayed(commandExpiry, 40000L)
            } else {
                if (!startOfflineStage(true)) restoreWake()
                else handler.postDelayed(commandExpiry, 15000L)
            }
        }
    }
    private fun releaseLiveLock(){liveLock?.let{if(it.isHeld)runCatching{it.release()}};liveLock=null}
    private fun clearWakeLive(){
        ++liveGeneration
        liveCollector?.cancel();liveCollector=null
        wakeLive?.release();wakeLive=null
        liveTelemetry.value=GeminiLiveDuplexEngine.Telemetry()
        releaseLiveLock()
    }
    private fun beginWakeLive(){
        clearWakeLive()
        processing=true
        commandPhase=false
        recognitionReady.value=false
        val epoch=liveGeneration
        recognitionStage.value="Gemini Live connecting"
        status.value="Wake detected — connecting live voice"
        OmegaAssistantCore.backgroundPhase.value="Gemini Live connecting"
        refresh()
        runCatching {
            liveLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"omega:liveConversation")
            liveLock?.setReferenceCounted(false);liveLock?.acquire(660000L)
        }
        val live=GeminiLiveDuplexEngine(applicationContext,wakeOwned=true,
            onTerminal={ reason -> handler.post { if(!closed && epoch==liveGeneration) finishWakeLive(reason) } },
            onReady={
                if(!closed && epoch==liveGeneration){
                    recognitionStage.value="Gemini Live conversation"
                    status.value="Live voice connected • speak now"
                    OmegaAssistantCore.backgroundPhase.value="Gemini Live conversation"
                    refresh()
                    wakeLive?.sendTextTurn("Greet me briefly: Yes, Sir! Then wait for my question.")
                }
            })
        wakeLive=live
        liveCollector=sessionScope.launch {
            live.telemetry.collect { t -> if(epoch==liveGeneration) liveTelemetry.value=t }
        }
        live.connect(OmegaVoicePreferences.liveModel(this),OmegaVoicePreferences.liveVoice(this),OmegaVoicePreferences.liveRoute(this))
    }
    private fun finishWakeLive(reason:String){
        clearWakeLive()
        if(closed)return
        if(OmegaVoicePreferences.liveAfterWake(this) && OmegaVoicePreferences.get(this)!=OmegaVoiceMode.LOCAL &&
            OmegaEliteConversation.retryLive(reason,liveRetries)){
            liveRetries++
            status.value="Live reconnecting (1/1)";refresh()
            val epoch=liveGeneration
            handler.postDelayed({if(!closed && epoch==liveGeneration && active.value && OmegaVoicePreferences.liveAfterWake(this))beginWakeLive()
                else if(!closed && epoch==liveGeneration){processing=false;OmegaAssistantCore.sessions.endBackground();restoreWake()}},1000L)
            return
        }
        processing=false
        OmegaAssistantCore.sessions.endBackground()
        OmegaAssistantCore.backgroundPhase.value=reason
        if(reason=="Conversation idle" || reason=="Conversation ended" || reason.startsWith("Session time limit") || reason=="Audio focus lost" || !OmegaVoicePreferences.liveAfterWake(this)){
            restoreWake();return
        }
        lastError.value=reason+" — command recognition fallback"
        armed=true;commandPhase=true
        acknowledgeWake()
        startCommandCapture(true)
    }
    private fun notification(message: String, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 7203, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 7204, Intent(this, OmegaWakeService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("JARVIS OMEGA").setContentText(message)
            .setContentIntent(open).setOngoing(ongoing).setAutoCancel(!ongoing).setVisibility(Notification.VISIBILITY_PRIVATE)
        if (ongoing) b.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop microphone", stop).build())
        return b.build()
    }
    private fun refresh() {
        if (!closed) runCatching { getSystemService(NotificationManager::class.java).notify(MIC, notification(status.value, true)) }
    }
    private fun fail(message: String) {
        OmegaAlwaysReady.onFailure()
        lastError.value = message
        recognitionReady.value = false
        status.value = message
        stopSelf()
    }
    override fun onPartialResult(hypothesis: String?) {
        if (closed || !active.value || processing || enrollmentActive.value || (!strictGrammar && !OmegaRecoveryPolicy.mayAcceptWake(pendingCommand.value))) return
        val partial = runCatching { JSONObject(hypothesis.orEmpty()).optString("partial") }
            .getOrDefault("").trim().take(300)
        if (partial.isEmpty()) return
        lastPartial.value = partial
        recognitionRestarts = 0
        if (strictGrammar) return // Strict mode accepts a complete final phrase, never a partial guess.
        // A partial hypothesis may wake the assistant, but only a final result commits a command.
        if (!armed && OmegaWakeEngine.match(partial, OmegaVoicePreferences.activeWakePhrases(this),
                OmegaVoicePreferences.sensitivity(this)) != null) armWake()
    }
    override fun onFinalResult(hypothesis: String?) = onResult(hypothesis)
    private fun recoverOfflineRecognition(reason: String) {
        handler.post {
            if (closed || !active.value || reconnectPending || processing) return@post
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                fail("Microphone permission revoked; stop offline listening")
                return@post
            }
            if (recognitionRestarts >= 2) {
                fail("Offline recognition failed after two reconnect attempts: " + reason)
                return@post
            }
            recognitionRestarts++
            reconnectPending = true
            recognitionReady.value = false
            status.value = "Reconnecting offline recognition (" + recognitionRestarts + "/2)"
            refresh()
            if (strictGrammar) closeOfflineStage() else runCatching { speech?.stop() }
            handler.postDelayed({
                if (closed || !active.value) return@postDelayed
                reconnectPending = false
                val resumed = if (strictGrammar) startOfflineStage(commandPhase) else runCatching { speech?.startListening(this) == true }.getOrDefault(false)
                if (resumed) {
                    recognitionReady.value = true
                    if (!strictGrammar) status.value = "Listening for " + OmegaVoicePreferences.wakePhrase(this).label
                    refresh()
                } else {
                    fail("Offline voice engine could not restart")
                }
            }, 350L)
        }
    }
    override fun onTimeout() {
        if(enrollmentActive.value){endEnrollment("Enrollment timed out");return}
        if (!closed && strictGrammar && commandPhase) restoreWake()
        else if (!closed) recoverOfflineRecognition("timeout")
    }
    override fun onError(exception: Exception?) {
        if(enrollmentActive.value){endEnrollment("Enrollment microphone error");return}
        if (!closed) recoverOfflineRecognition("recognizer error")
    }
    override fun onResult(hypothesis: String?) {
        if (closed || !active.value || processing) return
        if(enrollmentActive.value){runCatching{JSONObject(hypothesis.orEmpty())}.getOrNull()?.let{enrollmentResult(it)};return}
        val text = runCatching { JSONObject(hypothesis.orEmpty()).optString("text") }.getOrDefault("").trim()
        if (text.isEmpty()) return
        if (strictGrammar && !commandPhase) {
            if (OmegaWakeEngine.strictGrammarMatch(text, OmegaVoicePreferences.activeWakePhrases(this))) {
                val words=runCatching{JSONObject(hypothesis.orEmpty()).optJSONArray("result")}.getOrNull()
                val conf=if(words==null) emptyList() else (0 until words.length()).map{words.optJSONObject(it)?.optDouble("conf",Double.NaN)?:Double.NaN}
                val count=OmegaPolicy.normalize(text).split(" ").size
                if(OmegaEliteConversation.confidenceAccepted(conf,count)) {
                    if(ownerGate){
                        val vector=runCatching{OmegaSpeakerPolicy.fromResult(JSONObject(hypothesis.orEmpty()),50)}.getOrNull()
                        if(vector==null || ownerTemplate?.let{OmegaSpeakerPolicy.match(it,vector)}!=true){
                            speakerRejections.value++;lastError.value="Optional speaker filter rejected wake; short phrase or mismatch";return
                        }
                    }
                    if(!hasReviewRoom()) {status.value="Wake heard — review queue full (3/3)";refresh();return}
                    startStrictCommand()
                }else{rejectedWakeCount.value+=1;lastError.value="Wake rejected: insufficient word confidence"}
            }
            return
        }
        if ((!strictGrammar && !OmegaRecoveryPolicy.mayAcceptWake(pendingCommand.value)) || (strictGrammar && !hasReviewRoom())) return
        if (armed && OmegaWakeAck.isEcho(text, OmegaWakeAck.spokenText(
                OmegaVoicePreferences.acknowledgement(this),
                OmegaVoicePreferences.customAcknowledgement(this)))) return
        if (strictGrammar && commandPhase) { deliver(text.take(300)); return }
        val match = OmegaWakeEngine.match(text, OmegaVoicePreferences.activeWakePhrases(this),
            OmegaVoicePreferences.sensitivity(this))
        if (match != null) {
            if (match.tail.isNotBlank()) {
                if (armed || armWake(speakAck = false)) deliver(match.tail)
            } else if (!armed) armWake()
        } else if (armed) deliver(text.take(300))
    }
    private fun deliver(text: String) {
        if(strictGrammar && OmegaEliteConversation.isEndPhrase(text)){restoreWake();return}
        armed = false
        handler.removeCallbacks(expiry)
        handler.removeCallbacks(commandExpiry)
        if (strictGrammar) {
            captureJob?.cancel(); captureJob = null
            platformCommand?.release(); platformCommand = null
            closeOfflineStage(); commandPhase = false
        }
        if ((!strictGrammar && pendingCommand.value.isNotBlank()) || !hasReviewRoom()) {if(strictGrammar)restoreWake();return}
        if (OmegaAssistantCore.routeWake(text, OmegaVoicePreferences.backgroundAi(this), OmegaVoicePreferences.get(this)) == WakeRoute.AI &&
            OmegaAssistantCore.sessions.beginBackground()) {
            capturedCount.value += 1
            runBackgroundReply(text)
            return
        }
        if(!offerPending(text)) lastError.value="Review queue full; command not saved"
        capturedCount.value += 1
        lastPartial.value = ""
        status.value = "Command captured; review in JARVIS"
        runCatching { getSystemService(NotificationManager::class.java).notify(RESULT, notification("Command captured. Tap to review.", false)) }
        if (OmegaRecoveryPolicy.keepListeningAfterCapture(OmegaAlwaysReady.enabled.value || OmegaVoicePreferences.continuousWake(this))) {
            if (strictGrammar && !startOfflineStage(false)) { fail("Wake restart failed"); return }
            status.value = "Wake standby • "+pendingQueueSize.value+" command(s) awaiting review"
            refresh()
        } else stopSelf()
    }
    private fun runBackgroundReply(text: String) {
        processing = true
        recognitionReady.value = false
        OmegaAssistantCore.backgroundPhase.value = "Processing wake question"
        status.value = "Processing wake question — microphone paused"
        refresh()
        recognitionStage.value = "AI processing • mic paused"
        if (!runCatching { speech == null || speech?.stop() == true }.getOrDefault(false)) {
            processing = false
            OmegaAssistantCore.sessions.endBackground()
            OmegaAssistantCore.backgroundPhase.value = "Microphone pause failed"
            if(!offerPending(text)) lastError.value="Review queue full; command not saved"
            fail("Could not pause wake microphone; review captured command")
            return
        }
        sessionScope.launch {
            var answered=false
            try {
                val chat = ChatRepository(AppDatabase.getInstance(applicationContext).chatMessageDao())
                val history = chat.messages.first()
                chat.saveUserMessage(text, true)
                var answer = ""
                val prompt = OmegaAssistantCore.promptWithMemory(applicationContext, text)
                withTimeout(90_000L) {
                    PanduAiRepository(applicationContext).streamReply(history, prompt).collect { event ->
                        if (event is AiStreamEvent.Completed) answer = event.fullText
                    }
                }
                check(answer.isNotBlank())
                val id = chat.saveAssistantMessage(answer)
                OmegaAssistantCore.backgroundPhase.value = "AI reply saved"
                val config = TtsPreferencesRepository(applicationContext).ttsConfig.first()
                if (config.autoSpeakEnabled) {
                    val speaker = TeluguTtsController(applicationContext)
                    assistantTts = speaker
                    speaker.applyConfig(config)
                    withTimeoutOrNull(8_000L) { speaker.readiness.first { it != TtsReadiness.INITIALIZING } }
                    if (speaker.speak(answer, id)) {
                        OmegaAssistantCore.backgroundPhase.value = "Speaking AI reply"
                        status.value = "Speaking AI reply — microphone paused"; refresh()
                        withTimeoutOrNull(45_000L) { speaker.isSpeaking.first { !it } }
                    }
                    speaker.release(); assistantTts = null
                }
                answered=true
                runCatching { getSystemService(NotificationManager::class.java).notify(RESULT, notification("AI reply ready. Open conversation.", false)) }
            } catch (e: TimeoutCancellationException) {
                if(!offerPending(text)) lastError.value="Review queue full; command not saved"
                OmegaAssistantCore.backgroundPhase.value = "AI timed out; review command"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if(!offerPending(text)) lastError.value="Review queue full; command not saved"
                OmegaAssistantCore.backgroundPhase.value = "AI failed; review command"
            } finally {
                assistantTts?.release(); assistantTts = null
                if (!closed) OmegaAssistantCore.sessions.endBackground()
                processing = false
                if (!closed) {
                    if(strictGrammar && answered && hasReviewRoom() && OmegaEliteConversation.canFollowup(OmegaVoicePreferences.followupAfterWake(this@OmegaWakeService),followupTurns,"")) {
                        followupTurns++;armed=true;commandPhase=true
                        status.value="Follow-up "+followupTurns+"/3 — speak again";refresh()
                        startCommandCapture(false)
                    } else if (!OmegaAlwaysReady.enabled.value && !OmegaVoicePreferences.continuousWake(this@OmegaWakeService)) stopSelf()
                    else if (if (strictGrammar) startOfflineStage(false) else runCatching { speech?.startListening(this@OmegaWakeService) == true }.getOrDefault(false)) {
                        recognitionReady.value = true
                        status.value = "Listening for " + OmegaVoicePreferences.wakePhrase(this@OmegaWakeService).label + " • pending "+pendingQueueSize.value
                        refresh()
                    } else fail("Offline microphone could not resume after reply")
                }
            }
        }
    }
    override fun onDestroy() {
        closed = true
        val wasProcessing = processing
        ++enrollmentEpoch;enrollmentRequested=false;enrollmentActive.value=false;enrollmentSamples.clear()
        enrollmentStatus.value="Not recording";enrollmentJob?.cancel()
        clearWakeLive()
        sessionScope.cancel()
        platformCommand?.release(); platformCommand = null
        assistantTts?.release(); assistantTts = null
        if (wasProcessing) OmegaAssistantCore.sessions.endBackground()
        OmegaAssistantCore.backgroundPhase.value = "Idle"
        handler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ttsReady = false
        closeOfflineStage()
        runCatching{speakerModel?.close()};speakerModel=null;ownerTemplate=null
        runCatching { model?.close() }; model = null
        if (ownsMic) { ownsMic = false; OmegaAssistantCore.sessions.releaseMic(MicOwner.WAKE) }
        active.value = false
        OmegaAlwaysReady.onServiceStopped()
        recognitionReady.value = false
        recognitionStage.value = "Stopped"
        reconnectPending = false
        if (lastError.value.isBlank()) status.value = "Stopped"
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

