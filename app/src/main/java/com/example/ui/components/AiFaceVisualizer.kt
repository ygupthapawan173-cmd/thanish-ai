package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ai.AiActivityState
import com.example.ui.theme.OmegaColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Lightweight 2D Animated AI Face Visualizer (Sections 12 & 14):
 * - Replaces legacy heavy 3D Orbs cleanly.
 * - Distinct real-time states: Standby, Listening, Thinking, Searching, Speaking, Executing.
 * - Dark Navy / Neon Blue / Cyan aesthetic with zero per-frame allocations.
 */
@Composable
fun AiFaceVisualizer(
    activityState: AiActivityState,
    audioAmplitude: Float,
    size: Dp = 200.dp,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val reduced = LocalReducedRendering.current
    val inf = rememberInfiniteTransition(label = "jarvisFace")

    val smoothAmp by animateFloatAsState(
        targetValue = audioAmplitude.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "faceAmp"
    )
    val rotation by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when (activityState) {
                    AiActivityState.SEARCHING -> 2400
                    AiActivityState.THINKING -> 3400
                    AiActivityState.EXECUTING -> 2000
                    else -> 8000
                },
                easing = LinearEasing
            )
        ),
        label = "ringRot"
    )
    val pulse by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "facePulse"
    )

    val primaryGlow = when (activityState) {
        AiActivityState.LISTENING -> OmegaColors.NeonCyan
        AiActivityState.SEARCHING -> Color(0xFF38BDF8)
        AiActivityState.THINKING -> OmegaColors.NeonBlue
        AiActivityState.SPEAKING -> OmegaColors.NeonCyan
        AiActivityState.EXECUTING -> OmegaColors.EmeraldLive
        AiActivityState.STANDBY -> OmegaColors.NeonBlue.copy(alpha = 0.85f)
    }

    Canvas(
        modifier = modifier
            .size(size)
            .clickable(onClick = onTap)
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val baseR = minOf(w, h) * 0.42f

        // Subtle outer halo ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryGlow.copy(alpha = 0.22f + smoothAmp * 0.18f), Color.Transparent),
                center = Offset(cx, cy),
                radius = baseR * 1.25f
            ),
            radius = baseR * 1.25f,
            center = Offset(cx, cy)
        )

        // Outer segmented telemetry arc
        rotate(degrees = if (reduced) 0f else rotation, pivot = Offset(cx, cy)) {
            drawArc(
                color = primaryGlow.copy(alpha = 0.85f),
                startAngle = 0f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - baseR, cy - baseR),
                size = Size(baseR * 2f, baseR * 2f),
                style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = OmegaColors.NeonCyan.copy(alpha = 0.65f),
                startAngle = 180f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = Offset(cx - baseR, cy - baseR),
                size = Size(baseR * 2f, baseR * 2f),
                style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Inner circular visor
        val innerR = baseR * 0.84f
        drawCircle(
            color = OmegaColors.SurfaceNavy,
            radius = innerR,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = OmegaColors.BorderBlue,
            radius = innerR,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Eyes geometry adapting to AI state
        val eyeOffsetX = innerR * 0.34f
        val eyeY = cy - innerR * 0.14f
        val blinkFactor = if (activityState == AiActivityState.STANDBY && pulse > 0.94f) 0.22f else 1f
        val eyeWidth = innerR * 0.28f
        val eyeHeight = when (activityState) {
            AiActivityState.THINKING -> innerR * 0.14f
            AiActivityState.SEARCHING -> innerR * 0.22f
            AiActivityState.EXECUTING -> innerR * 0.12f
            else -> (innerR * 0.22f + smoothAmp * innerR * 0.08f) * blinkFactor
        }

        val scanShift = if (activityState == AiActivityState.SEARCHING && !reduced) {
            sin(pulse * PI.toFloat() * 2f) * (innerR * 0.08f)
        } else 0f

        drawRoundRect(
            color = OmegaColors.NeonCyan,
            topLeft = Offset(cx - eyeOffsetX - eyeWidth / 2f + scanShift, eyeY - eyeHeight / 2f),
            size = Size(eyeWidth, eyeHeight.coerceAtLeast(3.dp.toPx())),
            cornerRadius = CornerRadius(eyeHeight, eyeHeight)
        )
        drawRoundRect(
            color = OmegaColors.NeonCyan,
            topLeft = Offset(cx + eyeOffsetX - eyeWidth / 2f + scanShift, eyeY - eyeHeight / 2f),
            size = Size(eyeWidth, eyeHeight.coerceAtLeast(3.dp.toPx())),
            cornerRadius = CornerRadius(eyeHeight, eyeHeight)
        )

        // Voice / Mouth equalizer bars inside 2D visor
        val barCount = 7
        val totalBarSpan = innerR * 0.88f
        val barStep = totalBarSpan / (barCount - 1)
        val mouthY = cy + innerR * 0.30f
        for (i in 0 until barCount) {
            val distFromCenter = 1f - abs(i - (barCount - 1) / 2f) / (barCount / 2f)
            val dynamic = when (activityState) {
                AiActivityState.LISTENING, AiActivityState.SPEAKING ->
                    (0.22f + smoothAmp * 0.78f * (0.55f + 0.45f * sin(pulse * PI.toFloat() * 2f + i * 0.9f))) * distFromCenter
                AiActivityState.THINKING, AiActivityState.SEARCHING ->
                    (0.20f + 0.35f * (0.5f + 0.5f * sin(pulse * PI.toFloat() * 2f + i * 0.8f))) * distFromCenter
                AiActivityState.EXECUTING -> 0.45f * distFromCenter
                AiActivityState.STANDBY -> 0.14f * distFromCenter
            }
            val barH = (innerR * 0.36f * dynamic).coerceAtLeast(3.dp.toPx())
            val bx = (cx - totalBarSpan / 2f) + i * barStep
            drawLine(
                color = primaryGlow,
                start = Offset(bx, mouthY - barH / 2f),
                end = Offset(bx, mouthY + barH / 2f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
