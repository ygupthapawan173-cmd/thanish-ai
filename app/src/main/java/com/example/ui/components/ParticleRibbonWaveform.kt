package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.OrbColorPreset
import kotlin.math.PI
import kotlin.math.sin

/**
 * Flowing 3D Silk-Particle Ribbon Waveform (matches Image 1 right screen).
 */
@Composable
fun ParticleRibbonWaveform(
    isActive: Boolean,
    audioAmplitude: Float,
    preset: OrbColorPreset,
    height: Dp = 170.dp,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "ribbonWave")
    val smoothAmp by animateFloatAsState(
        targetValue = if (isActive) (0.28f + audioAmplitude.coerceIn(0f, 1f) * 0.72f) else 0.16f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rAmp"
    )
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(if (isActive) 2800 else 6500, easing = LinearEasing)),
        label = "rPhase"
    )

    val c1 = Color(preset.accentHex)
    val c2 = Color(preset.secondaryHex)
    val c3 = Color(preset.primaryHex)

    val reduced = LocalReducedRendering.current
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = this.size.width
        val h = this.size.height
        val cy = h / 2f
        val strands = if (reduced) 6 else 15
        val points = if (reduced) 30 else 68

        for (s in 0 until strands) {
            val t = s.toFloat() / (strands - 1)
            val strandColor = if (t < 0.5f) lerp(c1, c2, t * 2f) else lerp(c2, c3, (t - 0.5f) * 2f)
            val path = Path()
            val freq = 2.2f + t * 0.55f
            val shift = phase + t * 1.45f
            val maxY = h * 0.38f * smoothAmp

            for (p in 0..points) {
                val u = p.toFloat() / points
                val x = u * w
                // Envelope fades smoothly at left & right edges
                val env = sin(u * PI).toFloat()
                val y = cy + (
                    sin(u * freq * 2f * PI.toFloat() + shift) * 0.70f +
                        sin(u * 4.5f * PI.toFloat() - shift * 1.3f) * 0.30f
                    ) * maxY * env + (t - 0.5f) * 18.dp.toPx() * env

                if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)

                // Subtle luminous dots along crests
                if (p % 3 == 0 && s % 2 == 0) {
                    drawCircle(
                        color = strandColor.copy(alpha = 0.55f * env),
                        radius = 1.15.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            drawPath(
                path = path,
                color = strandColor.copy(alpha = 0.34f + 0.32f * (1f - kotlin.math.abs(t - 0.5f) * 2f)),
                style = Stroke(width = 1.1.dp.toPx())
            )
        }
    }
}
