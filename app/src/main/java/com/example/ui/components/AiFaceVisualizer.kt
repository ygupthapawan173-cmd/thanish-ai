package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ai.AiActivityState
import kotlin.math.*

/** Lightweight projected Fibonacci sphere. No face, no simulated speech. */
@Composable
fun AiFaceVisualizer(
    activityState: AiActivityState,
    audioAmplitude: Float,
    size: Dp = 200.dp,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val reduced = LocalReducedRendering.current
    val count = if (reduced) 140 else 360
    val points = remember(count) {
        FloatArray(count * 3).also { p ->
            val golden = PI * (3.0 - sqrt(5.0))
            for (i in 0 until count) {
                val y = 1.0 - 2.0 * (i + 0.5) / count
                val r = sqrt(1.0 - y * y)
                val phi = i * golden
                p[i * 3] = (cos(phi) * r).toFloat()
                p[i * 3 + 1] = y.toFloat()
                p[i * 3 + 2] = (sin(phi) * r).toFloat()
            }
        }
    }
    val angle = if (reduced) 0f else {
        val inf = rememberInfiniteTransition(label = "globe")
        val rotation by inf.animateFloat(0f, (2 * PI).toFloat(),
            infiniteRepeatable(tween(22000, easing = LinearEasing)), label = "rotation")
        rotation
    }
    val active = activityState == AiActivityState.LISTENING || activityState == AiActivityState.SPEAKING
    val amp by animateFloatAsState(if (active) audioAmplitude.coerceIn(0f, 1f) else 0f,
        tween(100), label = "actualAmplitude")
    val tint = if (activityState == AiActivityState.SPEAKING) Color(0xFFB9DEFF) else Color(0xFFDDF8FF)
    Canvas(modifier.size(size).semantics { contentDescription = "JARVIS orb, ${activityState.label}" }.clickable(role = Role.Button, onClick = onTap)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = minOf(this.size.width, this.size.height) * (0.36f + amp * 0.04f)
        val a = angle
        val ca = cos(a)
        val sa = sin(a)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF78C8FF).copy(alpha = 0.28f), Color.Transparent),
            center, radius * 1.4f), radius * 1.4f, center)
        val halo = radius * 1.18f
        drawCircle(tint.copy(alpha = 0.15f), halo, center, style = Stroke(0.8.dp.toPx()))
        drawCircle(tint.copy(alpha = 0.10f), radius * 1.32f, center, style = Stroke(0.6.dp.toPx()))
        drawArc(tint.copy(alpha = if (active) 0.75f else 0.35f),
            startAngle = a * 180f / PI.toFloat(), sweepAngle = 68f, useCenter = false,
            topLeft = Offset(center.x-halo, center.y-halo), size = Size(halo*2,halo*2),
            style = Stroke(1.4.dp.toPx()))
        for (i in 0 until count) {
            val x = points[i * 3]
            val y = points[i * 3 + 1]
            val z = points[i * 3 + 2]
            val rx = x * ca + z * sa
            val rz = -x * sa + z * ca
            val depth = (rz + 1f) * 0.5f
            val perspective = 1f / (1f - rz * 0.16f)
            drawCircle(tint.copy(alpha = 0.18f + depth * 0.82f),
                (0.7f + depth * 1.0f + amp * 0.55f).dp.toPx(),
                Offset(center.x + rx * radius * perspective, center.y + y * radius * perspective))
        }
    }
}
