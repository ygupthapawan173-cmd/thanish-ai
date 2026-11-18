package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.R
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
import com.example.voice.TeluguTtsConfig
import com.example.voice.TeluguVoiceOption
import com.example.voice.TtsPersonalityPreset
import com.example.voice.TtsReadiness
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TtsCalibrationSheet(
    ttsConfig: TeluguTtsConfig,
    readiness: TtsReadiness,
    availableVoices: List<TeluguVoiceOption>,
    isSpeaking: Boolean,
    onSelectPreset: (TtsPersonalityPreset) -> Unit,
    onPitchChange: (Float) -> Unit,
    onRateChange: (Float) -> Unit,
    onSelectVoice: (String?) -> Unit,
    onTestVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onInstallTeluguData: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JarvisSurface,
        contentColor = IceWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tts_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = IceWhite
                    )
                    Text(
                        text = stringResource(R.string.tts_sheet_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateCyan
                    )
                }
            }

            // Telugu Readiness Indicator
            val (statusText, statusColor) = when (readiness) {
                TtsReadiness.TELUGU_READY -> stringResource(R.string.tts_status_ready) to HudOnlineGreen
                TtsReadiness.FALLBACK_LOCALE -> stringResource(R.string.tts_status_fallback) to HudWarningAmber
                TtsReadiness.UNAVAILABLE -> stringResource(R.string.tts_status_unavailable) to HudAlertRed
                TtsReadiness.INITIALIZING -> "Initializing te-IN engine..." to ElectricCyan
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = JarvisSurfaceElevated,
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

                    if (readiness == TtsReadiness.FALLBACK_LOCALE ||
                        readiness == TtsReadiness.UNAVAILABLE
                    ) {
                        OutlinedButton(
                            onClick = onInstallTeluguData,
                            border = BorderStroke(1.dp, ElectricCyan),
                            modifier = Modifier.testTag("install_telugu_tts_button")
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
                }
            }

            // Personality Presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.tts_preset_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
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
                        val selected = ttsConfig.preset == preset
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(50))
                                .clickable(role = Role.Button) { onSelectPreset(preset) }
                                .testTag("tts_preset_${preset.id}"),
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
            }

            // Pitch Slider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                        text = "${((ttsConfig.pitch * 100).roundToInt() / 100f)}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = IceWhite
                    )
                }
                Slider(
                    value = ttsConfig.pitch,
                    onValueChange = onPitchChange,
                    valueRange = 0.75f..1.35f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = JarvisSurfaceElevated
                    ),
                    modifier = Modifier.testTag("tts_pitch_slider")
                )
            }

            // Speech Speed Slider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.tts_rate_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                    Text(
                        text = "${((ttsConfig.speechRate * 100).roundToInt() / 100f)}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = IceWhite
                    )
                }
                Slider(
                    value = ttsConfig.speechRate,
                    onValueChange = onRateChange,
                    valueRange = 0.75f..1.40f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonSapphire,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = JarvisSurfaceElevated
                    ),
                    modifier = Modifier.testTag("tts_rate_slider")
                )
            }

            // Available Telugu (te-IN) Voice Selection
            if (availableVoices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.tts_voice_list_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )

                    VoiceChoiceRow(
                        label = stringResource(R.string.tts_voice_auto),
                        selected = ttsConfig.selectedVoiceName == null,
                        onClick = { onSelectVoice(null) }
                    )

                    availableVoices.forEach { voice ->
                        VoiceChoiceRow(
                            label = voice.displayLabel,
                            selected = ttsConfig.selectedVoiceName == voice.name,
                            onClick = { onSelectVoice(voice.name) }
                        )
                    }
                }
            }

            // Test Voice CTA
            Button(
                onClick = if (isSpeaking) onStopVoice else onTestVoice,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSpeaking) HudAlertRed else ElectricCyan,
                    contentColor = if (isSpeaking) IceWhite else JarvisVoid
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("tts_test_voice_button")
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSpeaking) {
                        stringResource(R.string.tts_stop_pill)
                    } else {
                        stringResource(R.string.tts_test_button)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VoiceChoiceRow(
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
        color = if (selected) JarvisSurfaceElevated else JarvisVoid.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (selected) ElectricCyan.copy(alpha = 0.65f) else MutedHudBlue.copy(alpha = 0.25f)
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
