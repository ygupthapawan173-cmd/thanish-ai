package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.JarvisStatus
import com.example.ui.theme.ArcGlowBlue
import com.example.ui.theme.CyberAmberGlow
import com.example.ui.theme.CyberOrange
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudOnlineGreen
import com.example.ui.theme.IceWhite
import com.example.ui.theme.JarvisDeepSpace
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisVoid
import com.example.ui.theme.MutedHudBlue
import com.example.ui.theme.NeonSapphire
import com.example.ui.theme.SlateCyan
import kotlin.math.PI
import kotlin.math.sin

enum class CyberpunkTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    CHAT(R.string.nav_chat, Icons.Filled.Forum, Icons.Outlined.Forum, "nav_chat"),
    APPS(R.string.nav_apps, Icons.Filled.Apps, Icons.Outlined.Apps, "nav_apps"),
    HISTORY(R.string.nav_history, Icons.Filled.History, Icons.Outlined.History, "nav_history"),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings"),
    OMEGA(R.string.nav_omega, Icons.Filled.GraphicEq, Icons.Filled.GraphicEq, "nav_omega")
}

fun cyberDualNeonBrush(reverse: Boolean = false): Brush {
    return Brush.linearGradient(
        colors = if (reverse) {
            listOf(CyberOrange, NeonSapphire, ElectricCyan)
        } else {
            listOf(ElectricCyan, NeonSapphire, CyberOrange)
        }
    )
}

@Composable
fun HolographicJarvisCore(
    status: JarvisStatus,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "holo_core")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing)
        ),
        label = "outer_angle"
    )
    val midAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing)
        ),
        label = "mid_angle"
    )
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing)
        ),
        label = "inner_angle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val liveAmp by animateFloatAsState(
        targetValue = when (status) {
            JarvisStatus.LISTENING -> amplitude.coerceIn(0.25f, 1f)
            JarvisStatus.SPEAKING -> 0.72f
            JarvisStatus.THINKING -> 0.55f
            else -> 0.2f
        },
        animationSpec = spring(stiffness = 260f),
        label = "live_amp"
    )

    Box(
        modifier = modifier.size(214.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f

            // Ambient dual-tone holographic glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = 0.24f + 0.14f * liveAmp),
                        Color(0xFF8563FF).copy(alpha = 0.10f + 0.08f * liveAmp),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR * pulseScale
                ),
                center = center,
                radius = maxR * pulseScale
            )

            // Ring 1: Outer Orange-Gold Dashed Telemetry Ring
            val r1Inset = 6.dp.toPx()
            val r1Size = Size(size.width - r1Inset * 2, size.height - r1Inset * 2)
            drawArc(
                color = Color(0xFF8563FF).copy(alpha = 0.8f),
                startAngle = outerAngle,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(r1Inset, r1Inset),
                size = r1Size,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                )
            )
            drawArc(
                color = ElectricCyan.copy(alpha = 0.75f),
                startAngle = outerAngle + 180f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(r1Inset, r1Inset),
                size = r1Size,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                )
            )

            // Ring 2: Middle Cyan-Blue Segmented Arcs
            val r2Inset = 18.dp.toPx()
            val r2Size = Size(size.width - r2Inset * 2, size.height - r2Inset * 2)
            for (i in 0 until 4) {
                drawArc(
                    color = if (i % 2 == 0) ElectricCyan else NeonSapphire,
                    startAngle = midAngle + i * 90f,
                    sweepAngle = 56f,
                    useCenter = false,
                    topLeft = Offset(r2Inset, r2Inset),
                    size = r2Size,
                    style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Ring 3: Inner Neon Orange & Cyan Orbit Ring
            val r3Inset = 31.dp.toPx()
            val r3Size = Size(size.width - r3Inset * 2, size.height - r3Inset * 2)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(ElectricCyan, Color(0xFF8563FF), NeonSapphire, ElectricCyan)
                ),
                startAngle = innerAngle,
                sweepAngle = 255f,
                useCenter = false,
                topLeft = Offset(r3Inset, r3Inset),
                size = r3Size,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Holographic tick marks
            val tickOuter = maxR - 2.dp.toPx()
            val tickInner = maxR - 7.dp.toPx()
            for (deg in 0 until 360 step 30) {
                val rad = (deg + outerAngle * 0.25f) * PI / 180.0
                val cos = kotlin.math.cos(rad).toFloat()
                val sin = kotlin.math.sin(rad).toFloat()
                drawLine(
                    color = if (deg % 90 == 0) CyberAmberGlow else ElectricCyan.copy(alpha = 0.45f),
                    start = Offset(center.x + tickInner * cos, center.y + tickInner * sin),
                    end = Offset(center.x + tickOuter * cos, center.y + tickOuter * sin),
                    strokeWidth = if (deg % 90 == 0) 2.dp.toPx() else 1.dp.toPx()
                )
            }
        }

        // Central Large Circular AI Avatar
        Surface(
            modifier = Modifier.size(116.dp),
            shape = CircleShape,
            color = JarvisVoid,
            border = BorderStroke(2.5.dp, cyberDualNeonBrush()),
            shadowElevation = 16.dp
        ) {
            Image(
                painter = painterResource(id = R.drawable.pandu_jarvis_icon_1794946931645),
                contentDescription = stringResource(R.string.logo_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun CyberpunkAudioWaveform(
    status: JarvisStatus,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    JarvisStatus.LISTENING, JarvisStatus.SPEAKING -> 750
                    JarvisStatus.THINKING -> 950
                    else -> 2200
                },
                easing = LinearEasing
            )
        ),
        label = "wave_phase"
    )

    val energyTarget = when (status) {
        JarvisStatus.LISTENING -> (0.45f + amplitude * 0.55f).coerceIn(0.35f, 1f)
        JarvisStatus.SPEAKING -> 0.85f
        JarvisStatus.THINKING -> 0.6f
        else -> 0.26f
    }
    val energy by animateFloatAsState(
        targetValue = energyTarget,
        animationSpec = spring(stiffness = 220f),
        label = "wave_energy"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        val barCount = 29
        val spacing = size.width / (barCount * 1.9f)
        val barWidth = (size.width - spacing * (barCount - 1)) / barCount
        val centerIndex = barCount / 2f
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val distanceFromCenter = kotlin.math.abs(i - centerIndex) / centerIndex
            val envelope = 1f - distanceFromCenter * 0.68f
            val wave = (sin(phase + i * 0.55f) * 0.5f + 0.5f)
            val harmonic = (sin(phase * 1.7f - i * 0.85f) * 0.5f + 0.5f)
            val combined = (wave * 0.65f + harmonic * 0.35f)

            val barHeight = (6.dp.toPx() + (size.height - 10.dp.toPx()) * envelope * energy * (0.3f + 0.7f * combined))
                .coerceAtMost(size.height)

            val t = i.toFloat() / (barCount - 1)
            val barColor = when {
                t < 0.35f -> ElectricCyan
                t < 0.68f -> ArcGlowBlue
                else -> CyberOrange
            }.copy(alpha = 0.45f + 0.5f * energy)

            val x = i * (barWidth + spacing)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun CyberpunkHeroMicButton(
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_mic")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing)
        ),
        label = "ring_rot"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse"
    )

    val amp by animateFloatAsState(
        targetValue = if (isListening) amplitude.coerceIn(0.25f, 1f) else 0.18f,
        animationSpec = spring(stiffness = 280f),
        label = "hero_amp"
    )

    Box(
        modifier = modifier.size(116.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseR = 44.dp.toPx()

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (isListening) ElectricCyan else CyberOrange).copy(alpha = 0.25f * amp + 0.08f),
                        NeonSapphire.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension / 2f * pulse
                ),
                center = center,
                radius = size.minDimension / 2f * pulse
            )

            val inset = 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                brush = cyberDualNeonBrush(reverse = isListening),
                startAngle = ringRotation,
                sweepAngle = if (isListening || isThinking) 240f else 150f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = CyberOrange.copy(alpha = 0.7f),
                startAngle = ringRotation + 195f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            if (isListening) {
                drawCircle(
                    color = ElectricCyan.copy(alpha = 0.45f),
                    radius = baseR + 12.dp.toPx() * amp,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        Surface(
            modifier = Modifier
                .size(82.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .testTag("hero_mic_button"),
            shape = CircleShape,
            color = if (isListening) ElectricCyan else JarvisSurfaceElevated,
            border = BorderStroke(
                width = 2.5.dp,
                brush = if (isListening) {
                    Brush.linearGradient(listOf(IceWhite, CyberOrange))
                } else {
                    cyberDualNeonBrush()
                }
            ),
            shadowElevation = 14.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when {
                        isListening -> Icons.Default.Stop
                        isSpeaking -> Icons.Default.GraphicEq
                        else -> Icons.Default.Mic
                    },
                    contentDescription = if (isListening) {
                        stringResource(R.string.mic_button_stop_cd)
                    } else {
                        stringResource(R.string.mic_button_start_cd)
                    },
                    tint = if (isListening) JarvisVoid else ElectricCyan,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun CyberpunkShortcutDeck(
    onYouTubeClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onAppsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShortcutCard(
            title = stringResource(R.string.shortcut_youtube),
            subtitle = stringResource(R.string.shortcut_youtube_sub),
            icon = Icons.Default.PlayArrow,
            accentColor = CyberOrange,
            borderBrush = Brush.linearGradient(listOf(CyberOrange, ElectricCyan)),
            onClick = onYouTubeClick,
            testTag = "shortcut_youtube",
            modifier = Modifier.weight(1f)
        )
        ShortcutCard(
            title = stringResource(R.string.shortcut_whatsapp),
            subtitle = stringResource(R.string.shortcut_whatsapp_sub),
            icon = Icons.AutoMirrored.Filled.Chat,
            accentColor = HudOnlineGreen,
            borderBrush = Brush.linearGradient(listOf(ElectricCyan, HudOnlineGreen)),
            onClick = onWhatsAppClick,
            testTag = "shortcut_whatsapp",
            modifier = Modifier.weight(1f)
        )
        ShortcutCard(
            title = stringResource(R.string.shortcut_apps),
            subtitle = stringResource(R.string.shortcut_apps_sub),
            icon = Icons.Default.Apps,
            accentColor = ElectricCyan,
            borderBrush = cyberDualNeonBrush(),
            onClick = onAppsClick,
            testTag = "shortcut_apps",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ShortcutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    borderBrush: Brush,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        color = JarvisSurface.copy(alpha = 0.92f),
        border = BorderStroke(1.2.dp, borderBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(1.dp, accentColor.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = IceWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SlateCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CyberpunkBottomNavBar(
    selectedTab: CyberpunkTab,
    onSelectTab: (CyberpunkTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = JarvisDeepSpace.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        border = BorderStroke(1.dp, cyberDualNeonBrush())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CyberpunkTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) ElectricCyan else MutedHudBlue,
                    animationSpec = tween(200),
                    label = "nav_color"
                )
                val pillColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        ElectricCyan.copy(alpha = 0.14f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(200),
                    label = "nav_pill"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(pillColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 1.dp,
                                    brush = cyberDualNeonBrush(),
                                    shape = RoundedCornerShape(14.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable(role = Role.Tab) { onSelectTab(tab) }
                        .padding(vertical = 6.dp)
                        .testTag(tab.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(tab.titleRes),
                        tint = if (isSelected && tab == CyberpunkTab.APPS) CyberOrange else contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(tab.titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) IceWhite else MutedHudBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
