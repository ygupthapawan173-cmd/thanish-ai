package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Mirrored amplitude display; not an FFT. Silence collapses to a resting line. */
@Composable
fun ParticleRibbonWaveform(
    isActive: Boolean,
    audioAmplitude: Float,
    height: Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    val target = if (isActive) audioAmplitude.coerceIn(0f, 1f) else 0f
    val amp by animateFloatAsState(target, tween(if (target > 0.1f) 65 else 220), label = "waveAmplitude")
    val count = if (LocalReducedRendering.current) 15 else 33
    Canvas(modifier.fillMaxWidth().height(height)) {
        val middle = size.height * 0.5f
        val step = size.width / (count + 1)
        for (i in 0 until count) {
            val d = abs(i - (count - 1) / 2f) / ((count - 1) / 2f)
            val envelope = (1f - d).pow(1.6f)
            val h = size.height * 0.43f * amp * envelope
            val x = (i + 1) * step
            val color = Color(0xFFDDF8FF)
            drawLine(color.copy(alpha = 0.2f), Offset(x, middle - h), Offset(x, middle + h),
                strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color, Offset(x, middle - h - 1f), Offset(x, middle + h + 1f),
                strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
        }
        if (amp < 0.01f) drawLine(Color(0xFFDDF8FF).copy(alpha = 0.55f),
            Offset(step, middle), Offset(size.width - step, middle), strokeWidth = 1.dp.toPx())
    }
}
