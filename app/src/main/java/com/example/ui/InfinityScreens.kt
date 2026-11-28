package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.CatalogVerificationState
import com.example.ai.KnowledgeDomain
import com.example.ai.ModelCatalogPolicy
import com.example.control.NotificationInbox
import com.example.data.AiProvider
import com.example.data.IntelligenceMode
import com.example.omega.TimeAutomationScheduler
import com.example.ui.components.AiFaceVisualizer
import com.example.ui.components.ParticleRibbonWaveform
import com.example.ui.theme.OmegaColors
import com.example.ui.theme.omegaGlassCard
import com.example.ui.theme.omegaSwitchColors
import com.example.ui.theme.omegaTextFieldColors
import com.example.voice.SarvamVoiceClient

// ---------------------------------------------------------------------------
// 1. HOME SCREEN (2D AI Face + Live Voice Waveform + Live Answer & Sources)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeTabScreen(
    uiState: PanduUiState,
    viewModel: PanduViewModel,
    onOpenCamera: () -> Unit
) {
    val context = LocalContext.current
    var promptDraft by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .omegaGlassCard(cornerRadius = 22.dp, highlighted = uiState.isListening || uiState.isThinking)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AiFaceVisualizer(
                    activityState = uiState.aiActivityState,
                    audioAmplitude = uiState.audioAmplitude,
                    size = 186.dp,
                    onTap = { viewModel.toggleVoiceListening() }
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        uiState.isListening -> uiState.liveTranscript.ifBlank { uiState.voiceDiagNote }
                        uiState.isThinking -> uiState.lastBrainRouteSummary
                        uiState.isSpeaking -> uiState.ttsStatusNote
                        uiState.liveDuplexState.isConnected -> "Gemini Live Connected (${uiState.liveDuplexState.activeModel})"
                        uiState.openAiLiveState.isConnected -> "GPT-Live Connected (${uiState.openAiLiveState.activeModel})"
                        else -> "Tap AI Face or say \"Hey JARVIS\""
                    },
                    color = OmegaColors.IceWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))
                ParticleRibbonWaveform(
                    isActive = uiState.isListening || uiState.isSpeaking || uiState.liveDuplexState.isConnected || uiState.openAiLiveState.isConnected,
                    audioAmplitude = uiState.audioAmplitude,
                    height = 68.dp
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.toggleVoiceListening() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isListening) OmegaColors.CrimsonErr else OmegaColors.NeonBlue
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.isListening) "Stop Mic" else "Listen", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { viewModel.toggleLiveSession(useOpenAi = uiState.settings.activeProvider == AiProvider.OPENAI && uiState.settings.effectiveOpenAiKey().isNotBlank() && uiState.settings.effectiveGeminiKey().isBlank()) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (uiState.liveDuplexState.isConnected || uiState.openAiLiveState.isConnected) "End Live" else "Live Voice",
                            color = OmegaColors.NeonCyan,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hey JARVIS Background Wake",
                        color = OmegaColors.MutedSlate,
                        fontSize = 12.sp
                    )
                    Switch(
                        checked = uiState.settings.wakeWordEnabled,
                        onCheckedChange = { viewModel.toggleBackgroundWake(it) },
                        colors = omegaSwitchColors()
                    )
                }
            }
        }

        // Attached Camera/Vision Preview
        uiState.visionBitmap?.let { bmp ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .omegaGlassCard(16.dp, highlighted = true)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Vision photo",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Vision image attached. Ask your question below.",
                        color = OmegaColors.IceWhite,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearVisionBitmap() }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Remove", tint = OmegaColors.MutedSlate)
                    }
                }
            }
        }

        // Direct Query / Voice Command Input Box
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptDraft,
                    onValueChange = { promptDraft = it },
                    placeholder = { Text("Ask in Telugu or English...", color = OmegaColors.MutedSlate) },
                    colors = omegaTextFieldColors(),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(
                    onClick = onOpenCamera,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(OmegaColors.SurfaceNavy)
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = "Camera Vision", tint = OmegaColors.NeonCyan)
                }
                IconButton(
                    onClick = {
                        if (promptDraft.isNotBlank()) {
                            val send = promptDraft
                            promptDraft = ""
                            viewModel.askAssistant(send)
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(OmegaColors.NeonBlue)
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = "Send", tint = OmegaColors.IceWhite)
                }
            }
        }

        // Current Live Answer + Domain/Model Telemetry + Verified Web Sources
        if (uiState.lastAiReply.isNotBlank() || uiState.streamingReply.isNotBlank()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .omegaGlassCard(18.dp)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.lastDomainLabel} • ${uiState.settings.intelligenceMode.label}",
                            color = OmegaColors.NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Clear",
                            color = OmegaColors.MutedSlate,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { viewModel.clearSessionContext() }
                        )
                    }
                    if (uiState.lastQuery.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("You: ${uiState.lastQuery}", color = OmegaColors.MutedSlate, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = uiState.streamingReply.ifBlank { uiState.lastAiReply },
                        color = OmegaColors.IceWhite,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    if (uiState.lastModelUsed.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${uiState.lastModelUsed} • ${uiState.lastLatencyMs}ms",
                            color = OmegaColors.MutedSlate,
                            fontSize = 11.sp
                        )
                    }
                    if (uiState.lastSources.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.lastSources.take(5).forEach { src ->
                                Text(
                                    text = "🔗 ${src.title.take(28)}",
                                    color = OmegaColors.NeonCyan,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(OmegaColors.ElevatedNavy)
                                        .clickable {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(src.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

// ---------------------------------------------------------------------------
// 2. DASHBOARD SCREEN (20 Real Measured Indicators -> Interactive Routing)
// ---------------------------------------------------------------------------
@Composable
fun DashboardTabScreen(
    uiState: PanduUiState,
    viewModel: PanduViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Live Telemetry Dashboard", color = OmegaColors.IceWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("20 measured indicators • Tap any tile to configure", color = OmegaColors.MutedSlate, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { viewModel.refreshSystemMetrics() }) {
                    Text("Refresh", color = OmegaColors.NeonCyan, fontSize = 12.sp)
                }
            }
        }

        items(uiState.indicators.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { ind ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .omegaGlassCard(cornerRadius = 16.dp, highlighted = ind.healthy)
                            .clickable {
                                if (ind.targetTab == JarvisTab.SETTINGS && ind.settingsPage >= 0) {
                                    viewModel.openSettingsPage(ind.settingsPage)
                                } else {
                                    viewModel.navigateTo(ind.targetTab)
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#${ind.id} ${ind.title}", color = OmegaColors.MutedSlate, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (ind.healthy) OmegaColors.EmeraldLive else OmegaColors.AmberWarn)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(ind.value, color = OmegaColors.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(ind.detail, color = OmegaColors.NeonCyan, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

// ---------------------------------------------------------------------------
// 3. AI BRAIN SCREEN (Gemini Direct + OpenAI GPT + 8 Models + 20 Domains)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiBrainTabScreen(
    uiState: PanduUiState,
    viewModel: PanduViewModel
) {
    val s = uiState.settings
    var quickSearch by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp).padding(14.dp)) {
                Text("AI Brain Architecture", color = OmegaColors.IceWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Gemini Direct & OpenAI GPT with Live Search Grounding", color = OmegaColors.MutedSlate, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))

                Text("Active Provider", color = OmegaColors.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    AiProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = s.activeProvider == provider,
                            onClick = { viewModel.updateSettings { it.copy(activeProvider = provider) } },
                            label = { Text(provider.displayName) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OmegaColors.NeonBlue, selectedLabelColor = OmegaColors.IceWhite)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Intelligence Mode", color = OmegaColors.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    IntelligenceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = s.intelligenceMode == mode,
                            onClick = { viewModel.updateSettings { it.copy(intelligenceMode = mode) } },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OmegaColors.NeonBlue, selectedLabelColor = OmegaColors.IceWhite)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                BrainToggleRow("Live Internet Search Grounding", s.webSearchGrounding) { checked ->
                    viewModel.updateSettings { it.copy(webSearchGrounding = checked) }
                }
                BrainToggleRow("Gemini Deep Research Workflows", s.deepResearchEnabled) { checked ->
                    viewModel.updateSettings { it.copy(deepResearchEnabled = checked) }
                }
                BrainToggleRow("Free Model Priority", s.freeModelPriority) { checked ->
                    viewModel.updateSettings { it.copy(freeModelPriority = checked) }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.discoverAllModels() },
                    enabled = !uiState.isDiscoveringModels,
                    colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isDiscoveringModels) "Discovering Official Catalogs..." else "Discover Live Provider Models")
                }
            }
        }

        // Live Internet Search Quick Runner
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Public, contentDescription = null, tint = OmegaColors.NeonCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Live Internet Research / Website URL", color = OmegaColors.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = quickSearch,
                        onValueChange = { quickSearch = it },
                        placeholder = { Text("News, fact check, or https://...", color = OmegaColors.MutedSlate) },
                        colors = omegaTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (quickSearch.isNotBlank()) {
                                val q = quickSearch
                                quickSearch = ""
                                viewModel.navigateTo(JarvisTab.HOME)
                                viewModel.askAssistant(q)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue)
                    ) { Text("Research") }
                }
            }
        }

        // 8 Requested Models Honest Verification Cards
        item {
            Text("Target Models & Catalog Substitution Status", color = OmegaColors.IceWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        items(uiState.catalogStatuses) { status ->
            val selected = status.effectiveModelId == s.activeChatModel() || status.spec.officialId == s.activeChatModel()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .omegaGlassCard(14.dp, highlighted = selected)
                    .clickable {
                        viewModel.updateSettings { cur ->
                            if (status.spec.provider == "GEMINI") {
                                cur.copy(activeProvider = AiProvider.GEMINI, geminiChatModel = status.effectiveModelId)
                            } else {
                                cur.copy(activeProvider = AiProvider.OPENAI, openAiModel = status.effectiveModelId)
                            }
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${status.spec.displayName} → ${status.effectiveModelId}", color = OmegaColors.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("${status.spec.specialty}", color = OmegaColors.MutedSlate, fontSize = 11.sp)
                    Text(status.note, color = when (status.state) {
                        CatalogVerificationState.VERIFIED_IN_CATALOG -> OmegaColors.EmeraldLive
                        CatalogVerificationState.SUBSTITUTED -> OmegaColors.NeonCyan
                        CatalogVerificationState.UNVERIFIED_KEY_REQUIRED -> OmegaColors.AmberWarn
                    }, fontSize = 11.sp)
                }
                Text(if (selected) "ACTIVE" else "USE", color = OmegaColors.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 20 Knowledge Domains
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp).padding(14.dp)) {
                Text("20 Master Intelligence Knowledge Workflows", color = OmegaColors.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KnowledgeDomain.entries.forEach { d ->
                        Text(
                            text = d.label,
                            color = OmegaColors.IceWhite,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(OmegaColors.ElevatedNavy)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

// ---------------------------------------------------------------------------
// 4. VOICE SCREEN (Sarvam Telugu Voice + Online/Offline Wake + Live Audio)
// ---------------------------------------------------------------------------
@Composable
fun VoiceTabScreen(
    uiState: PanduUiState,
    viewModel: PanduViewModel
) {
    val s = uiState.settings

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sarvam Telugu Premium Voice Card
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp, highlighted = uiState.isSpeaking).padding(14.dp)) {
                Text("Sarvam Telugu + English Premium Voice", color = OmegaColors.IceWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(uiState.ttsStatusNote, color = OmegaColors.NeonCyan, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))

                Text("Language Mode", color = OmegaColors.MutedSlate, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    listOf("auto" to "TE + EN Auto", "te-IN" to "Telugu", "en-IN" to "English").forEach { (tag, label) ->
                        FilterChip(
                            selected = s.languageTag == tag,
                            onClick = { viewModel.updateSettings { it.copy(languageTag = tag) } },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OmegaColors.NeonBlue, selectedLabelColor = OmegaColors.IceWhite)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Sarvam Male Voice Profile", color = OmegaColors.MutedSlate, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                SarvamVoiceClient.MALE_SPEAKERS.forEach { profile ->
                    val chosen = s.sarvamSpeaker == profile.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (chosen) OmegaColors.NeonBlue.copy(alpha = 0.22f) else OmegaColors.ElevatedNavy)
                            .clickable { viewModel.updateSettings { it.copy(sarvamSpeaker = profile.id, sarvamModel = profile.model) } }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(profile.label, color = OmegaColors.IceWhite, fontSize = 12.sp)
                        if (chosen) Text("SELECTED", color = OmegaColors.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                }

                BrainToggleRow("Speak Replies Automatically (Sarvam)", s.autoSpeak) { checked ->
                    viewModel.updateSettings { it.copy(autoSpeak = checked) }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.testSarvamVoice() },
                    colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Test Sarvam Telugu Voice") }

                uiState.voiceTestStatus?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = OmegaColors.NeonCyan, fontSize = 12.sp)
                }
            }
        }

        // Ultra Online Wake + Offline Fallback + Yes Boss Latency Card
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp).padding(14.dp)) {
                Text("Wake Word & Streaming STT Engine", color = OmegaColors.IceWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Active: ${uiState.voiceBenchmark.selectedEngine.label} • ${uiState.voiceBenchmark.reason}", color = OmegaColors.NeonCyan, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))

                BrainToggleRow("Hey JARVIS Wake Service", s.wakeWordEnabled) { checked ->
                    viewModel.toggleBackgroundWake(checked)
                }
                BrainToggleRow("Online Wake Priority (Saaras V4)", s.onlineWakePriority) { checked ->
                    viewModel.updateSettings { it.copy(onlineWakePriority = checked) }
                }
                BrainToggleRow("Cloud Audio Streaming Consent", s.cloudAudioConsentGranted) { checked ->
                    viewModel.updateSettings { it.copy(cloudAudioConsentGranted = checked) }
                }
                BrainToggleRow("Screen-Lock Local Wake ONLY", s.screenLockWakeEnabled) { checked ->
                    viewModel.updateSettings { it.copy(screenLockWakeEnabled = checked) }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Last measured 'Yes Boss' ack: ${if (uiState.measuredAckLatencyMs > 0) "${uiState.measuredAckLatencyMs}ms" else "Not measured yet"} • Target 300–500ms • ${if (uiState.ackUsingSarvamCache) "Preloaded Sarvam Audio" else "Local Fast Chime"}",
                    color = OmegaColors.MutedSlate,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { viewModel.testYesBossAck() }, modifier = Modifier.weight(1f)) {
                        Text("Test Yes Boss", color = OmegaColors.NeonCyan, maxLines = 1)
                    }
                    Button(
                        onClick = { if (s.wakeWordEnabled) viewModel.startBackgroundWakeService() else viewModel.toggleBackgroundWake(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue),
                        modifier = Modifier.weight(1f)
                    ) { Text("Start Service", maxLines = 1) }
                }
                Spacer(Modifier.height(4.dp))
                Text("Background FGS: ${uiState.bgServiceStatus}", color = OmegaColors.MutedSlate, fontSize = 11.sp)
            }
        }

        // Live Duplex Models (Gemini 3.8 Live / GPT-Live 1.1)
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp).padding(14.dp)) {
                Text("Live Voice Conversation Engines", color = OmegaColors.IceWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                ModelCatalogPolicy.REQUESTED_VOICE_MODELS.filter { it.officialId !in setOf("saaras:v4", "gemini-3.5-transcribe-live", "gpt-live-transcribe") }.forEach { vm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (s.geminiLiveVoiceModel == vm.officialId || s.openAiLiveVoiceModel == vm.officialId) OmegaColors.NeonBlue.copy(alpha = 0.2f) else OmegaColors.ElevatedNavy)
                            .clickable {
                                if (vm.provider == "GEMINI") viewModel.updateSettings { it.copy(geminiLiveVoiceModel = vm.officialId) }
                                else if (vm.provider == "OPENAI") viewModel.updateSettings { it.copy(openAiLiveVoiceModel = vm.officialId) }
                            }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${vm.displayName} (${vm.officialId})", color = OmegaColors.IceWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(vm.verifiedRole, color = OmegaColors.MutedSlate, fontSize = 11.sp)
                        }
                        Text(vm.provider, color = OmegaColors.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.toggleLiveSession(useOpenAi = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (uiState.liveDuplexState.isConnected) OmegaColors.CrimsonErr else OmegaColors.NeonBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (uiState.liveDuplexState.isConnected) "Stop Gemini Live" else "Start Gemini Live", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.toggleLiveSession(useOpenAi = true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (uiState.openAiLiveState.isConnected) "Stop OpenAI Live" else "Start OpenAI Live", color = OmegaColors.NeonCyan, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

// ---------------------------------------------------------------------------
// 5. CONTROL SCREEN (Apps, Hardware, Calls/SMS, Accessibility, Notifications, Schedule, Vision)
// ---------------------------------------------------------------------------
@Composable
fun ControlTabScreen(
    uiState: PanduUiState,
    viewModel: PanduViewModel,
    onOpenCamera: () -> Unit
) {
    var appFilter by remember { mutableStateOf("") }
    var callTarget by remember { mutableStateOf("") }
    var smsBody by remember { mutableStateOf("") }
    var volSlider by remember(uiState.mediaVolumePercent) { mutableFloatStateOf(uiState.mediaVolumePercent.toFloat()) }
    var schedName by remember { mutableStateOf("Night Torch Off") }
    var schedHour by remember { mutableStateOf("22") }
    var schedMinute by remember { mutableStateOf("30") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Flashlight & Volume Control
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp).padding(14.dp)) {
                Text("Hardware & Volume Control", color = OmegaColors.IceWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.toggleFlashlight(!uiState.flashlightOn) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (uiState.flashlightOn) OmegaColors.NeonCyan else OmegaColors.NeonBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.FlashlightOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.flashlightOn) "Flashlight ON" else "Flashlight OFF")
                    }
                    OutlinedButton(onClick = onOpenCamera, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = OmegaColors.NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Vision Camera", color = OmegaColors.NeonCyan)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Media Volume: ${volSlider.toInt()}%", color = OmegaColors.IceWhite, fontSize = 12.sp)
                Slider(
                    value = volSlider,
                    onValueChange = { volSlider = it },
                    onValueChangeFinished = { viewModel.setMediaVolume(volSlider.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = OmegaColors.NeonCyan, activeTrackColor = OmegaColors.NeonBlue)
                )
            }
        }

        // Open Applications
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp).padding(14.dp)) {
                Text("Open Applications (${uiState.installedApps.size})", color = OmegaColors.IceWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = appFilter,
                    onValueChange = { appFilter = it },
                    placeholder = { Text("Search installed app...", color = OmegaColors.MutedSlate) },
                    colors = omegaTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                val matching = uiState.installedApps.filter {
                    appFilter.isBlank() || it.label.contains(appFilter, ignoreCase = true)
                }.take(6)
                matching.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(OmegaColors.ElevatedNavy)
                            .clickable { viewModel.launchAppByName(app.label) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(app.label, color = OmegaColors.IceWhite, fontSize = 13.sp)
                        Text("OPEN", color = OmegaColors.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // Calls & Messages (with confirmation)
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp).padding(14.dp)) {
                Text("Confirmed Calls & SMS", color = OmegaColors.IceWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = callTarget,
                    onValueChange = { callTarget = it },
                    label = { Text("Contact Name or Number") },
                    colors = omegaTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = smsBody,
                    onValueChange = { smsBody = it },
                    label = { Text("SMS Draft Message (Optional)") },
                    colors = omegaTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { if (callTarget.isNotBlank()) viewModel.dialContact(callTarget) },
                        colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Dialer")
                    }
                    OutlinedButton(
                        onClick = { if (callTarget.isNotBlank()) viewModel.openSmsDraft(callTarget, smsBody) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("SMS Draft", color = OmegaColors.NeonCyan)
                    }
                }
            }
        }

        // Accessibility Automation & Vision
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp, highlighted = uiState.accessibilityConnected).padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Accessibility & Screen Vision", color = OmegaColors.IceWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (uiState.accessibilityConnected) "ACTIVE" else "OFF",
                        color = if (uiState.accessibilityConnected) OmegaColors.EmeraldLive else OmegaColors.AmberWarn,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { viewModel.openAccessibilitySettings() }, modifier = Modifier.weight(1f)) {
                        Text("Accessibility Settings", color = OmegaColors.NeonCyan, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { viewModel.captureScreenVisionAndAsk() },
                        colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Analyze Screen", fontSize = 12.sp)
                    }
                }
            }
        }

        // Smart Notifications
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp).padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Smart Notifications (${uiState.unreadNotifCount})", color = OmegaColors.IceWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (uiState.notificationAccessGranted) "Connected" else "Grant Access",
                        color = OmegaColors.NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.openNotificationListenerSettings() }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(NotificationInbox.formatForAgent(4), color = OmegaColors.MutedSlate, fontSize = 12.sp)
            }
        }

        // Scheduled Automations
        item {
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp).padding(14.dp)) {
                Text("Scheduled Automation", color = OmegaColors.IceWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = schedName, onValueChange = { schedName = it }, label = { Text("Name") }, colors = omegaTextFieldColors(), modifier = Modifier.weight(2f), singleLine = true)
                    OutlinedTextField(value = schedHour, onValueChange = { schedHour = it.filter(Char::isDigit).take(2) }, label = { Text("HH") }, colors = omegaTextFieldColors(), modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = schedMinute, onValueChange = { schedMinute = it.filter(Char::isDigit).take(2) }, label = { Text("MM") }, colors = omegaTextFieldColors(), modifier = Modifier.weight(1f), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.saveFlashlightSchedule(schedName, schedHour.toIntOrNull() ?: 22, schedMinute.toIntOrNull() ?: 30, turnOn = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue),
                        modifier = Modifier.weight(1f)
                    ) { Text("Schedule Torch OFF") }
                    OutlinedButton(
                        onClick = { viewModel.saveFlashlightSchedule(schedName, schedHour.toIntOrNull() ?: 6, schedMinute.toIntOrNull() ?: 0, turnOn = true) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Torch ON", color = OmegaColors.NeonCyan) }
                }
                uiState.routines.forEach { r ->
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(OmegaColors.ElevatedNavy).padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(r.name, color = OmegaColors.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(r.actionsSummary, color = OmegaColors.MutedSlate, fontSize = 11.sp)
                        }
                        Text("Run", color = OmegaColors.NeonCyan, fontSize = 12.sp, modifier = Modifier.clickable { viewModel.runRoutineByName(r.name) }.padding(end = 10.dp))
                        Text("Del", color = OmegaColors.CrimsonErr, fontSize = 12.sp, modifier = Modifier.clickable { viewModel.deleteRoutine(r.id, r.name) })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
fun BrainToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = OmegaColors.IceWhite, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = omegaSwitchColors())
    }
}
