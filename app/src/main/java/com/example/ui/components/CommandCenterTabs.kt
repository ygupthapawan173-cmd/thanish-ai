package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.control.BatteryStatusInfo
import com.example.data.ChatMessageEntity
import com.example.ui.AiGenState
import com.example.ui.JarvisStatus
import com.example.ui.PanduUiState
import com.example.ui.theme.ArcGlowBlue
import com.example.ui.theme.CyberOrange
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudAlertRed
import com.example.ui.theme.HudOnlineGreen
import com.example.ui.theme.HudWarningAmber
import com.example.ui.theme.IceWhite
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisVoid
import com.example.ui.theme.MutedHudBlue
import com.example.ui.theme.NeonSapphire
import com.example.ui.theme.SlateCyan
import com.example.voice.SpeechLocaleOption
import com.example.voice.TtsPersonalityPreset
import com.example.voice.TtsReadiness
import com.example.voice.VoiceInputState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeCommandTab(
    uiState: PanduUiState,
    onMicClick: () -> Unit,
    onStopSpeaking: () -> Unit,
    onReplayMessage: (ChatMessageEntity) -> Unit,
    onStarterPromptClick: (String) -> Unit,
    onOpenHelp: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToApps: () -> Unit,
    onYouTubeShortcut: () -> Unit,
    onWhatsAppShortcut: () -> Unit,
    onSpeakBattery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listeningState = uiState.voiceInputState as? VoiceInputState.Listening
    val isListening = listeningState != null
    val isThinking = uiState.aiGenState is AiGenState.Thinking || uiState.aiGenState is AiGenState.Streaming
    val amplitude = listeningState?.amplitude ?: 0.2f

    val starterPrompts = listOf(
        stringResource(R.string.starter_prompt_joke),
        stringResource(R.string.starter_prompt_motivation),
        stringResource(R.string.starter_prompt_tech),
        stringResource(R.string.starter_prompt_food)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_command_tab"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Central Holographic Avatar + Animated Waveform + Large Circular Mic Button
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = JarvisSurface.copy(alpha = 0.88f),
                border = BorderStroke(1.2.dp, cyberDualNeonBrush())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HolographicJarvisCore(
                        status = uiState.jarvisStatus,
                        amplitude = amplitude
                    )

                    Text(
                        text = when {
                            isListening -> listeningState?.partialText?.ifBlank {
                                stringResource(R.string.listening_hint_te)
                            }.orEmpty()
                            uiState.aiGenState is AiGenState.Streaming -> uiState.aiGenState.partialText
                            uiState.aiGenState is AiGenState.Thinking -> stringResource(R.string.status_thinking)
                            else -> stringResource(R.string.welcome_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = IceWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    CyberpunkAudioWaveform(
                        status = uiState.jarvisStatus,
                        amplitude = amplitude,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    CyberpunkHeroMicButton(
                        isListening = isListening,
                        isThinking = isThinking,
                        isSpeaking = uiState.isSpeaking,
                        amplitude = amplitude,
                        onClick = onMicClick
                    )

                    Text(
                        text = if (isListening) {
                            stringResource(R.string.status_listening)
                        } else {
                            stringResource(R.string.tap_to_speak_telugu)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isListening) ElectricCyan else CyberOrange
                    )

                    AnimatedVisibility(
                        visible = uiState.isSpeaking,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        OutlinedButton(
                            onClick = onStopSpeaking,
                            border = BorderStroke(1.dp, HudOnlineGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = stringResource(R.string.stop_voice_cd),
                                tint = HudOnlineGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.tts_stop_pill),
                                color = HudOnlineGreen,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Quick Launch Deck: YouTube, WhatsApp, Apps Shortcut Cards
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.command_deck_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                CyberpunkShortcutDeck(
                    onYouTubeClick = onYouTubeShortcut,
                    onWhatsAppClick = onWhatsAppShortcut,
                    onAppsClick = onNavigateToApps
                )
            }
        }

        // Power Core Telemetry Card (Battery Percentage & Charging State)
        item {
            JarvisDashboardBatteryCard(
                battery = uiState.deviceControlState.battery,
                onSpeakBattery = onSpeakBattery
            )
        }

        // Latest Jarvis Transmission Card (if conversation exists)
        val latestAssistant = uiState.messages.lastOrNull {
            it.role == ChatMessageEntity.ROLE_ASSISTANT
        }
        if (latestAssistant != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = JarvisSurface.copy(alpha = 0.92f),
                    border = BorderStroke(1.2.dp, cyberDualNeonBrush(reverse = true))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.last_reply_card_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberOrange
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onReplayMessage(latestAssistant) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    val speakingThis = uiState.isSpeaking &&
                                        uiState.speakingMessageId == latestAssistant.id
                                    Icon(
                                        imageVector = if (speakingThis) {
                                            Icons.Default.Stop
                                        } else {
                                            Icons.AutoMirrored.Filled.VolumeUp
                                        },
                                        contentDescription = stringResource(R.string.replay_voice_cd),
                                        tint = if (speakingThis) HudOnlineGreen else ElectricCyan
                                    )
                                }
                                OutlinedButton(
                                    onClick = onNavigateToChat,
                                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = stringResource(R.string.open_full_chat),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ElectricCyan
                                    )
                                }
                            }
                        }

                        Text(
                            text = latestAssistant.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = IceWhite
                        )
                    }
                }
            }
        }

        // Quick Telugu Prompt Chips + Help Guide CTA
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = JarvisSurface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        starterPrompts.forEachIndexed { index, prompt ->
                            Surface(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(50))
                                    .clickable(role = Role.Button) {
                                        onStarterPromptClick(prompt)
                                    }
                                    .testTag("starter_chip_$index"),
                                shape = RoundedCornerShape(50),
                                color = JarvisSurfaceElevated.copy(alpha = 0.9f),
                                border = BorderStroke(1.dp, cyberDualNeonBrush())
                            ) {
                                Text(
                                    text = prompt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IceWhite,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenHelp,
                        border = BorderStroke(1.dp, CyberOrange.copy(alpha = 0.75f)),
                        modifier = Modifier.testTag("hero_help_examples_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = null,
                            tint = CyberOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.help_examples_cta),
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JarvisDashboardBatteryCard(
    battery: BatteryStatusInfo,
    onSpeakBattery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasValidLevel = battery.levelPercent >= 0
    val clampedPercent = battery.levelPercent.coerceIn(0, 100)
    val accentColor = when {
        battery.isCharging -> HudOnlineGreen
        hasValidLevel && clampedPercent <= 20 -> HudAlertRed
        else -> ElectricCyan
    }
    val chargingStatusLabel = when {
        battery.isFull -> stringResource(R.string.battery_full_status)
        battery.isCharging -> stringResource(R.string.battery_charging_status)
        else -> stringResource(R.string.battery_discharging_status)
    }
    val batteryIcon = when {
        battery.isCharging -> Icons.Default.BatteryChargingFull
        hasValidLevel && clampedPercent <= 20 -> Icons.Default.BatteryAlert
        else -> Icons.Default.BatteryStd
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.battery_cd),
                onClick = onSpeakBattery
            )
            .testTag("home_battery_card"),
        shape = RoundedCornerShape(18.dp),
        color = JarvisSurface.copy(alpha = 0.9f),
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.linearGradient(
                listOf(accentColor.copy(alpha = 0.9f), ElectricCyan.copy(alpha = 0.35f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f))
                        .border(1.dp, accentColor.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = batteryIcon,
                        contentDescription = stringResource(R.string.battery_cd),
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.battery_telemetry_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateCyan
                    )
                    Text(
                        text = "$chargingStatusLabel • ${battery.chargingSource}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("home_battery_charging_state")
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f))
                ) {
                    Text(
                        text = if (hasValidLevel) "$clampedPercent%" else "--%",
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("home_battery_percentage")
                    )
                }
            }

            // Neon power cell level bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(JarvisSurfaceElevated)
            ) {
                if (hasValidLevel && clampedPercent > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(clampedPercent / 100f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accentColor, ElectricCyan)
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCommandTab(
    messages: List<ChatMessageEntity>,
    isSpeaking: Boolean,
    speakingMessageId: Long?,
    onSpeakClick: (ChatMessageEntity) -> Unit,
    onRequestClearChat: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val voiceCount = messages.count { it.isVoiceInput }
    val assistantCount = messages.count { it.role == ChatMessageEntity.ROLE_ASSISTANT }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("history_command_tab"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.history_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = ElectricCyan
                    )
                    Text(
                        text = stringResource(R.string.history_screen_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateCyan
                    )
                }
                if (messages.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onRequestClearChat,
                        border = BorderStroke(1.dp, HudAlertRed.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_chat_cd),
                            tint = HudAlertRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.clear_dialog_confirm),
                            color = HudAlertRed,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Telemetry stats row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    label = "TOTAL LOGS",
                    value = messages.size.toString(),
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "VOICE INPUTS",
                    value = voiceCount.toString(),
                    accent = CyberOrange,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "JARVIS REPLIES",
                    value = assistantCount.toString(),
                    accent = HudOnlineGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (messages.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = JarvisSurface.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, cyberDualNeonBrush())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = stringResource(R.string.history_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = IceWhite,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.history_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateCyan,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onOpenChat,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = JarvisVoid
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.open_full_chat),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        } else {
            items(
                items = messages.reversed(),
                key = { it.id }
            ) { message ->
                ChatMessageBubble(
                    message = message,
                    isCurrentlySpeaking = isSpeaking && speakingMessageId == message.id,
                    onSpeakClick = { onSpeakClick(message) }
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = JarvisSurface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(accent, ElectricCyan.copy(alpha = 0.3f))))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SlateCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCommandTab(
    uiState: PanduUiState,
    onToggleAutoSpeak: () -> Unit,
    onToggleLocale: () -> Unit,
    onSelectPreset: (TtsPersonalityPreset) -> Unit,
    onPitchChange: (Float) -> Unit,
    onRateChange: (Float) -> Unit,
    onSelectVoice: (String?) -> Unit,
    onTestVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onInstallTeluguData: () -> Unit,
    onOpenHelp: () -> Unit,
    onRequestClearChat: () -> Unit,
    onSaveOpenRouterKey: (String) -> Unit,
    onClearOpenRouterKey: () -> Unit,
    onSaveOpenJarvisSettings: (String, String, String) -> Unit,
    onClearOpenJarvisSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var openRouterKeyInput by rememberSaveable { mutableStateOf("") }
    var openJarvisUrlInput by rememberSaveable { mutableStateOf("") }
    var openJarvisKeyInput by rememberSaveable { mutableStateOf("") }
    var openJarvisModelInput by rememberSaveable { mutableStateOf("openrouter/free") }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_command_tab"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricCyan
                )
                Text(
                    text = stringResource(R.string.settings_screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateCyan
                )
            }
        }

        // OpenRouter Free provider configuration
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = JarvisSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.65f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("AI Provider — OpenRouter Free", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                    Text(
                        text = if (uiState.openRouterKeyConfigured) "API key ఈ device‌లో secure‌గా save అయింది." else "OpenRouter API key ఇంకా save చేయలేదు.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.openRouterKeyConfigured) HudOnlineGreen else SlateCyan
                    )
                    OutlinedTextField(
                        value = openRouterKeyInput,
                        onValueChange = { openRouterKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OpenRouter API key") },
                        placeholder = { Text("sk-or-...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Button(
                        onClick = {
                            onSaveOpenRouterKey(openRouterKeyInput)
                            openRouterKeyInput = ""
                        },
                        enabled = openRouterKeyInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save API Key Securely")
                    }
                    if (uiState.openRouterKeyConfigured) {
                        OutlinedButton(
                            onClick = onClearOpenRouterKey,
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, HudAlertRed)
                        ) {
                            Text("Remove Saved Key", color = HudAlertRed)
                        }
                    }
                    Text(
                        "Key Android Keystore encryption‌తో ఈ phone‌లో మాత్రమే నిల్వ ఉంటుంది. Chat‌లో key పంపవద్దు.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateCyan
                    )
                }
            }
        }

        // OpenAI-compatible multi-provider cloud API connection
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = JarvisSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, NeonSapphire.copy(alpha = 0.8f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("AI Provider — Cloud Providers", style = MaterialTheme.typography.titleMedium, color = NeonSapphire)
                    Text(
                        text = if (uiState.openJarvisConfigured) "Cloud provider connection ఈ device‌లో secure‌గా save అయ్యింది." else "OpenAI-compatible provider endpoint, API key, model ID ఇవ్వండి.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.openJarvisConfigured) HudOnlineGreen else SlateCyan
                    )
                    OutlinedTextField(
                        value = openJarvisUrlInput,
                        onValueChange = { openJarvisUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Provider API base URL (HTTPS)") },
                        placeholder = { Text("https://api.deepseek.com లేదా provider URL") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = openJarvisKeyInput,
                        onValueChange = { openJarvisKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Provider API key") },
                        placeholder = { Text("Provider dashboard నుంచి తీసుకున్న key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = openJarvisModelInput,
                        onValueChange = { openJarvisModelInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model ID") },
                        placeholder = { Text("openrouter/free") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            onSaveOpenJarvisSettings(openJarvisUrlInput, openJarvisKeyInput, openJarvisModelInput)
                            openJarvisKeyInput = ""
                        },
                        enabled = openJarvisUrlInput.startsWith("https://") &&
                            openJarvisKeyInput.length >= 20 && openJarvisModelInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect Cloud Provider Securely")
                    }
                    if (uiState.openJarvisConfigured) {
                        OutlinedButton(
                            onClick = onClearOpenJarvisSettings,
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, HudAlertRed)
                        ) {
                            Text("Disconnect Cloud Provider", color = HudAlertRed)
                        }
                    }
                    Text(
                        "ఈ కనెక్షన్ OpenAI-compatible /v1/chat/completions APIలను ఉపయోగిస్తుంది (ఉదా: DeepSeek, NVIDIA NIM, OpenAI-compatible gateways, custom HTTPS servers). Gemini native API మరియు Ollama local API ఇంకా ఈ కనెక్షన్‌లో లేవు. API key Android Keystore‌లో encrypt అవుతుంది; GitHub లేదా Chat‌లో పెట్టవద్దు.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateCyan
                    )
                }
            }
        }

        // Auto-speak + Speech Input Language Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = JarvisSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, cyberDualNeonBrush())
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_auto_speak_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = IceWhite
                            )
                            Text(
                                text = stringResource(R.string.settings_auto_speak_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateCyan
                            )
                        }
                        Switch(
                            checked = uiState.autoSpeakEnabled,
                            onCheckedChange = { onToggleAutoSpeak() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisVoid,
                                checkedTrackColor = ElectricCyan,
                                uncheckedThumbColor = SlateCyan,
                                uncheckedTrackColor = JarvisSurfaceElevated
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = CyberOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_input_lang_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = IceWhite,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onToggleLocale,
                            border = BorderStroke(1.dp, CyberOrange.copy(alpha = 0.8f))
                        ) {
                            Text(
                                text = if (uiState.speechLocale == SpeechLocaleOption.TELUGU) {
                                    "తెలుగు (te-IN)"
                                } else {
                                    "English (en-IN)"
                                },
                                color = ElectricCyan,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Telugu TTS Personality & Calibration Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = JarvisSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.2.dp, cyberDualNeonBrush(reverse = true))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val (statusText, statusColor) = when (uiState.ttsReadiness) {
                        TtsReadiness.TELUGU_READY -> stringResource(R.string.tts_status_ready) to HudOnlineGreen
                        TtsReadiness.FALLBACK_LOCALE -> stringResource(R.string.tts_status_fallback) to HudWarningAmber
                        TtsReadiness.UNAVAILABLE -> stringResource(R.string.tts_status_unavailable) to HudAlertRed
                        TtsReadiness.INITIALIZING -> "Initializing te-IN engine..." to ElectricCyan
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = IceWhite
                        )
                    }

                    if (uiState.ttsReadiness == TtsReadiness.FALLBACK_LOCALE ||
                        uiState.ttsReadiness == TtsReadiness.UNAVAILABLE
                    ) {
                        OutlinedButton(
                            onClick = onInstallTeluguData,
                            border = BorderStroke(1.dp, ElectricCyan)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.tts_install_data_button),
                                color = ElectricCyan
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.tts_preset_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberOrange
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            TtsPersonalityPreset.TELUGU_MALE_JARVIS to "తెలుగు Male • Jarvis",
                            TtsPersonalityPreset.TELUGU_FEMALE_SWEET to "తెలుగు Female • Sweet",
                            TtsPersonalityPreset.TELUGU_FUNNY_COMEDY to "తెలుగు Funny • Comedy",
                            TtsPersonalityPreset.TELUGU_NATURAL to "తెలుగు Natural • Normal"
                        )
                        presets.forEach { (preset, label) ->
                            val selected = uiState.ttsConfig.preset == preset
                            Surface(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(50))
                                    .clickable(role = Role.Button) { onSelectPreset(preset) },
                                shape = RoundedCornerShape(50),
                                color = if (selected) ElectricCyan.copy(alpha = 0.2f) else JarvisSurfaceElevated,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) ElectricCyan else MutedHudBlue.copy(alpha = 0.4f)
                                )
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) ElectricCyan else IceWhite,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    // Pitch
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.tts_pitch_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            Text(
                                text = "${((uiState.ttsConfig.pitch * 100).roundToInt() / 100f)}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = IceWhite
                            )
                        }
                        Slider(
                            value = uiState.ttsConfig.pitch,
                            onValueChange = onPitchChange,
                            valueRange = 0.75f..1.35f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = JarvisSurfaceElevated
                            )
                        )
                    }

                    // Speed
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.tts_rate_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberOrange
                            )
                            Text(
                                text = "${((uiState.ttsConfig.speechRate * 100).roundToInt() / 100f)}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = IceWhite
                            )
                        }
                        Slider(
                            value = uiState.ttsConfig.speechRate,
                            onValueChange = onRateChange,
                            valueRange = 0.75f..1.40f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyberOrange,
                                activeTrackColor = CyberOrange,
                                inactiveTrackColor = JarvisSurfaceElevated
                            )
                        )
                    }

                    if (uiState.availableTeluguVoices.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.tts_voice_list_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                        SettingsVoiceOption(
                            label = stringResource(R.string.tts_voice_auto),
                            selected = uiState.ttsConfig.selectedVoiceName == null,
                            onClick = { onSelectVoice(null) }
                        )
                        uiState.availableTeluguVoices.forEach { voice ->
                            SettingsVoiceOption(
                                label = voice.displayLabel,
                                selected = uiState.ttsConfig.selectedVoiceName == voice.name,
                                onClick = { onSelectVoice(voice.name) }
                            )
                        }
                    }

                    Button(
                        onClick = if (uiState.isSpeaking) onStopVoice else onTestVoice,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isSpeaking) HudAlertRed else ElectricCyan,
                            contentColor = if (uiState.isSpeaking) IceWhite else JarvisVoid
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isSpeaking) {
                                stringResource(R.string.tts_stop_pill)
                            } else {
                                stringResource(R.string.tts_test_button)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Help & Clear Chat Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenHelp,
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.help_sheet_title),
                        color = ElectricCyan,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onRequestClearChat,
                    enabled = uiState.messages.isNotEmpty(),
                    border = BorderStroke(
                        1.dp,
                        if (uiState.messages.isNotEmpty()) HudAlertRed else MutedHudBlue.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = if (uiState.messages.isNotEmpty()) HudAlertRed else MutedHudBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.clear_dialog_confirm),
                        color = if (uiState.messages.isNotEmpty()) HudAlertRed else MutedHudBlue,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsVoiceOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) JarvisSurfaceElevated else JarvisVoid.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            if (selected) ElectricCyan.copy(alpha = 0.7f) else MutedHudBlue.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = ElectricCyan,
                    unselectedColor = SlateCyan
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = IceWhite
            )
        }
    }
}
