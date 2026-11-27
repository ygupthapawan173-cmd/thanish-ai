package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autonomous.AntigravityTaskHandoff
import com.example.autonomous.TermuxControlHelper
import com.example.data.OmegaSettings
import com.example.ui.theme.OmegaColors
import com.example.ui.theme.omegaTextFieldColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NavyBgTop = Color(0xFF050B18)
private val NavyBgBottom = Color(0xFF0A1428)
private val NavyCard = Color(0xFF0E1B36)
private val NeonCyan = Color(0xFF00F0FF)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun AutonomousLabScreen(
    uiState: PanduUiState,
    onBack: () -> Unit,
    onSaveSettings: ((OmegaSettings) -> OmegaSettings) -> Unit,
    onRunDiagnostics: () -> Unit,
    onReconnectAi: () -> Unit = {},
    onStartSelfUpgrade: (String, String) -> Unit,
    onEmergencyStop: () -> Unit,
    onCheckReleaseUpdate: () -> Unit,
    onApproveAndInstallUpdate: () -> Unit,
    onAnalyzeLastCrash: () -> Unit = {},
    onGenerateHandoff: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onAutoUpdateCheckAndDownload: () -> Unit = {},
    onRefreshControlCenter: () -> Unit = {},
    onLaunchTermux: () -> Unit = {},
    onLaunchTermuxHandoff: () -> Unit = {},
    onOpenBatterySettings: (String) -> Unit = {},
    onOpenInstallSettings: () -> Unit = {},
    onPrepareRollback: () -> Unit = {},
    onRestoreRollback: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings = uiState.settings
    val theme = settings.visualTheme
    val preset = settings.orbColorPreset
    val tfColors = omegaTextFieldColors(theme, preset)

    var repoDraft by remember(settings.githubRepoSlug) { mutableStateOf(settings.githubRepoSlug) }
    var tokenDraft by remember(settings.githubToken) { mutableStateOf(settings.githubToken) }
    var featurePrompt by remember { mutableStateOf("") }
    var targetFile by remember { mutableStateOf("app/src/main/java/com/example/ui/OmegaDashboardApp.kt") }
    var selectedCliModel by remember { mutableStateOf(AntigravityTaskHandoff.PERMITTED_CLI_MODELS.first()) }
    var selectedEffort by remember { mutableStateOf("medium") }
    var ghSaved by remember { mutableStateOf(false) }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(Unit) { onRefreshControlCenter() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NavyBgTop, NavyBgBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, theme = theme, preset = preset, onClick = onBack)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text("ANTIGRAVITY CONTROL CENTER", color = TextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("V108 ULTIMATE MASTER AI • ${uiState.lastBrainRouteSummary}", color = NeonCyan, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                GlassCircleButton(Icons.Rounded.Refresh, theme = theme, preset = preset, onClick = onRunDiagnostics)
            }

            if (!uiState.controlCenterStatus.isNullOrBlank()) {
                Text(uiState.controlCenterStatus, color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            // 1. LIVE CONNECTION, QUOTA & MODEL SELECTION
            val conn = uiState.connectionHealth
            ControlCard {
                SectionHeader(Icons.Rounded.Hub, "Live AI Connection & Model Switching", conn.status.name)
                Text("Provider: ${conn.providerLabel} • Quota: ${conn.quotaState} (${conn.latencyMs}ms)", color = TextMuted, fontSize = 11.5.sp)
                Text("Chat: ${conn.activeChatModel.ifBlank { "--" }} • Coding: ${conn.activeCodingModel.ifBlank { "--" }} • TTS: ${conn.activeTtsModel.ifBlank { "--" }}", color = TextPrimary, fontSize = 11.5.sp)
                Text(conn.detail, color = TextMuted, fontSize = 11.sp)

                val availableModels = (uiState.discoveredChatModels.ifEmpty { listOf(settings.geminiChatModel, "gemini-2.5-flash", "gemini-2.5-pro") }).distinct().take(6)
                Text("Active Gemini Coding / Chat Model:", color = TextMuted, fontSize = 11.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableModels.forEach { m ->
                        FilterChip(
                            selected = settings.geminiChatModel == m,
                            onClick = { onSaveSettings { it.copy(geminiChatModel = m) } },
                            label = { Text(m, fontSize = 11.sp, maxLines = 1) }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Zero Paid Credits Protection", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(com.example.ai.ZeroPaidCreditsPolicy.statusLabel(settings.zeroPaidCreditsMode), color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(checked = settings.zeroPaidCreditsMode, onCheckedChange = { enabled -> onSaveSettings { it.copy(zeroPaidCreditsMode = enabled) } })
                }
                Text("Usage: ${uiState.apiTelemetry.diagnosticSummary()}", color = TextMuted, fontSize = 11.sp)

                Button(
                    onClick = onReconnectAi,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Auto-Connect & Recover AI Engine", color = NavyBgTop, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }

            // 2. TERMUX, LIFECYCLE & BATTERY DIAGNOSTICS
            val env = uiState.controlEnv
            ControlCard {
                SectionHeader(Icons.Rounded.Terminal, "Termux, Battery & Install Diagnostics", if (env.termuxInstalled) "TERMUX READY" else "NOT INSTALLED")
                Text(
                    "Termux: ${if (env.termuxInstalled) "Installed (${env.termuxVersion})" else "Not Installed"} • Termux:API: ${if (env.termuxApiInstalled) "Yes" else "No"}",
                    color = TextPrimary, fontSize = 12.sp
                )
                Text(
                    "Battery Optimization Ignored: ${if (env.batteryOptimizationIgnored) "Yes (Unrestricted)" else "No (May sleep)"} • APK Install Permission: ${if (env.canInstallPackages) "Granted" else "Needs Approval"}",
                    color = TextMuted, fontSize = 11.5.sp
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onLaunchTermux,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Launch Termux", color = NavyBgTop, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { onOpenBatterySettings(context.packageName) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("JARVIS Battery", color = NeonCyan, fontSize = 12.sp) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onOpenBatterySettings(TermuxControlHelper.TERMUX_PACKAGE) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Termux Battery", color = TextPrimary, fontSize = 11.5.sp) }
                    OutlinedButton(
                        onClick = onOpenInstallSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Install Permission", color = TextPrimary, fontSize = 11.5.sp) }
                }
            }

            // 3. SYSTEM HEALTH & CRASH BUG-FIX PLANNER
            val report = uiState.healthReport
            ControlCard {
                SectionHeader(Icons.Rounded.HealthAndSafety, "Crash Analysis & Health", "${report?.score ?: "--"}/100")
                if (report != null) {
                    Text(
                        "Battery: ${com.example.autonomous.FinalIntegrationPolicy.percentLabel(report.batteryPercent)} (${com.example.autonomous.FinalIntegrationPolicy.tempLabel(report.batteryTempC)}) • RAM: ${com.example.autonomous.FinalIntegrationPolicy.percentLabel(report.ramUsedPercent)} • Crashes: ${report.crashCount}",
                        color = TextMuted, fontSize = 11.5.sp
                    )
                    report.issues.take(4).forEach { issue ->
                        val col = when (issue.severity) {
                            "CRITICAL" -> OmegaColors.CrimsonErr
                            "WARN" -> OmegaColors.AmberWarn
                            else -> OmegaColors.EmeraldLive
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(col.copy(0.12f))
                                .border(1.dp, col.copy(0.35f), RoundedCornerShape(10.dp)).padding(8.dp)
                        ) {
                            Text("[${issue.severity}] ${issue.category}: ${issue.title}", color = col, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Text("Fix: ${issue.repairSuggestion}", color = TextPrimary, fontSize = 11.sp)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRunDiagnostics,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Run Diagnostics", color = NavyBgTop, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = onAnalyzeLastCrash,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Fix Last Crash", color = NeonCyan, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                }
            }

            // 4. AUTONOMOUS SELF-UPGRADE & MANUAL ANTIGRAVITY CLI HANDOFF
            val up = uiState.upgradeState
            ControlCard {
                SectionHeader(Icons.Rounded.Code, "Autonomous Self-Upgrade & CLI Handoff", "PR GATE")
                OutlinedTextField(repoDraft, { repoDraft = it; ghSaved = false }, label = { Text("GitHub Repo (owner/repo)") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = tfColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tokenDraft, { tokenDraft = it; ghSaved = false }, label = { Text("Least-Privilege GitHub Token (Encrypted)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(12.dp), colors = tfColors, modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = { onSaveSettings { it.copy(githubRepoSlug = repoDraft, githubToken = tokenDraft) }; ghSaved = true },
                    colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.RoyalIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (ghSaved) "Encrypted GitHub Config Saved" else "Save Encrypted GitHub Config", color = Color.White, fontSize = 12.sp) }

                OutlinedTextField(targetFile, { targetFile = it }, label = { Text("1–4 Kotlin paths (app/src/*.kt)") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = tfColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(featurePrompt, { featurePrompt = it }, label = { Text("Self-Upgrade / Bug-Fix Request...") }, shape = RoundedCornerShape(12.dp), colors = tfColors, modifier = Modifier.fillMaxWidth())

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSaveSettings { it.copy(githubRepoSlug = repoDraft, githubToken = tokenDraft) }
                            if (featurePrompt.isNotBlank()) onStartSelfUpgrade(featurePrompt, targetFile)
                        },
                        enabled = !up.isRunning && featurePrompt.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (up.isRunning) "Upgrading (${up.progressPercent}%)..." else "Auto Code, Test & PR", color = NavyBgTop, fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                    if (up.isRunning) {
                        Button(onClick = onEmergencyStop, colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.CrimsonErr), shape = RoundedCornerShape(12.dp)) {
                            Text("STOP", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (up.isRunning || up.stepLogs.isNotEmpty() || up.error != null) {
                    LinearProgressIndicator(progress = { up.progressPercent / 100f }, color = NeonCyan, trackColor = Color.White.copy(0.12f), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
                    Text("Stage: ${up.currentStage} (Repair ${up.repairAttempt}/${up.maxRepairAttempts})", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (up.error != null) Text(up.error, color = OmegaColors.CrimsonErr, fontSize = 12.sp)
                    up.stepLogs.takeLast(6).forEach { line -> Text("• $line", color = TextMuted, fontSize = 11.sp) }
                    if (!up.pullRequestUrl.isNullOrBlank()) {
                        Button(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(up.pullRequestUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                            colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.EmeraldLive),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Review & Approve PR on GitHub", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }

                Text("Antigravity CLI Models (Real Account Availability, useG1Credits:false):", color = TextMuted, fontSize = 11.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AntigravityTaskHandoff.MODEL_CATALOG.forEach { opt ->
                        FilterChip(
                            selected = selectedCliModel == opt.id,
                            onClick = { if (opt.verifiedAvailable) selectedCliModel = opt.id },
                            enabled = opt.verifiedAvailable,
                            label = { Text(opt.label, fontSize = 10.5.sp, maxLines = 1) }
                        )
                    }
                }
                Text("Thinking Effort:", color = TextMuted, fontSize = 11.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AntigravityTaskHandoff.THINKING_EFFORTS.forEach { eff ->
                        FilterChip(selected = selectedEffort == eff, onClick = { selectedEffort = eff }, label = { Text(eff.uppercase(), fontSize = 10.5.sp, maxLines = 1) })
                    }
                }
                OutlinedButton(
                    onClick = { if (featurePrompt.isNotBlank()) onGenerateHandoff(featurePrompt, targetFile, selectedCliModel, selectedEffort) },
                    enabled = featurePrompt.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Generate Manual Antigravity CLI Handoff", color = NeonCyan, fontSize = 12.sp) }

                val bundle = uiState.handoffBundle
                if (bundle != null) {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.05f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(bundle.title, color = OmegaColors.EmeraldLive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(bundle.cliCommand, color = TextMuted, fontSize = 11.sp, maxLines = 4)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { clipboard.setText(AnnotatedString(bundle.cliCommand)) },
                                colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.RoyalIndigo),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("Copy Command", color = Color.White, fontSize = 11.5.sp) }
                            Button(
                                onClick = onLaunchTermuxHandoff,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("Copy & Open Termux", color = NavyBgTop, fontWeight = FontWeight.Bold, fontSize = 11.5.sp) }
                        }
                    }
                }
            }

            // 5. GITHUB BUILD MONITORING & VERIFIED SELF-UPDATER
            ControlCard {
                SectionHeader(Icons.Rounded.VerifiedUser, "GitHub Builds & Verified APK Update", "SHA-256 + CERT")
                if (uiState.recentBuilds.isNotEmpty()) {
                    uiState.recentBuilds.take(3).forEach { run ->
                        Text("• Run #${run.runId}: ${run.status} / ${run.conclusion.ifBlank { "running" }} (${run.headSha.take(7)})", color = TextMuted, fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = { onSaveSettings { it.copy(githubRepoSlug = repoDraft, githubToken = tokenDraft) }; onCheckReleaseUpdate(); onRefreshControlCenter() },
                    enabled = !uiState.isCheckingUpdate,
                    colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.RoyalIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (uiState.isCheckingUpdate) "Checking Releases..." else "Check Verified Release & Builds", color = Color.White, fontWeight = FontWeight.Bold) }

                OutlinedButton(
                    onClick = { onSaveSettings { it.copy(githubRepoSlug = repoDraft, githubToken = tokenDraft) }; onAutoUpdateCheckAndDownload() },
                    enabled = !uiState.isCheckingUpdate,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Auto-Detect, Backup & Stage Verified APK", color = NeonCyan, fontSize = 12.sp) }

                if (uiState.updateStatusMessage != null) Text(uiState.updateStatusMessage, color = NeonCyan, fontSize = 12.sp)
                val rel = uiState.availableRelease
                if (rel != null) {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.06f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Release: ${rel.tagName}", color = OmegaColors.EmeraldLive, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(rel.changelog.take(240), color = TextMuted, fontSize = 11.sp)
                        Text("SHA-256: ${rel.expectedSha256?.take(24) ?: "MISSING (Blocked)"}...", color = TextPrimary, fontSize = 11.sp)
                        Button(
                            onClick = onApproveAndInstallUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.EmeraldLive),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Verify SHA-256 & Install (User Approval)", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            // 6. SAFE ROLLBACK PREPARATION & UPGRADE HISTORY
            ControlCard {
                SectionHeader(Icons.Rounded.History, "Safe Rollback & Upgrade History", "${uiState.rollbackSnapshots.size} SNAPSHOTS")
                Button(
                    onClick = onPrepareRollback,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Prepare Verified Rollback Snapshot", color = NavyBgTop, fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                uiState.rollbackSnapshots.take(3).forEach { snap ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${snap.versionName} (v${snap.versionCode})", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("SHA: ${snap.sha256.take(16)}... • ${timeFmt.format(Date(snap.createdAtMs))}", color = TextMuted, fontSize = 10.5.sp)
                        }
                        TextButton(onClick = { onRestoreRollback(snap.id) }) { Text("Restore", color = NeonCyan, fontSize = 12.sp) }
                    }
                }

                HorizontalDivider(color = Color.White.copy(0.1f))
                if (uiState.auditLogs.isEmpty()) {
                    Text("No upgrade or audit actions logged yet.", color = TextMuted, fontSize = 11.5.sp)
                } else {
                    uiState.auditLogs.take(6).forEach { log ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("[${log.category}] ${log.summary}", color = TextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("${log.status} • ${timeFmt.format(Date(log.timestampMs))}", color = NeonCyan, fontSize = 10.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NavyCard)
            .border(1.dp, NeonCyan.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, badge: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Icon(icon, null, tint = NeonCyan, modifier = Modifier.size(18.dp))
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(NeonCyan.copy(0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(badge, color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
