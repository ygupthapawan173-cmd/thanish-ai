package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voice.AudioSpectrumBus
import kotlinx.coroutines.delay
import kotlin.math.*

/** Mirrored FFT frequency bars. No fabricated audio if a source has no PCM/FFT access. */
@Composable
fun ParticleRibbonWaveform(
    isActive: Boolean,
    audioAmplitude: Float,
    height: Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    val frame by AudioSpectrumBus.frame.collectAsState()
    var stale by remember { mutableStateOf(true) }
    LaunchedEffect(frame.time, isActive) {
        stale = !isActive || frame.time == 0L || android.os.SystemClock.elapsedRealtime() - frame.time > 250L
        if (!stale) { delay(250L); stale = true }
    }
    val count = if (LocalReducedRendering.current) 15 else 31
    val levels = (0 until count).map { i ->
        val band = (abs(i - count / 2) * 15 / (count / 2)).coerceIn(0,15)
        val target = if (!stale && isActive) frame.bands.getOrElse(band) { 0f } else 0f
        val value by animateFloatAsState(target, tween(if (target > 0.1f) 65 else 220), label = "band" + i)
        value
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val middle = size.height * 0.5f
        val step = size.width / (count + 1)
        val color = if (frame.playback) Color(0xFFB9DEFF) else Color(0xFFDDF8FF)
        for (i in 0 until count) {
            val d = abs(i - (count - 1) / 2f) / ((count - 1) / 2f)
            val h = size.height * 0.43f * levels[i] * (1f - 0.25f*d)
            val x = (i+1)*step
            drawLine(color.copy(alpha = 0.18f), Offset(x,middle-h), Offset(x,middle+h), 5.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(x,middle-h-1f), Offset(x,middle+h+1f), 1.8.dp.toPx(), StrokeCap.Round)
        }
        if (levels.all { it < 0.01f }) drawLine(color.copy(alpha = 0.55f), Offset(step,middle), Offset(size.width-step,middle), 1.dp.toPx())
    }
}
