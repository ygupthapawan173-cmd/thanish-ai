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

/** Perspective-projected spherical lattice and shaded core, rendered in Compose Canvas. */
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
    Box(modifier=modifier.size(216.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(216.dp)) {
            val c=Offset(size.width*.5f,size.height*.5f)
            val baseRadius=size.minDimension*.285f*breathe+level*7.dp.toPx()
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

            // Project actual rotating 3D unit-sphere coordinates onto the Canvas.
            val rotation=outerRotation*PI/180.0
            val tilt=.38
            fun point(lat: Double, lon: Double): Pair<Offset, Double> {
                val x=cos(lat)*cos(lon+rotation)
                val y=sin(lat)
                val z=cos(lat)*sin(lon+rotation)
                val yy=y*cos(tilt)-z*sin(tilt)
                val zz=y*sin(tilt)+z*cos(tilt)
                val perspective=3.5/(3.5-zz)
                return Offset(c.x+(x*perspective*baseRadius*.88).toFloat(),
                    c.y+(yy*perspective*baseRadius*.88).toFloat()) to zz
            }
            for (ring in -3..3) {
                val lat=ring*PI/9.0
                for (segment in 0 until 32) {
                    val a=point(lat,segment*PI/16.0)
                    val b=point(lat,(segment+1)*PI/16.0)
                    if (a.second>=0 && b.second>=0) drawLine(
                        cyan.copy(alpha=(.24+.46*a.second).toFloat()),
                        a.first,b.first,strokeWidth=.85.dp.toPx())
                }
            }
            for (meridian in 0 until 12) {
                for (segment in 0 until 18) {
                    val a=point(-PI/2+segment*PI/18,meridian*PI/6)
                    val b=point(-PI/2+(segment+1)*PI/18,meridian*PI/6)
                    if (a.second>=0 && b.second>=0) drawLine(
                        violet.copy(alpha=.38f),a.first,b.first,strokeWidth=.75.dp.toPx())
                }
            }
            // Contra-rotating orbital segments.
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
                val pr=baseRadius*(if(i%2==0) 1.60f else 1.54f)
                val pc=Offset(c.x+(pr*cos(rad)).toFloat(),
                    c.y+(pr*sin(rad)).toFloat())
                drawCircle(color=if(i%4==0) violet else cyan,
                    radius=(if(i%3==0) 1.7f else 1.05f).dp.toPx(),
                    center=pc,alpha=if(i%3==0) .8f else .44f)
            }
        }

    }
}
