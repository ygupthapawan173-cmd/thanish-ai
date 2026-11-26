package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.PanduAiRepository
import com.example.control.PanduAccessibilityService
import com.example.data.ChatMessageEntity
import com.example.omega.OmegaWakeService
import com.example.omega.OmegaAssistantCore
import com.example.ui.AiGenState
import com.example.ui.JarvisStatus
import com.example.ui.PanduUiState
import com.example.voice.VoiceInputState
import kotlin.math.abs
import kotlin.math.sin

private val Void104 = Color(0xFF050B1A)
private val Panel104 = Color(0xFF0B1830)
private val Cyan104 = Color(0xFF54F1FF)
private val Purple104 = Color(0xFFAC76FF)
private val Muted104 = Color(0xFF94B3D1)
private val White104 = Color(0xFFEAFBFF)

@Composable
fun OmegaR104PremiumHome(
    uiState: PanduUiState,
    onMicClick: () -> Unit,
    onStopSpeaking: () -> Unit,
    onReplayMessage: (ChatMessageEntity) -> Unit,
    onStarterPromptClick: (String) -> Unit,
    onOpenHelp: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToApps: () -> Unit,
    onYouTubeShortcut: () -> Unit,
    onWhatsAppShortcut: () -> Unit,
    onSpeakBattery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listening = uiState.voiceInputState as? VoiceInputState.Listening
    val thinking = uiState.aiGenState is AiGenState.Thinking || uiState.aiGenState is AiGenState.Streaming
    val amplitude = listening?.amplitude?.coerceIn(0f, 1f) ?: 0f
    val wake by OmegaWakeService.active.collectAsState()
    val wakeStatus by OmegaWakeService.status.collectAsState()
    val accessibility by PanduAccessibilityService.isConnected.collectAsState()
    val lastRoute by PanduAiRepository.lastRoute.collectAsState()
    val requestState by PanduAiRepository.requestState.collectAsState()
    val wakeReady by OmegaWakeService.recognitionReady.collectAsState()
    val micOwner by OmegaAssistantCore.sessions.owner.collectAsState()
    val backgroundPhase by OmegaAssistantCore.backgroundPhase.collectAsState()
    val backgroundBusy by OmegaAssistantCore.sessions.backgroundBusy.collectAsState()
    val speechEngine by com.example.voice.VoiceInputController.engineStatus.collectAsState()
    val micAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    val lastAnswer = uiState.messages.lastOrNull { it.role == ChatMessageEntity.ROLE_ASSISTANT }
    val battery = uiState.deviceControlState.battery
    val stateLabel = when {
        listening != null -> "LISTENING"
        thinking -> "THINKING"
        uiState.isSpeaking -> "SPEAKING"
        else -> "READY"
    }
    val line = Brush.horizontalGradient(listOf(Cyan104, Color(0xFF287DFF), Purple104))
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("home_command_tab").background(Void104),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal=8.dp, vertical=4.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(7.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("NEURAL CORE  /  " + stateLabel, color=Cyan104, style=MaterialTheme.typography.labelSmall)
                        Text(if (wakeReady) "WAKE LISTENING" else if (wake) "WAKE STARTING" else "WAKE OFF",
                            color=if(wakeReady) Cyan104 else Muted104,
                            style=MaterialTheme.typography.labelSmall)
                    }
                    OmegaSafeSingularityVisual(uiState.jarvisStatus, amplitude, Modifier.clickable(onClick = onMicClick).testTag("r105_orb"))
                    Text(
                        text = when {
                            listening != null -> listening.partialText.ifBlank { "వింటున్నాను బాస్..." }
                            thinking -> "మీ ప్రశ్నను పరిశీలిస్తున్నాను..."
                            uiState.isSpeaking -> "మాట్లాడుతున్నాను..."
                            else -> "నమస్తే బాస్! నేను మీ జార్విస్"
                        },
                        color=White104, textAlign=TextAlign.Center,
                        style=MaterialTheme.typography.titleMedium, maxLines=3,
                        overflow=TextOverflow.Ellipsis
                    )
                    R104Waveform(amplitude=amplitude, active=listening != null)
                    Row(
                        Modifier.fillMaxWidth().padding(top=6.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.Center
                    ) {
                        Button(
                            onClick=onMicClick,
                            modifier=Modifier.heightIn(min=54.dp).testTag("r104_main_mic"),
                            shape=RoundedCornerShape(22.dp),
                            colors=ButtonDefaults.buttonColors(containerColor=Cyan104, contentColor=Void104)
                        ) {
                            Icon(if(listening!=null) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription=null)
                            Spacer(Modifier.width(8.dp))
                            Text(if(listening!=null) "STOP LISTENING" else "TAP TO SPEAK",
                                style=MaterialTheme.typography.labelLarge, fontWeight=FontWeight.Bold)
                        }
                        if (uiState.isSpeaking) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick=onStopSpeaking, modifier=Modifier.heightIn(min=48.dp)) {
                                Text("Stop voice")
                            }
                        }
                    }
                    Text(if(micAllowed) "MIC PERMISSION GRANTED" else "MIC PERMISSION REQUIRED",
                        color=if(micAllowed) Muted104 else Color(0xFFFFBD79),
                        style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                R104Metric("BATTERY",
                    if(battery.levelPercent in 0..100) battery.levelPercent.toString()+"%" else "N/A",
                    onSpeakBattery, Modifier.weight(1f))
                R104Metric("ACCESSIBILITY", if(accessibility) "ACTIVE" else "OFF",
                    onNavigateToApps, Modifier.weight(1f))
                R104Metric("VOICE", if(wake) "WAKE ON" else "MANUAL",
                    onOpenHelp, Modifier.weight(1f))
            }
        }
        item {
            Text("COMMAND CENTER", color=Cyan104, style=MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                R104Quick("YOUTUBE", Icons.Default.PlayArrow, onYouTubeShortcut, Modifier.weight(1f))
                R104Quick("WHATSAPP", Icons.Default.Chat, onWhatsAppShortcut, Modifier.weight(1f))
                R104Quick("PHONE", Icons.Default.Apps, onNavigateToApps, Modifier.weight(1f))
            }
        }
        item {
            Surface(
                color=Panel104, shape=RoundedCornerShape(20.dp),
                border=androidx.compose.foundation.BorderStroke(1.dp, Purple104.copy(alpha=.45f)),
                modifier=Modifier.fillMaxWidth().clickable { onNavigateToChat() }
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription=null,
                            tint=Purple104, modifier=Modifier.size(21.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI INTELLIGENCE", color=White104, style=MaterialTheme.typography.titleSmall,
                            modifier=Modifier.weight(1f))
                        Icon(Icons.Default.ArrowForward, contentDescription="Open chat", tint=Cyan104)
                    }
                    Text(requestState, color=Cyan104, style=MaterialTheme.typography.labelMedium)
                    Text("Last success: " + lastRoute, color=Muted104, style=MaterialTheme.typography.bodySmall,
                        maxLines=2, overflow=TextOverflow.Ellipsis)
                    if (lastAnswer != null) {
                        Text(lastAnswer.text, color=White104, style=MaterialTheme.typography.bodyMedium,
                            maxLines=3, overflow=TextOverflow.Ellipsis)
                        OutlinedButton(onClick={ onReplayMessage(lastAnswer) },
                            modifier=Modifier.heightIn(min=44.dp)) { Text("Replay latest reply") }
                    } else {
                        Text("Open Chat to ask your first question", color=Muted104,
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick={ onStarterPromptClick("తెలుగులో ఒక మంచి మోటివేషన్ చెప్పు") },
                    modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("Motivation", maxLines=1) }
                OutlinedButton(onClick={ onStarterPromptClick("Tell me a useful technology tip") },
                    modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("Tech tip", maxLines=1) }
            }
        }
        item {
            Text("Mic: " + micOwner.name + " · " + speechEngine, color=Cyan104, style=MaterialTheme.typography.bodySmall, modifier=Modifier.testTag("unified_mic_owner"))
            if (backgroundBusy) Text(backgroundPhase, color=Purple104, style=MaterialTheme.typography.bodyMedium)
            Text("Wake status: " + wakeStatus,
                color=Muted104, style=MaterialTheme.typography.bodySmall)
            Text("Actions run through the existing permission-controlled app modules.",
                color=Muted104, style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun R104Metric(label: String, value: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(modifier=modifier.heightIn(min=62.dp).clickable(onClick=onClick),
        color=Panel104, shape=RoundedCornerShape(17.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp, Cyan104.copy(alpha=.25f))) {
        Column(Modifier.padding(horizontal=10.dp, vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text(label, color=Muted104, style=MaterialTheme.typography.labelSmall,
                maxLines=1, overflow=TextOverflow.Ellipsis)
            Text(value, color=Cyan104, style=MaterialTheme.typography.titleSmall,
                fontWeight=FontWeight.Bold, maxLines=1, overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun R104Quick(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, modifier: Modifier) {
    Surface(modifier=modifier.heightIn(min=62.dp).clickable(onClick=onClick),
        color=Panel104, shape=RoundedCornerShape(18.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,
            Brush.linearGradient(listOf(Cyan104.copy(alpha=.7f), Purple104.copy(alpha=.5f))))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Icon(icon, contentDescription=label, tint=Cyan104, modifier=Modifier.size(24.dp))
            Text(label, color=White104, style=MaterialTheme.typography.labelSmall,
                maxLines=1, overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun R104Waveform(amplitude: Float, active: Boolean) {
    val level = if(active) amplitude.coerceIn(0f, 1f) else 0f
    val transition = rememberInfiniteTransition(label="r104_wave_clock")
    val phase by transition.animateFloat(0f, 6.28f,
        infiniteRepeatable(tween(1100, easing=LinearEasing)),label="phase")
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val w=size.width/42f
        val base=size.height/2f
        for(i in 0 until 41) {
            val shape=abs(sin(i*.37f+if(active) phase else 0f))
            val h=(3f+if(active) shape*level*(base-5f) else 1.8f)
            val x=(i+1)*w
            drawLine(if(i%5==0) Purple104 else Cyan104.copy(alpha=.5f+.4f*shape),
                Offset(x,base-h),Offset(x,base+h),strokeWidth=2.5.dp.toPx(),cap=StrokeCap.Round)
        }
    }
}
