package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.control.AppControlTarget
import com.example.control.DeviceControlState
import com.example.control.DeviceSettingTarget
import com.example.control.GlobalNavigationAction
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
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneControlDashboard(
    controlState: DeviceControlState,
    onRefreshControls: () -> Unit,
    onAppClick: (AppControlTarget) -> Unit,
    onTorchToggle: (Boolean) -> Unit,
    onVolumeAdjust: (Int) -> Unit,
    onVolumeSet: (Int) -> Unit,
    onBrightnessSet: (Int) -> Unit,
    onBrightnessAdjust: (Int) -> Unit,
    onGrantBrightnessPermission: () -> Unit,
    onReadBatterySpeak: () -> Unit,
    onOpenDeviceSetting: (DeviceSettingTarget) -> Unit,
    onGlobalNavAction: (GlobalNavigationAction) -> Unit,
    onRequestAccessibilityConsent: () -> Unit,
    onRunVoiceCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val voiceCommandSamples = listOf(
        "YouTube open chey",
        "WhatsApp open chey",
        "Torch on chey",
        "Torch off chey",
        "Volume penchu",
        "Brightness tagginchu",
        "Wi-Fi settings open chey",
        "Bluetooth settings open chey",
        "బ్యాటరీ ఎంత ఉంది",
        "టార్చ్ ఆన్ చెయ్",
        "go back",
        "home ki vellu",
        "open recents",
        "open notifications"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("phone_control_dashboard"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.control_center_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = ElectricCyan
                    )
                    Text(
                        text = stringResource(R.string.control_center_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateCyan
                    )
                }
                IconButton(
                    onClick = onRefreshControls,
                    modifier = Modifier.testTag("refresh_controls_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh device telemetry",
                        tint = ElectricCyan
                    )
                }
            }
        }

        // Honest Command Feedback Pill
        controlState.lastFeedbackMessage?.let { feedback ->
            item {
                val borderCol = if (controlState.lastFeedbackSuccess) HudOnlineGreen else HudAlertRed
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("control_feedback_card"),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, borderCol.copy(alpha = 0.75f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (controlState.lastFeedbackSuccess) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.WarningAmber
                            },
                            contentDescription = null,
                            tint = borderCol,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = feedback,
                            style = MaterialTheme.typography.bodyMedium,
                            color = IceWhite
                        )
                    }
                }
            }
        }

        // 1. APP CONTROL (9 Apps in 3x3 grid)
        item {
            SectionTitle(stringResource(R.string.section_app_control), ElectricCyan)
        }

        item {
            val appRows = listOf(
                listOf(AppControlTarget.YOUTUBE, AppControlTarget.WHATSAPP, AppControlTarget.INSTAGRAM),
                listOf(AppControlTarget.CHROME, AppControlTarget.CAMERA, AppControlTarget.GALLERY),
                listOf(AppControlTarget.PHONE, AppControlTarget.MESSAGES, AppControlTarget.SETTINGS)
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                appRows.forEach { rowApps ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowApps.forEach { target ->
                            AppControlCard(
                                target = target,
                                onClick = { onAppClick(target) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 2. DEVICE CONTROL
        item {
            SectionTitle(stringResource(R.string.section_device_control), CyberOrange)
        }

        // Flashlight ON/OFF Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = JarvisSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.2.dp, cyberDualNeonBrush())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                (if (controlState.isTorchOn) CyberOrange else ElectricCyan).copy(alpha = 0.16f)
                            )
                            .border(
                                1.dp,
                                if (controlState.isTorchOn) CyberOrange else ElectricCyan.copy(alpha = 0.6f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (controlState.isTorchOn) {
                                Icons.Default.FlashlightOn
                            } else {
                                Icons.Default.FlashlightOff
                            },
                            contentDescription = "Flashlight",
                            tint = if (controlState.isTorchOn) CyberOrange else ElectricCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Flashlight / Torch",
                            style = MaterialTheme.typography.labelLarge,
                            color = IceWhite
                        )
                        Text(
                            text = when {
                                !controlState.hasFlashUnit -> "No camera flash hardware detected"
                                controlState.isTorchOn -> "Torch is ON"
                                else -> "Torch is OFF"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (controlState.hasFlashUnit) SlateCyan else HudWarningAmber
                        )
                    }
                    Switch(
                        checked = controlState.isTorchOn,
                        onCheckedChange = onTorchToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisVoid,
                            checkedTrackColor = CyberOrange,
                            uncheckedThumbColor = SlateCyan,
                            uncheckedTrackColor = JarvisSurfaceElevated
                        ),
                        modifier = Modifier.testTag("torch_switch")
                    )
                }
            }
        }

        // Volume Control Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                            Text(
                                text = "Media Volume",
                                style = MaterialTheme.typography.labelLarge,
                                color = IceWhite
                            )
                        }
                        Text(
                            text = "${controlState.currentVolume} / ${controlState.maxVolume}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }

                    var sliderVolume by remember(controlState.currentVolume) {
                        mutableFloatStateOf(controlState.currentVolume.toFloat())
                    }
                    Slider(
                        value = sliderVolume,
                        onValueChange = { newVal ->
                            sliderVolume = newVal
                            val step = newVal.roundToInt()
                            if (step != controlState.currentVolume) {
                                onVolumeSet(step)
                            }
                        },
                        valueRange = 0f..controlState.maxVolume.coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = ElectricCyan,
                            inactiveTrackColor = JarvisSurfaceElevated
                        ),
                        modifier = Modifier.testTag("volume_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onVolumeAdjust(-1) },
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("volume_down_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("- Vol", color = IceWhite)
                        }
                        OutlinedButton(
                            onClick = { onVolumeSet(0) },
                            border = BorderStroke(1.dp, CyberOrange.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("volume_mute_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = null,
                                tint = CyberOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mute", color = IceWhite)
                        }
                        OutlinedButton(
                            onClick = { onVolumeAdjust(+1) },
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("volume_up_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Vol", color = IceWhite)
                        }
                    }
                }
            }
        }

        // Screen Brightness Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = JarvisSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.2.dp, cyberDualNeonBrush())
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrightnessHigh,
                                contentDescription = null,
                                tint = CyberOrange
                            )
                            Text(
                                text = "Screen Brightness",
                                style = MaterialTheme.typography.labelLarge,
                                color = IceWhite
                            )
                        }
                        Text(
                            text = "${controlState.brightnessPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberOrange
                        )
                    }

                    if (!controlState.canWriteSystemSettings) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = JarvisSurfaceElevated,
                            border = BorderStroke(1.dp, HudWarningAmber.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Android requires 'Modify System Settings' permission to change global screen brightness.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HudWarningAmber
                                )
                                Button(
                                    onClick = onGrantBrightnessPermission,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberOrange,
                                        contentColor = JarvisVoid
                                    ),
                                    modifier = Modifier.testTag("grant_brightness_permission_button")
                                ) {
                                    Text(
                                        text = "Grant Brightness Permission",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }

                    var sliderBrightness by remember(controlState.brightnessPercent) {
                        mutableFloatStateOf(controlState.brightnessPercent.toFloat())
                    }
                    Slider(
                        value = sliderBrightness,
                        onValueChange = { sliderBrightness = it },
                        onValueChangeFinished = {
                            onBrightnessSet(sliderBrightness.roundToInt())
                        },
                        enabled = controlState.canWriteSystemSettings,
                        valueRange = 5f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberOrange,
                            activeTrackColor = CyberOrange,
                            inactiveTrackColor = JarvisSurfaceElevated
                        ),
                        modifier = Modifier.testTag("brightness_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onBrightnessAdjust(-15) },
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("brightness_down_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrightnessLow,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("-15%", color = IceWhite)
                        }
                        OutlinedButton(
                            onClick = { onBrightnessAdjust(+15) },
                            border = BorderStroke(1.dp, CyberOrange.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("brightness_up_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrightnessHigh,
                                contentDescription = null,
                                tint = CyberOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+15%", color = IceWhite)
                        }
                    }
                }
            }
        }

        // Battery Information Card
        item {
            val b = controlState.battery
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("battery_info_card"),
                shape = RoundedCornerShape(18.dp),
                color = JarvisSurface.copy(alpha = 0.92f),
                border = BorderStroke(
                    1.2.dp,
                    Brush.linearGradient(listOf(HudOnlineGreen, ElectricCyan))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(HudOnlineGreen.copy(alpha = 0.16f))
                            .border(1.dp, HudOnlineGreen.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (b.isCharging) {
                                Icons.Default.BatteryChargingFull
                            } else {
                                Icons.Default.BatteryStd
                            },
                            contentDescription = "Battery",
                            tint = HudOnlineGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (b.levelPercent >= 0) {
                                "Battery: ${b.levelPercent}% (${b.chargingSource})"
                            } else {
                                "Battery: Reading..."
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = IceWhite
                        )
                        val formattedTemp = String.format(Locale.US, "%.1f", b.temperatureCelsius)
                        Text(
                            text = "Temp: ${formattedTemp}°C • Health: ${b.healthLabel} • ${b.voltageMillivolts}mV",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateCyan
                        )
                    }
                    OutlinedButton(
                        onClick = onReadBatterySpeak,
                        border = BorderStroke(1.dp, HudOnlineGreen.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("battery_speak_button")
                    ) {
                        Text("Speak", color = HudOnlineGreen)
                    }
                }
            }
        }

        // System Settings Shortcuts (Wi-Fi, Bluetooth, Hotspot, Mobile Network)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DeviceSettingCard(
                        title = "Wi-Fi Settings",
                        icon = Icons.Default.Wifi,
                        accent = ElectricCyan,
                        onClick = { onOpenDeviceSetting(DeviceSettingTarget.WIFI) },
                        testTag = "setting_wifi",
                        modifier = Modifier.weight(1f)
                    )
                    DeviceSettingCard(
                        title = "Bluetooth Settings",
                        icon = Icons.Default.Bluetooth,
                        accent = NeonSapphire,
                        onClick = { onOpenDeviceSetting(DeviceSettingTarget.BLUETOOTH) },
                        testTag = "setting_bluetooth",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DeviceSettingCard(
                        title = "Hotspot Settings",
                        icon = Icons.Default.WifiTethering,
                        accent = CyberOrange,
                        onClick = { onOpenDeviceSetting(DeviceSettingTarget.HOTSPOT) },
                        testTag = "setting_hotspot",
                        modifier = Modifier.weight(1f)
                    )
                    DeviceSettingCard(
                        title = "Mobile Network",
                        icon = Icons.Default.SignalCellularAlt,
                        accent = HudOnlineGreen,
                        onClick = { onOpenDeviceSetting(DeviceSettingTarget.MOBILE_NETWORK) },
                        testTag = "setting_mobile_network",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. VOICE COMMANDS (Telugu & Roman Telugu)
        item {
            SectionTitle(stringResource(R.string.section_voice_commands), ElectricCyan)
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = JarvisSurface.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, cyberDualNeonBrush())
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Tap any command to test or speak it in Telugu / Tenglish:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateCyan
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        voiceCommandSamples.forEach { cmd ->
                            Surface(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(50))
                                    .clickable(role = Role.Button) { onRunVoiceCommand(cmd) },
                                shape = RoundedCornerShape(50),
                                color = JarvisSurfaceElevated,
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = cmd,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IceWhite,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. ANDROID RESTRICTIONS & OPTIONAL ACCESSIBILITY SERVICE
        item {
            SectionTitle(stringResource(R.string.section_android_restrictions), CyberOrange)
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = JarvisSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, CyberOrange.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "• Android 10+ restricts apps from silently toggling Wi-Fi, Bluetooth, Hotspot, or Mobile Data — Pandu Babu opens the official system settings panel instead.\n• System Brightness requires 'Modify System Settings' permission.\n• Phone & Messages always ask explicit confirmation before opening.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessibilityNew,
                                contentDescription = null,
                                tint = if (controlState.isAccessibilityEnabled) HudOnlineGreen else CyberOrange
                            )
                            Text(
                                text = if (controlState.isAccessibilityEnabled) {
                                    "Hands-Free Navigation: Enabled"
                                } else {
                                    "Hands-Free Navigation (Optional)"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = IceWhite
                            )
                        }
                        if (!controlState.isAccessibilityEnabled) {
                            OutlinedButton(
                                onClick = onRequestAccessibilityConsent,
                                border = BorderStroke(1.dp, ElectricCyan),
                                modifier = Modifier.testTag("accessibility_consent_button")
                            ) {
                                Text("Enable", color = ElectricCyan)
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlobalNavigationAction.entries.forEach { action ->
                            OutlinedButton(
                                onClick = { onGlobalNavAction(action) },
                                enabled = controlState.isAccessibilityEnabled,
                                border = BorderStroke(
                                    1.dp,
                                    if (controlState.isAccessibilityEnabled) ElectricCyan else MutedHudBlue.copy(alpha = 0.35f)
                                )
                            ) {
                                Text(
                                    text = action.displayName,
                                    color = if (controlState.isAccessibilityEnabled) IceWhite else MutedHudBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
    )
}

@Composable
private fun AppControlCard(
    target: AppControlTarget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, accent) = when (target) {
        AppControlTarget.YOUTUBE -> Icons.Default.PlayArrow to CyberOrange
        AppControlTarget.WHATSAPP -> Icons.AutoMirrored.Filled.Chat to HudOnlineGreen
        AppControlTarget.INSTAGRAM -> Icons.Default.Share to CyberOrange
        AppControlTarget.CHROME -> Icons.Default.Public to ElectricCyan
        AppControlTarget.CAMERA -> Icons.Default.CameraAlt to ArcGlowBlue
        AppControlTarget.GALLERY -> Icons.Default.PhotoLibrary to ElectricCyan
        AppControlTarget.PHONE -> Icons.Default.Call to HudOnlineGreen
        AppControlTarget.MESSAGES -> Icons.AutoMirrored.Filled.Message to ArcGlowBlue
        AppControlTarget.SETTINGS -> Icons.Default.Settings to CyberOrange
    }

    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("app_control_${target.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        color = JarvisSurface.copy(alpha = 0.92f),
        border = BorderStroke(
            1.1.dp,
            Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), ElectricCyan.copy(alpha = 0.45f)))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = target.displayName,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (target.requiresConfirmation) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Confirmation required",
                        tint = CyberOrange,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                text = target.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = IceWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeviceSettingCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = JarvisSurface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(accent, ElectricCyan.copy(alpha = 0.4f))))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = IceWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF02050D)
@Composable
fun PhoneControlDashboardPreview() {
    com.example.ui.theme.PanduBabuTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            JarvisAtmosphericBackdrop()
            PhoneControlDashboard(
                controlState = DeviceControlState(
                    hasFlashUnit = true,
                    isTorchOn = true,
                    currentVolume = 9,
                    maxVolume = 15,
                    canWriteSystemSettings = false,
                    brightnessPercent = 65,
                    battery = com.example.control.BatteryStatusInfo(
                        levelPercent = 84,
                        isCharging = true,
                        chargingSource = "USB",
                        temperatureCelsius = 31.5f,
                        voltageMillivolts = 4150,
                        healthLabel = "Good"
                    ),
                    isAccessibilityEnabled = false,
                    lastFeedbackMessage = "డన్ బాస్! YouTube ఓపెన్ చేశాను!",
                    lastFeedbackSuccess = true
                ),
                onRefreshControls = {},
                onAppClick = {},
                onTorchToggle = {},
                onVolumeAdjust = {},
                onVolumeSet = {},
                onBrightnessSet = {},
                onBrightnessAdjust = {},
                onGrantBrightnessPermission = {},
                onReadBatterySpeak = {},
                onOpenDeviceSetting = {},
                onGlobalNavAction = {},
                onRequestAccessibilityConsent = {},
                onRunVoiceCommand = {}
            )
        }
    }
}
