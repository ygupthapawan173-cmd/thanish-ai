package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.OmegaVisualTheme
import com.example.data.OrbColorPreset
import com.example.ui.components.ParticleAiOrb
import com.example.ui.theme.OmegaColors
import com.example.ui.theme.omegaGlassCard

private val ChatNavyBg = Color(0xFF040916)
private val ChatNavyCard = Color(0xFF0E1B36)
private val ChatNeonCyan = Color(0xFF00F0FF)
private val ChatElectricBlue = Color(0xFF2563EB)
private val ChatTextPrimary = Color(0xFFF8FAFC)
private val ChatTextMuted = Color(0xFF94A3B8)

@Composable
fun OmegaDashboardApp(viewModel: PanduViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settingsExitGuard = remember { mutableStateOf<(() -> Boolean)?>(null) }

    val stateHolder = rememberSaveableStateHolder()
    val chatListState = rememberLazyListState()
    val controlScrollState = rememberScrollState()
    val labScrollState = rememberScrollState()
    val routinesListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()

    BackHandler(enabled = uiState.currentScreen != OmegaScreen.HOME && uiState.currentScreen != OmegaScreen.SETTINGS) {
        viewModel.navigateBack()
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceListening()
    }
    val camPreview = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) viewModel.analyzeCameraBitmap(bmp)
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camPreview.launch(null)
    }
    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.analyzeDocumentUri(uri)
    }

    fun launchVoice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (uiState.isSpeaking) viewModel.interruptAndListen() else viewModel.startVoiceListening()
        } else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camPreview.launch(null)
        } else camPerm.launch(Manifest.permission.CAMERA)
    }

    fun launchDocumentPicker() {
        docLauncher.launch(arrayOf("application/pdf", "text/*", "application/json"))
    }

    LaunchedEffect(uiState.pendingWidgetCameraLaunch) {
        if (uiState.pendingWidgetCameraLaunch) {
            viewModel.consumeWidgetCameraLaunch()
            launchCamera()
        }
    }

    Column(Modifier.fillMaxSize().background(ChatNavyBg).navigationBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = uiState.currentScreen,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200)) + slideInHorizontally(animationSpec = tween(220)) { it / 16 })
                        .togetherWith(fadeOut(animationSpec = tween(160)))
                },
                label = "screenTransition"
            ) { screen ->
                stateHolder.SaveableStateProvider(key = screen.name) {
                    when (screen) {
                        OmegaScreen.HOME, OmegaScreen.LISTENING -> InfinityHome(
                            s = uiState,
                            onTap = {
                                if (uiState.settings.micPrivacyMuted) viewModel.unmuteMicAndListen()
                                else if (uiState.isListening || uiState.isThinking || uiState.isSpeaking || uiState.liveDuplexState.isConnected || uiState.liveDuplexState.isConnecting || com.example.omega.OmegaWakeService.isBackgroundConversationActive()) viewModel.stopAllActivity()
                                else launchVoice()
                            },
                            onInterruptSpeak = { launchVoice() },
                            onOpenChat = { viewModel.navigateTo(OmegaScreen.CHAT) },
                            onOpenVision = { launchCamera() },
                            onDailyPlan = { viewModel.runDailyBriefing() },
                            onRunSuggestion = { viewModel.runProactiveSuggestion(it) }
                        )
                        OmegaScreen.CONTROL -> InfinityControl(
                            s = uiState,
                            scrollState = controlScrollState,
                            onSettings = { page -> viewModel.navigateTo(OmegaScreen.SETTINGS, page) },
                            onChat = { viewModel.navigateTo(OmegaScreen.CHAT) },
                            onCamera = { launchCamera() },
                            onRoutines = { viewModel.navigateTo(OmegaScreen.ROUTINES) },
                            onLab = { viewModel.navigateTo(OmegaScreen.AUTONOMOUS_LAB) },
                            onRefresh = { viewModel.runSystemDiagnostics() }
                        )
                        OmegaScreen.CHAT -> ChatScreen(
                            uiState = uiState,
                            listState = chatListState,
                            onBack = { viewModel.navigateBack() },
                            onMic = { launchVoice() },
                            onCamera = { launchCamera() },
                            onDocument = { launchDocumentPicker() },
                            onSummarize = { viewModel.summarizeConversation() },
                            onConfirmReply = { viewModel.confirmPendingNotifReply() },
                            onCancelReply = { viewModel.cancelPendingNotifReply() },
                            onSend = { viewModel.sendTextPrompt(it, openChat = false) },
                            onSpeak = { viewModel.speakMessage(it) }
                        )
                        OmegaScreen.ROUTINES -> RoutinesAndMemoryScreen(
                            settings = uiState.settings,
                            routines = uiState.routines,
                            memories = uiState.memories,
                            locationReminders = uiState.locationReminders,
                            proactiveSuggestions = uiState.proactiveSuggestions,
                            routineSuggestions = uiState.routineSuggestions,
                            conditionalRules = uiState.conditionalRules,
                            listState = routinesListState,
                            onBack = { viewModel.navigateBack() },
                            onUpdateSettings = { viewModel.updateSettings(it) },
                            onTestTrigger = { viewModel.testSmartTrigger(it) },
                            onScheduleRoutine = { name, h, m -> viewModel.scheduleDailyRoutine(name, h, m) },
                            onCancelSchedule = { viewModel.cancelDailyRoutine(it) },
                            scheduleMessage = uiState.lastTriggerBanner,
                            onRunRoutine = { viewModel.runRoutineNow(it) },
                            onAddRoutine = { n, t, s, j -> viewModel.saveCustomRoutine(n, t, s, j) },
                            onDeleteRoutine = { viewModel.deleteRoutine(it) },
                            onDeleteMemory = { viewModel.deleteMemory(it) },
                            onToggleConditionalRule = { viewModel.toggleConditionalRule(it) },
                            onAcceptRoutineSuggestion = { viewModel.acceptRoutineSuggestion(it) },
                            onRunDailyPlan = { viewModel.runDailyBriefing() }
                        )
                        OmegaScreen.AUTONOMOUS_LAB -> AutonomousLabScreen(
                            uiState = uiState,
                            scrollState = labScrollState,
                            onBack = { viewModel.navigateBack() },
                            onSaveSettings = { viewModel.updateSettings(it) },
                            onRunDiagnostics = { viewModel.runSystemDiagnostics(); viewModel.refreshControlCenter() },
                            onReconnectAi = { viewModel.autoConnectAndRecover() },
                            onStartSelfUpgrade = { req, path -> viewModel.startSelfUpgrade(req, path) },
                            onEmergencyStop = { viewModel.emergencyStopUpgrade() },
                            onCheckReleaseUpdate = { viewModel.checkReleaseUpdate() },
                            onApproveAndInstallUpdate = { viewModel.approveAndInstallRelease() },
                            onAnalyzeLastCrash = { viewModel.analyzeLastCrashForFix() },
                            onVerifyGitHub = { r, b, t -> viewModel.verifyGitHubConnection(r, b, t) },
                            onGenerateHandoff = { req, path, model, effort -> viewModel.generateAntigravityHandoff(req, path, model, effort) },
                            onAutoUpdateCheckAndDownload = { viewModel.autoDownloadAndVerifyUpdate() },
                            onRefreshControlCenter = { viewModel.refreshControlCenter() },
                            onLaunchTermux = { viewModel.launchTermuxApp() },
                            onLaunchTermuxHandoff = { viewModel.launchTermuxWithHandoff() },
                            onOpenBatterySettings = { viewModel.openBatteryOptimization(it) },
                            onOpenInstallSettings = { viewModel.openInstallPermissionSettings() },
                            onPrepareRollback = { viewModel.prepareRollbackSnapshot() },
                            onRestoreRollback = { viewModel.restoreRollbackSnapshot(it) }
                        )
                        OmegaScreen.SETTINGS -> SettingsScreen(
                            uiState = uiState,
                            scrollState = settingsScrollState,
                            onBack = { viewModel.navigateBack() },
                            onSaveSettings = { viewModel.updateSettings(it) },
                            registerExitGuard = { settingsExitGuard.value = it },
                            onRunConnectionTest = { viewModel.runConnectionTest(it) },
                            onDiscoverModels = { viewModel.discoverRealModels() },
                            onTestSpeaker = { viewModel.testSpeakerVoice() },
                            onRefreshVoiceCatalog = { viewModel.refreshVoiceCatalog() },
                            onVoicePreview = { viewModel.previewVoice(it) },
                            onToggleWakeService = { viewModel.setWakeWordEnabled(it) },
                            onExportBackup = { viewModel.exportEncryptedBackup() },
                            onImportBackup = { viewModel.importEncryptedBackup(it) },
                            onClearCrashLogs = { viewModel.clearCrashLogs() },
                            onClearHistory = { viewModel.clearChatHistory() },
                            onOpenMemory = { viewModel.navigateTo(OmegaScreen.ROUTINES) },
                            onOpenLab = { viewModel.navigateTo(OmegaScreen.AUTONOMOUS_LAB) }
                        )
                    }
                }
            }
        }
        InfinityNavigation(uiState.currentScreen) { target ->
            if (target != uiState.currentScreen && (uiState.currentScreen != OmegaScreen.SETTINGS || settingsExitGuard.value?.invoke() != false)) {
                viewModel.navigateTo(target)
            }
        }
    }
}

@Composable
private fun ChatScreen(
    uiState: PanduUiState,
    listState: LazyListState,
    onBack: () -> Unit,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onDocument: () -> Unit,
    onSummarize: () -> Unit,
    onConfirmReply: () -> Unit,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
    onSpeak: (String) -> Unit
) {
    val theme = uiState.settings.visualTheme
    val preset = uiState.settings.orbColorPreset
    var text by remember { mutableStateOf("") }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    var lastMessageCount by remember { mutableStateOf(uiState.messages.size) }
    LaunchedEffect(uiState.messages.size, uiState.isThinking) {
        if (uiState.messages.isNotEmpty() && (uiState.messages.size != lastMessageCount || uiState.isThinking)) {
            lastMessageCount = uiState.messages.size
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ChatNavyBg, Color(0xFF071126))))) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, theme = theme, preset = preset, onClick = onBack)
                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "JARVIS INFINITY Chat",
                        color = ChatTextPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${uiState.activeBrainLabel} • ${uiState.lastLatencyMs}ms • ${uiState.lastVerificationBadge}",
                        color = ChatNeonCyan,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassCircleButton(Icons.Rounded.Description, theme = theme, preset = preset, onClick = onDocument)
                    GlassCircleButton(Icons.Rounded.Summarize, theme = theme, preset = preset, onClick = onSummarize)
                    GlassCircleButton(Icons.Rounded.CameraAlt, theme = theme, preset = preset, onClick = onCamera)
                }
            }

            AnimatedVisibility(visible = uiState.pendingNotifReply != null) {
                val p = uiState.pendingNotifReply
                if (p != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            .clip(RoundedCornerShape(18.dp)).background(ChatNavyCard)
                            .border(1.dp, ChatNeonCyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Confirm Reply to ${p.resolvedTitle} (${p.appName})", color = ChatNeonCyan, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text("\"${p.replyText}\"", color = ChatTextPrimary, fontSize = 13.5.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onConfirmReply, colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.EmeraldLive), shape = RoundedCornerShape(12.dp)) { Text("Send", color = Color.White, fontSize = 12.sp) }
                            OutlinedButton(onClick = onCancelReply, shape = RoundedCornerShape(12.dp)) { Text("Cancel", color = ChatTextPrimary, fontSize = 12.sp) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.messages, key = { it.id }) { m ->
                    if (m.isFromUser) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Column(
                                modifier = Modifier.widthIn(max = 285.dp).clip(RoundedCornerShape(20.dp))
                                    .background(Brush.linearGradient(listOf(ChatElectricBlue, Color(0xFF0891B2))))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                if (m.isVoiceInput) Text("Voice • ${m.audioDuration}", color = Color.White.copy(0.9f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                if (m.hasImageAttachment) Text("Vision / PDF Page Attached", color = Color.White.copy(0.92f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(m.text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(0.94f), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ParticleAiOrb(size = 28.dp, preset = preset)
                            Column(
                                modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                    .background(ChatNavyCard)
                                    .border(1.dp, if (m.isError) OmegaColors.CrimsonErr.copy(alpha = 0.45f) else ChatNeonCyan.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                if (m.executedTools.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 5.dp)) {
                                        m.executedTools.distinct().take(3).forEach { tool ->
                                            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(ChatNeonCyan.copy(0.16f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                                Text(tool, color = ChatNeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Text(cleanReply(m.text), color = if (m.isError) Color(0xFFFDA4AF) else ChatTextPrimary, fontSize = 14.sp, lineHeight = 20.5.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Text("Copy", color = ChatTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { clipboard.setText(androidx.compose.ui.text.AnnotatedString(m.text)) })
                                    if (!m.isError) {
                                        Row(modifier = Modifier.clickable { onSpeak(m.text) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = ChatNeonCyan, modifier = Modifier.size(14.dp))
                                            Text("Listen", color = ChatNeonCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (uiState.isThinking) {
                    item {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                .background(ChatNavyCard)
                                .border(1.dp, ChatNeonCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = uiState.liveToolStatus ?: uiState.streamingReply.ifBlank { "${uiState.activePipelineStage.label}..." },
                                color = ChatNeonCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(ChatNavyCard)
                        .border(1.dp, ChatNeonCyan.copy(alpha = 0.28f), RoundedCornerShape(28.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCamera) { Icon(Icons.Rounded.AddAPhoto, "Camera Vision", tint = ChatTextMuted) }
                    IconButton(onClick = onDocument) { Icon(Icons.Rounded.AttachFile, "PDF or Document", tint = ChatTextMuted) }
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Ask or send PDF/image...", color = ChatTextMuted, fontSize = 13.5.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = ChatTextPrimary, unfocusedTextColor = ChatTextPrimary,
                            cursorColor = ChatNeonCyan
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onMic) {
                        Icon(if (uiState.isSpeaking) Icons.Rounded.GraphicEq else Icons.Rounded.Mic, "Voice", tint = if (uiState.isSpeaking) ChatNeonCyan else ChatTextMuted)
                    }
                }
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ChatElectricBlue, ChatNeonCyan)))
                        .clickable { if (text.isNotBlank() && !uiState.isThinking) { onSend(text); text = "" } else onMic() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (text.isNotBlank()) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Mic, "Send", tint = ChatNavyBg, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    size: Dp = 42.dp,
    theme: OmegaVisualTheme,
    preset: OrbColorPreset = OrbColorPreset.SOLAR_NOVA,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size)
            .clip(CircleShape)
            .background(ChatNavyCard)
            .border(1.dp, ChatNeonCyan.copy(alpha = 0.28f), CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = ChatTextPrimary, modifier = Modifier.size(20.dp))
    }
}
