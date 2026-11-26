package com.example.omega

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ai.*
import com.example.ui.theme.PanduBabuTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class OmegaControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PanduBabuTheme { OmegaCenter { finish() } } }
    }
}
@Composable
fun OmegaCenter(
    embedded: Boolean = false,
    close: () -> Unit = {},
    onOpenPermissions: (() -> Unit)? = null
) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val alwaysReady by OmegaAlwaysReady.enabled.collectAsState()
    val wakeStatus by OmegaWakeService.status.collectAsState()
    val wakeActive by OmegaWakeService.active.collectAsState()
    val wakeReady by OmegaWakeService.recognitionReady.collectAsState()
    val wakePartial by OmegaWakeService.lastPartial.collectAsState()
    val wakeError by OmegaWakeService.lastError.collectAsState()
    val lastWakeTime by OmegaWakeService.lastWakeAt.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var wakePhrase by remember { mutableStateOf(OmegaVoicePreferences.wakePhrase(c)) }
    var sensitivity by remember { mutableStateOf(OmegaVoicePreferences.sensitivity(c)) }
    var customWakeInput by remember { mutableStateOf(OmegaVoicePreferences.customWake(c)) }
    var acknowledgement by remember { mutableStateOf(OmegaVoicePreferences.acknowledgement(c)) }
    var customAckInput by remember { mutableStateOf(OmegaVoicePreferences.customAcknowledgement(c)) }
    var replyStyle by remember { mutableStateOf(ReplyPreferences.get(c)) }
    var mode by remember { mutableStateOf(OmegaVoicePreferences.get(c)) }
    var continuous by remember { mutableStateOf(OmegaVoicePreferences.continuous(c)) }
    var continuousWake by remember { mutableStateOf(OmegaVoicePreferences.continuousWake(c)) }
    var strictGrammar by remember { mutableStateOf(OmegaVoicePreferences.strictWakeGrammar(c)) }
    var bilingualCommand by remember { mutableStateOf(OmegaVoicePreferences.bilingualWakeCommand(c)) }
    var autoLive by remember { mutableStateOf(OmegaVoicePreferences.liveAfterWake(c)) }
    var liveModel by remember { mutableStateOf(OmegaVoicePreferences.liveModel(c)) }
    var liveVoice by remember { mutableStateOf(OmegaVoicePreferences.liveVoice(c)) }
    var route by remember { mutableStateOf(OmegaVoicePreferences.liveRoute(c)) }
    var followup by remember { mutableStateOf(OmegaVoicePreferences.followupAfterWake(c)) }
    var aliases by remember { mutableStateOf(OmegaVoicePreferences.expandedWake(c)) }
    val liveState by OmegaWakeService.liveTelemetry.collectAsState()
    val rejectedWakes by OmegaWakeService.rejectedWakeCount.collectAsState()
    val enrolling by OmegaWakeService.enrollmentActive.collectAsState()
    val enrollStatus by OmegaWakeService.enrollmentStatus.collectAsState()
    val hasSpeaker by OmegaWakeService.enrolledSpeaker.collectAsState()
    val speakerRejects by OmegaWakeService.speakerRejections.collectAsState()
    var speakerGate by remember { mutableStateOf(OmegaVoicePreferences.speakerGate(c)) }
    var enrollConsent by remember { mutableStateOf(false) }
    val acceptedWakes by OmegaWakeService.acceptedWakeCount.collectAsState()
    val recognitionStage by OmegaWakeService.recognitionStage.collectAsState()
    var backgroundAi by remember { mutableStateOf(OmegaVoicePreferences.backgroundAi(c)) }
    var memoryContext by remember { mutableStateOf(OmegaVoicePreferences.memoryContext(c)) }
    val micOwner by OmegaAssistantCore.sessions.owner.collectAsState()
    val backgroundPhase by OmegaAssistantCore.backgroundPhase.collectAsState()
    val memoryState by OmegaAssistantCore.memoryState.collectAsState()
    val capturedCount by OmegaWakeService.capturedCount.collectAsState()
    val voiceEngine by com.example.voice.VoiceInputController.engineStatus.collectAsState()
    val voiceLanguage by com.example.voice.VoiceInputController.detectedLanguage.collectAsState()
    val speechError by com.example.voice.VoiceInputController.lastSpeechError.collectAsState()
    var notice by remember { mutableStateOf("") }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (OmegaAlwaysReady.missingPermissions(c).isEmpty()) {
            notice = "Permissions granted. Hey JARVIS standby will start automatically."
            if (!embedded) OmegaAlwaysReady.tryStart(c, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        } else {
            OmegaAlwaysReady.setEnabled(c, false)
            notice = "Permission denied; Always Ready is off."
        }
    }
    DisposableEffect(lifecycle, embedded) {
        val observer = LifecycleEventObserver { _, event ->
            if(event==Lifecycle.Event.ON_PAUSE) {OmegaWakeService.cancelEnrollment(c);enrollConsent=false}
            if (event == Lifecycle.Event.ON_RESUME) {
                OmegaWakeService.enrolledSpeaker.value=OmegaSpeakerStore(c).exists()
                OmegaAlwaysReady.refresh(c, allowRetry = true)
                if (!embedded) OmegaAlwaysReady.tryStart(c, true)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);OmegaWakeService.cancelEnrollment(c) }
    }
    val notebook = remember { OmegaNotebook(c) }
    var entries by remember { mutableStateOf(emptyList<OmegaEntry>()) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var diagnostic by remember { mutableStateOf("") }
    // AI provider configuration remains in the existing main Settings connection panel.

    fun operate(op: () -> List<OmegaEntry>) {
        if (busy) return
        busy = true
        scope.launch {
            try { entries = withContext(Dispatchers.IO) { op() }; ready = true }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { notice = "Vault operation failed. Existing data was not reset." }
            finally { busy = false }
        }
    }
    fun measure() {
        val memory = ActivityManager.MemoryInfo().also { c.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val battery = c.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val local = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(c)
        diagnostic = "Android API " + Build.VERSION.SDK_INT + "\nBattery: " +
            (if (battery in 0..100) "$battery%" else "unavailable") + "\nAvailable RAM: " +
            memory.availMem / 1048576 + " MB\nFree app volume: " + StatFs(c.filesDir.path).availableBytes / 1048576 +
            " MB\nOn-device speech service: " + local + "\nWake: " + wakeStatus
    }
    LaunchedEffect(Unit) { operate { notebook.list() }; measure() }
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("OMEGA / UNIFIED CORE", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF69F5FF))
                    Text(if (wakeActive) wakeStatus else "Voice • memory • system")
                }
                if (!embedded) TextButton(onClick = close) { Text("Close") }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Master", "Voice", "Memory", "System").forEachIndexed { i, label ->
                    if (tab == i) Button(onClick = { tab = i }, modifier = Modifier.weight(1f).heightIn(min=48.dp).testTag("unified_omega_" + label.lowercase()),
                        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal=2.dp)) { Text(label, maxLines=1) }
                    else OutlinedButton(onClick = { tab = i }, modifier = Modifier.weight(1f).heightIn(min=48.dp).testTag("unified_omega_" + label.lowercase()),
                        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal=2.dp)) { Text(label, maxLines=1) }
                }
            }
            OutlinedButton(onClick = { tab = 4 }, modifier = Modifier.fillMaxWidth().testTag("singularity_hud_open")) { Text("10,000X · Singularity Lab") }
            if (onOpenPermissions != null) {
                OutlinedButton(
                    onClick = onOpenPermissions,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Permissions & Android Setup") }
            }
            if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    4 -> com.example.ui.components.OmegaSingularityHudScreen()
                    0 -> {
                        OmegaMasterDashboard(
                            noteCount = entries.size,
                            onVoice = { tab = 1 },
                            onMemory = { tab = 2 },
                            onSystem = { tab = 3 },
                            onSettings = onOpenPermissions
                        )
                    }
                    1 -> {
                        Text("OMEGA • Wake & Voice", style = MaterialTheme.typography.titleLarge)
                        Text("Android speech language packs depend on your phone. Hybrid may use the online speech service if local language recognition is unsupported. Offline-only never switches to online.")
                        OmegaVoiceMode.entries.forEach { item -> Row {
                            RadioButton(selected = item == mode, onClick = { mode = item; OmegaVoicePreferences.set(c, item) })
                            Text(item.label, modifier = Modifier.padding(top = 14.dp))
                        } }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Continuous conversation (opt-in)", modifier = Modifier.weight(1f))
                            Switch(
                                checked = continuous,
                                onCheckedChange = {
                                    continuous = it
                                    OmegaVoicePreferences.setContinuous(c, it)
                                }
                            )
                        }
                        Text("When enabled, chat listens for one follow-up after finishing a reply while the app is visible. Stop microphone to cancel. This does not bypass Android permissions.")
                        HorizontalDivider()
                        Text("Always Ready • Hey JARVIS", style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Always Ready Mode", modifier = Modifier.weight(1f))
                            Switch(checked = alwaysReady, onCheckedChange = { enabled ->
                                OmegaAlwaysReady.setEnabled(c, enabled)
                                if (enabled) {
                                    val missing = OmegaAlwaysReady.missingPermissions(c)
                                    if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
                                    else if (!embedded) OmegaAlwaysReady.tryStart(c, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                                }
                            }, modifier = Modifier.testTag("always_ready_switch"))
                        }
                        Text(if (wakeReady) "Ready — say the selected wake phrase" else if (alwaysReady) wakeStatus else "Standby disabled")
                        Text("Opt in once: wake microphone starts when the app is visible, stays active with a notification, and listens again after a reply. Turn off here or in the notification to stop. Wake audio stays on device.")
                        if (alwaysReady && !wakeActive && OmegaAlwaysReady.missingPermissions(c).isNotEmpty()) {
                            OutlinedButton(onClick = { permissions.launch(OmegaAlwaysReady.missingPermissions(c).toTypedArray()) }) { Text("Grant required permissions") }
                        }
                        Text("ELITE Live Conversation", style = MaterialTheme.typography.titleLarge)
                        Text("After Hey JARVIS, optionally stream conversation audio to Gemini using your key. Wake audio stays local. Stop notification ends capture. Live session closes after 45 seconds idle or 10 minutes total. No device actions in Live.")
                        OutlinedTextField(liveModel,{liveModel=it.take(120);OmegaVoicePreferences.setLiveModel(c,liveModel)},label={Text("Gemini Live model ID")},enabled=!wakeActive,singleLine=true,modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(liveVoice,{liveVoice=it.take(40);OmegaVoicePreferences.setLiveVoice(c,liveVoice)},label={Text("Live voice name (optional)")},enabled=!wakeActive,singleLine=true,modifier=Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            Text("Wake → Gemini Live (cloud audio consent)",Modifier.weight(1f))
                            Switch(checked=autoLive,onCheckedChange={autoLive=it;OmegaVoicePreferences.setLiveAfterWake(c,it)},enabled=!wakeActive && strictGrammar && GeminiLiveDuplexEngine.validModel(liveModel) && mode!=OmegaVoiceMode.LOCAL,modifier=Modifier.testTag("elite_auto_live"))
                        }
                        Text("Live state: "+liveState.stateLabel)
                        if(liveState.connected){Text("Input: "+liveState.inputTranscript);Text("Reply: "+liveState.lastTranscript)}
                        OmegaLiveRoute.entries.forEach { item -> Row {
                            RadioButton(selected=route==item,enabled=!wakeActive,onClick={route=item;OmegaVoicePreferences.setLiveRoute(c,item)})
                            Text(item.label,Modifier.padding(top=14.dp))
                        }}
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            Text("Follow-up listening after spoken AI reply (up to 3)",Modifier.weight(1f))
                            Switch(checked=followup,onCheckedChange={followup=it;OmegaVoicePreferences.setFollowupAfterWake(c,it)},enabled=!wakeActive,modifier=Modifier.testTag("elite_followup"))
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            Text("Additional ELITE wake phrases",Modifier.weight(1f))
                            Switch(checked=aliases,onCheckedChange={aliases=it;OmegaVoicePreferences.setExpandedWake(c,it)},enabled=!wakeActive)
                        }
                        Text("Optional local speaker wake filter",style=MaterialTheme.typography.titleLarge)
                        Text("Experimental convenience filter, not secure authentication or anti-spoofing. Short wake phrases can reject the owner. Only your own voice with explicit consent. No raw audio saved or uploaded; encrypted template can be deleted.")
                        Row { Checkbox(enrollConsent,{enrollConsent=it;if(!it)OmegaWakeService.cancelEnrollment(c)},enabled=!enrolling);Text("I consent to creating my local voice template") }
                        Button(enabled=enrollConsent && wakeActive && wakeReady && !enrolling,onClick={if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))OmegaWakeService.requestEnrollment(c)},modifier=Modifier.testTag("elite_enroll_speaker")){Text("Enroll my voice (3 phrases)")}
                        if(enrolling)OutlinedButton(onClick={OmegaWakeService.cancelEnrollment(c)}){Text("Cancel enrollment")}
                        Text(enrollStatus+" · template: "+(if(hasSpeaker)"saved" else "none")+" · rejected: "+speakerRejects)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            Text("Use speaker match before wake (experimental)",Modifier.weight(1f))
                            Switch(checked=speakerGate,onCheckedChange={speakerGate=it;OmegaVoicePreferences.setSpeakerGate(c,it)},enabled=!wakeActive && (hasSpeaker || speakerGate),modifier=Modifier.testTag("elite_speaker_gate"))
                        }
                        OutlinedButton(enabled=!wakeActive && hasSpeaker,onClick={runCatching{OmegaSpeakerStore(c).delete()}.onSuccess{speakerGate=false;OmegaVoicePreferences.setSpeakerGate(c,false);OmegaWakeService.enrolledSpeaker.value=false;enrollConsent=false}.onFailure{notice="Could not delete local voice template"}}){Text("Delete voice template")}
                        Text("ELITE wake engine • offline Vosk · rejected confidence: "+rejectedWakes)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Strict phrase grammar", modifier = Modifier.weight(1f))
                            Switch(checked = strictGrammar || speakerGate, enabled = !wakeActive && !speakerGate, onCheckedChange = {
                                strictGrammar = it; OmegaVoicePreferences.setStrictWakeGrammar(c, it)
                            }, modifier = Modifier.testTag("elite_strict_grammar"))
                        }
                        Text("Strict: complete final phrase and word confidence required. Wait for the acknowledgement or Live connected status. Turn off for legacy combined-phrase mode. Custom phrases depend on model vocabulary.")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Telugu + English AUTO after wake", modifier = Modifier.weight(1f))
                            Switch(checked = bilingualCommand, enabled = !wakeActive && strictGrammar, onCheckedChange = {
                                bilingualCommand = it; OmegaVoicePreferences.setBilingualWakeCommand(c, it)
                            }, modifier = Modifier.testTag("elite_bilingual_command"))
                        }
                        Text("Optional Android speech service after offline wake. Platform/hybrid may send command audio online; local-only mode does not. English Vosk fallback if the service fails.")
                        Text("Engine stage: " + recognitionStage + " · accepted wakes: " + acceptedWakes)
                        Text("Offline wake phrases • on-device Vosk")
                        OmegaWakePhrase.entries.forEach { phrase ->
                            Row {
                                RadioButton(
                                    selected = phrase == wakePhrase,
                                    enabled = !wakeActive,
                                    onClick = {
                                        wakePhrase = phrase
                                        OmegaVoicePreferences.setWakePhrase(c, phrase)
                                    }
                                )
                                Text(phrase.label, modifier = Modifier.padding(top = 14.dp))
                            }
                        }
                        if (wakePhrase == OmegaWakePhrase.CUSTOM) {
                            OutlinedTextField(
                                value = customWakeInput,
                                onValueChange = { customWakeInput = it.take(40) },
                                enabled = !wakeActive,
                                label = { Text("Custom English wake phrase (2-4 words)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                enabled = !wakeActive && OmegaWakeEngine.validCustomPhrase(customWakeInput) != null,
                                onClick = {
                                    if (OmegaVoicePreferences.setCustomWake(c, customWakeInput)) notice = "Custom phrase saved."
                                }
                            ) { Text("Save custom wake phrase") }
                            Text("Unusual names may not be recognized by the bundled English speech model.")
                        }
                        Text("Wake sensitivity • applies to legacy open-vocabulary mode")
                        WakeSensitivity.entries.forEach { value ->
                            Row {
                                RadioButton(selected = sensitivity == value, enabled = !wakeActive && !strictGrammar,
                                    onClick = { sensitivity = value; OmegaVoicePreferences.setSensitivity(c, value) })
                                Text(value.label, Modifier.padding(top = 14.dp))
                            }
                        }
                        HorizontalDivider()
                        Text("Wake acknowledgement")
                        WakeAckStyle.entries.forEach { value ->
                            Row {
                                RadioButton(selected = acknowledgement == value, enabled = !wakeActive,
                                    onClick = { acknowledgement = value; OmegaVoicePreferences.setAcknowledgement(c, value) })
                                Text(value.label, Modifier.padding(top = 14.dp))
                            }
                        }
                        if (acknowledgement == WakeAckStyle.CUSTOM) {
                            OutlinedTextField(customAckInput, {
                                customAckInput = OmegaWakeAck.sanitizeCustom(it)
                                OmegaVoicePreferences.setCustomAcknowledgement(c, customAckInput)
                            }, enabled = !wakeActive, label = { Text("Wake reply (max 40 characters)") },
                                modifier = Modifier.fillMaxWidth())
                        }
                        HorizontalDivider()
                        Text("AI reply style")
                        ReplyStyle.entries.forEach { value ->
                            Row {
                                RadioButton(selected = replyStyle == value,
                                    onClick = { replyStyle = value; ReplyPreferences.set(c, value) })
                                Text(value.label, Modifier.padding(top = 14.dp))
                            }
                        }
                        Text("Reply styles apply to direct Gemini/OpenAI-compatible API requests; Firebase fallback retains its existing configuration.")
                        Text(wakeStatus)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Keep wake session active (Always Ready includes this)", modifier = Modifier.weight(1f))
                            Switch(checked = alwaysReady || continuousWake, enabled = !wakeActive && !alwaysReady,
                                onCheckedChange = { continuousWake = it; OmegaVoicePreferences.setContinuousWake(c, it) })
                        }
                        Text("Opt-in. Review or discard pending commands before another wake. Stop microphone ends the session. Offline wake uses the English model.")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Answer wake questions with AI", modifier = Modifier.weight(1f))
                            Switch(checked = backgroundAi, enabled = !wakeActive, onCheckedChange = {
                                backgroundAi = it; OmegaVoicePreferences.setBackgroundAi(c, it)
                            }, modifier = Modifier.testTag("unified_background_ai"))
                        }
                        Text("Opt-in: sends captured command transcript and chat context to your configured AI. Not in on-device-only mode. Phone commands still require review. With continuous wake, resumes after reply; Stop microphone cancels.")
                        Text("Assistant: " + backgroundPhase)
                        Text("Microphone owner: " + micOwner.name)
                        Text("Bundled English acoustic model. Selected phrase: " +
                            wakePhrase.label + ". Stop listening before changing the phrase. " +
                            "No wake audio is uploaded. Custom trained phrase models and " +
                            "offline Telugu recognition are not bundled.")
                        Text("Android 16: Always Ready starts while the app is visible. Background and lock-screen listening depend on device policies. No automatic microphone start after force-stop or reboot.")
                    }
                    2 -> {
                        Text("Encrypted notebook & tasks", style = MaterialTheme.typography.titleLarge)
                        Text("Local, opt-in memory; separate from chat history. Maximum 80 entries.")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Use relevant saved notes in AI replies", modifier = Modifier.weight(1f))
                            Switch(checked = memoryContext, onCheckedChange = {
                                memoryContext = it; OmegaVoicePreferences.setMemoryContext(c, it)
                            }, modifier = Modifier.testTag("unified_memory_context"))
                        }
                        Text("Opt-in: up to three matching notes are shared with your selected AI provider; tasks excluded.")
                        Text(memoryState)
                        OutlinedTextField(note, { note = it.take(1000) }, label = { Text("Note / task") }, modifier = Modifier.fillMaxWidth())
                        if (editId == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = ready && !busy && note.isNotBlank(), onClick = { val value = note; operate { notebook.add(value, false) }; note = "" }) { Text("Save note") }
                            OutlinedButton(enabled = ready && !busy && note.isNotBlank(), onClick = { val value = note; operate { notebook.add(value, true) }; note = "" }) { Text("Add task") }
                        } else {
                            Button(enabled = ready && !busy && note.isNotBlank(), onClick = { val id = editId; val value = note; if (id != null) operate { notebook.edit(id, value) }; editId = null; note = "" }) { Text("Save edit") }
                            TextButton(onClick = { editId = null; note = "" }) { Text("Cancel") }
                        }
                        OutlinedTextField(query, { query = it.take(150) }, label = { Text("Search Telugu / English") }, modifier = Modifier.fillMaxWidth())
                        Text(entries.count { it.task && it.done }.toString() + "/" + entries.count { it.task } + " tasks complete")
                        entries.filter { OmegaPolicy.matches(it.text, query) }.forEach { e ->
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                                Text(if (e.task) if (e.done) "Task · done" else "Task · pending" else "Memory")
                                SelectionContainer { Text(e.text) }
                                Row {
                                    if (e.task) TextButton(enabled = !busy, onClick = { operate { notebook.toggle(e.id) } }) { Text(if (e.done) "Reopen" else "Done") }
                                    TextButton(onClick = { editId = e.id; note = e.text }) { Text("Edit") }
                                    TextButton(onClick = { deleteId = e.id }) { Text("Delete") }
                                }
                            } }
                        }
                        OutlinedButton(enabled = entries.isNotEmpty(), onClick = {
                            val text = entries.joinToString("\n\n") { (if (it.task) if (it.done) "[Done] " else "[Task] " else "[Note] ") + it.text }
                            c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share decrypted notes"))
                        }) { Text("Share decrypted notes") }
                    }
                    3 -> {
                        Text("OMEGA • System diagnostics", style = MaterialTheme.typography.titleLarge)
                        Text(diagnostic)
                        Text("Wake last detected: " + (if (lastWakeTime == 0L) "Never" else java.text.DateFormat.getTimeInstance().format(java.util.Date(lastWakeTime))))
                        Text("Last recognized partial: " + wakePartial.ifBlank { "No audio hypothesis yet" })
                        Text("Last wake error: " + wakeError.ifBlank { "None reported" })
                        Text("Foreground microphone service active: " + wakeActive)
                        Text("Offline recognizer started: " + wakeReady)
                        Text("Accepted wake phrases: " + acceptedWakes)
                        Text("Recognition stage: " + recognitionStage)
                        Text("Captured commands this session: " + capturedCount)
                        Text("Microphone owner: " + micOwner.name)
                        Text("Background assistant: " + backgroundPhase)
                        Text("Speech engine: " + voiceEngine)
                        Text("Detected language: " + voiceLanguage)
                        Text("Speech error: " + speechError)
                        Button(onClick = { measure() }) { Text("Refresh measurements") }
                        Text("Microphone permissions, accessibility controls and voice configuration remain under user control. AI connections are managed in one Settings panel.")
                    }
                }
            }
        }
    }
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete this local entry?") },
        text = { Text("The selected entry will be removed from the encrypted notebook.") },
        confirmButton = { TextButton(onClick = { val id = deleteId; deleteId = null; if (id != null) operate { notebook.delete(id) } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } })
}

