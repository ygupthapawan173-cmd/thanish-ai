package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import com.example.BuildConfig
import com.example.omega.OmegaWakeService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.control.BiometricSafetyGate
import com.example.control.PanduAccessibilityService
import com.example.data.AiProvider
import com.example.ai.SmartModelPolicy
import com.example.data.OmegaSettings
import com.example.data.OmegaVisualTheme
import com.example.data.OrbColorPreset
import com.example.data.RenderQuality
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TtsEmotionStyle
import com.example.ui.components.ParticleAiOrb
import com.example.ui.theme.OmegaColors
import com.example.ui.theme.OmegaLiquidBackdrop
import com.example.ui.theme.accentColor
import com.example.ui.theme.mutedText
import com.example.ui.theme.omegaSwitchColors
import com.example.ui.theme.omegaTextFieldColors
import com.example.ui.theme.primaryText
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * Premium Grouped List Settings UI (v21.1):
 * Pure AMOLED black, zero aura light, clean grouped list rows with icon plates & hairline dividers.
 */
@Composable
fun SettingsScreen(
    uiState: PanduUiState,
    onBack: () -> Unit,
    onSaveSettings: ((OmegaSettings) -> OmegaSettings) -> Boolean,
    registerExitGuard: ((() -> Boolean)?) -> Unit,
    onRunConnectionTest: (AiProvider) -> Unit,
    onDiscoverModels: () -> Unit,
    onTestSpeaker: () -> Unit,
    onRefreshVoiceCatalog: () -> Unit,
    onVoicePreview: (String) -> Unit,
    onToggleWakeService: (Boolean) -> Unit,
    onExportBackup: () -> String,
    onImportBackup: (String) -> Unit,
    onClearCrashLogs: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenLab: () -> Unit
) {
    val context = LocalContext.current
    val settings = uiState.settings
    val theme = settings.visualTheme
    val preset = settings.orbColorPreset
    val accent = Color(preset.primaryHex)
    val tfColors = OutlinedTextFieldDefaults.colors(focusedTextColor = ObsidianText, unfocusedTextColor = ObsidianText, focusedBorderColor = accent, unfocusedBorderColor = accent.copy(alpha=.35f), focusedLabelColor = accent, unfocusedLabelColor = ObsidianMuted, cursorColor = accent)

    var assistantNameDraft by remember(settings.assistantName) { mutableStateOf(settings.assistantName) }
    var userNameDraft by remember(settings.userName) { mutableStateOf(settings.userName) }
    var geminiKeyDraft by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
    var openRouterKeyDraft by remember(settings.openRouterApiKey) { mutableStateOf(settings.openRouterApiKey) }
    var geminiChatModelDraft by remember(settings.geminiChatModel) { mutableStateOf(settings.geminiChatModel) }
    var geminiLiveModelDraft by remember(settings.geminiLiveVoiceModel) { mutableStateOf(settings.geminiLiveVoiceModel) }
    var openRouterModelDraft by remember(settings.openRouterModel) { mutableStateOf(settings.openRouterModel) }
    var gatewayUrlDraft by remember(settings.gatewayBaseUrl) { mutableStateOf(settings.gatewayBaseUrl) }
    var providerDraft by remember(settings.activeProvider) { mutableStateOf(settings.activeProvider) }
    var wakeWordDraft by remember(settings.wakeWord) { mutableStateOf(settings.wakeWord) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var showOrKey by remember { mutableStateOf(false) }
    var savedBanner by remember { mutableStateOf(false) }
    var showCrashLogs by remember { mutableStateOf(false) }

    var pendingWakePermission by remember { mutableStateOf(false) }
    var permissionRefresh by remember { mutableStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionRefresh++
        if (pendingWakePermission) {
            pendingWakePermission = false
            onToggleWakeService(grants[Manifest.permission.RECORD_AUDIO] == true)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasMic = remember(permissionRefresh, settings.wakeWordEnabled) { ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasCalendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    val hasAcc = PanduAccessibilityService.instance != null
    val hasNotif = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun persistDrafts(): Boolean {
        val unchanged = userNameDraft == settings.userName && assistantNameDraft == settings.assistantName && geminiKeyDraft == settings.geminiApiKey && openRouterKeyDraft == settings.openRouterApiKey && geminiChatModelDraft == settings.geminiChatModel && geminiLiveModelDraft == settings.geminiLiveVoiceModel && openRouterModelDraft == settings.openRouterModel && gatewayUrlDraft == settings.gatewayBaseUrl && providerDraft == settings.activeProvider && wakeWordDraft == settings.wakeWord
        if (unchanged) return true
        val ok = onSaveSettings {
            it.copy(
                userName = userNameDraft,
                assistantName = assistantNameDraft,
                geminiApiKey = geminiKeyDraft,
                openRouterApiKey = openRouterKeyDraft,
                geminiChatModel = geminiChatModelDraft,
                geminiLiveVoiceModel = geminiLiveModelDraft,
                openRouterModel = openRouterModelDraft,
                gatewayBaseUrl = gatewayUrlDraft,
                activeProvider = providerDraft,
                wakeWord = wakeWordDraft
            )
        }
        savedBanner = ok
        return ok
    }

    val latestExitGuard by rememberUpdatedState<() -> Boolean>({ persistDrafts() })
    DisposableEffect(Unit) {
        registerExitGuard { latestExitGuard() }
        onDispose { registerExitGuard(null) }
    }
    var page by rememberSaveable(uiState.initialSettingsPage) { mutableStateOf(uiState.initialSettingsPage.coerceIn(-1,14)) }
    var search by rememberSaveable { mutableStateOf("") }
    var confirmHistory by remember { mutableStateOf(false) }
    var confirmOwner by remember { mutableStateOf(false) }
    val wakeStatus by OmegaWakeService.status.collectAsStateWithLifecycle()
    val categories = listOf(
        Triple("AI & API Engine", "Universal Smart API, model routing, Gemini Live", Icons.Rounded.Memory),
        Triple("Voice & Audio", "Telugu English voice packs, speed, pitch, preview", Icons.Rounded.Mic),
        Triple("Wake Word", "Hands-free wake controls", Icons.Rounded.RecordVoiceOver),
        Triple("Owner Voice", "Saved profile management", Icons.Rounded.Person),
        Triple("Conversation", "Continuous conversation, Gemini Live", Icons.Rounded.Loop),
        Triple("Background & Battery", "Bluetooth, battery, background availability", Icons.Rounded.BluetoothAudio),
        Triple("Mobile Control", "Accessibility, phone permissions", Icons.Rounded.AccessibilityNew),
        Triple("Assistant Personality", "Assistant name, owner name, reply style", Icons.Rounded.Person),
        Triple("Appearance & Themes", "Home theme, orb colors, animation", Icons.Rounded.BubbleChart),
        Triple("Personal Memory", "History, memories, routines", Icons.Rounded.Memory),
        Triple("Privacy & Security", "Microphone mute, biometric, reply confirmation", Icons.Rounded.Security),
        Triple("Diagnostics", "API health, crash logs, voice diagnostics", Icons.Rounded.BugReport),
        Triple("System & Engineering", "Release checks, encrypted backup, restore", Icons.Rounded.Restore),
        Triple("Camera & Vision AI", "Camera permission, image analysis", Icons.Rounded.CameraAlt),
        Triple("Smart Triggers", "Bluetooth, charging, low battery", Icons.Rounded.Bolt)
    )
    fun goBack() { if(page in listOf(0,2,7) && !persistDrafts()) return; savedBanner=false; if(page == -1) onBack() else page=-1 }
    BackHandler { goBack() }
    if(confirmHistory) AlertDialog(onDismissRequest={confirmHistory=false},title={Text("Clear chat history?")},text={Text("This permanently removes saved chat messages.")},confirmButton={TextButton(onClick={confirmHistory=false;onClearHistory()}) { Text("Clear") }},dismissButton={TextButton(onClick={confirmHistory=false}) { Text("Cancel") }})
    if(confirmOwner) AlertDialog(onDismissRequest={confirmOwner=false},title={Text("Delete owner voice profile?")},confirmButton={TextButton(onClick={confirmOwner=false;com.example.voice.OwnerSpeaker(context).clear()}) { Text("Delete") }},dismissButton={TextButton(onClick={confirmOwner=false}) { Text("Cancel") }})
    CompositionLocalProvider(LocalInfinityPreset provides preset) {
    MaterialTheme(colorScheme = darkColorScheme(primary=ObsidianAccent,onPrimary=Color(preset.backgroundHex),surface=ObsidianPanel,onSurface=ObsidianText,background=Color(preset.backgroundHex),onBackground=ObsidianText,secondary=ObsidianAccent,onSecondary=Color(preset.backgroundHex),outline=ObsidianBorder, onSurfaceVariant=ObsidianMuted, secondaryContainer=Color(preset.surfaceHex), onSecondaryContainer=ObsidianText)) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(preset.backgroundHex),Color(preset.surfaceHex).copy(alpha=0.72f),Color(preset.backgroundHex))))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Minimalist Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick={goBack()},modifier=Modifier.size(48.dp).background(ObsidianPanel,CircleShape)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Back",tint=ObsidianAccent) }
                Column(modifier=Modifier.weight(1f).padding(horizontal=8.dp),horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if(page == -1) "Settings" else categories[page].first, color = ObsidianText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("JARVIS INFINITY ${BuildConfig.VERSION_NAME.substringBefore('-')} • ${preset.label}", color = ObsidianMuted, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .heightIn(min=48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(accent)
                        .clickable { persistDrafts() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Save", color = Color.Black, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (savedBanner) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OmegaColors.EmeraldLive.copy(alpha = 0.14f))
                        .border(1.dp, OmegaColors.EmeraldLive.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Settings saved locally.", color = OmegaColors.EmeraldLive, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Settings index and individual submenu contents
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(key(page) { rememberScrollState() })
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ==========================================================
                // SECTION 1: AI & API KEYS
                // ==========================================================
                if (page == -1) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color(preset.surfaceHex), Color(preset.backgroundHex)))).border(1.dp,ObsidianBorder,RoundedCornerShape(24.dp)).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.size(76.dp).background(Color(preset.surfaceHex),CircleShape).border(2.dp,ObsidianAccent,CircleShape),contentAlignment=Alignment.Center) { Text("∞",color=ObsidianAccent,fontSize=40.sp) }
                        Spacer(Modifier.height(12.dp)); Text("Control Center",color=ObsidianText,fontSize=22.sp,fontWeight=FontWeight.Bold)
                        Text(BuildConfig.VERSION_NAME,color=ObsidianMuted,fontSize=12.sp)
                    }
                    OutlinedTextField(search,{search=it.take(SettingsSearchPolicy.MAX_QUERY)},label={Text("Search settings")},leadingIcon={Icon(Icons.Rounded.Search,null,tint=ObsidianAccent)},singleLine=true,colors=tfColors,modifier=Modifier.fillMaxWidth())
                    val searchAliases = listOf("key url model fallback token gateway connection", "recognition language speech tts microphone speed pitch online", "wake sensitivity hey jarvis", "speaker training biometric filter", "continuous conversation reply", "battery standby service bluetooth notification", "accessibility permission contacts calls calendar", "name creator personality reply style", "color theme palette orb quality animation performance", "history memory routines", "privacy security microphone mute confirmation", "diagnostics crash voice test logs", "backup restore update engineering", "camera vision image permission", "triggers charging low battery bluetooth")
                    val visibleCategories = categories.withIndex().filter { (index, category) ->
                        SettingsSearchPolicy.matches(search, category.first, category.second, searchAliases.getOrElse(index) { "" })
                    }
                    Text("${visibleCategories.size} of ${categories.size} categories", color=ObsidianMuted, fontSize=12.sp)
                    val columns = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f || androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 480) 1 else 2
                    visibleCategories.chunked(columns).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            pair.forEach { (index, category) ->
                                val tint = preset.primaryHex
                                Column(Modifier.weight(1f).heightIn(min = 132.dp).clip(RoundedCornerShape(18.dp)).background(ObsidianPanel).border(1.dp,ObsidianBorder,RoundedCornerShape(18.dp)).clickable { page=index }.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(tint).copy(alpha=.16f)),contentAlignment=Alignment.Center) { Icon(category.third,null,tint=Color(tint)) }
                                    Text(category.first,color=ObsidianText,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                                    Text(category.second,color=ObsidianMuted,fontSize=12.sp,lineHeight=16.sp,maxLines=4)
                                }
                            }
                            if(columns==2 && pair.size==1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if(visibleCategories.isEmpty()) Column { Text("No matching settings",color=ObsidianMuted); TextButton(onClick={search=""}) { Text("Clear search") } }
                } else when(page) {
                    0 -> SettingsGroupSection(title = categories[0].first.uppercase()) {

                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Rounded.Cloud, null, tint = accent, modifier = Modifier.size(28.dp)); Text("Universal Smart API", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        Text("One gateway key, models selected for each question. Native Gemini remains an optional compatibility mode using the Gemini key below.", color = ObsidianMuted, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(AiProvider.OPENROUTER to "Smart Gateway", AiProvider.GEMINI to "Gemini Direct").forEach { (prov, label) ->
                                FilterChip(selected = providerDraft == prov, enabled = settings.providerBudgetUsd == 0.0 || prov == AiProvider.OPENROUTER, onClick = { providerDraft = prov; savedBanner = false }, label = { Text(label) }, modifier = Modifier.weight(1f))
                            }
                        }
                        if (providerDraft == AiProvider.OPENROUTER) {
                            Text("OpenAI-compatible gateway (AICredits, AIRouter, Velona, Yantra, TokenRex, OpenRouter or custom). Enter its official HTTPS API base URL.", color = ObsidianMuted, fontSize = 12.sp)
                            OutlinedTextField(value = gatewayUrlDraft, onValueChange = { newUrl ->
                                if (newUrl.trim().trimEnd('/') != gatewayUrlDraft.trim().trimEnd('/')) openRouterKeyDraft = ""
                                gatewayUrlDraft = newUrl; savedBanner = false
                            }, label = { Text("Gateway HTTPS base URL") }, singleLine = true, colors = tfColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = openRouterKeyDraft, onValueChange = { openRouterKeyDraft = it; savedBanner = false },
                                label = { Text("Smart API key (encrypted)") }, singleLine = true,
                                visualTransformation = if (showOrKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = { IconButton(onClick = { showOrKey = !showOrKey }) { Icon(if (showOrKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = ObsidianMuted) } },
                                colors = tfColors, modifier = Modifier.fillMaxWidth())
                            Text("Model routing mode", color = ObsidianMuted, fontSize = 12.sp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(SmartModelPolicy.Mode.FREE to "Strict Free (OpenRouter)", SmartModelPolicy.Mode.ECONOMY to "Economy", SmartModelPolicy.Mode.SMART to "Smart Auto", SmartModelPolicy.Mode.ULTRA to "Ultra Performance", SmartModelPolicy.Mode.MANUAL to "Manual model").forEach { (mode, label) ->
                                    FilterChip(selected = settings.smartMode == mode, onClick = { onSaveSettings { it.copy(smartMode = mode) } }, label = { Text(label) })
                                }
                            }
                            Text("Strict Free sends only to OpenRouter :free models with zero published token prices. Provider billing controls remain authoritative. Gemini Direct and Live are blocked in Strict Free mode.", color = ObsidianMuted, fontSize = 11.5.sp)
                            var budgetDraft by remember(settings.providerBudgetUsd) { mutableStateOf(if (settings.providerBudgetUsd == 0.0) "0" else settings.providerBudgetUsd.toString()) }
                            val budgetValue = budgetDraft.toDoubleOrNull()?.takeIf { com.example.ai.ProviderBudgetPolicy.validCap(it) }
                            OutlinedTextField(value = budgetDraft, onValueChange = { budgetDraft = it.take(16) },
                                label = { Text("OpenRouter lifetime credit cap (USD; 0 = off)") }, singleLine = true,
                                isError = budgetValue == null, colors = tfColors, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { budgetValue?.let { cap -> onSaveSettings { it.copy(providerBudgetUsd = cap) } } }, enabled = budgetValue != null && budgetValue != settings.providerBudgetUsd) { Text("Save credit cap") }
                            Text("Saved cap: ${settings.providerBudgetUsd} USD. Positive cap requires a matching lifetime OpenRouter key limit including BYOK; other gateways and Gemini are blocked. Provider billing remains authoritative. In-flight requests cannot be recalled.", color = ObsidianMuted, fontSize = 11.5.sp)
                            Text("Auto ranking uses published model metadata when available and model-name hints. Price and access are not guaranteed; set a hard spending cap in the provider dashboard.", color = ObsidianMuted, fontSize = 11.5.sp)
                            if (settings.smartMode == SmartModelPolicy.Mode.MANUAL) {
                                OutlinedTextField(value = openRouterModelDraft, onValueChange = { openRouterModelDraft = it; savedBanner = false }, label = { Text("Exact discovered model ID") }, singleLine = true, colors = tfColors, modifier = Modifier.fillMaxWidth())
                                val modelSaved = openRouterModelDraft.trim() == settings.openRouterModel && gatewayUrlDraft.trim().trimEnd('/') == settings.gatewayBaseUrl.trim().trimEnd('/') && openRouterKeyDraft == settings.openRouterApiKey
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = settings.manualVisionConfirmed && modelSaved, enabled = modelSaved, onCheckedChange = { v -> onSaveSettings { it.copy(manualVisionConfirmed = v) } })
                                    Text("I verified this exact model supports image input (if catalog metadata is missing). Save model/key first.", color = ObsidianMuted, fontSize = 11.5.sp)
                                }
                            }
                        } else {
                            OutlinedTextField(value = geminiChatModelDraft, onValueChange = { geminiChatModelDraft = it; savedBanner = false }, label = { Text("Gemini Direct chat model") }, singleLine = true, colors = tfColors, modifier = Modifier.fillMaxWidth())
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { if (persistDrafts()) onRunConnectionTest(providerDraft) }, enabled = !uiState.isTestingConnection, modifier = Modifier.weight(1f)) { Text("Test Chat API", color = ObsidianText, fontSize = 12.sp) }
                            OutlinedButton(onClick = { if (persistDrafts()) onDiscoverModels() }, enabled = !uiState.isDiscoveringModels, modifier = Modifier.weight(1f)) { Text("Discover Models", color = accent, fontSize = 12.sp) }
                        }
                        if (!savedBanner) uiState.discoveryStatus?.takeIf { it.contains("not saved", true) }?.let { Text(it, color = OmegaColors.CrimsonErr, fontSize = 12.sp) }
                        if (savedBanner && providerDraft == settings.activeProvider && gatewayUrlDraft.trim().trimEnd('/') == settings.gatewayBaseUrl.trim().trimEnd('/') && openRouterKeyDraft == settings.openRouterApiKey && geminiKeyDraft == settings.geminiApiKey && openRouterModelDraft == settings.openRouterModel && geminiChatModelDraft == settings.geminiChatModel) {
                            uiState.discoveryStatus?.let { Text(it, color = ObsidianMuted, fontSize = 11.5.sp) }
                            if (uiState.discoveredChatModels.isNotEmpty()) {
                                Text("Discovered chat models", color = ObsidianMuted, fontSize = 11.5.sp)
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    uiState.discoveredChatModels.take(100).forEach { id ->
                                        AssistChip(onClick = {
                                            if (providerDraft == AiProvider.GEMINI) { geminiChatModelDraft = id; onSaveSettings { it.copy(geminiChatModel = id) } }
                                            else { openRouterModelDraft = id; onSaveSettings { it.copy(openRouterModel = id, smartMode = if (it.smartMode == SmartModelPolicy.Mode.FREE) SmartModelPolicy.Mode.FREE else SmartModelPolicy.Mode.MANUAL) } }
                                        }, label = { Text(id) })
                                    }
                                }
                            }
                            uiState.diagnosticResult?.takeIf { it.provider == providerDraft }?.let { result ->
                                Text(result.summary, color = if (result.isSuccess) OmegaColors.EmeraldLive else OmegaColors.CrimsonErr, fontSize = 12.sp)
                            }
                        }
                        Text("Last successful model: ${uiState.apiTelemetry.lastUsedModel.ifBlank { "Not used yet" }}", color = ObsidianMuted, fontSize = 12.sp)
                        SettingsHairlineDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Rounded.Mic, null, tint = accent, modifier = Modifier.size(28.dp)); Text("Slot 2 • Gemini Voice", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        Text("Google key for Gemini TTS or Live. For dual API keep Smart Gateway selected and Live OFF, then enable Gemini TTS in Voice & Audio. Live ON replaces gateway conversation.", color = ObsidianMuted, fontSize = 12.sp)
                        OutlinedTextField(value = geminiKeyDraft, onValueChange = { geminiKeyDraft = it; savedBanner = false },
                            label = { Text("Gemini API key (encrypted)") }, singleLine = true,
                            visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { IconButton(onClick = { showGeminiKey = !showGeminiKey }) { Icon(if (showGeminiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = ObsidianMuted) } },
                            colors = tfColors, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = geminiLiveModelDraft, onValueChange = { geminiLiveModelDraft = it; savedBanner = false }, label = { Text("Gemini Live model") }, singleLine = true, colors = tfColors, modifier = Modifier.fillMaxWidth())
                        if (savedBanner && providerDraft == AiProvider.GEMINI && uiState.discoveredLiveModels.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                uiState.discoveredLiveModels.take(20).forEach { id -> AssistChip(onClick = { geminiLiveModelDraft = id; onSaveSettings { it.copy(geminiLiveVoiceModel = id) } }, label = { Text(id) }) }
                            }
                        }
                        Text("Gemini Live speaker (next session; requires separate Google key)", color = ObsidianMuted, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            com.example.voice.LiveVoiceChoices.names.forEach { name ->
                                FilterChip(selected = settings.geminiLiveSpeaker == name, onClick = { onSaveSettings { it.copy(geminiLiveSpeaker = name) } }, label = { Text(com.example.voice.LiveVoiceChoices.label(name)) })
                            }
                        }
                        SettingsSwitchRow(Icons.Rounded.Mic, "Gemini Live duplex", "Separate real-time voice session (disabled in Strict Free mode)", settings.useGeminiLiveAudio, theme, preset) { v -> onSaveSettings { it.copy(useGeminiLiveAudio = v && it.smartMode != SmartModelPolicy.Mode.FREE && it.providerBudgetUsd == 0.0) } }
                        OutlinedButton(onClick = { persistDrafts() }, modifier = Modifier.fillMaxWidth()) { Text("Save API settings", color = accent) }
                    }

                    SettingsHairlineDivider()
                    Text("Online AI only: general answers use your configured cloud provider. Explicit device controls and private memory remain local.", color=ObsidianMuted, fontSize=12.sp, modifier=Modifier.padding(14.dp))
                    SettingsHairlineDivider()
                    SettingsSwitchRow(Icons.Rounded.Search, "Google Search Grounding", "Use web grounding on compatible cloud models", settings.webSearchGrounding, theme, preset) { v -> onSaveSettings { it.copy(webSearchGrounding = v) } }
                    }
                    1 -> SettingsGroupSection(title = categories[1].first.uppercase()) {
                    Text("Online system speech recognition (Internet and unlocked screen required).", color=ObsidianMuted, fontSize=12.sp, modifier=Modifier.padding(14.dp))
                    SettingsHairlineDivider()
Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Mic: ${uiState.voiceDiagNote} • TTS: ${uiState.ttsStatusNote}", color = ObsidianMuted, fontSize = 12.sp)
                        OutlinedButton(onClick = onTestSpeaker, modifier = Modifier.fillMaxWidth()) { Text("Test Speaker Voice") }
                    }

                    SettingsSwitchRow(
                        icon = Icons.Rounded.VolumeUp,
                        title = "Auto-Speak All Replies (TTS)",
                        subtitle = "Speak assistant replies using the selected voice engine",
                        checked = settings.autoSpeak,
                        theme = theme,
                        preset = preset,
                        onCheckedChange = { v -> onSaveSettings { it.copy(autoSpeak = v) } }
                    )



                    VoicePackPanel(uiState, onRefreshVoiceCatalog, onVoicePreview)



                    // Language & TTS Speed Row
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Voice Language", color = ObsidianMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("auto" to "Auto (TE+EN)", "te-IN" to "తెలుగు", "en-IN" to "English").forEach { (tag, label) ->
                                val sel = settings.languageTag == tag
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) accent else ObsidianAccent.copy(alpha=.14f))
                                        .clickable { onSaveSettings { it.copy(languageTag = tag) } }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (sel) Color.Black else ObsidianText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Text(
                            "TTS Speed: ${String.format(Locale.US, "%.2fx", settings.ttsSpeed)} • Pitch: ${String.format(Locale.US, "%.2fx", settings.ttsPitch)}",
                            color = ObsidianMuted,
                            fontSize = 11.5.sp
                        )
                        Slider(
                            value = settings.ttsSpeed,
                            onValueChange = { v -> onSaveSettings { it.copy(ttsSpeed = v) } },
                            valueRange = 0.7f..1.5f
                        )
                        Text("Pitch", color = ObsidianMuted, fontSize = 11.5.sp)
                        Slider(settings.ttsPitch, { v -> onSaveSettings { it.copy(ttsPitch = v) } }, valueRange = 0.7f..1.4f)
                        Text("Reply voice style (pitch and speed; not a different speaker)", color = ObsidianMuted, fontSize = 11.5.sp)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TtsEmotionStyle.entries.forEach { st ->
                                FilterChip(selected = settings.ttsStyle == st, onClick = { onSaveSettings { it.copy(ttsStyle = st) } }, label = { Text(st.label) })
                            }
                        }
                        Text("Preview style using the installed Telugu or English voice", color = ObsidianMuted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val canPreview = !uiState.isListening && !uiState.liveDuplexState.isConnected && !uiState.liveDuplexState.isConnecting
                            OutlinedButton(onClick = { onVoicePreview("te-IN") }, enabled = canPreview, modifier = Modifier.weight(1f)) { Text("Telugu preview") }
                            OutlinedButton(onClick = { onVoicePreview("en-IN") }, enabled = canPreview, modifier = Modifier.weight(1f)) { Text("English preview") }
                        }
                    }
                    }
                    2 -> SettingsGroupSection(title = categories[2].first.uppercase()) {
                        Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) { HandsFreeWakeControls(uiState) }
                    }
                    3 -> SettingsGroupSection(title = categories[3].first.uppercase()) {
                        Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("Owner voice filtering is unavailable without the wake engine. Existing profile stays on this device.", color=ObsidianMuted)
                            OutlinedButton(onClick={confirmOwner=true}) { Text("Delete saved owner profile") }
                        }
                    }
                    4 -> SettingsGroupSection(title = categories[4].first.uppercase()) {

                    SettingsSwitchRow(
                        icon = Icons.Rounded.Loop,
                        title = "Continuous Conversation",
                        subtitle = "Auto-rearm mic after JARVIS finishes speaking",
                        checked = settings.continuousConversation,
                        theme = theme,
                        preset = preset,
                        onCheckedChange = { v -> onSaveSettings { it.copy(continuousConversation = v) } }
                    )

SettingsHairlineDivider()

                    SettingsSwitchRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Gemini Live Duplex Voice (WS)",
                        subtitle = "Low-latency WebSocket audio (not available in Strict Free mode)",
                        checked = settings.useGeminiLiveAudio,
                        theme = theme,
                        preset = preset,
                        onCheckedChange = { v -> onSaveSettings { it.copy(useGeminiLiveAudio = v && it.smartMode != SmartModelPolicy.Mode.FREE && it.providerBudgetUsd == 0.0) } }
                    )

                    }
                    5 -> SettingsGroupSection(title = categories[5].first.uppercase()) {

                    Text("Background microphone wake is disabled. Online speech is available when the app is open.", color=ObsidianMuted, modifier=Modifier.padding(14.dp))

SettingsHairlineDivider()

                    SettingsSwitchRow(
                        icon = Icons.Rounded.BluetoothAudio,
                        title = "Bluetooth Headset Routing",
                        subtitle = "Route mic & audio to BT earbuds",
                        checked = settings.bluetoothVoiceRouting,
                        theme = theme,
                        preset = preset,
                        onCheckedChange = { v -> onSaveSettings { it.copy(bluetoothVoiceRouting = v) } }
                    )

SettingsActionRow(Icons.Rounded.Settings, "Battery settings", "Android controls background restrictions", theme) { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
                    Text("Background wake: disabled in Online Edition", color = ObsidianMuted, modifier = Modifier.padding(14.dp), fontSize = 12.sp)
                    }
                    6 -> SettingsGroupSection(title = categories[6].first.uppercase()) {

                    SettingsActionRow(
                        icon = Icons.Rounded.Mic,
                        title = "App Permissions (Mic, Camera, Contacts, Calendar, Bluetooth)",
                        subtitle = "Mic: ${if (hasMic) "OK" else "Denied"} • Cam: ${if (hasCamera) "OK" else "Denied"} • Contacts: ${if (hasContacts) "OK" else "Denied"}",
                        theme = theme,
                        onClick = {
                            val perms = buildList {
                                add(Manifest.permission.RECORD_AUDIO)
                                add(Manifest.permission.CAMERA)
                                add(Manifest.permission.READ_CONTACTS)
                                add(Manifest.permission.READ_CALENDAR)
                                add(Manifest.permission.CALL_PHONE)
                                if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permLauncher.launch(perms.toTypedArray())
                        }
                    )

SettingsHairlineDivider()

                    SettingsActionRow(
                        icon = Icons.Rounded.AccessibilityNew,
                        title = "Accessibility Service",
                        subtitle = if (hasAcc) "Active — UI automation ready" else "Tap to enable in Android Settings",
                        theme = theme,
                        onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
                    )

SettingsHairlineDivider()

                    SettingsActionRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Notification Listener",
                        subtitle = if (hasNotif) "Active — Can read & reply alerts" else "Tap to grant notification access",
                        theme = theme,
                        onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
                    )

SettingsHairlineDivider()

                    SettingsActionRow(Icons.Rounded.Settings, "Default Assistant", "Open Android voice input settings", theme) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
                    }

                    }
                    7 -> SettingsGroupSection(title = categories[7].first.uppercase()) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(assistantNameDraft,{assistantNameDraft=it.take(40)},label={Text("Assistant name (chat identity)")},singleLine=true,colors=tfColors,modifier=Modifier.fillMaxWidth())
                        Text("Reply style",color=ObsidianMuted,fontSize=12.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            listOf("Natural","Professional","Friendly","Concise").forEach { style -> FilterChip(selected=settings.replyStyle==style,onClick={onSaveSettings { it.copy(replyStyle=style) }},label={Text(style)}) }
                        }
                    }

                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = userNameDraft,
                            onValueChange = { userNameDraft = it; savedBanner = false },
                            label = { Text("Your Name") },
                            singleLine = true,
                            colors = tfColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    }
                    8 -> SettingsGroupSection(title = categories[8].first.uppercase()) {

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ParticleAiOrb(size = 110.dp, isListening = false, preset = preset)
                        Text("Animation quality", color=ObsidianText, fontWeight=FontWeight.Medium)
                        RenderQuality.entries.forEach { quality ->
                            FilterChip(selected=settings.renderQuality==quality, onClick={ onSaveSettings { it.copy(renderQuality=quality) } }, label={Text(quality.label)}, modifier=Modifier.fillMaxWidth())
                        }
                        Text("Adaptive reduces particle density at low battery. System battery saver overrides high detail. Display refresh rate is controlled by Android.",color=ObsidianMuted,fontSize=11.sp)
                        val themeColumns = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f || androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360) 1 else 2
                        OrbColorPreset.entries.sortedBy { if(it == OrbColorPreset.AURORA_CYAN) 0 else it.ordinal+1 }.chunked(themeColumns).forEach { pair ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                pair.forEach { p ->
                                    val selected = preset == p
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(p.surfaceHex)).border(if(selected) 2.dp else 1.dp,Color(p.primaryHex).copy(alpha=if(selected) 1f else .30f),RoundedCornerShape(14.dp)).clickable { onSaveSettings { it.copy(orbColorPreset=p) } }.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) { listOf(p.primaryHex,p.secondaryHex,p.accentHex).forEach { h -> Box(Modifier.size(19.dp).clip(CircleShape).background(Color(h))) } }
                                        Text(p.label,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Medium)
                                        if(selected) Text("Selected",color=Color(p.primaryHex),fontSize=10.sp)
                                    }
                                }
                                if(themeColumns==2 && pair.size==1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    SettingsHairlineDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.ScreenRotation,
                        title = "3D Gyroscope Parallax",
                        subtitle = "Subtle depth tilt on Home Orb",
                        checked = settings.gyroscopeParallax,
                        theme = theme,
                        preset = preset,
                        onCheckedChange = { v -> onSaveSettings { it.copy(gyroscopeParallax = v) } }
                    )
                    }
                    9 -> SettingsGroupSection(title = categories[9].first.uppercase()) {
SettingsActionRow(Icons.Rounded.Memory,"Saved memories & routines","${uiState.memories.size} saved memories",theme,onClick=onOpenMemory)
                    SettingsActionRow(Icons.Rounded.DeleteOutline,"Clear chat history","Permanently remove saved chat messages",theme,titleColor=OmegaColors.CrimsonErr) { confirmHistory=true }
                    }
                    10 -> SettingsGroupSection(title = categories[10].first.uppercase()) {

                    SettingsSwitchRow(
                        icon = Icons.Rounded.MicOff,
                        title = "Microphone Privacy Mute",
                        subtitle = "Temporarily block all microphone access",
                        checked = settings.micPrivacyMuted,
                        theme = theme,
                        preset = preset,
                        onCheckedChange = { v -> onSaveSettings { it.copy(micPrivacyMuted = v) } }
                    )


                    SettingsSwitchRow(Icons.Rounded.Notifications, "Confirm Notification Replies", "Require confirmation before sending", settings.confirmNotifReply, theme, preset) { v -> onSaveSettings { it.copy(confirmNotifReply = v) } }

SettingsHairlineDivider()

                    SettingsSwitchRow(Icons.Rounded.Security, "Biometric Protection (Optional)", "Authenticate sensitive tools", settings.biometricSafetyLock, theme, preset) { v -> if (v) BiometricSafetyGate.authenticate(context, onSuccess = { onSaveSettings { it.copy(biometricSafetyLock = true) } }, onError = {}) else onSaveSettings { it.copy(biometricSafetyLock = false) } }
                    }
                    11 -> SettingsGroupSection(title = categories[11].first.uppercase()) {

                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val snap = uiState.apiTelemetry
                        Text("API requests: ${snap.totalRequests} • Failed: ${snap.failedRequests} • Average: ${snap.averageLatencyMs} ms", color = ObsidianMuted, fontSize = 12.sp)
                        Text("Reported chat tokens: ${snap.reportedPromptTokens} input / ${snap.reportedOutputTokens} output • Missing usage: ${snap.requestsWithoutUsage}", color = ObsidianMuted, fontSize = 12.sp)
                        Text("Known chat cost (USD, estimated subtotal): ${String.format(java.util.Locale.US, "%.6f", snap.knownCostUsd)} • Unpriced: ${snap.requestsWithoutCost}. ${if (snap.persistenceHealthy) "Saved across restarts" else "Persistence unavailable"}; excludes Live/audio and unreported charges.", color = ObsidianMuted, fontSize = 12.sp)
                        uiState.backupStatusText?.let { Text(it, color = ObsidianMuted, fontSize = 11.5.sp) }
                    }

SettingsHairlineDivider()

                    SettingsActionRow(
                        icon = Icons.Rounded.BugReport,
                        title = "Crash Logs",
                        subtitle = if (showCrashLogs) "Tap to hide logs" else "Tap to view or clear crash logs",
                        theme = theme,
                        onClick = { showCrashLogs = !showCrashLogs }
                    )
                    if (showCrashLogs) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = uiState.crashLogText.ifBlank { "No crash logs recorded." }.takeLast(4000),
                                color = ObsidianMuted,
                                fontSize = 11.sp
                            )
                            TextButton(onClick = onClearCrashLogs) {
                                Text("Clear Crash Logs", color = OmegaColors.CrimsonErr, fontSize = 12.sp)
                            }
                        }
                    }

SettingsActionRow(Icons.Rounded.GraphicEq,"Voice diagnostics","${uiState.voiceDiagNote} • ${uiState.ttsStatusNote}",theme) { page=1 }
                    SettingsActionRow(Icons.Rounded.BugReport,"System health report","Open existing diagnostic laboratory",theme,onClick=onOpenLab)
                    SettingsActionRow(Icons.Rounded.Cloud,"API connection test","Test actual chat model",theme) { onRunConnectionTest(settings.activeProvider) }
                    uiState.diagnosticResult?.let { Text(it.summary,color=ObsidianText,modifier=Modifier.padding(14.dp)) }
                    }
                    12 -> SettingsGroupSection(title = categories[12].first.uppercase()) {
                    PortableBackupPanel()
                    SettingsHairlineDivider()

                    SettingsActionRow(Icons.Rounded.Restore, "Restore Backup", "Read backup from clipboard", theme) {
                        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        onImportBackup(clip)
                    }

SettingsHairlineDivider()

                    SettingsActionRow(
                        icon = Icons.Rounded.ContentCopy,
                        title = "Export Encrypted Backup",
                        subtitle = "Copies settings backup to clipboard",
                        theme = theme,
                        onClick = {
                            val data = onExportBackup()
                            if (data.isNotBlank()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("INFINITY_BACKUP", data))
                                savedBanner = true
                            }
                        }
                    )

SettingsActionRow(Icons.Rounded.Refresh,"Release updates","Open existing verified update controls",theme,onClick=onOpenLab)
                    SettingsHairlineDivider()
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("JARVIS INFINITY",color=ObsidianText,fontSize=20.sp,fontWeight=FontWeight.Bold)
                        Text("Created by Yeluchuri Guptha",color=ObsidianMuted)
                        Text("${BuildConfig.VERSION_NAME} • Android ${Build.VERSION.RELEASE} • Target SDK 36",color=ObsidianMuted,fontSize=12.sp)
                        Text("Package: ${context.packageName}",color=ObsidianMuted,fontSize=12.sp)
                    }
                    }
                    13 -> SettingsGroupSection(title = categories[13].first.uppercase()) {
                        SettingsActionRow(Icons.Rounded.CameraAlt,"Camera permission",if(hasCamera) "Granted" else "Tap to request camera access",theme) { permLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }
                        Text("Use Vision Scan from Home to capture and analyze an image with the configured model.",color=ObsidianMuted,modifier=Modifier.padding(14.dp),fontSize=12.sp)
                    }
                    14 -> SettingsGroupSection(title = categories[14].first.uppercase()) {
                        SettingsSwitchRow(Icons.Rounded.BluetoothAudio,"Bluetooth briefing","Announce connected audio",settings.triggerBluetoothBriefing,theme,preset) { v -> onSaveSettings { it.copy(triggerBluetoothBriefing=v) } }
                        SettingsSwitchRow(Icons.Rounded.Bolt,"Charging alert","Announce charging connection",settings.triggerChargingAlert,theme,preset) { v -> onSaveSettings { it.copy(triggerChargingAlert=v) } }
                        SettingsSwitchRow(Icons.Rounded.BatteryAlert,"Low battery saver","Enable low battery announcement",settings.triggerLowBatterySaver,theme,preset) { v -> onSaveSettings { it.copy(triggerLowBatterySaver=v) } }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

}

}

private val LocalInfinityPreset = staticCompositionLocalOf { OrbColorPreset.AURORA_CYAN }
private val ObsidianAccent: Color @Composable get() = Color(LocalInfinityPreset.current.primaryHex)
private val ObsidianText = Color(0xFFEDFAFF)
private val ObsidianMuted = Color(0xFF91ADB7)
private val ObsidianPanel: Color @Composable get() = Color(LocalInfinityPreset.current.surfaceHex)
private val ObsidianBorder: Color @Composable get() = Color(LocalInfinityPreset.current.primaryHex).copy(alpha=.27f)

@Composable
private fun SettingsGroupSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if(title.isNotBlank()) Text(
            text = title,
            color = ObsidianMuted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 6.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianPanel)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp)),
            content = content
        )
    }
}

@Composable
private fun SettingsHairlineDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        thickness = 1.dp,
        color = ObsidianBorder
    )
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    theme: OmegaVisualTheme,
    preset: OrbColorPreset,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(preset.primaryHex).copy(alpha=.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color(preset.primaryHex), modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ObsidianText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ObsidianMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor=Color(preset.primaryHex),checkedThumbColor=Color(preset.backgroundHex),uncheckedTrackColor=Color(preset.surfaceHex),uncheckedThumbColor=ObsidianMuted)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    theme: OmegaVisualTheme,
    titleColor: Color = ObsidianText,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(ObsidianAccent.copy(alpha=.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (titleColor == OmegaColors.CrimsonErr) OmegaColors.CrimsonErr else ObsidianAccent, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ObsidianMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = ObsidianMuted, modifier = Modifier.size(20.dp))
    }
}
