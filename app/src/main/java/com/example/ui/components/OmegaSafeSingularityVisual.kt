package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.JarvisStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** R103 visual-only: no network, fake statuses, or automatic phone actions. */
@Composable
fun OmegaSafeSingularityVisual(status: JarvisStatus, amplitude: Float, modifier: Modifier = Modifier) {
    val cyan = Color(0xFF54E6FF)
    val violet = Color(0xFF9D6BFF)
    val blue = Color(0xFF1E88E5)
    val level = if (status == JarvisStatus.LISTENING) amplitude.coerceIn(0f, 1f) else 0f
    val animation = rememberInfiniteTransition(label = "omega_r103_orb")
    val rotation by animation.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "rotation")
    val pulse by animation.animateFloat(.92f, 1.08f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(modifier = modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(196.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * .34f * pulse + level * 14f
            drawCircle(brush = Brush.radialGradient(listOf(cyan.copy(alpha=.25f), violet.copy(alpha=.1f), Color.Transparent), center=center, radius=radius*1.5f),
                radius = radius * 1.5f, center = center)
            for (i in 0 until 12) {
                drawArc(if (i%2==0) cyan else blue, rotation + i*30f, 18f, false,
                    topLeft=Offset(center.x-radius,center.y-radius), size=Size(radius*2,radius*2),
                    style=Stroke(3.5.dp.toPx(), cap=StrokeCap.Round))
            }
            val inner = radius*.74f
            drawArc(violet,-rotation*1.5f,240f,false,topLeft=Offset(center.x-inner,center.y-inner),
                size=Size(inner*2,inner*2),style=Stroke(2.5.dp.toPx(),cap=StrokeCap.Round))
            for (i in 0 until 24) {
                val a = (i*15.0+rotation*.5) * PI/180.0
                val r = radius * 1.12f
                val n = 6.dp.toPx() + level*24.dp.toPx()*((i%3)+1)/3f
                val start = Offset(center.x+(r*cos(a)).toFloat(),center.y+(r*sin(a)).toFloat())
                val end = Offset(center.x+((r+n)*cos(a)).toFloat(),center.y+((r+n)*sin(a)).toFloat())
                drawLine(cyan.copy(alpha=.78f),start,end,strokeWidth=2.dp.toPx())
            }
        }
        Text("Ω", color=cyan, style=MaterialTheme.typography.displayLarge)
    }
}
