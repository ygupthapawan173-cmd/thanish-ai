package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.example.ui.JarvisStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Visual-only extraction from Singularity v2. No network, tokens, actions or APK installer.
 * Host provides actual mic amplitude and assistant state; no dummy "connected" flag. */
@Composable
fun OmegaSafeSingularityVisual(
    status: JarvisStatus,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val cyan = Color(0xFF54E6FF)
    val blue = Color(0xFF1E88E5)
    val violet = Color(0xFF9D6BFF)
    val level = if (status == JarvisStatus.LISTENING) amplitude.coerceIn(0f, 1f) else 0f
    val animation = rememberInfiniteTransition(label = "omega_safe_hud")
    val rotation by animation.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "rotation"
    )
    val pulse by animation.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(modifier = modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(196.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.34f * pulse + level * 14f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyan.copy(alpha = 0.25f),
                        violet.copy(alpha = 0.1f), Color.Transparent
                    ),
                    center = center, radius = baseRadius * 1.5f
                ),
                radius = baseRadius * 1.5f, center = center
            )
            for (segment in 0 until 12) {
                val start = rotation + segment * 30f
                drawArc(
                    color = if (segment % 2 == 0) cyan else blue,
                    startAngle = start, sweepAngle = 18f, useCenter = false,
                    topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                    size = Size(baseRadius * 2, baseRadius * 2),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            val inner = baseRadius * .74f
            drawArc(
                color = violet, startAngle = -rotation * 1.5f, sweepAngle = 240f,
                useCenter = false, topLeft = Offset(center.x - inner, center.y - inner),
                size = Size(inner * 2, inner * 2),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            val spokes = baseRadius * 1.12f
            for (i in 0 until 24) {
                val theta = (i * 15.0 + rotation * .5) * PI / 180.0
                val length = 6.dp.toPx() + level * 24.dp.toPx() * ((i % 3) + 1) / 3f
                val start = Offset(
                    center.x + (spokes * cos(theta)).toFloat(),
                    center.y + (spokes * sin(theta)).toFloat()
                )
                val end = Offset(
                    center.x + ((spokes + length) * cos(theta)).toFloat(),
                    center.y + ((spokes + length) * sin(theta)).toFloat()
                )
                drawLine(cyan.copy(alpha = .78f), start, end, strokeWidth = 2.dp.toPx())
            }
        }
        Text(text = "Ω", color = cyan, style = MaterialTheme.typography.displayLarge)
    }
}
