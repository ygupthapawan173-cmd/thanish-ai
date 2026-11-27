package com.example.ui

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.SmartApiSlotManager
import com.example.ai.SlotRoutePurpose
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.deepGlassSurface
import com.example.ui.theme.mutedText
import com.example.ui.theme.primaryText
import com.example.voice.CloudVoicePolicy
import com.example.voice.CloudVoicePreferences
import com.example.voice.GeminiSpeechPreferences
import com.example.voice.GeminiSpeechPolicy
import com.example.voice.TtsEngineChoice
import com.example.voice.VoicePackCatalog

private enum class VoiceStudioTab(val label: String) {
    GEMINI("Gemini 30"),
    CLOUD_ONYX("Onyx & Cloud"),
    LOCAL_AND_BG("Offline & Bg")
}

/**
 * V110.1 Premium Voice Studio (Module 2, Targets 8-16):
 * Compact 3-tab grouped studio replacing long linear voice panels.
 * Shows real TTS provider errors and separates Telugu & English voice profiles.
 */
@Composable
internal fun VoicePackPanel(state: PanduUiState, refresh: () -> Unit, preview: (String) -> Unit) {
    val context = LocalContext.current
    val theme = state.settings.visualTheme
    var activeTab by remember { mutableStateOf(VoiceStudioTab.GEMINI) }
    var dualVoice by remember { mutableStateOf(GeminiSpeechPreferences.enabled(context)) }
    var installStatus by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { refresh() }

    val scheme = MaterialTheme.colorScheme.copy(
        onSurface = IceWhite,
        onSurfaceVariant = Color(0xFFBFCBDD),
        onSecondaryContainer = IceWhite,
        surfaceContainer = Color(0xFF182435),
        surfaceContainerHigh=Color(0xFF182435),
        surfaceContainerHighest = Color(0xFF243348),
        outline = Color(0xFF8799AE)
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides theme.primaryText()) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PREMIUM VOICE STUDIO",
                        color = NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val activeEngineLabel = when {
                        dualVoice -> "Gemini Direct"
                        CloudVoicePreferences.enabled(context) -> "Cloud / Onyx"
                        else -> "Android Local"
                    }
                    Surface(
                        color = theme.deepGlassSurface(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f))
                    ) {
                        Text(
                            activeEngineLabel,
                            color = IceWhite,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Live real TTS status / actual provider errors
                Surface(
                    color = theme.deepGlassSurface(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (state.ttsStatusNote.contains("error", true) || state.ttsStatusNote.contains("fallback", true))
                            Color(0xFFFF8A65).copy(alpha = 0.55f)
                        else ElectricBlue.copy(alpha = 0.35f)
                    )
                ) {
                    Text(
                        state.ttsStatusNote.ifBlank { "Voice engine ready" },
                        color = theme.primaryText(),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                }

                // Compact submenu selector
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VoiceStudioTab.entries.forEach { tab ->
                        FilterChip(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            modifier = Modifier.weight(1f),
                            label = { Text(tab.label, fontSize = 11.sp, maxLines = 1) }
                        )
                    }
                }

                when (activeTab) {
                    VoiceStudioTab.GEMINI -> {
                        GeminiSpeechOptions(state, preview, dualVoice) { dualVoice = it }
                    }
                    VoiceStudioTab.CLOUD_ONYX -> {
                        key(dualVoice) { CloudVoiceOptions(state, preview) }
                    }
                    VoiceStudioTab.LOCAL_AND_BG -> {
                        OfflineAndBackgroundSection(
                            state = state,
                            refresh = refresh,
                            preview = preview,
                            installStatus = installStatus,
                            onInstallStatus = { installStatus = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineAndBackgroundSection(
    state: PanduUiState,
    refresh: () -> Unit,
    preview: (String) -> Unit,
    installStatus: String,
    onInstallStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val theme = state.settings.visualTheme
    var locale by remember { mutableStateOf("te-IN") }
    var selected by remember(locale) { mutableStateOf(VoicePackCatalog.selected(context, locale.substringBefore('-'))) }
    var expanded by remember { mutableStateOf(false) }
    var engines by remember { mutableStateOf(VoicePackCatalog.engines(context)) }
    var engineChoice by remember(locale) { mutableStateOf(VoicePackCatalog.engine(context, locale.substringBefore('-'))) }
    var engineMenu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OnlineBackgroundControls(state)
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        Text("OFFLINE VOICE PACKS & FALLBACK", color = NeonCyan, style = MaterialTheme.typography.titleSmall)
        Text(
            "Install a compatible offline TTS engine for lock-screen and instant offline fallback (Telugu + English).",
            color = theme.mutedText(),
            fontSize = 11.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onInstallStatus(openVoiceGuide(context, "https://github.com/HayaiApp/HayaiTTS/releases")) },
                modifier = Modifier.weight(1f)
            ) { Text("English • HayaiTTS", fontSize = 11.sp, maxLines = 1) }
            OutlinedButton(
                onClick = { onInstallStatus(openVoiceGuide(context, "https://hear2read.org/solution-android")) },
                modifier = Modifier.weight(1f)
            ) { Text("Telugu • Hear2Read", fontSize = 11.sp, maxLines = 1) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = locale == "te-IN", onClick = { locale = "te-IN" }, label = { Text("తెలుగు") })
            FilterChip(selected = locale == "en-IN", onClick = { locale = "en-IN" }, label = { Text("English") })
        }

        Text("Engine for ${if (locale == "te-IN") "Telugu" else "English"}", fontSize = 12.sp)
        Box {
            OutlinedButton(
                onClick = { engines = VoicePackCatalog.engines(context); engineMenu = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(engines.firstOrNull { it.id == engineChoice }?.label ?: engineChoice.ifBlank { "Android default" }, maxLines = 1)
            }
            DropdownMenu(expanded = engineMenu, onDismissRequest = { engineMenu = false }) {
                (listOf(TtsEngineChoice("", "Android default")) + engines).forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label, color = Color(0xFFF8FAFC)) },
                        onClick = {
                            VoicePackCatalog.selectEngine(context, locale.substringBefore('-'), item.id)
                            engineChoice = item.id; selected = ""; engineMenu = false
                        }
                    )
                }
            }
        }

        val voices = state.ttsVoiceChoices.filter { it.locale.substringBefore('-') == locale.substringBefore('-') && it.engineRequest==engineChoice }
        val eligible = voices.filter { !it.needsData && !it.network }
        Text("Voices: ${voices.size} reported · ${eligible.size} offline-ready", color = theme.mutedText(), fontSize = 11.sp)
        if (selected.isNotBlank() && eligible.none { it.id == selected }) {
            Text("Saved voice unavailable; automatic eligible voice fallback active.", color = theme.mutedText(), fontSize = 11.sp)
        }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selected.isBlank()) "Automatic voice" else selected, maxLines = 1)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Automatic (installed voice)", color = Color(0xFFF8FAFC)) },
                    onClick = { VoicePackCatalog.select(context, locale.substringBefore('-'), ""); selected = ""; expanded = false }
                )
                eligible.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text("${voice.id}\n${voice.locale} • ${if (voice.network) "Online" else "Offline"}", fontSize = 12.sp, color = Color(0xFFF8FAFC)) },
                        onClick = { VoicePackCatalog.select(context, locale.substringBefore('-'), voice.id); selected = voice.id; expanded = false }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { preview(locale) },
                enabled = !state.isListening && !state.isSpeaking && !state.liveDuplexState.isConnected && !state.liveDuplexState.isConnecting,
                modifier = Modifier.weight(1f)
            ) { Text("Preview selected", fontSize = 11.sp, maxLines = 1) }
            OutlinedButton(
                onClick = { engines = VoicePackCatalog.engines(context); refresh() },
                modifier = Modifier.weight(1f)
            ) { Text("Refresh voices", fontSize = 11.sp, maxLines = 1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    onInstallStatus(runCatching { context.startActivity(intent); "Install voice data, then refresh." }.getOrElse { "Open Android TTS settings." })
                },
                modifier = Modifier.weight(1f)
            ) { Text("Install TTS data", fontSize = 11.sp, maxLines = 1) }
            OutlinedButton(
                onClick = {
                    onInstallStatus(runCatching {
                        context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                        "Choose an installed engine in Android settings."
                    }.getOrElse { "Open Android Settings > Text-to-speech." })
                },
                modifier = Modifier.weight(1f)
            ) { Text("Android TTS", fontSize = 11.sp, maxLines = 1) }
        }
        if (installStatus.isNotBlank()) Text(installStatus, color = theme.mutedText(), fontSize = 11.sp)
    }
}

private fun openVoiceGuide(context: android.content.Context, url: String): String = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); "Install from the provider, then return and select the engine."
}.getOrElse { "No browser available to open provider page." }

@Composable
private fun GeminiSpeechOptions(state: PanduUiState, preview: (String) -> Unit, enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val ready = state.settings.effectiveGeminiKey().isNotBlank() &&
        (state.settings.activeProvider == com.example.data.AiProvider.GEMINI || state.settings.activeProvider == com.example.data.AiProvider.OPENROUTER) &&
        !state.settings.useGeminiLiveAudio
    Text("DUAL API • GEMINI NATURAL VOICE (30 VOICES)", color = NeonCyan, style = MaterialTheme.typography.titleSmall)
    Text("Select separate English and Telugu speakers across all 30 official Gemini voices. Falls back automatically with explicit provider error display.", fontSize = 11.sp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Use Gemini TTS", modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = { GeminiSpeechPreferences.enabled(context, it); onEnabled(it) }, enabled = ready || enabled)
    }
    if (!ready) Text("Save Gemini key and keep Gemini Live duplex OFF to activate.", fontSize = 11.sp, color = Color(0xFFFFB74D))
    for (lang in listOf("en", "te")) {
        key(lang) {
            val label = if (lang == "te") "Telugu" else "English"
            var speaker by remember { mutableStateOf(GeminiSpeechPreferences.voice(context, lang)) }
            var menu by remember { mutableStateOf(false) }
            Text("$label Gemini speaker", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { menu = true },
                        enabled = enabled && ready,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF8FAFC), disabledContentColor=Color(0xFF9CAFC2))
                    ) { Text(speaker, maxLines = 1) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        GeminiSpeechPolicy.speakers.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name, color = Color(0xFFF8FAFC)) },
                                onClick = { GeminiSpeechPreferences.voice(context, lang, name); speaker = name; menu = false }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { preview(if (lang == "te") "te-IN" else "en-IN") },
                    enabled = enabled && ready && !state.isListening && !state.isSpeaking && !state.liveDuplexState.isConnected && !state.liveDuplexState.isConnecting
                ) { Text("Preview $label Gemini voice", fontSize = 11.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun CloudVoiceOptions(state: PanduUiState, preview: (String) -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(CloudVoicePreferences.enabled(context)) }
    var hd by remember { mutableStateOf(CloudVoicePreferences.hd(context)) }
    var en by remember { mutableStateOf(CloudVoicePreferences.voice(context, "en")) }
    var te by remember { mutableStateOf(CloudVoicePreferences.voice(context, "te")) }
    val slotVoiceReady = remember(state.settings) {
        SmartApiSlotManager.engineOrNull()
            ?.selectOrderedSlots(SlotRoutePurpose.VOICE_TTS, state.settings.zeroPaidCreditsMode)
            ?.any { it.providerKind != com.example.ai.ApiProviderKind.GEMINI_DIRECT && it.effectiveBaseUrl() in CloudVoicePolicy.SUPPORTED_TTS_BASES } == true
    }
    val legacyGatewayReady = state.settings.gatewayBaseUrl.trim().trimEnd('/') in CloudVoicePolicy.SUPPORTED_TTS_BASES &&
        state.settings.activeProvider == com.example.data.AiProvider.OPENROUTER &&
        state.settings.providerBudgetUsd == 0.0 &&
        state.settings.smartMode != com.example.ai.SmartModelPolicy.Mode.FREE &&
        state.settings.effectiveOpenRouterKey().isNotBlank()
    val supported = !GeminiSpeechPreferences.enabled(context) && (legacyGatewayReady || slotVoiceReady)

    Text("ONYX & CLOUD NEURAL VOICES (AICredits / OpenAI / OneAI)", color = NeonCyan, style = MaterialTheme.typography.titleSmall)
    Text("OpenAI Onyx is preferred for crisp English speech; natural Telugu Bulbul voices use Sarvam v2 on AICredits. Respects Zero Paid Credits policy.", fontSize = 11.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Enable paid cloud voice", modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = { enabled = it; CloudVoicePreferences.enabled(context,it) }, enabled = supported || enabled)
    }
    if (!supported) {
        Text("Requires AICredits / OpenAI / OneAI Voice slot (with Zero Paid Credits OFF & Free mode OFF) and Gemini TTS OFF.", fontSize = 11.sp, color = Color(0xFFFFB74D))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("HD Onyx / English voice", modifier = Modifier.weight(1f))
        Switch(checked = hd, onCheckedChange = { hd = it; CloudVoicePreferences.hd(context, it) }, enabled = enabled && supported)
    }
    CloudSpeaker(state, preview, enabled && supported, "English (Onyx preferred)", "en", CloudVoicePolicy.english, en) { en = it }
    CloudSpeaker(state, preview, enabled && supported, "Telugu (Natural)", "te", CloudVoicePolicy.telugu, te) { te = it }
}

@Composable
private fun CloudSpeaker(state: PanduUiState, preview: (String) -> Unit, available: Boolean, label: String, lang: String, values: List<String>, current: String, change: (String) -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Text(label, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { menu = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = available,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF8FAFC), disabledContentColor = Color(0xFF9CAFC2))
            ) { Text(current, maxLines = 1) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                values.forEach { id ->
                    DropdownMenuItem(text = { Text(id, color = Color(0xFFF8FAFC)) }, onClick = { CloudVoicePreferences.voice(context, lang, id); change(id); menu = false })
                }
            }
        }
        OutlinedButton(
            onClick = { preview(if (lang == "te") "te-IN" else "en-IN") },
            enabled = available && !state.isListening && !state.isSpeaking && !state.liveDuplexState.isConnected && !state.liveDuplexState.isConnecting
        ) { Text("Preview $label (paid)", fontSize = 11.sp, maxLines = 1) }
    }
}

@Composable
private fun OnlineBackgroundControls(state: PanduUiState) {
    val context = LocalContext.current
    val status by com.example.omega.OnlineBackgroundVoiceService.status.collectAsState()
    var note by remember { mutableStateOf("") }
    val mic = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> note = if (granted) "Tap Start again." else "Microphone permission denied." }
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> note = if (granted) "Tap Start again." else "Notification permission denied." }
    Text("ONLINE BACKGROUND CONVERSATION", color = NeonCyan, style = MaterialTheme.typography.titleSmall)
    Text("Start while visible, then minimize. Respects Android 16 privacy rules. Live status: $status", fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                when {
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED -> mic.launch(android.Manifest.permission.RECORD_AUDIO)
                    android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED -> notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else -> note = if (com.example.omega.OnlineBackgroundVoiceService.start(context)) "Start requested; check live status." else "Start failed; keep JARVIS visible."
                }
            },
            enabled = !state.settings.micPrivacyMuted && state.settings.autoSpeak && !state.isListening && !state.isSpeaking && !state.isThinking && !state.liveDuplexState.isConnected && !state.liveDuplexState.isConnecting,
            modifier = Modifier.weight(1f)
        ) { Text("Start online background voice", fontSize = 11.sp, maxLines = 1) }
        OutlinedButton(
            onClick = { com.example.omega.OnlineBackgroundVoiceService.stop(context); note = "Stop requested." },
            modifier = Modifier.weight(1f)
        ) { Text("Stop background voice", fontSize = 11.sp, maxLines = 1) }
    }
    if (note.isNotBlank()) Text(note, fontSize = 11.sp)
}

@Composable
internal fun HandsFreeWakeControls(state: PanduUiState) {
    val context = LocalContext.current
    val status by com.example.omega.OnlineBackgroundVoiceService.status.collectAsState()
    var note by remember { mutableStateOf("") }
    val mic = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> note = if (granted) "Tap Start again." else "Microphone permission denied." }
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> note = if (granted) "Tap Start again." else "Notification permission denied." }
    val theme = state.settings.visualTheme
    Text("HEY JARVIS • Hands-free", color = theme.primaryText(), fontWeight = FontWeight.SemiBold)
    Text("Local wake phrase detector works minimized & locked (local-only while locked); online Q&A activates overlay without losing current screen.", color = theme.mutedText(), fontSize = 11.sp)
    Text(status, color = NeonCyan, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                when {
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED -> mic.launch(android.Manifest.permission.RECORD_AUDIO)
                    android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED -> notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else -> note = if (com.example.omega.OnlineBackgroundVoiceService.startWake(context)) "Start requested; check live status." else "Stop any existing voice session and start again while JARVIS is visible."
                }
            },
            enabled = !state.settings.micPrivacyMuted && !state.isListening && !state.isSpeaking && !state.isThinking && !state.liveDuplexState.isConnected && !state.liveDuplexState.isConnecting,
            modifier = Modifier.weight(1f)
        ) { Text("Start Hey JARVIS", fontSize = 11.sp, maxLines = 1) }
        OutlinedButton(
            onClick = { com.example.omega.OnlineBackgroundVoiceService.stop(context); note = "Stop requested." },
            modifier = Modifier.weight(1f)
        ) { Text("Stop Hey JARVIS", fontSize = 11.sp, maxLines = 1) }
    }
    if (note.isNotBlank()) Text(note, color = theme.mutedText(), fontSize = 11.sp)
}
