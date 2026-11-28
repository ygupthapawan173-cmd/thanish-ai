package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.OmegaColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Live Neon Blue & Cyan Voice Waveform (Sections 2.25 & 12):
 * Reuses pre-allocated `Path` buffers across frames for smooth 60fps performance.
 */
@Composable
fun ParticleRibbonWaveform(
    isActive: Boolean,
    audioAmplitude: Float,
    height: Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "ribbonWave")
    val smoothAmp by animateFloatAsState(
        targetValue = if (isActive) (0.28f + audioAmplitude.coerceIn(0f, 1f) * 0.72f) else 0.14f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rAmp"
    )
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(if (isActive) 2200 else 6000, easing = LinearEasing)),
        label = "rPhase"
    )

    val c1 = OmegaColors.NeonCyan
    val c2 = Color(0xFF38BDF8)
    val c3 = OmegaColors.NeonBlue
    val reduced = LocalReducedRendering.current
    val strands = if (reduced) 4 else 9
    val points = if (reduced) 24 else 52
    val paths = remember(strands) { List(strands) { Path() } }

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = this.size.width
        val h = this.size.height
        val cy = h / 2f

        for (s in 0 until strands) {
            val t = s.toFloat() / (strands - 1).coerceAtLeast(1)
            val strandColor = if (t < 0.5f) lerp(c1, c2, t * 2f) else lerp(c2, c3, (t - 0.5f) * 2f)
            val path = paths[s]
            path.reset()

            val freq = 2.1f + t * 0.5f
            val shift = phase + t * 1.4f
            val maxY = h * 0.42f * smoothAmp

            for (p in 0..points) {
                val u = p.toFloat() / points
                val x = u * w
                val env = sin(u * PI).toFloat()
                val y = cy + (
                    sin(u * freq * 2f * PI.toFloat() + shift) * 0.72f +
                        sin(u * 4.4f * PI.toFloat() - shift * 1.3f) * 0.28f
                    ) * maxY * env + (t - 0.5f) * 10.dp.toPx() * env

                if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)

                if (isActive && p % 4 == 0 && s % 2 == 0) {
                    drawCircle(
                        color = strandColor.copy(alpha = 0.5f * env),
                        radius = 1.2.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            drawPath(
                path = path,
                color = strandColor.copy(alpha = 0.36f + 0.38f * (1f - abs(t - 0.5f) * 2f)),
                style = Stroke(width = 1.25.dp.toPx())
            )
        }
    }
}
