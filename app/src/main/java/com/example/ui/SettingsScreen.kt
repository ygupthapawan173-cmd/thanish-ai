package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ai.CreatorIdentity
import com.example.data.ApiSlot
import com.example.data.SlotProvider
import com.example.data.SlotStatus
import com.example.ui.theme.OmegaColors
import com.example.ui.theme.omegaGlassCard
import com.example.ui.theme.omegaTextFieldColors

private data class SettingsSectionMeta(val index: Int, val title: String, val subtitle: String, val aliases: String)

private val SETTINGS_SECTIONS = listOf(
    SettingsSectionMeta(0, "API Keys & Multi-Slot Manager", "Gemini Direct, OpenAI GPT & Sarvam Voice key slots", "api key slot gemini openai sarvam"),
    SettingsSectionMeta(1, "Voice Engine Preferences", "Telugu/English recognition & Sarvam Voice standby", "voice sarvam wake lock telugu"),
    SettingsSectionMeta(2, "Model Discovery & Health", "Free priority, paid authorization & catalog discovery", "models discover health free paid"),
    SettingsSectionMeta(3, "Background Service & Battery", "Foreground microphone service & battery optimization", "battery background service ram"),
    SettingsSectionMeta(4, "Permissions Manager", "Microphone, Notifications, Camera, Contacts, Accessibility", "permissions mic notification camera accessibility"),
    SettingsSectionMeta(5, "Privacy & Security", "Confirm actions, biometric gate, keystore & lock-screen guard", "privacy security biometric keystore"),
    SettingsSectionMeta(6, "Self-Upgrade Center", "Verify signed GitHub releases, SHA-256 & install", "update upgrade release sha256 apk"),
    SettingsSectionMeta(7, "About JARVIS INFINITY", "Developer Guptha, Version V110 & legal attributions", "about guptha version developer license")
)

@Composable
fun SettingsTabScreen(
    uiState: PanduUiState,
    viewModel: PanduViewModel
) {
    val page = uiState.settingsCategoryPage
    var searchQuery by remember { mutableStateOf("") }

    val multiPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshSystemMetrics()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (page in 0..7) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "← All Settings",
                        color = OmegaColors.NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.openSettingsPage(-1) }
                    )
                    Text(
                        text = SETTINGS_SECTIONS[page].title,
                        color = OmegaColors.IceWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            item {
                Column {
                    Text("Settings & System Setup", color = OmegaColors.IceWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search settings...", color = OmegaColors.MutedSlate) },
                        colors = omegaTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            val visible = SETTINGS_SECTIONS.filter { sec ->
                SettingsSearchPolicy.matches(searchQuery, sec.title, sec.subtitle, sec.aliases)
            }
            items(visible.size) { idx ->
                val sec = visible[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .omegaGlassCard(16.dp)
                        .clickable { viewModel.openSettingsPage(sec.index) }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sec.title, color = OmegaColors.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(sec.subtitle, color = OmegaColors.MutedSlate, fontSize = 12.sp)
                    }
                    Text("→", color = OmegaColors.NeonCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
            return@LazyColumn
        }

        // Render active category page 0..7
        when (page) {
            0 -> item { ApiSlotManagerSection(uiState, viewModel) }
            1 -> item { VoicePreferencesSection(uiState, viewModel) }
            2 -> item { ModelDiscoverySection(uiState, viewModel) }
            3 -> item { BackgroundBatterySection(uiState, viewModel) }
            4 -> item {
                PermissionsSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onRequestRuntimePerms = {
                        val list = mutableListOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.CAMERA,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.READ_CALENDAR
                        )
                        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
                        multiPermLauncher.launch(list.toTypedArray())
                    }
                )
            }
            5 -> item { PrivacySecuritySection(uiState, viewModel) }
            6 -> item { SelfUpgradeSection(uiState, viewModel) }
            7 -> item { AboutSection() }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun ApiSlotManagerSection(uiState: PanduUiState, viewModel: PanduViewModel) {
    val s = uiState.settings
    var gemKey by remember(s.geminiApiKey) { mutableStateOf(s.geminiApiKey) }
    var oaiKey by remember(s.openAiApiKey) { mutableStateOf(s.openAiApiKey) }
    var sarvamKey by remember(s.sarvamApiKey) { mutableStateOf(s.sarvamApiKey) }
    var showKeys by remember { mutableStateOf(false) }

    var newProvider by remember { mutableStateOf(SlotProvider.GEMINI) }
    var newLabel by remember { mutableStateOf("") }
    var newSlotKey by remember { mutableStateOf("") }
    var newSlotPaid by remember { mutableStateOf(false) }

    val visual = if (showKeys) VisualTransformation.None else PasswordVisualTransformation()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(16.dp).padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Primary Provider Keys (Auto-Saved)", color = OmegaColors.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(if (showKeys) "Hide" else "Show", color = OmegaColors.NeonCyan, fontSize = 12.sp, modifier = Modifier.clickable { showKeys = !showKeys })
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = gemKey,
                onValueChange = { gemKey = it; viewModel.updateSettings { cur -> cur.copy(geminiApiKey = it) } },
                label = { Text("Google Gemini Direct API Key") },
                visualTransformation = visual,
                colors = omegaTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = oaiKey,
                onValueChange = { oaiKey = it; viewModel.updateSettings { cur -> cur.copy(openAiApiKey = it) } },
                label = { Text("OpenAI GPT API Key") },
                visualTransformation = visual,
                colors = omegaTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = sarvamKey,
                onValueChange = { sarvamKey = it; viewModel.updateSettings { cur -> cur.copy(sarvamApiKey = it) } },
                label = { Text("Sarvam AI Voice API Key") },
                visualTransformation = visual,
                colors = omegaTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(16.dp).padding(14.dp)) {
            Text("Configured API Slots (${uiState.slots.size})", color = OmegaColors.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            uiState.slots.forEach { slot ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(OmegaColors.ElevatedNavy)
                        .padding(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${slot.label} (${slot.provider.label})", color = OmegaColors.IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Key: ${slot.maskedKey()} • ${slot.status.label} • ${slot.lastLatencyMs}ms", color = OmegaColors.MutedSlate, fontSize = 11.sp)
                            if (slot.lastError.isNotBlank()) {
                                Text(slot.lastError, color = OmegaColors.AmberWarn, fontSize = 11.sp)
                            }
                        }
                        Text(
                            text = if (slot.enabled) "ON" else "OFF",
                            color = if (slot.enabled) OmegaColors.EmeraldLive else OmegaColors.MutedSlate,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { viewModel.toggleSlot(slot.id, !slot.enabled) }.padding(horizontal = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.testSlotConnection(slot) }) {
                            Text("Test Slot", color = OmegaColors.NeonCyan, fontSize = 11.sp)
                        }
                        if (!slot.id.startsWith("primary_")) {
                            OutlinedButton(onClick = { viewModel.deleteSlot(slot.id) }) {
                                Text("Remove", color = OmegaColors.CrimsonErr, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            uiState.diagnosticResult?.let { diag ->
                Text(
                    text = diag.summary,
                    color = if (diag.isSuccess) OmegaColors.EmeraldLive else OmegaColors.CrimsonErr,
                    fontSize = 12.sp
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(16.dp).padding(14.dp)) {
            Text("Add Extra Backup API Slot", color = OmegaColors.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                SlotProvider.entries.forEach { p ->
                    FilterChip(
                        selected = newProvider == p,
                        onClick = { newProvider = p; newSlotPaid = p.defaultPaid },
                        label = { Text(p.name, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OmegaColors.NeonBlue, selectedLabelColor = OmegaColors.IceWhite)
                    )
                }
            }
            OutlinedTextField(value = newLabel, onValueChange = { newLabel = it }, label = { Text("Slot Label") }, colors = omegaTextFieldColors(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = newSlotKey, onValueChange = { newSlotKey = it }, label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), colors = omegaTextFieldColors(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            BrainToggleRow("Paid Account / Tier Key", newSlotPaid) { newSlotPaid = it }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    if (newSlotKey.isNotBlank()) {
                        viewModel.upsertSlot(
                            ApiSlot(
                                label = newLabel.ifBlank { "${newProvider.name} Extra" },
                                provider = newProvider,
                                apiKey = newSlotKey.trim(),
                                isPaidTier = newSlotPaid
                            )
                        )
                        newLabel = ""
                        newSlotKey = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add API Slot") }
        }
    }
}

@Composable
private fun VoicePreferencesSection(uiState: PanduUiState, viewModel: PanduViewModel) {
    Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(16.dp).padding(14.dp)) {
        val s = uiState.settings
        BrainToggleRow("Online Wake Priority (Sarvam Saaras V4)", s.onlineWakePriority) { checked -> viewModel.updateSettings { it.copy(onlineWakePriority = checked) } }
        BrainToggleRow("Cloud Audio Streaming Consent", s.cloudAudioConsentGranted) { checked -> viewModel.updateSettings { it.copy(cloudAudioConsentGranted = checked) } }
        BrainToggleRow("Screen-Lock Local Wake ONLY", s.screenLockWakeEnabled) { checked -> viewModel.updateSettings { it.copy(screenLockWakeEnabled = checked) } }
        BrainToggleRow("Auto-Speak Replies via Sarvam", s.autoSpeak) { checked -> viewModel.updateSettings { it.copy(autoSpeak = checked) } }
        BrainToggleRow("Live Audio Session Mode", s.useLiveSessionAudio) { checked -> viewModel.updateSettings { it.copy(useLiveSessionAudio = checked) } }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { viewModel.navigateTo(JarvisTab.VOICE) }, modifier = Modifier.fillMaxWidth()) {
            Text("Open Full Voice Tab", color = OmegaColors.NeonCyan)
        }
    }
}

@Composable
private fun ModelDiscoverySection(uiState: PanduUiState, viewModel: PanduViewModel) {
    val s = uiState.settings
    Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(16.dp).padding(14.dp)) {
        BrainToggleRow("Free Model Priority", s.freeModelPriority) { checked -> viewModel.updateSettings { it.copy(freeModelPriority = checked) } }
        BrainToggleRow("Authorize Paid Models (Explicit)", s.allowPaidModels) { checked -> viewModel.updateSettings { it.copy(allowPaidModels = checked) } }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.discoverAllModels() },
            colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Discover Gemini & OpenAI Models Now") }
        Spacer(Modifier.height(8.dp))
        Text("Discovered Gemini (${uiState.discoveredGeminiModels.size}): ${uiState.discoveredGeminiModels.take(6).joinToString()}", color = OmegaColors.MutedSlate, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text("Discovered OpenAI (${uiState.discoveredOpenAiModels.size}): ${uiState.discoveredOpenAiModels.take(6).joinToString()}", color = OmegaColors.MutedSlate, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Text(uiState.telemetry.diagnosticSummary(), color = OmegaColors.NeonCyan, fontSize = 11.sp)
    }
}

@Composable
private fun BackgroundBatterySection(uiState: PanduUiState, viewModel: PanduViewModel) {
    Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(16.dp).padding(14.dp)) {
        Text("Service Status: ${uiState.bgServiceStatus}", color = OmegaColors.NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Battery Optimization Ignored: ${if (uiState.batteryOptIgnored) "Yes" else "No (Recommended ON)"}", color = OmegaColors.IceWhite, fontSize = 12.sp)
        Text("Free Memory: ${uiState.ramFreeMb} MB / ${uiState.ramTotalMb} MB", color = OmegaColors.MutedSlate, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { viewModel.openBatteryOptimizationSettings() }, colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue), modifier = Modifier.weight(1f)) {
                Text("Unrestricted Battery", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { viewModel.stopBackgroundVoiceService() }, modifier = Modifier.weight(1f)) {
                Text("Stop Service", color = OmegaColors.CrimsonErr, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PermissionsSection(
    uiState: PanduUiState,
    viewModel: PanduViewModel,
    onRequestRuntimePerms: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(16.dp).padding(14.dp)) {
        Text("Android Permissions Status", color = OmegaColors.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        PermRow("Microphone (RECORD_AUDIO)", uiState.micPermGranted)
        PermRow("Notifications (POST_NOTIFICATIONS)", uiState.notifPermGranted)
        PermRow("Camera (Vision AI)", uiState.cameraPermGranted)
        PermRow("Contacts (Dial Confirmation)", uiState.contactsPermGranted)
        PermRow("Phone Call Intent", uiState.phonePermGranted)
        PermRow("Calendar Read", uiState.calendarPermGranted)
        PermRow("Accessibility Service", uiState.accessibilityConnected)
        PermRow("Notification Listener", uiState.notificationAccessGranted)

        Spacer(Modifier.height(10.dp))
        Button(onClick = onRequestRuntimePerms, colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue), modifier = Modifier.fillMaxWidth()) {
            Text("Grant Runtime Permissions")
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { viewModel.openAccessibilitySettings() }, modifier = Modifier.weight(1f)) {
                Text("Accessibility", color = OmegaColors.NeonCyan, fontSize = 12.sp)
            }
            OutlinedButton(onClick = { viewModel.openNotificationListenerSettings() }, modifier = Modifier.weight(1f)) {
                Text("Notifications", color = OmegaColors.NeonCyan, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { viewModel.openAppDetailsSettings() }, modifier = Modifier.fillMaxWidth()) {
            Text("App System Settings (Android 16 Restricted)", color = OmegaColors.MutedSlate, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PermRow(label: String, granted: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = OmegaColors.IceWhite, fontSize = 12.sp)
        Text(if (granted) "GRANTED" else "NEEDED", color = if (granted) OmegaColors.EmeraldLive else OmegaColors.AmberWarn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrivacySecuritySection(uiState: PanduUiState, viewModel: PanduViewModel) {
    val s = uiState.settings
    Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(16.dp).padding(14.dp)) {
        BrainToggleRow("Confirm Sensitive Phone Actions", s.confirmSensitiveActions) { checked -> viewModel.updateSettings { it.copy(confirmSensitiveActions = checked) } }
        BrainToggleRow("Biometric Safety Lock (Replies/Routines)", s.biometricSafetyLock) { checked -> viewModel.updateSettings { it.copy(biometricSafetyLock = checked) } }
        BrainToggleRow("Microphone Privacy Mute (Instant Kill)", s.micPrivacyMuted) { checked -> viewModel.updateSettings { it.copy(micPrivacyMuted = checked) } }
        Spacer(Modifier.height(8.dp))
        Text("All API keys and tokens are encrypted with Android Keystore AES-256-GCM. Screen-lock wake runs locally offline only.", color = OmegaColors.MutedSlate, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { viewModel.clearSessionContext() }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear Temporary Live Session Context", color = OmegaColors.NeonCyan)
        }
    }
}

@Composable
private fun SelfUpgradeSection(uiState: PanduUiState, viewModel: PanduViewModel) {
    val s = uiState.settings
    var slug by remember(s.releaseRepoSlug) { mutableStateOf(s.releaseRepoSlug) }
    var tok by remember(s.releaseAuthToken) { mutableStateOf(s.releaseAuthToken) }

    Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(16.dp).padding(14.dp)) {
        Text("Secure Automatic Self-Upgrade", color = OmegaColors.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Verifies SHA-256 & matches APK signing certificate before launching OS installer.", color = OmegaColors.MutedSlate, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = slug, onValueChange = { slug = it; viewModel.updateSettings { cur -> cur.copy(releaseRepoSlug = it) } }, label = { Text("GitHub Repo (owner/repo)") }, colors = omegaTextFieldColors(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = tok, onValueChange = { tok = it; viewModel.updateSettings { cur -> cur.copy(releaseAuthToken = it) } }, label = { Text("Release Read Token (Optional)") }, visualTransformation = PasswordVisualTransformation(), colors = omegaTextFieldColors(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.checkSignedUpdates() },
            enabled = !uiState.isCheckingUpdate,
            colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (uiState.isCheckingUpdate) "Checking Signed Release..." else "Check for Verified Update") }

        uiState.availableRelease?.takeIf { it.isNewerThanInstalled }?.let {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.downloadVerifyAndInstall() },
                colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.EmeraldLive),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Download, Verify & Install ${it.tagName}") }
        }
        uiState.updateStatusMessage?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = OmegaColors.NeonCyan, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AboutSection() {
    Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(16.dp).padding(14.dp)) {
        Text(CreatorIdentity.APP_NAME, color = OmegaColors.IceWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Version: ${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})", color = OmegaColors.NeonCyan, fontSize = 12.sp)
        Text("Developer: ${CreatorIdentity.CREATOR}", color = OmegaColors.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Package: ${BuildConfig.APPLICATION_ID}", color = OmegaColors.MutedSlate, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Creator Attribution: Developed by ${CreatorIdentity.CREATOR}. Underlying AI & voice models are provided by Google (Gemini), OpenAI (GPT), and Sarvam AI.",
            color = OmegaColors.IceWhite.copy(alpha = 0.9f),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Open-Source Offline Fallbacks: Vosk Speech Recognition (Apache-2.0) and Silero VAD (MIT).",
            color = OmegaColors.MutedSlate,
            fontSize = 11.sp
        )
    }
}
