package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.control.AgentHudBus
import com.example.control.AgentHudState
import com.example.omega.OmegaWakeService
import com.example.ui.components.LocalReducedRendering
import com.example.ui.components.rememberParallaxOffset
import kotlin.math.*

internal fun cleanReply(text: String): String = text // Preserve markdown and code verbatim.

@Composable
internal fun InfinityNavigation(screen: OmegaScreen, onSelect: (OmegaScreen) -> Unit) {
    val tabs = listOf(Triple(OmegaScreen.HOME,"Home",Icons.Rounded.Home), Triple(OmegaScreen.CHAT,"Chat",Icons.Rounded.Chat), Triple(OmegaScreen.ROUTINES,"Automation",Icons.Rounded.Bolt), Triple(OmegaScreen.CONTROL,"Control",Icons.Rounded.Memory), Triple(OmegaScreen.SETTINGS,"Settings",Icons.Rounded.Settings))
    val selected = when(screen) { OmegaScreen.LISTENING -> OmegaScreen.HOME; OmegaScreen.AUTONOMOUS_LAB -> OmegaScreen.ROUTINES; else -> screen }
    NavigationBar(containerColor=MaterialTheme.colorScheme.surface, windowInsets=WindowInsets(0,0,0,0)) {
        tabs.forEach { (target,label,icon) -> NavigationBarItem(selected=target==selected,onClick={onSelect(target)},icon={Icon(icon,label)},label={Text(label,fontSize=10.sp,maxLines=1)},alwaysShowLabel=true) }
    }
}

@Composable
internal fun InfinityHome(s: PanduUiState,onTap:()->Unit) {
    val hud by AgentHudBus.state.collectAsStateWithLifecycle()
    val active = hud as? AgentHudState.Active
    val label = when { s.settings.micPrivacyMuted -> "Microphone muted"; s.isSpeaking || s.liveDuplexState.isAiSpeaking || active?.statusText?.contains("Speaking",ignoreCase=true)==true -> "Speaking…"; s.isThinking || active?.isThinking==true -> "Thinking…"; s.liveDuplexState.isConnecting -> "Connecting…"; s.isListening || s.liveDuplexState.isConnected || active!=null -> "Listening…"; else -> "Ready, Boss" }
    val accent = MaterialTheme.colorScheme.primary
    val reduced = LocalReducedRendering.current
    val parallax by rememberParallaxOffset(s.settings.gyroscopeParallax && !reduced, maxDpShift=8f)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=20.dp,vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text("JARVIS INFINITY",fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onBackground)
        Text("V${BuildConfig.VERSION_CODE}  •  ONLINE EDITION",fontSize=11.sp,color=accent,letterSpacing=2.sp)
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                InfinityOrb(Modifier.fillMaxWidth(.84f).aspectRatio(1f).clickable(role=Role.Button,onClickLabel="Start or stop voice",onClick=onTap),accent, label in setOf("Listening…","Speaking…","Thinking…","Connecting…"), maxOf(s.audioAmplitude,s.liveDuplexState.amplitude,active?.audioAmplitude ?: 0f), parallax)
                Text(label,fontSize=22.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center)
                Text(s.voiceError ?: if(s.settings.micPrivacyMuted) "Microphone privacy is on. Change it in Settings." else if(label=="Ready, Boss") "Tap the orb or say Hey JARVIS" else "Tap the orb to stop",fontSize=13.sp,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp),maxLines=3)
            }
        }
    }
}

@Composable
private fun InfinityOrb(modifier: Modifier, accent:Color, active:Boolean, level:Float, parallax:Offset) {
    val reduced=LocalReducedRendering.current
    val transition=rememberInfiniteTransition(label="orb")
    val phase by transition.animateFloat(0f, (2*PI).toFloat(), infiniteRepeatable(tween(if(reduced) 18000 else 9000,easing=LinearEasing)),label="rotation")
    Canvas(modifier) {
        val safeLevel = if (level.isFinite()) level.coerceIn(0f,1f) else 0f
        val r=size.minDimension*.39f*(if(active) 1f+safeLevel*.045f else 1f)
        val cx=center.x+parallax.x.dp.toPx(); val cy=center.y+parallax.y.dp.toPx()
        drawCircle(accent.copy(alpha=.045f),r)
        val rings=if(reduced) 8 else if(active) 24 else 14
        val segments=if(reduced) 24 else if(active) 56 else 36
        repeat(rings) { j ->
            val a=j*PI/rings+phase*.13
            val flatten=.13+.87*abs(sin(j*1.71+phase*.25))
            val path=Path()
            for(i in 0..segments) {
                val t=i*2*PI/segments
                val x=r*cos(t); val y=r*flatten*sin(t)
                val px=(cx+x*cos(a)-y*sin(a)).toFloat(); val py=(cy+x*sin(a)+y*cos(a)).toFloat()
                if(i==0) path.moveTo(px,py) else path.lineTo(px,py)
            }
            drawPath(path,accent.copy(alpha=if(active) .29f else .18f),style=Stroke(width=if(active) 1.6f else 1.1f))
        }
        val mark=Path().apply { moveTo(cx,cy); cubicTo(cx-r*.36f,cy-r*.62f,cx-r*.76f,cy-r*.05f,cx-r*.43f,cy+r*.22f); cubicTo(cx-r*.2f,cy+r*.42f,cx,cy,cx,cy); cubicTo(cx+r*.36f,cy-r*.62f,cx+r*.76f,cy-r*.05f,cx+r*.43f,cy+r*.22f); cubicTo(cx+r*.2f,cy+r*.42f,cx,cy,cx,cy) }
        drawPath(mark,accent,style=Stroke(width=r*.035f,cap=StrokeCap.Round))
    }
}

@Composable
internal fun InfinityControl(s:PanduUiState,onSettings:(Int)->Unit,onChat:()->Unit,onCamera:()->Unit,onRoutines:()->Unit,onLab:()->Unit,onRefresh:()->Unit) {
    val modules=listOf(Triple("AI Brain","API and model settings",{onSettings(0)}),Triple("Voice Engines","Recognition and TTS settings",{onSettings(1)}),Triple("Mobile Control","Permissions and controls",{onSettings(6)}),Triple("Personal Memory","Saved facts and routines",onRoutines),Triple("Camera & Vision","Capture image",onCamera),Triple("Local Commands","Private memory and commands",{onSettings(0)}),Triple("Self-Engineering","GitHub engineering lab",onLab),Triple("System Diagnostics","View health report",{onSettings(11)}))
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Command Center",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        val icons=listOf(Icons.Rounded.Memory,Icons.Rounded.Mic,Icons.Rounded.AccessibilityNew,Icons.Rounded.Folder,Icons.Rounded.CameraAlt,Icons.Rounded.Cloud,Icons.Rounded.Settings,Icons.Rounded.BugReport)
        modules.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) { pair.forEach { (title,detail,action) -> Card(onClick=action,modifier=Modifier.weight(1f).heightIn(min=124.dp),shape=RoundedCornerShape(18.dp)) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { Icon(icons[modules.indexOfFirst { it.first==title }],null,tint=MaterialTheme.colorScheme.primary); Text(title,fontWeight=FontWeight.SemiBold); Text(detail,fontSize=12.sp,lineHeight=16.sp,maxLines=3) } } } } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Live System Monitor",fontWeight=FontWeight.Bold)
            Text("Connection: ${s.connectionHealth.status.name} • ${s.connectionHealth.providerLabel}", fontSize=12.sp)
            Text("Last API model: ${s.apiTelemetry.lastUsedModel.ifBlank { "Not tested" }} | Quota: ${s.connectionHealth.quotaState}")
            Text("Usage: ${s.apiTelemetry.diagnosticSummary()}", fontSize=12.sp)
            Text("Voice: ${s.voiceDiagNote}")
            Text("TTS: ${s.ttsStatusNote}")
            Text("Background wake: disabled in Online Edition")
            Text("Battery: ${s.batteryPercent}% (last snapshot)")
            TextButton(onClick=onRefresh) { Text("Refresh diagnostics") }
            TextButton(onClick=onChat) { Text("Open conversation") }
        } }
    }
}
