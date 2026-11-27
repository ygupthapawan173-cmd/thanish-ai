package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.OmegaVisualTheme
import com.example.data.OrbColorPreset
import com.example.ui.components.OmegaHorizonOrb
import com.example.ui.components.ParticleAiOrb
import com.example.ui.components.ParticleRibbonWaveform
import com.example.ui.components.rememberParallaxOffset
import com.example.ui.theme.OmegaColors
import com.example.ui.theme.OmegaLiquidBackdrop
import com.example.ui.theme.accentColor
import com.example.ui.theme.mutedText
import com.example.ui.theme.omegaGlassCard
import com.example.ui.theme.primaryText
import kotlinx.coroutines.delay

@Composable
fun OmegaDashboardApp(viewModel: PanduViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settingsExitGuard = remember { mutableStateOf<(() -> Boolean)?>(null) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceListening()
    }
    val camPreview = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) viewModel.analyzeCameraBitmap(bmp)
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camPreview.launch(null)
    }

    fun launchVoice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startVoiceListening()
        } else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camPreview.launch(null)
        } else camPerm.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(uiState.pendingWidgetCameraLaunch) {
        if (uiState.pendingWidgetCameraLaunch) {
            viewModel.consumeWidgetCameraLaunch()
            launchCamera()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
        when (uiState.currentScreen) {
            OmegaScreen.HOME, OmegaScreen.LISTENING -> InfinityHome(uiState, onTap = {
                if (uiState.settings.micPrivacyMuted) viewModel.unmuteMicAndListen()
                else if (uiState.isListening || uiState.isThinking || uiState.isSpeaking || uiState.liveDuplexState.isConnected || uiState.liveDuplexState.isConnecting || com.example.omega.OmegaWakeService.isBackgroundConversationActive()) viewModel.stopAllActivity()
                else launchVoice()
            })
            OmegaScreen.CONTROL -> InfinityControl(uiState, onSettings = { page -> viewModel.navigateTo(OmegaScreen.SETTINGS, page) }, onChat = { viewModel.navigateTo(OmegaScreen.CHAT) }, onCamera = { launchCamera() }, onRoutines = { viewModel.navigateTo(OmegaScreen.ROUTINES) }, onLab = { viewModel.navigateTo(OmegaScreen.AUTONOMOUS_LAB) }, onRefresh = { viewModel.runSystemDiagnostics() })
            OmegaScreen.CHAT -> ChatScreen(
                uiState = uiState,
                onBack = { viewModel.navigateTo(OmegaScreen.HOME) },
                onMic = { launchVoice() },
                onCamera = { launchCamera() },
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
                onBack = { viewModel.navigateTo(OmegaScreen.HOME) },
                onUpdateSettings = { viewModel.updateSettings(it) },
                onTestTrigger = { viewModel.testSmartTrigger(it) },
                onScheduleRoutine = { name, h, m -> viewModel.scheduleDailyRoutine(name, h, m) },
                onCancelSchedule = { viewModel.cancelDailyRoutine(it) },
                scheduleMessage = uiState.lastTriggerBanner,
                onRunRoutine = { viewModel.runRoutineNow(it) },
                onAddRoutine = { n, t, s, j -> viewModel.saveCustomRoutine(n, t, s, j) },
                onDeleteRoutine = { viewModel.deleteRoutine(it) },
                onDeleteMemory = { viewModel.deleteMemory(it) }
            )
            OmegaScreen.AUTONOMOUS_LAB -> AutonomousLabScreen(
                uiState = uiState,
                onBack = { viewModel.navigateTo(OmegaScreen.HOME) },
                onSaveSettings = { viewModel.updateSettings(it) },
                onRunDiagnostics = { viewModel.runSystemDiagnostics(); viewModel.refreshControlCenter() },
                onReconnectAi = { viewModel.autoConnectAndRecover() },
                onStartSelfUpgrade = { req, path -> viewModel.startSelfUpgrade(req, path) },
                onEmergencyStop = { viewModel.emergencyStopUpgrade() },
                onCheckReleaseUpdate = { viewModel.checkReleaseUpdate() },
                onApproveAndInstallUpdate = { viewModel.approveAndInstallRelease() },
                onAnalyzeLastCrash = { viewModel.analyzeLastCrashForFix() },
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
                onBack = { viewModel.navigateTo(OmegaScreen.HOME) },
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
      InfinityNavigation(uiState.currentScreen) { target ->
          if (target != uiState.currentScreen && (uiState.currentScreen != OmegaScreen.SETTINGS || settingsExitGuard.value?.invoke() != false)) viewModel.navigateTo(target)
      }
    }
}

@Composable
private fun ChatScreen(
    uiState: PanduUiState,
    onBack: () -> Unit,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onSummarize: () -> Unit,
    onConfirmReply: () -> Unit,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
    onSpeak: (String) -> Unit
) {
    val theme = uiState.settings.visualTheme
    val preset = uiState.settings.orbColorPreset
    val accent = theme.accentColor(preset)
    var text by remember { mutableStateOf("") }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size, uiState.isThinking) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    OmegaLiquidBackdrop(theme = theme, preset = preset) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, theme = theme, preset = preset, onClick = onBack)
                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "JARVIS INFINITY Chat",
                        color = theme.primaryText(),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (uiState.lastLatencyMs > 0) {
                        Text(
                            text = "${uiState.lastLatencyMs}ms • Online",
                            color = accent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassCircleButton(Icons.Rounded.Summarize, theme = theme, preset = preset, onClick = onSummarize)
                    GlassCircleButton(Icons.Rounded.CameraAlt, theme = theme, preset = preset, onClick = onCamera)
                }
            }

            AnimatedVisibility(visible = uiState.pendingNotifReply != null) {
                val p = uiState.pendingNotifReply
                if (p != null) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).omegaGlassCard(18.dp, theme, preset).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Confirm Reply to ${p.resolvedTitle} (${p.appName})", color = accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text("\"${p.replyText}\"", color = theme.primaryText(), fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onConfirmReply, colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.EmeraldLive), shape = RoundedCornerShape(12.dp)) { Text("Send", color = Color.White, fontSize = 12.sp) }
                            OutlinedButton(onClick = onCancelReply, shape = RoundedCornerShape(12.dp)) { Text("Cancel", color = theme.primaryText(), fontSize = 12.sp) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.messages, key = { it.id }) { m ->
                    if (m.isFromUser) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Column(
                                modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(20.dp))
                                    .background(Brush.linearGradient(listOf(Color(preset.accentHex), Color(preset.secondaryHex))))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                if (m.isVoiceInput) Text("Voice • ${m.audioDuration}", color = Color.White.copy(0.85f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                if (m.hasImageAttachment) Text("Vision Image Attached", color = Color.White.copy(0.9f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(m.text, color = Color.White, fontSize = 13.5.sp, lineHeight = 19.sp)
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(0.92f), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ParticleAiOrb(size = 28.dp, preset = preset)
                            Column(modifier = Modifier.omegaGlassCard(18.dp, theme, preset).padding(horizontal = 14.dp, vertical = 11.dp)) {
                                if (m.executedTools.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 5.dp)) {
                                        m.executedTools.distinct().take(3).forEach { tool ->
                                            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(accent.copy(0.16f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                                Text(tool, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Text(cleanReply(m.text), color = if (m.isError) OmegaColors.CrimsonErr else theme.primaryText(), fontSize = 13.5.sp, lineHeight = 19.sp)
                                TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(m.text)) }) { Text("Copy") }
                                if (!m.isError) {
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Row(modifier = Modifier.clickable { onSpeak(m.text) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = accent, modifier = Modifier.size(14.dp))
                                        Text("Listen", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
                if (uiState.isThinking) {
                    item {
                        Box(modifier = Modifier.omegaGlassCard(16.dp, theme, preset).padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(uiState.liveToolStatus ?: uiState.streamingReply.ifBlank { "JARVIS thinking..." }, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.weight(1f).omegaGlassCard(28.dp, theme, preset).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCamera) { Icon(Icons.Rounded.Add, "Camera Vision", tint = theme.mutedText()) }
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Message...", color = theme.mutedText(), fontSize = 14.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = theme.primaryText(), unfocusedTextColor = theme.primaryText()
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onMic) { Icon(Icons.Rounded.Mic, "Voice", tint = theme.mutedText()) }
                }
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(preset.accentHex), Color(preset.secondaryHex), Color(preset.primaryHex))))
                        .clickable { if (text.isNotBlank() && !uiState.isThinking) { onSend(text); text = "" } else onMic() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (text.isNotBlank()) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Mic, "Send", tint = Color.White, modifier = Modifier.size(20.dp))
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
    IconButton(onClick = onClick, modifier = Modifier.size(size).omegaGlassCard(size / 2, theme, preset)) {
        Icon(icon, contentDescription = null, tint = theme.primaryText(), modifier = Modifier.size(20.dp))
    }
}
