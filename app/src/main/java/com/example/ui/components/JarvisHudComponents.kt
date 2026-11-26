package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.JarvisStatus
import com.example.ui.theme.ArcGlowBlue
import com.example.ui.theme.CyberOrange
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudAlertContainer
import com.example.ui.theme.HudAlertRed
import com.example.ui.theme.HudOnlineGreen
import com.example.ui.theme.HudWarningAmber
import com.example.ui.theme.IceWhite
import com.example.ui.theme.JarvisDeepSpace
import com.example.ui.theme.JarvisNavyMid
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisVoid
import com.example.ui.theme.MutedHudBlue
import com.example.ui.theme.NeonSapphire
import com.example.ui.theme.SlateCyan

@Composable
fun JarvisAtmosphericBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(JarvisVoid, JarvisDeepSpace, JarvisNavyMid, JarvisVoid)
            )
        )

        // Top radial cyan-blue core glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricCyan.copy(alpha = 0.16f),
                    NeonSapphire.copy(alpha = 0.07f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.35f, size.height * 0.18f),
                radius = size.maxDimension * 0.50f
            ),
            center = Offset(size.width * 0.35f, size.height * 0.18f),
            radius = size.maxDimension * 0.50f
        )

        // Secondary cyberpunk neon orange ambient glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CyberOrange.copy(alpha = 0.11f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f, size.height * 0.42f),
                radius = size.maxDimension * 0.38f
            ),
            center = Offset(size.width * 0.85f, size.height * 0.42f),
            radius = size.maxDimension * 0.38f
        )

        // Bottom dock ambient glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonSapphire.copy(alpha = 0.18f),
                    CyberOrange.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, size.height * 0.94f),
                radius = size.maxDimension * 0.44f
            ),
            center = Offset(size.width * 0.5f, size.height * 0.94f),
            radius = size.maxDimension * 0.44f
        )

        // Subtle futuristic HUD grid lines
        val gridSpacing = 40.dp.toPx()
        val lineColor = ElectricCyan.copy(alpha = 0.04f)
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            x += gridSpacing
        }
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += gridSpacing
        }
    }
}

@Composable
fun JarvisTopBar(
    status: JarvisStatus,
    speechLocaleLabel: String,
    ttsPresetLabel: String,
    autoSpeakEnabled: Boolean,
    hasMessages: Boolean,
    onToggleLocale: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onRequestClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hud_status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val (statusLabel, statusColor) = when (status) {
        JarvisStatus.ONLINE -> stringResource(R.string.status_online) to HudOnlineGreen
        JarvisStatus.LISTENING -> stringResource(R.string.status_listening) to ElectricCyan
        JarvisStatus.THINKING -> stringResource(R.string.status_thinking) to ArcGlowBlue
        JarvisStatus.SPEAKING -> stringResource(R.string.status_speaking) to HudOnlineGreen
        JarvisStatus.ALERT -> stringResource(R.string.status_error) to HudAlertRed
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = JarvisSurface.copy(alpha = 0.90f),
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        border = BorderStroke(1.2.dp, cyberDualNeonBrush())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, ElectricCyan.copy(alpha = 0.75f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Ω", style = MaterialTheme.typography.headlineMedium, color = ElectricCyan)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.jarvis_header_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = IceWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.jarvis_header_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onOpenHelp,
                    modifier = Modifier.testTag("help_examples_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = stringResource(R.string.help_examples_cd),
                        tint = ElectricCyan
                    )
                }

                IconButton(
                    onClick = onToggleAutoSpeak,
                    modifier = Modifier.testTag("auto_speak_toggle_button")
                ) {
                    Icon(
                        imageVector = if (autoSpeakEnabled) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = if (autoSpeakEnabled) {
                            stringResource(R.string.voice_toggle_on_cd)
                        } else {
                            stringResource(R.string.voice_toggle_off_cd)
                        },
                        tint = if (autoSpeakEnabled) ElectricCyan else MutedHudBlue
                    )
                }

                IconButton(
                    onClick = onOpenTtsSettings,
                    modifier = Modifier.testTag("tts_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.tts_settings_cd),
                        tint = ElectricCyan
                    )
                }

                IconButton(
                    onClick = onRequestClearChat,
                    enabled = hasMessages,
                    modifier = Modifier.testTag("clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.clear_chat_cd),
                        tint = if (hasMessages) HudAlertRed.copy(alpha = 0.9f) else MutedHudBlue.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Telemetry status row with speech input language toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = pulseAlpha))
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "TTS: te-IN • $ttsPresetLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan.copy(alpha = 0.78f)
                )
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            role = Role.Button,
                            onClick = onToggleLocale
                        )
                        .testTag("locale_toggle_button"),
                    shape = RoundedCornerShape(50),
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = speechLocaleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarvisArcCoreHero(
    onStarterPromptClick: (String) -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_core")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing)
        ),
        label = "outer_angle"
    )
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing)
        ),
        label = "inner_angle"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    val starterPrompts = listOf(
        stringResource(R.string.starter_prompt_joke),
        stringResource(R.string.starter_prompt_motivation),
        stringResource(R.string.starter_prompt_tech),
        stringResource(R.string.starter_prompt_food)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(164.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f

                // Outer radial halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.25f),
                            NeonSapphire.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius * glowScale
                    ),
                    center = center,
                    radius = maxRadius * glowScale
                )

                // Segmented outer HUD arc
                val outerInset = 8.dp.toPx()
                val outerRectSize = Size(size.width - outerInset * 2, size.height - outerInset * 2)
                for (i in 0 until 4) {
                    drawArc(
                        color = ElectricCyan.copy(alpha = 0.7f),
                        startAngle = outerAngle + i * 90f,
                        sweepAngle = 52f,
                        useCenter = false,
                        topLeft = Offset(outerInset, outerInset),
                        size = outerRectSize,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Counter-rotating dashed inner ring
                val innerInset = 20.dp.toPx()
                val innerRectSize = Size(size.width - innerInset * 2, size.height - innerInset * 2)
                drawArc(
                    color = NeonSapphire.copy(alpha = 0.85f),
                    startAngle = innerAngle,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(innerInset, innerInset),
                    size = innerRectSize,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    )
                )
            }

            Surface(
                modifier = Modifier.size(92.dp),
                shape = CircleShape,
                color = JarvisVoid,
                border = BorderStroke(2.dp, ElectricCyan.copy(alpha = 0.8f)),
                shadowElevation = 12.dp
            ) {
                Text("Ω", style = MaterialTheme.typography.headlineMedium, color = ElectricCyan)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.titleMedium,
            color = IceWhite,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = SlateCyan,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

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
                    color = JarvisSurfaceElevated.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.42f))
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

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = onOpenHelp,
            border = BorderStroke(1.dp, NeonSapphire.copy(alpha = 0.75f)),
            modifier = Modifier.testTag("hero_help_examples_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = ElectricCyan,
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

@Composable
fun JarvisMicOrb(
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_orb")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing)
        ),
        label = "spin"
    )
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    val animatedAmp by animateFloatAsState(
        targetValue = if (isListening) amplitude else 0.15f,
        animationSpec = spring(stiffness = 300f),
        label = "amp"
    )

    val ringColor = when {
        isListening -> ElectricCyan
        isThinking -> ArcGlowBlue
        isSpeaking -> HudOnlineGreen
        else -> NeonSapphire
    }

    Box(
        modifier = modifier.size(76.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = 28.dp.toPx()

            if (isListening) {
                val outerWave = baseRadius + 10.dp.toPx() * animatedAmp + 4.dp.toPx()
                val midWave = baseRadius + 6.dp.toPx() * animatedAmp
                drawCircle(
                    color = ElectricCyan.copy(alpha = 0.22f * animatedAmp),
                    radius = outerWave,
                    center = center
                )
                drawCircle(
                    color = ElectricCyan.copy(alpha = 0.45f),
                    radius = midWave,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            } else if (isThinking) {
                val inset = 4.dp.toPx()
                drawArc(
                    color = ArcGlowBlue,
                    startAngle = spinAngle,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = ElectricCyan.copy(alpha = 0.6f),
                    startAngle = spinAngle + 180f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                drawCircle(
                    color = ringColor.copy(alpha = 0.22f),
                    radius = baseRadius * idlePulse + 4.dp.toPx(),
                    center = center
                )
            }
        }

        Surface(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .clickable(
                    role = Role.Button,
                    onClick = onClick
                )
                .testTag("mic_button"),
            shape = CircleShape,
            color = if (isListening) ElectricCyan else JarvisSurfaceElevated,
            border = BorderStroke(
                width = 2.dp,
                color = if (isListening) IceWhite else ringColor
            ),
            shadowElevation = 8.dp
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
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun LiveVoiceTranscriptChip(
    partialText: String,
    localeLabel: String,
    hintText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = JarvisSurfaceElevated.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LISTENING [$localeLabel]",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                Text(
                    text = partialText.ifBlank { hintText },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (partialText.isBlank()) SlateCyan else IceWhite
                )
            }
        }
    }
}

@Composable
fun JarvisAlertBanner(
    visible: Boolean,
    title: String?,
    message: String,
    isPermissionBanner: Boolean,
    permanentlyDenied: Boolean,
    canRetry: Boolean,
    onPrimaryAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("error_banner"),
            shape = RoundedCornerShape(16.dp),
            color = if (isPermissionBanner) JarvisSurfaceElevated else HudAlertContainer,
            border = BorderStroke(
                1.dp,
                if (isPermissionBanner) HudWarningAmber.copy(alpha = 0.7f) else HudAlertRed.copy(alpha = 0.75f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = if (isPermissionBanner) HudWarningAmber else HudAlertRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title ?: stringResource(R.string.status_error),
                        style = MaterialTheme.typography.labelLarge,
                        color = IceWhite,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dismiss_error_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.dismiss_button),
                            tint = SlateCyan
                        )
                    }
                }

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IceWhite.copy(alpha = 0.92f)
                )

                if (isPermissionBanner || canRetry) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPermissionBanner) {
                            Button(
                                onClick = onPrimaryAction,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = JarvisVoid
                                ),
                                modifier = Modifier.testTag("permission_action_button")
                            ) {
                                Icon(
                                    imageVector = if (permanentlyDenied) Icons.Default.Settings else Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(
                                        if (permanentlyDenied) R.string.permission_settings_button
                                        else R.string.permission_grant_button
                                    ),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        } else if (canRetry) {
                            OutlinedButton(
                                onClick = onPrimaryAction,
                                border = BorderStroke(1.dp, ElectricCyan),
                                modifier = Modifier.testTag("retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.retry_button),
                                    color = ElectricCyan,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
