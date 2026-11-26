package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.control.AppControlTarget
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.CyberpunkBottomNavBar
import com.example.ui.components.CyberpunkTab
import com.example.ui.components.HistoryCommandTab
import com.example.ui.components.HomeCommandTab
import com.example.ui.components.JarvisAlertBanner
import com.example.ui.components.JarvisArcCoreHero
import com.example.ui.components.JarvisAtmosphericBackdrop
import com.example.ui.components.JarvisMicOrb
import com.example.ui.components.JarvisTopBar
import com.example.ui.components.LiveVoiceTranscriptChip
import com.example.ui.components.PhoneControlDashboard
import com.example.ui.components.SettingsCommandTab
import com.example.ui.components.StreamingAssistantBubble
import com.example.ui.components.TeluguHelpExamplesSheet
import com.example.ui.components.TtsCalibrationSheet
import com.example.ui.components.cyberDualNeonBrush
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudAlertRed
import com.example.ui.theme.HudOnlineGreen
import com.example.ui.theme.IceWhite
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisVoid
import com.example.ui.theme.MutedHudBlue
import com.example.ui.theme.NeonSapphire
import com.example.ui.theme.PanduBabuTheme
import com.example.ui.theme.SlateCyan
import com.example.voice.SpeechLocaleOption
import com.example.voice.VoiceInputState
import kotlinx.coroutines.launch

@Composable
fun PanduDashboardScreen(
    viewModel: PanduViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val omegaCommand by com.example.omega.OmegaWakeService.pendingCommand.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(CyberpunkTab.HOME) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showTtsSheet by remember { mutableStateOf(false) }
    var showHelpSheet by remember { mutableStateOf(false) }
    var showAccessibilityConsentDialog by remember { mutableStateOf(false) }
    var showAgentConsentDialog by remember { mutableStateOf(false) }
    var pendingAgentGoal by remember { mutableStateOf("") }

    BackHandler(enabled = selectedTab != CyberpunkTab.HOME) {
        selectedTab = CyberpunkTab.HOME
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val activity = context.findActivity()
        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.RECORD_AUDIO
            )
        } ?: false
        viewModel.onMicPermissionResult(
            granted = isGranted,
            shouldShowRationale = shouldShowRationale
        )
    }

    // Sync RECORD_AUDIO permission and TTS voices on start and resume
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    viewModel.updateMicPermissionStatus(granted = granted)
                    viewModel.refreshTtsVoices()
                    viewModel.refreshDeviceControls()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.cancelVoiceListening()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isListening = uiState.voiceInputState is VoiceInputState.Listening

    val onMicAction: () -> Unit = {
        if (isListening) {
            viewModel.stopVoiceListening()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.updateMicPermissionStatus(granted = true)
                viewModel.startVoiceListening()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = JarvisVoid,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            JarvisAtmosphericBackdrop()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .widthIn(max = 640.dp)
                    .align(Alignment.Center)
            ) {
                JarvisTopBar(
                    status = uiState.jarvisStatus,
                    speechLocaleLabel = uiState.speechLocale.shortLabel,
                    ttsPresetLabel = uiState.ttsConfig.preset.shortHudLabel,
                    autoSpeakEnabled = uiState.autoSpeakEnabled,
                    hasMessages = uiState.messages.isNotEmpty(),
                    onToggleLocale = { viewModel.toggleSpeechLocale() },
                    onToggleAutoSpeak = { viewModel.toggleAutoSpeak() },
                    onOpenTtsSettings = { showTtsSheet = true },
                    onOpenHelp = { showHelpSheet = true },
                    onRequestClearChat = { showClearDialog = true },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (omegaCommand.isNotBlank()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Text("Offline command captured: $omegaCommand")
                        Row {
                            TextButton(onClick = {
                                viewModel.onInputTextChanged(omegaCommand)
                                selectedTab = CyberpunkTab.CHAT
                                com.example.omega.OmegaWakeService.pendingCommand.value = ""
                            }) { Text("Use in chat") }
                            TextButton(onClick = { com.example.omega.OmegaWakeService.pendingCommand.value = "" }) { Text("Discard") }
                        }
                    }
                }
                val showPermissionBanner = uiState.showPermissionRationale
                val bannerMessage = when {
                    showPermissionBanner -> stringResource(R.string.permission_mic_message)
                    uiState.bannerError != null -> uiState.bannerError.orEmpty()
                    else -> ""
                }

                JarvisAlertBanner(
                    visible = showPermissionBanner || uiState.bannerError != null,
                    title = if (showPermissionBanner) {
                        stringResource(R.string.permission_mic_title)
                    } else {
                        null
                    },
                    message = bannerMessage,
                    isPermissionBanner = showPermissionBanner,
                    permanentlyDenied = uiState.permissionPermanentlyDenied,
                    canRetry = !showPermissionBanner && uiState.lastFailedPrompt != null,
                    onPrimaryAction = {
                        if (showPermissionBanner) {
                            if (uiState.permissionPermanentlyDenied) {
                                context.openAppSettings()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            viewModel.retryLastFailed()
                        }
                    },
                    onDismiss = {
                        if (showPermissionBanner) {
                            viewModel.dismissPermissionCard()
                        } else {
                            viewModel.dismissError()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )

                // Main Content Area with Smooth Transitions across the 5 Bottom Nav Tabs
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(240)) togetherWith
                                fadeOut(animationSpec = tween(180))
                        },
                        label = "tab_transition"
                    ) { tab ->
                        when (tab) {
                            CyberpunkTab.HOME -> HomeCommandTab(
                                uiState = uiState,
                                onMicClick = onMicAction,
                                onStopSpeaking = { viewModel.stopSpeaking() },
                                onReplayMessage = { viewModel.speakMessage(it) },
                                onStarterPromptClick = { prompt ->
                                    selectedTab = CyberpunkTab.CHAT
                                    viewModel.sendStarterPrompt(prompt)
                                },
                                onOpenHelp = { showHelpSheet = true },
                                onNavigateToChat = { selectedTab = CyberpunkTab.CHAT },
                                onNavigateToApps = { selectedTab = CyberpunkTab.APPS },
                                onYouTubeShortcut = {
                                    viewModel.onAppControlClicked(AppControlTarget.YOUTUBE)
                                },
                                onWhatsAppShortcut = {
                                    viewModel.onAppControlClicked(AppControlTarget.WHATSAPP)
                                },
                                onSpeakBattery = {
                                    viewModel.checkBatteryInfo()
                                }
                            )

                            CyberpunkTab.CHAT -> ChatCommandTab(
                                uiState = uiState,
                                onInputTextChanged = { viewModel.onInputTextChanged(it) },
                                onSendTypedMessage = { viewModel.sendTypedMessage() },
                                onStarterPromptClick = { viewModel.sendStarterPrompt(it) },
                                onOpenHelp = { showHelpSheet = true },
                                onSpeakMessage = { viewModel.speakMessage(it) },
                                onStopSpeaking = { viewModel.stopSpeaking() },
                                onMicClick = onMicAction
                            )

                            CyberpunkTab.APPS -> PhoneControlDashboard(
                                controlState = uiState.deviceControlState,
                                onRefreshControls = { viewModel.refreshDeviceControls() },
                                onAppClick = { viewModel.onAppControlClicked(it) },
                                onTorchToggle = { viewModel.setTorchEnabled(it) },
                                onVolumeAdjust = { viewModel.adjustVolume(it) },
                                onVolumeSet = { viewModel.setVolumeLevel(it) },
                                onBrightnessSet = { viewModel.setSystemBrightnessPercent(it) },
                                onBrightnessAdjust = { viewModel.adjustBrightness(it) },
                                onGrantBrightnessPermission = {
                                    runCatching {
                                        context.startActivity(viewModel.createWriteSettingsPermissionIntent())
                                    }.onFailure {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Unable to open Modify System Settings.")
                                        }
                                    }
                                },
                                onReadBatterySpeak = { viewModel.checkBatteryInfo() },
                                onOpenDeviceSetting = { viewModel.openDeviceSetting(it) },
                                onGlobalNavAction = { viewModel.onGlobalNavClicked(it) },
                                onRequestAccessibilityConsent = { showAccessibilityConsentDialog = true },
                                onRunVoiceCommand = { cmd ->
                                    selectedTab = CyberpunkTab.CHAT
                                    viewModel.sendStarterPrompt(cmd)
                                 },
                                 onStartAgent = { goal ->
                                     pendingAgentGoal = goal
                                     showAgentConsentDialog = true
                                 },
                                 onStopAgent = { viewModel.stopAgentTask() }
                             )

                            CyberpunkTab.HISTORY -> HistoryCommandTab(
                                messages = uiState.messages,
                                isSpeaking = uiState.isSpeaking,
                                speakingMessageId = uiState.speakingMessageId,
                                onSpeakClick = { viewModel.speakMessage(it) },
                                onRequestClearChat = { showClearDialog = true },
                                onOpenChat = { selectedTab = CyberpunkTab.CHAT }
                            )

                            CyberpunkTab.OMEGA -> com.example.omega.OmegaCenter(
                                embedded = true,
                                onOpenPermissions = { selectedTab = CyberpunkTab.SETTINGS }
                            )

                            CyberpunkTab.SETTINGS -> SettingsCommandTab(
                                uiState = uiState,
                                onToggleAutoSpeak = { viewModel.toggleAutoSpeak() },
                                onToggleLocale = { viewModel.toggleSpeechLocale() },
                                onSelectPreset = { viewModel.selectTtsPreset(it) },
                                onPitchChange = { viewModel.updateTtsPitch(it) },
                                onRateChange = { viewModel.updateTtsSpeechRate(it) },
                                onSelectVoice = { viewModel.selectTeluguVoice(it) },
                                onTestVoice = { viewModel.testTeluguVoice() },
                                onStopVoice = { viewModel.stopSpeaking() },
                                onInstallTeluguData = {
                                    runCatching {
                                        context.startActivity(viewModel.createInstallTeluguVoiceIntent())
                                    }.onFailure {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("TTS voice data installer is unavailable on this device.")
                                        }
                                    }
                                },
                                onOpenHelp = { showHelpSheet = true },
                                onRequestClearChat = { showClearDialog = true },
                                onMicPermissionChanged = { granted -> 
                                    viewModel.updateMicPermissionStatus(granted)
                                },
                                onRefreshDeviceState = { viewModel.refreshDeviceControls() }
                            )
                        }
                    }
                }

                CyberpunkBottomNavBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = JarvisSurface,
            titleContentColor = IceWhite,
            textContentColor = SlateCyan,
            title = {
                Text(
                    text = stringResource(R.string.clear_dialog_title),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.clear_dialog_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearChatHistory()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudAlertRed,
                        contentColor = IceWhite
                    ),
                    modifier = Modifier.testTag("confirm_clear_button")
                ) {
                    Text(stringResource(R.string.clear_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    modifier = Modifier.testTag("cancel_clear_button")
                ) {
                    Text(
                        text = stringResource(R.string.clear_dialog_cancel),
                        color = ElectricCyan
                    )
                }
            }
        )
    }

    if (showTtsSheet) {
        TtsCalibrationSheet(
            ttsConfig = uiState.ttsConfig,
            readiness = uiState.ttsReadiness,
            availableVoices = uiState.availableTeluguVoices,
            isSpeaking = uiState.isSpeaking,
            onSelectPreset = { viewModel.selectTtsPreset(it) },
            onPitchChange = { viewModel.updateTtsPitch(it) },
            onRateChange = { viewModel.updateTtsSpeechRate(it) },
            onSelectVoice = { viewModel.selectTeluguVoice(it) },
            onTestVoice = { viewModel.testTeluguVoice() },
            onStopVoice = { viewModel.stopSpeaking() },
            onInstallTeluguData = {
                runCatching {
                    context.startActivity(viewModel.createInstallTeluguVoiceIntent())
                }.onFailure {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("TTS voice data installer is unavailable on this device.")
                    }
                }
            },
            onDismiss = { showTtsSheet = false }
        )
    }

    if (showHelpSheet) {
        TeluguHelpExamplesSheet(
            onRunExample = { command ->
                showHelpSheet = false
                selectedTab = CyberpunkTab.CHAT
                viewModel.sendStarterPrompt(command)
            },
            onDismiss = { showHelpSheet = false }
        )
    }

    uiState.pendingSensitiveAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingAction() },
            containerColor = JarvisSurface,
            titleContentColor = IceWhite,
            textContentColor = SlateCyan,
            title = {
                Text(
                    text = pending.title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = pending.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingAction() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = JarvisVoid
                    ),
                    modifier = Modifier.testTag("confirm_sensitive_action_button")
                ) {
                    Text(stringResource(R.string.confirm_action_yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissPendingAction() },
                    modifier = Modifier.testTag("cancel_sensitive_action_button")
                ) {
                    Text(
                        text = stringResource(R.string.confirm_action_no),
                        color = SlateCyan
                    )
                }
            }
        )
    }

    if (showAgentConsentDialog) {
        AlertDialog(
            onDismissRequest = { showAgentConsentDialog = false },
            containerColor = JarvisSurface,
            titleContentColor = IceWhite,
            textContentColor = SlateCyan,
            title = { Text("Allow AI Phone Agent screen access?") },
            text = {
                Text(
                    "During this task, visible screen labels and controls from supported apps are sent to your selected AI provider. Password fields are excluded; common messaging, email, payment and authenticator apps are blocked. Every app launch, tap, text entry, scroll and navigation action requires a separate confirmation. You can stop the task at any time."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAgentConsentDialog = false
                        viewModel.startAgentTask(pendingAgentGoal)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = JarvisVoid
                    )
                ) {
                    Text("Allow & Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAgentConsentDialog = false }) {
                    Text("Cancel", color = SlateCyan)
                }
            }
        )
    }

    if (showAccessibilityConsentDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityConsentDialog = false },
            containerColor = JarvisSurface,
            titleContentColor = IceWhite,
            textContentColor = SlateCyan,
            title = {
                Text(
                    text = stringResource(R.string.accessibility_consent_title),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.accessibility_consent_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityConsentDialog = false
                        runCatching {
                            context.startActivity(viewModel.createAccessibilitySettingsIntent())
                        }.onFailure {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Unable to open Accessibility Settings.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = JarvisVoid
                    ),
                    modifier = Modifier.testTag("confirm_accessibility_consent_button")
                ) {
                    Text(stringResource(R.string.permission_settings_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAccessibilityConsentDialog = false },
                    modifier = Modifier.testTag("cancel_accessibility_consent_button")
                ) {
                    Text(
                        text = stringResource(R.string.confirm_action_no),
                        color = SlateCyan
                    )
                }
            }
        )
    }
}

@Composable
private fun ChatCommandTab(
    uiState: PanduUiState,
    onInputTextChanged: (String) -> Unit,
    onSendTypedMessage: () -> Unit,
    onStarterPromptClick: (String) -> Unit,
    onOpenHelp: () -> Unit,
    onSpeakMessage: (com.example.data.ChatMessageEntity) -> Unit,
    onStopSpeaking: () -> Unit,
    onMicClick: () -> Unit
) {
    val listState = rememberLazyListState()
    val streamingText = (uiState.aiGenState as? AiGenState.Streaming)?.partialText
    val isThinking = uiState.aiGenState is AiGenState.Thinking || uiState.aiGenState is AiGenState.Streaming
    val isListening = uiState.voiceInputState is VoiceInputState.Listening

    LaunchedEffect(uiState.messages.size, streamingText, isThinking) {
        val totalItems = uiState.messages.size + if (isThinking) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.messages.isEmpty() && !isThinking) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        JarvisArcCoreHero(
                            onStarterPromptClick = onStarterPromptClick,
                            onOpenHelp = onOpenHelp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_history_list"),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id }
                    ) { message ->
                        ChatMessageBubble(
                            message = message,
                            isCurrentlySpeaking = uiState.isSpeaking &&
                                uiState.speakingMessageId == message.id,
                            onSpeakClick = { onSpeakMessage(message) }
                        )
                    }

                    if (isThinking) {
                        item(key = "streaming_assistant") {
                            StreamingAssistantBubble(partialText = streamingText)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = uiState.isSpeaking,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Surface(
                    onClick = onStopSpeaking,
                    shape = RoundedCornerShape(50),
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, HudOnlineGreen.copy(alpha = 0.7f)),
                    modifier = Modifier.testTag("stop_tts_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = stringResource(R.string.stop_voice_cd),
                            tint = HudOnlineGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.tts_stop_pill),
                            style = MaterialTheme.typography.labelLarge,
                            color = HudOnlineGreen
                        )
                    }
                }
            }

            val listeningState = uiState.voiceInputState as? VoiceInputState.Listening
            AnimatedVisibility(
                visible = listeningState != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (listeningState != null) {
                    val hintText = if (uiState.speechLocale == SpeechLocaleOption.TELUGU) {
                        stringResource(R.string.listening_hint_te)
                    } else {
                        stringResource(R.string.listening_hint_en)
                    }
                    LiveVoiceTranscriptChip(
                        partialText = listeningState.partialText,
                        localeLabel = uiState.speechLocale.tag,
                        hintText = hintText
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = JarvisSurface.copy(alpha = 0.94f),
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = cyberDualNeonBrush(reverse = isListening)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = onInputTextChanged,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input"),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.input_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedHudBlue
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = IceWhite),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { onSendTypedMessage() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = JarvisSurface.copy(alpha = 0f),
                            unfocusedContainerColor = JarvisSurface.copy(alpha = 0f),
                            focusedBorderColor = JarvisSurface.copy(alpha = 0f),
                            unfocusedBorderColor = JarvisSurface.copy(alpha = 0f),
                            cursorColor = ElectricCyan
                        )
                    )

                    val canSend = uiState.inputText.isNotBlank() && !isThinking
                    IconButton(
                        onClick = onSendTypedMessage,
                        enabled = canSend,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_button")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (canSend) NeonSapphire else JarvisSurfaceElevated,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.send_button_cd),
                                    tint = if (canSend) IceWhite else MutedHudBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    JarvisMicOrb(
                        isListening = isListening,
                        isThinking = isThinking,
                        isSpeaking = uiState.isSpeaking,
                        amplitude = listeningState?.amplitude ?: 0.15f,
                        onClick = onMicClick
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF02050D)
@Composable
fun PanduCyberpunkCommandCenterPreview() {
    PanduBabuTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            JarvisAtmosphericBackdrop()
            Column(modifier = Modifier.fillMaxSize()) {
                JarvisTopBar(
                    status = JarvisStatus.ONLINE,
                    speechLocaleLabel = "తెలుగు",
                    ttsPresetLabel = "WITTY",
                    autoSpeakEnabled = true,
                    hasMessages = false,
                    onToggleLocale = {},
                    onToggleAutoSpeak = {},
                    onOpenTtsSettings = {},
                    onOpenHelp = {},
                    onRequestClearChat = {},
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    HomeCommandTab(
                        uiState = PanduUiState(),
                        onMicClick = {},
                        onStopSpeaking = {},
                        onReplayMessage = {},
                        onStarterPromptClick = {},
                        onOpenHelp = {},
                        onNavigateToChat = {},
                        onNavigateToApps = {},
                        onYouTubeShortcut = {},
                        onWhatsAppShortcut = {},
                        onSpeakBattery = {}
                    )
                }
                CyberpunkBottomNavBar(
                    selectedTab = CyberpunkTab.HOME,
                    onSelectTab = {}
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
