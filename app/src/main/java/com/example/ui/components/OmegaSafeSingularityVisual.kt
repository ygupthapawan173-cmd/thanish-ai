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

/** Animated layered 2D plasma visualization; not a 3D engine or a fake microphone meter. */
@Composable
fun OmegaSafeSingularityVisual(
    status: JarvisStatus,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val cyan = Color(0xFF69F5FF)
    val violet = Color(0xFFAB6AFF)
    val deepBlue = Color(0xFF1068DA)
    val level = if (status == JarvisStatus.LISTENING) amplitude.coerceIn(0f, 1f) else 0f
    val motion = rememberInfiniteTransition(label="r104_plasma_motion")
    val outerRotation by motion.animateFloat(0f, 360f,
        infiniteRepeatable(tween(10500, easing=LinearEasing)),label="outer_rotation")
    val counterRotation by motion.animateFloat(360f, 0f,
        infiniteRepeatable(tween(7600, easing=LinearEasing)),label="counter_rotation")
    val breathe by motion.animateFloat(.93f, 1.065f,
        infiniteRepeatable(tween(2200, easing=FastOutSlowInEasing),RepeatMode.Reverse),
        label="plasma_breathe")
    Box(modifier=modifier.size(236.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(230.dp)) {
            val c=Offset(size.width*.5f,size.height*.5f)
            val baseRadius=size.minDimension*.265f*breathe+level*13.dp.toPx()
            val haloRadius=baseRadius*1.65f

            drawCircle(brush=Brush.radialGradient(
                colors=listOf(violet.copy(alpha=.21f), deepBlue.copy(alpha=.17f),
                    cyan.copy(alpha=.08f),Color.Transparent),
                center=c,radius=haloRadius),
                radius=haloRadius,center=c)

            // Iridescent shaded core: light from top-left + dark edge gives a dimensional sphere.
            drawCircle(brush=Brush.radialGradient(
                colors=listOf(Color(0xFF8EFAFF).copy(alpha=.94f),
                    Color(0xFF0B71C6),Color(0xFF10265E),Color(0xFF030B22)),
                center=Offset(c.x-baseRadius*.32f,c.y-baseRadius*.33f),
                radius=baseRadius*1.7f),
                radius=baseRadius,center=c)
            drawCircle(brush=Brush.radialGradient(
                colors=listOf(violet.copy(alpha=.38f),
                    deepBlue.copy(alpha=.15f),Color.Transparent),
                center=Offset(c.x+baseRadius*.32f,c.y+baseRadius*.32f),
                radius=baseRadius*.95f),
                center=c,radius=baseRadius)
            drawCircle(brush=Brush.radialGradient(
                colors=listOf(Color.White.copy(alpha=.49f),
                    cyan.copy(alpha=.08f),Color.Transparent),
                center=Offset(c.x-baseRadius*.38f,c.y-baseRadius*.4f),
                radius=baseRadius*.5f),
                center=Offset(c.x-baseRadius*.38f,c.y-baseRadius*.4f),
                radius=baseRadius*.5f)

            // Contra-rotating orbital segmented arcs with gentle depth illusion.
            for(i in 0 until 12) {
                val a=outerRotation+i*30f
                val r=baseRadius*1.32f
                drawArc(
                    color=if(i%3==0) violet else if(i%2==0) cyan else deepBlue,
                    startAngle=a,sweepAngle=16f,useCenter=false,
                    topLeft=Offset(c.x-r,c.y-r),size=Size(2f*r,2f*r),
                    style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))
            }
            for(i in 0 until 6) {
                val r=baseRadius*1.12f
                drawArc(
                    color=(if(i%2==0) violet else cyan).copy(alpha=.75f),
                    startAngle=counterRotation+i*60f,sweepAngle=39f,useCenter=false,
                    topLeft=Offset(c.x-r,c.y-r),size=Size(r*2f,r*2f),
                    style=Stroke(1.8.dp.toPx(),cap=StrokeCap.Round))
            }
            // Live 24-spoke voice ring, intensity follows actual microphone amplitude.
            for(i in 0 until 24) {
                val radians=(i*15.0+outerRotation*.32)*PI/180.0
                val radial=baseRadius*1.6f
                val dynamic=level*(4f+(i%4)*3f)*density
                val length=(4.dp.toPx()+dynamic)
                val p1=Offset(c.x+(radial*cos(radians)).toFloat(),
                    c.y+(radial*sin(radians)).toFloat())
                val p2=Offset(c.x+((radial+length)*cos(radians)).toFloat(),
                    c.y+((radial+length)*sin(radians)).toFloat())
                drawLine(cyan.copy(alpha=if(i%3==0) 1f else .55f),
                    start=p1,end=p2,strokeWidth=2.dp.toPx(),
                    cap=StrokeCap.Round)
            }
            // Discrete moving light particles; pure visual, never a network/AI status.
            for(i in 0 until 16) {
                val rad=(i*22.5+counterRotation*.8)*PI/180.0
                val pr=baseRadius*(if(i%2==0) 1.87f else 1.76f)
                val pc=Offset(c.x+(pr*cos(rad)).toFloat(),
                    c.y+(pr*sin(rad)).toFloat())
                drawCircle(color=if(i%4==0) violet else cyan,
                    radius=(if(i%3==0) 1.7f else 1.05f).dp.toPx(),
                    center=pc,alpha=if(i%3==0) .8f else .44f)
            }
        }
        Text("Ω",color=Color(0xFFCBFAFF),
            style=MaterialTheme.typography.displayMedium)
    }
}
