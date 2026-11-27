package com.example.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voice.VoicePackCatalog
import com.example.voice.TtsEngineChoice
import com.example.voice.CloudVoicePolicy
import com.example.voice.CloudVoicePreferences
import com.example.voice.GeminiSpeechPreferences
import com.example.voice.GeminiSpeechPolicy
import com.example.ui.theme.mutedText
import com.example.ui.theme.primaryText

/** Real capabilities only: local pack hashes, provider download status, installed engine voice IDs. */
@Composable
internal fun VoicePackPanel(state:PanduUiState, refresh:()->Unit, preview:(String)->Unit) {
    val context=LocalContext.current
    val theme=state.settings.visualTheme
    var locale by remember { mutableStateOf("te-IN") }
    var selected by remember(locale) { mutableStateOf(VoicePackCatalog.selected(context,locale.substringBefore('-'))) }
    var expanded by remember { mutableStateOf(false) }
    var engines by remember { mutableStateOf(VoicePackCatalog.engines(context)) }
    var engineChoice by remember(locale) { mutableStateOf(VoicePackCatalog.engine(context,locale.substringBefore('-'))) }
    var engineMenu by remember { mutableStateOf(false) }
    var installStatus by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { refresh() }
    val scheme = MaterialTheme.colorScheme.copy(onSurface=Color(0xFFF8FAFC), onSurfaceVariant=Color(0xFFBFCBDD), onSecondaryContainer=Color(0xFFF8FAFC), surfaceContainer=Color(0xFF182435), surfaceContainerHigh=Color(0xFF182435), surfaceContainerHighest=Color(0xFF243348), outline=Color(0xFF8799AE))
    MaterialTheme(colorScheme=scheme) {
    CompositionLocalProvider(LocalContentColor provides theme.primaryText()) {
    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("VOICE PACKS",color=theme.primaryText(),fontSize=14.sp)
        Text("NO-CREDIT VOICE SETUP", style=MaterialTheme.typography.titleSmall)
        Text("Paid voice is off by default after this update. Install a compatible offline TTS engine and download its voices in that engine. AI answers still use the online gateway. No neural models are bundled here.",fontSize=12.sp)
        OutlinedButton(onClick={installStatus=openVoiceGuide(context,"https://github.com/HayaiApp/HayaiTTS/releases")},modifier=Modifier.fillMaxWidth()) { Text("English neural packs • HayaiTTS / Kokoro") }
        OutlinedButton(onClick={installStatus=openVoiceGuide(context,"https://hear2read.org/solution-android")},modifier=Modifier.fillMaxWidth()) { Text("Telugu voice packs • Hear2Read") }
        CompositionLocalProvider(LocalContentColor provides theme.primaryText()) {
            var dualVoice by remember { mutableStateOf(GeminiSpeechPreferences.enabled(context)) }
            GeminiSpeechOptions(state, preview, dualVoice) { dualVoice = it }
            key(dualVoice) { CloudVoiceOptions(state, preview) }
            OnlineBackgroundControls(state)
        }
        Text("Online recognition uses the Android speech provider. Optional Hey JARVIS detector is local; questions use online recognition. Owner verification remains disabled.",color=theme.mutedText(),fontSize=11.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=locale=="te-IN",onClick={locale="te-IN"},label={Text("తెలుగు")})
            FilterChip(selected=locale=="en-IN",onClick={locale="en-IN"},label={Text("English")})
        }
        Text("Engine for ${if(locale=="te-IN") "Telugu" else "English"}",fontSize=12.sp)
        Box {
            OutlinedButton(onClick={engines=VoicePackCatalog.engines(context); engineMenu=true},modifier=Modifier.fillMaxWidth()) { Text(engines.firstOrNull { it.id==engineChoice }?.label ?: engineChoice.ifBlank { "Android default" },maxLines=2) }
            DropdownMenu(expanded=engineMenu,onDismissRequest={engineMenu=false}) {
                (listOf(TtsEngineChoice("","Android default"))+engines).forEach { item ->
                    DropdownMenuItem(text={Text(item.label,color=Color(0xFFF8FAFC))},onClick={VoicePackCatalog.selectEngine(context,locale.substringBefore('-'),item.id);engineChoice=item.id;selected="";engineMenu=false})
                }
            }
        }
        Text("Preview loads the chosen engine; then select one of its actual installed voices. If the engine is missing, install it or choose Android default.",fontSize=12.sp)
        val voices=state.ttsVoiceChoices.filter { it.locale.substringBefore('-')==locale.substringBefore('-') && it.engineRequest==engineChoice }
        val eligible=voices.filter { !it.needsData && !it.network }
        Text("Reply voices for selected engine: ${voices.size} reported · ${eligible.size} installed offline-capable",color=theme.mutedText(),fontSize=12.sp)
        if(selected.isNotBlank() && eligible.none { it.id==selected }) Text("Saved voice unavailable; automatic eligible voice fallback will be used.",color=theme.mutedText(),fontSize=12.sp)
        Box {
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) { Text(if(selected.isBlank()) "Automatic voice" else selected, maxLines=2) }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                DropdownMenuItem(text={Text("Automatic (installed voice)",color=Color(0xFFF8FAFC))},onClick={VoicePackCatalog.select(context,locale.substringBefore('-'),"");selected="";expanded=false})
                eligible.forEach { voice -> DropdownMenuItem(text={Text("${voice.id}\n${voice.locale} • ${if(voice.network) "Online" else "Offline"}",fontSize=12.sp,color=Color(0xFFF8FAFC))},onClick={VoicePackCatalog.select(context,locale.substringBefore('-'),voice.id);selected=voice.id;expanded=false}) }
            }
        }
        OutlinedButton(onClick={preview(locale)},enabled=!state.isListening && !state.isSpeaking && !state.liveDuplexState.isConnected && !state.liveDuplexState.isConnecting,modifier=Modifier.fillMaxWidth()) { Text("Preview selected engine (cloud if enabled)") }
        Text(state.ttsStatusNote,color=theme.primaryText(),fontSize=12.sp)
        OutlinedButton(onClick={
            val intent=Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            installStatus=runCatching { context.startActivity(intent); "Install voice data in the engine, then refresh." }.getOrElse { "No TTS installer available. Open Android Text-to-speech settings." }
        },modifier=Modifier.fillMaxWidth()) { Text("Install / repair Android TTS voices") }
        OutlinedButton(onClick={
            installStatus=runCatching {
                context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                "Choose an installed speech engine in Android settings. Reopen JARVIS to load it."
            }.getOrElse { "TTS settings unavailable on this device; use Android Settings search." }
        },modifier=Modifier.fillMaxWidth()) { Text("Android speech engine settings") }
        OutlinedButton(onClick={engines=VoicePackCatalog.engines(context);refresh()},modifier=Modifier.fillMaxWidth()) { Text("Refresh available voices") }
        if(installStatus.isNotBlank()) Text(installStatus,color=theme.mutedText(),fontSize=12.sp)
        Text("Voice names and offline capability are reported by the engine; Android does not guarantee neural quality or provide reliable male/female labels. Preview a voice before selecting it. Cloud voice is opt-in; installed voices remain available for fallback.",color=theme.mutedText(),fontSize=11.sp)
    }
    }
    }
}

private fun openVoiceGuide(context: android.content.Context, url:String):String = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); "Install from the provider, then return and select the engine."
}.getOrElse { "No browser available to open provider page." }

@Composable
private fun GeminiSpeechOptions(state: PanduUiState, preview: (String) -> Unit, enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val ready = state.settings.geminiApiKey.isNotBlank() && state.settings.activeProvider == com.example.data.AiProvider.OPENROUTER && !state.settings.useGeminiLiveAudio
    Text("DUAL API • GEMINI NATURAL VOICE", style=MaterialTheme.typography.titleSmall)
    Text("Save Google Gemini key in AI & API Engine, keep Smart Gateway selected and Gemini Live duplex OFF. Gemini speaks AI replies; AICredits remains the brain. Text is sent to Google for speech. Free quota depends on your Google project; billing may apply. Failure falls back to installed voices, not paid AICredits speech.",fontSize=12.sp)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Use Gemini TTS",modifier=Modifier.weight(1f))
        Switch(checked=enabled,onCheckedChange={GeminiSpeechPreferences.enabled(context,it);onEnabled(it)},enabled=ready || enabled)
    }
    if (!ready) Text("Save Slot 2 key and disable Gemini Live duplex first.",fontSize=12.sp)
    for (lang in listOf("en","te")) {
        key(lang) {
            val label = if(lang=="te") "Telugu" else "English"
            var speaker by remember { mutableStateOf(GeminiSpeechPreferences.voice(context,lang)) }
            var menu by remember { mutableStateOf(false) }
            Text("$label Gemini speaker",fontSize=12.sp)
            Box {
                OutlinedButton(onClick={menu=true},enabled=enabled && ready,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=Color(0xFFF8FAFC),disabledContentColor=Color(0xFF9CAFC2))) { Text(speaker) }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    GeminiSpeechPolicy.speakers.forEach { name -> DropdownMenuItem(text={Text(name,color=Color(0xFFF8FAFC))},onClick={GeminiSpeechPreferences.voice(context,lang,name);speaker=name;menu=false}) }
                }
            }
            OutlinedButton(onClick={preview(if(lang=="te") "te-IN" else "en-IN")},enabled=enabled && ready && !state.isListening && !state.isSpeaking && !state.liveDuplexState.isConnected && !state.liveDuplexState.isConnecting,modifier=Modifier.fillMaxWidth()) {Text("Preview $label Gemini voice")}
        }
    }
}

@Composable
private fun CloudVoiceOptions(state: PanduUiState, preview: (String) -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(CloudVoicePreferences.enabled(context)) }
    var hd by remember { mutableStateOf(CloudVoicePreferences.hd(context)) }
    var en by remember { mutableStateOf(CloudVoicePreferences.voice(context,"en")) }
    var te by remember { mutableStateOf(CloudVoicePreferences.voice(context,"te")) }
    val supported = !GeminiSpeechPreferences.enabled(context) && state.settings.gatewayBaseUrl.trim().trimEnd('/') == CloudVoicePolicy.BASE && state.settings.activeProvider == com.example.data.AiProvider.OPENROUTER && state.settings.providerBudgetUsd == 0.0 && state.settings.smartMode != com.example.ai.SmartModelPolicy.Mode.FREE && state.settings.effectiveOpenRouterKey().isNotBlank()
    Text("CLOUD NEURAL VOICES • AICredits", style=MaterialTheme.typography.titleSmall)
    Text("Optional paid speech; same API wallet. One request up to 1,000 characters; longer replies use Android TTS. Telugu uses Bulbul v2; English uses OpenAI TTS. Preview also costs credits.",fontSize=12.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
        Text("Enable paid cloud voice",modifier=Modifier.weight(1f))
        Switch(checked=enabled,onCheckedChange={ enabled=it; CloudVoicePreferences.enabled(context,it) },enabled=supported || enabled)
    }
    if (!supported) Text("Requires AICredits Smart Gateway, saved key, free mode OFF and app USD cap 0.",fontSize=12.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
        Text("HD English voice",modifier=Modifier.weight(1f))
        Switch(checked=hd,onCheckedChange={hd=it; CloudVoicePreferences.hd(context,it)},enabled=enabled && supported)
    }
    CloudSpeaker(state, preview, enabled && supported, "English", "en", CloudVoicePolicy.english, en) {en=it}
    CloudSpeaker(state, preview, enabled && supported, "Telugu", "te", CloudVoicePolicy.telugu, te) {te=it}
}

@Composable
private fun CloudSpeaker(state: PanduUiState, preview: (String)->Unit, available: Boolean, label: String, lang: String, values: List<String>, current: String, change: (String)->Unit) {
    val context = LocalContext.current
        var menu by remember { mutableStateOf(false) }
        Text(label,fontSize=12.sp)
        Box {
            OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth(),enabled=available,colors=ButtonDefaults.outlinedButtonColors(contentColor=Color(0xFFF8FAFC),disabledContentColor=Color(0xFF9CAFC2))) {Text(current)}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                values.forEach { id -> DropdownMenuItem(text={Text(id,color=Color(0xFFF8FAFC))},onClick={CloudVoicePreferences.voice(context,lang,id);change(id);menu=false}) }
            }
        }
        OutlinedButton(onClick={preview(if(lang=="te") "te-IN" else "en-IN")},enabled=available && !state.isListening && !state.isSpeaking && !state.liveDuplexState.isConnected && !state.liveDuplexState.isConnecting,modifier=Modifier.fillMaxWidth()) { Text("Preview $label (paid)") }
    }

@Composable
private fun OnlineBackgroundControls(state: PanduUiState) {
    val context = LocalContext.current
    val status by com.example.omega.OnlineBackgroundVoiceService.status.collectAsState()
    var note by remember { mutableStateOf("") }
    val mic = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> note = if (granted) "Tap Start again." else "Microphone permission denied." }
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> note = if (granted) "Tap Start again." else "Notification permission denied." }
    Text("ONLINE BACKGROUND CONVERSATION", style=MaterialTheme.typography.titleSmall)
    Text("Start while this app is visible, then minimize. Up to 3 turns / 3 minutes; screen must stay unlocked. Audio goes to the system speech provider. For hands-free activation use Wake Word settings. No background phone actions.", fontSize=12.sp)
    Text(status, fontSize=12.sp)
    OutlinedButton(onClick={
        when {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED -> mic.launch(android.Manifest.permission.RECORD_AUDIO)
            android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED -> notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else -> note = if(com.example.omega.OnlineBackgroundVoiceService.start(context)) "Start requested; check live status." else "Start failed; keep JARVIS visible."
        }
    }, enabled=!state.settings.micPrivacyMuted && state.settings.autoSpeak && !state.isListening && !state.isSpeaking && !state.isThinking && !state.liveDuplexState.isConnected && !state.liveDuplexState.isConnecting, modifier=Modifier.fillMaxWidth()) { Text("Start online background voice") }
    OutlinedButton(onClick={com.example.omega.OnlineBackgroundVoiceService.stop(context); note="Stop requested."},modifier=Modifier.fillMaxWidth()) { Text("Stop background voice") }
    if(note.isNotBlank()) Text(note,fontSize=12.sp)
}

@Composable
internal fun HandsFreeWakeControls(state: PanduUiState) {
    val context = LocalContext.current
    val status by com.example.omega.OnlineBackgroundVoiceService.status.collectAsState()
    var note by remember { mutableStateOf("") }
    val mic = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> note = if (granted) "Tap Start again." else "Microphone permission denied." }
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> note = if (granted) "Tap Start again." else "Notification permission denied." }
    val theme = state.settings.visualTheme
    Text("HEY JARVIS • Hands-free", color=theme.primaryText())
    Text("Start once while JARVIS is visible. Only the wake phrase is detected on the phone. Questions and replies use online AI. Stop in the notification or here. While locked, only the offline wake phrase is detected; unlock for questions and replies.", color=theme.mutedText(), fontSize=12.sp)
    Text(status, color=theme.primaryText(), fontSize=12.sp)
    OutlinedButton(onClick={
        when {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED -> mic.launch(android.Manifest.permission.RECORD_AUDIO)
            android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED -> notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else -> note = if(com.example.omega.OnlineBackgroundVoiceService.startWake(context)) "Start requested; check live status." else "Stop any existing voice session and start again while JARVIS is visible."
        }
    }, enabled=!state.settings.micPrivacyMuted && !state.isListening && !state.isSpeaking && !state.isThinking && !state.liveDuplexState.isConnected && !state.liveDuplexState.isConnecting, modifier=Modifier.fillMaxWidth()) { Text("Start Hey JARVIS") }
    OutlinedButton(onClick={com.example.omega.OnlineBackgroundVoiceService.stop(context); note="Stop requested."},modifier=Modifier.fillMaxWidth()) { Text("Stop Hey JARVIS") }
    if(note.isNotBlank()) Text(note, color=theme.mutedText(), fontSize=12.sp)
}
