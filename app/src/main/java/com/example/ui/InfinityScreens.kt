package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ai.BrainAvailability
import com.example.control.AgentHudBus
import com.example.control.AgentHudState
import com.example.ui.components.LocalReducedRendering
import com.example.ui.components.ParticleRibbonWaveform
import com.example.ui.components.rememberParallaxOffset
import kotlin.math.*

internal fun cleanReply(text: String): String = text // Preserve markdown and code verbatim.

private val V110NavyTop = Color(0xFF040916)
private val V110NavyMid = Color(0xFF071126)
private val V110NavyCard = Color(0xFF0D1B35)
private val V110NeonCyan = Color(0xFF00F0FF)
private val V110ElectricBlue = Color(0xFF3B82F6)
private val V110TextPrimary = Color(0xFFF8FAFC)
private val V110TextMuted = Color(0xFF94A3B8)

@Composable
internal fun InfinityNavigation(screen: OmegaScreen, onSelect: (OmegaScreen) -> Unit) {
    val tabs = listOf(
        Triple(OmegaScreen.HOME, "Home", Icons.Rounded.Home),
        Triple(OmegaScreen.CHAT, "Chat", Icons.Rounded.Chat),
        Triple(OmegaScreen.ROUTINES, "Automation", Icons.Rounded.Bolt),
        Triple(OmegaScreen.CONTROL, "Control", Icons.Rounded.Memory),
        Triple(OmegaScreen.SETTINGS, "Settings", Icons.Rounded.Settings)
    )
    val selected = when (screen) {
        OmegaScreen.LISTENING -> OmegaScreen.HOME
        OmegaScreen.AUTONOMOUS_LAB -> OmegaScreen.CONTROL
        else -> screen
    }
    NavigationBar(
        containerColor = V110NavyTop,
        contentColor = V110TextPrimary,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        tabs.forEach { (target, label, icon) ->
            val isSelected = target == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(target) },
                icon = { Icon(icon, label) },
                label = { Text(label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = V110NeonCyan,
                    selectedTextColor = V110NeonCyan,
                    indicatorColor = V110ElectricBlue.copy(alpha = 0.24f),
                    unselectedIconColor = V110TextMuted,
                    unselectedTextColor = V110TextMuted
                )
            )
        }
    }
}

@Composable
internal fun InfinityHome(
    s: PanduUiState,
    onTap: () -> Unit,
    onInterruptSpeak: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenVision: () -> Unit = {},
    onDailyPlan: () -> Unit = {},
    onRunSuggestion: (com.example.autonomous.ProactiveSuggestion) -> Unit = {}
) {
    val hud by AgentHudBus.state.collectAsStateWithLifecycle()
    val active = hud as? AgentHudState.Active
    val label = when {
        s.settings.micPrivacyMuted -> "Microphone muted"
        s.isSpeaking || s.liveDuplexState.isAiSpeaking || active?.statusText?.contains("Speaking", ignoreCase = true) == true -> "Speaking…"
        s.isThinking || active?.isThinking == true -> "Thinking…"
        s.liveDuplexState.isConnecting -> "Connecting…"
        s.isListening || s.liveDuplexState.isConnected || active != null -> "Listening…"
        else -> "Ready, Boss"
    }
    val accent = V110NeonCyan
    val reduced = LocalReducedRendering.current
    val parallax by rememberParallaxOffset(s.settings.gyroscopeParallax && !reduced, maxDpShift = 8f)
    val orbActive = label in setOf("Listening…", "Speaking…", "Thinking…", "Connecting…")
    val liveLevel = maxOf(s.audioAmplitude, s.liveDuplexState.amplitude, active?.audioAmplitude ?: 0f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(V110NavyTop, V110NavyMid, V110NavyTop)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("JARVIS INFINITY", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = V110TextPrimary, letterSpacing = 1.sp)
            Text("${BuildConfig.VERSION_NAME} (v${BuildConfig.VERSION_CODE})", fontSize = 10.5.sp, color = accent, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(10.dp))

            // Live AI Brain Activity Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(V110NavyCard.copy(alpha = 0.92f))
                    .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.connectionHealth.status == com.example.ai.ConnectionStatus.CONNECTED) Color(0xFF10B981) else accent))
                    Column {
                        Text(
                            text = "${s.activeBrainLabel} • ${s.activePipelineStage.label}",
                            color = V110TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${s.connectionHealth.providerLabel} • ${s.lastVerificationBadge}",
                            color = V110TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text("${s.lastLatencyMs}ms", color = accent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    InfinityOrb(
                        Modifier
                            .fillMaxWidth(0.68f)
                            .aspectRatio(1f)
                            .clickable(role = Role.Button, onClickLabel = "Start or stop voice", onClick = onTap),
                        accent,
                        orbActive,
                        liveLevel,
                        parallax
                    )
                    ParticleRibbonWaveform(
                        isActive = orbActive,
                        audioAmplitude = liveLevel,
                        preset = s.settings.orbColorPreset,
                        height = 46.dp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Text(label, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = V110TextPrimary)
                    if (s.liveTranscript.isNotBlank() && s.isListening) {
                        Text(s.liveTranscript, fontSize = 12.5.sp, color = accent, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(
                        s.voiceError ?: if (s.settings.micPrivacyMuted) "Microphone privacy is on. Change it in Settings." else if (label == "Ready, Boss") "Tap the orb or say Hey JARVIS" else "Tap the orb to stop",
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        color = if (s.voiceError != null) Color(0xFFF43F5E) else V110TextMuted,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AnimatedVisibility(visible = s.isSpeaking) {
                Button(
                    onClick = onInterruptSpeak,
                    colors = ButtonDefaults.buttonColors(containerColor = V110ElectricBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Interrupt & Speak", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (s.settings.proactiveSuggestionsEnabled && s.proactiveSuggestions.isNotEmpty()) {
                val top = s.proactiveSuggestions.first()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(V110NavyCard)
                        .border(1.dp, V110ElectricBlue.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable { onRunSuggestion(top) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(top.title, color = accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(top.detail, color = V110TextMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(top.actionLabel, color = V110TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = onDailyPlan, label = { Text("Daily Plan", color = V110TextPrimary, fontSize = 11.5.sp) }, leadingIcon = { Icon(Icons.Rounded.Today, null, tint = accent, modifier = Modifier.size(16.dp)) })
                AssistChip(onClick = onOpenVision, label = { Text("Vision Scan", color = V110TextPrimary, fontSize = 11.5.sp) }, leadingIcon = { Icon(Icons.Rounded.CameraAlt, null, tint = accent, modifier = Modifier.size(16.dp)) })
                AssistChip(onClick = onOpenChat, label = { Text("Open Chat", color = V110TextPrimary, fontSize = 11.5.sp) }, leadingIcon = { Icon(Icons.Rounded.Chat, null, tint = accent, modifier = Modifier.size(16.dp)) })
            }
        }
    }
}

@Composable
private fun InfinityOrb(modifier: Modifier, accent: Color, active: Boolean, level: Float, parallax: Offset) {
    val reduced = LocalReducedRendering.current
    val transition = rememberInfiniteTransition(label = "orb")
    val phase by transition.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(if (reduced) 18000 else 9000, easing = LinearEasing)), label = "rotation")
    Canvas(modifier) {
        val safeLevel = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        val r = size.minDimension * .39f * (if (active) 1f + safeLevel * .06f else 1f)
        val cx = center.x + parallax.x.dp.toPx(); val cy = center.y + parallax.y.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(V110ElectricBlue.copy(alpha = 0.22f), accent.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 1.25f
            ),
            radius = r * 1.25f,
            center = Offset(cx, cy)
        )
        val rings = if (reduced) 8 else if (active) 24 else 14
        val segments = if (reduced) 24 else if (active) 56 else 36
        repeat(rings) { j ->
            val a = j * PI / rings + phase * .13
            val flatten = .13 + .87 * abs(sin(j * 1.71 + phase * .25))
            val path = Path()
            for (i in 0..segments) {
                val t = i * 2 * PI / segments
                val x = r * cos(t); val y = r * flatten * sin(t)
                val px = (cx + x * cos(a) - y * sin(a)).toFloat(); val py = (cy + x * sin(a) + y * cos(a)).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            val ringColor = if (j % 2 == 0) accent else V110ElectricBlue
            drawPath(path, ringColor.copy(alpha = if (active) .32f else .20f), style = Stroke(width = if (active) 1.7f else 1.15f))
        }
        val mark = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - r * .36f, cy - r * .62f, cx - r * .76f, cy - r * .05f, cx - r * .43f, cy + r * .22f)
            cubicTo(cx - r * .2f, cy + r * .42f, cx, cy, cx, cy)
            cubicTo(cx + r * .36f, cy - r * .62f, cx + r * .76f, cy - r * .05f, cx + r * .43f, cy + r * .22f)
            cubicTo(cx + r * .2f, cy + r * .42f, cx, cy, cx, cy)
        }
        drawPath(mark, accent, style = Stroke(width = r * .038f, cap = StrokeCap.Round))
    }
}

@Composable
internal fun InfinityControl(
    s: PanduUiState,
    scrollState: ScrollState = rememberScrollState(),
    onSettings: (Int) -> Unit,
    onChat: () -> Unit,
    onCamera: () -> Unit,
    onRoutines: () -> Unit,
    onLab: () -> Unit,
    onRefresh: () -> Unit
) {
    val modules = listOf(
        Triple("AI Multi-Brain", "10 Brains, API slots & discovery", { onSettings(0) }),
        Triple("Voice Engines", "30 Gemini & Telugu/EN voices", { onSettings(1) }),
        Triple("Mobile Control", "Permissions and accessibility", { onSettings(6) }),
        Triple("Personal Memory", "Memories, plans & IF-THEN rules", onRoutines),
        Triple("Vision & Docs", "Camera Q&A & PDF analysis", onCamera),
        Triple("Security & Privacy", "Owner auth, backup & guard", { onSettings(10) }),
        Triple("Self-Engineering", "Antigravity Control Center", onLab),
        Triple("System Diagnostics", "Health, battery & logs", { onSettings(11) })
    )
    val icons = listOf(
        Icons.Rounded.Memory, Icons.Rounded.Mic, Icons.Rounded.AccessibilityNew, Icons.Rounded.Folder,
        Icons.Rounded.CameraAlt, Icons.Rounded.Security, Icons.Rounded.Code, Icons.Rounded.BugReport
    )
    val readyBrains = s.brainInventory.count { it.availability == BrainAvailability.READY || it.availability == BrainAvailability.FREE_READY }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(V110NavyTop, V110NavyMid)))) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Command Center", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = V110TextPrimary)
            modules.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (title, detail, action) ->
                        Card(
                            onClick = action,
                            modifier = Modifier.weight(1f).heightIn(min = 120.dp).border(1.dp, V110NeonCyan.copy(alpha = 0.22f), RoundedCornerShape(18.dp)),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = V110NavyCard)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(icons[modules.indexOfFirst { it.first == title }], null, tint = V110NeonCyan)
                                Text(title, fontWeight = FontWeight.SemiBold, color = V110TextPrimary)
                                Text(detail, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 3, color = V110TextMuted)
                            }
                        }
                    }
                }
            }
            Card(
                Modifier.fillMaxWidth().border(1.dp, V110ElectricBlue.copy(alpha = 0.32f), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = V110NavyCard)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Live System Monitor", fontWeight = FontWeight.Bold, color = V110NeonCyan)
                    Text("Active Brain: ${s.activeBrainLabel} ($readyBrains/10 Brains Ready)", fontSize = 12.sp, color = V110TextPrimary)
                    Text("Pipeline: ${s.lastBrainRouteSummary} • ${s.lastVerificationBadge}", fontSize = 11.5.sp, color = V110TextMuted)
                    Text("Connection: ${s.connectionHealth.status.name} • ${s.connectionHealth.providerLabel}", fontSize = 12.sp, color = V110TextPrimary)
                    Text("Last API model: ${s.apiTelemetry.lastUsedModel.ifBlank { "Not tested" }} | Latency: ${s.lastLatencyMs}ms | Quota: ${s.connectionHealth.quotaState}", fontSize = 12.sp, color = V110TextMuted)
                    Text("Usage: ${s.apiTelemetry.diagnosticSummary()}", fontSize = 11.5.sp, color = V110TextMuted)
                    Text("Voice: ${s.voiceDiagNote} • TTS: ${s.ttsStatusNote}", fontSize = 11.5.sp, color = V110TextMuted)
                    Text("Signing Cert SHA-256: ${s.signingCertSha256?.take(16)?.plus("...") ?: "Unavailable"}", fontSize = 11.5.sp, color = V110TextMuted)
                    Text("Battery: ${s.batteryPercent}% (last snapshot)", fontSize = 11.5.sp, color = V110TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onRefresh) { Text("Refresh diagnostics", color = V110NeonCyan) }
                        TextButton(onClick = onChat) { Text("Open conversation", color = V110ElectricBlue) }
                    }
                }
            }
        }
    }
}
