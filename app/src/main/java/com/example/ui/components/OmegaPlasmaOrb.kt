package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.JarvisStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Native animated liquid-plasma look; 2D Compose Canvas, not a 3D model.
 * Audio RMS drives the listening motion instead of a simulated mic level. */
@Composable
fun OmegaPlasmaOrb(status: JarvisStatus, amplitude: Float, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "omega_plasma")
    val phase by infinite.animateFloat(
        initialValue = 0f, targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "plasma_phase"
    )
    val intensity by animateFloatAsState(
        targetValue = when (status) {
            JarvisStatus.LISTENING -> amplitude.coerceIn(0.12f, 1f)
            JarvisStatus.THINKING -> 0.5f
            JarvisStatus.SPEAKING -> 0.76f
            JarvisStatus.ALERT -> 0.35f
            else -> 0.25f
        }, label = "plasma_intensity"
    )
    Box(modifier = modifier.size(228.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(228.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * .325f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFF00EAFF).copy(alpha = .18f + intensity * .12f),
                        Color(0xFF7858FF).copy(alpha = .18f),
                        Color.Transparent
                    ), center = center, radius = size.minDimension * .52f
                ),
                center = center, radius = size.minDimension * .52f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF104A75), Color(0xFF080C27), Color(0xFF120E32)),
                    center = center, radius = radius
                ), center = center, radius = radius
            )
            for (band in 0 until 7) {
                val path = Path()
                val shift = band * .33
                for (step in 0..180) {
                    val theta = 2.0 * PI * step / 180.0
                    val wobble = sin(theta * (3 + band % 3) + phase + shift) *
                        (0.045 + intensity * .04)
                    val r = radius * (1.0 + wobble) -
                        band * 1.1.dp.toPx() + 5 * sin(theta * 2 + phase * .7 + shift)
                    val x = center.x + r.toFloat() * cos(theta + phase * .025).toFloat()
                    val y = center.y + r.toFloat() * sin(theta + phase * .025).toFloat()
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(
                    path = path,
                    color = (if (band % 2 == 0) Color(0xFF00E5FF) else Color(0xFF9C65FF))
                        .copy(alpha = .37f + .07f * (band % 3)),
                    style = Stroke(width = (2.1f + intensity * 1.7f).dp.toPx(),
                        cap = StrokeCap.Round)
                )
            }
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFF00E5FF), Color(0xFF8E61FF),
                        Color(0xFF00DFFF), Color(0xFFB366FF), Color(0xFF00E5FF))
                ),
                center = center, radius = radius * 1.065f,
                style = Stroke(width = (2.8f + intensity * 2f).dp.toPx())
            )
        }
        Text("Ω", color = Color(0xFF5BF2FF),
            style = MaterialTheme.typography.displayLarge)
    }
}
