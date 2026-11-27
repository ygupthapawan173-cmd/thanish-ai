package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OmegaSettings
import com.example.ui.theme.OmegaColors
import com.example.ui.theme.OmegaLiquidBackdrop
import com.example.ui.theme.accentColor
import com.example.ui.theme.mutedText
import com.example.ui.theme.omegaGlassCard
import com.example.ui.theme.omegaTextFieldColors
import com.example.ui.theme.primaryText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AutonomousLabScreen(
    uiState: PanduUiState,
    onBack: () -> Unit,
    onSaveSettings: ((OmegaSettings) -> OmegaSettings) -> Unit,
    onRunDiagnostics: () -> Unit,
    onStartSelfUpgrade: (String, String) -> Unit,
    onEmergencyStop: () -> Unit,
    onCheckReleaseUpdate: () -> Unit,
    onApproveAndInstallUpdate: () -> Unit
) {
    val context = LocalContext.current
    val settings = uiState.settings
    val theme = settings.visualTheme
    val preset = settings.orbColorPreset
    val accent = theme.accentColor(preset)
    val tfColors = omegaTextFieldColors(theme, preset)

    var repoDraft by remember(settings.githubRepoSlug) { mutableStateOf(settings.githubRepoSlug) }
    var tokenDraft by remember(settings.githubToken) { mutableStateOf(settings.githubToken) }
    var featurePrompt by remember { mutableStateOf("") }
    var targetFile by remember { mutableStateOf("app/src/main/java/com/example/ui/OmegaDashboardApp.kt") }
    var ghSaved by remember { mutableStateOf(false) }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    OmegaLiquidBackdrop(theme = theme, preset = preset) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, theme = theme, preset = preset, onClick = onBack)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Autonomous Lab", color = theme.primaryText(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("SELF-DIAGNOSE • SELF-CODE • VERIFIED UPDATE", color = accent, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
                GlassCircleButton(Icons.Rounded.Refresh, theme = theme, preset = preset, onClick = onRunDiagnostics)
            }

            // 1. SELF-DIAGNOSTICS & SYSTEM HEALTH REPORT
            val report = uiState.healthReport
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(22.dp, theme, preset).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.HealthAndSafety, null, tint = accent, modifier = Modifier.size(20.dp))
                        Text("System Health Diagnostics", color = theme.primaryText(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    val scoreColor = when {
                        (report?.score ?: 100) >= 85 -> OmegaColors.EmeraldLive
                        (report?.score ?: 100) >= 60 -> OmegaColors.AmberWarn
                        else -> OmegaColors.CrimsonErr
                    }
                    Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(scoreColor.copy(0.2f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("${report?.score ?: "--"}/100", color = scoreColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (report != null) {
                    Text(
                        "Battery: ${com.example.autonomous.FinalIntegrationPolicy.percentLabel(report.batteryPercent)} (${com.example.autonomous.FinalIntegrationPolicy.tempLabel(report.batteryTempC)}) • RAM Used: ${com.example.autonomous.FinalIntegrationPolicy.percentLabel(report.ramUsedPercent)} • Crashes: ${report.crashCount} • Avg API: ${report.avgApiLatencyMs}ms",
                        color = theme.mutedText(), fontSize = 11.5.sp
                    )
                    report.issues.take(5).forEach { issue ->
                        val col = when (issue.severity) {
                            "CRITICAL" -> OmegaColors.CrimsonErr
                            "WARN" -> OmegaColors.AmberWarn
                            else -> OmegaColors.EmeraldLive
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(col.copy(0.12f))
                                .border(1.dp, col.copy(0.35f), RoundedCornerShape(12.dp)).padding(10.dp)
                        ) {
                            Text("[${issue.severity}] ${issue.category}: ${issue.title}", color = col, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Fix: ${issue.repairSuggestion}", color = theme.primaryText(), fontSize = 11.5.sp)
                        }
                    }
                }
                Button(onClick = onRunDiagnostics, colors = ButtonDefaults.buttonColors(containerColor = accent), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Run Full Self-Diagnostics", color = Color(0xFF040814), fontWeight = FontWeight.Bold)
                }
            }

            // 2. SELF-CODING & GITHUB ENGINEERING ENGINE
            val up = uiState.upgradeState
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(22.dp, theme, preset).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Code, null, tint = accent, modifier = Modifier.size(20.dp))
                        Text("Self-Coding & GitHub Engine", color = theme.primaryText(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Text("PR Approval Required", color = OmegaColors.EmeraldLive, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(repoDraft, { repoDraft = it; ghSaved = false }, label = { Text("GitHub Repo (owner/repo)") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = tfColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tokenDraft, { tokenDraft = it; ghSaved = false }, label = { Text("Least-Privilege GitHub Token (Keystore Encrypted)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp), colors = tfColors, modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        onSaveSettings { it.copy(githubRepoSlug = repoDraft, githubToken = tokenDraft) }
                        ghSaved = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.RoyalIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (ghSaved) "GitHub Credentials Encrypted & Saved" else "Save Encrypted GitHub Config", color = Color.White, fontSize = 12.sp) }

                OutlinedTextField(targetFile, { targetFile = it }, label = { Text("1–4 Kotlin paths, comma separated") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = tfColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(featurePrompt, { featurePrompt = it }, label = { Text("Describe Feature or Bug Fix to Self-Code...") }, shape = RoundedCornerShape(14.dp), colors = tfColors, modifier = Modifier.fillMaxWidth())

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSaveSettings { it.copy(githubRepoSlug = repoDraft, githubToken = tokenDraft) }
                            if (featurePrompt.isNotBlank()) onStartSelfUpgrade(featurePrompt, targetFile)
                        },
                        enabled = !up.isRunning && featurePrompt.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (up.isRunning) "Upgrading (${up.progressPercent}%)..." else "Generate, Build & Open PR", color = Color(0xFF040814), fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                    if (up.isRunning) {
                        Button(onClick = onEmergencyStop, colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.CrimsonErr), shape = RoundedCornerShape(14.dp)) {
                            Text("STOP", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (up.isRunning || up.stepLogs.isNotEmpty() || up.error != null) {
                    LinearProgressIndicator(
                        progress = { up.progressPercent / 100f },
                        color = accent,
                        trackColor = Color.White.copy(0.12f),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    )
                    Text("Stage: ${up.currentStage} (Repair ${up.repairAttempt}/${up.maxRepairAttempts})", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (up.error != null) Text(up.error, color = OmegaColors.CrimsonErr, fontSize = 12.sp)
                    up.stepLogs.takeLast(6).forEach { line ->
                        Text("• $line", color = theme.mutedText(), fontSize = 11.5.sp)
                    }
                    if (!up.pullRequestUrl.isNullOrBlank()) {
                        Button(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(up.pullRequestUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                            colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.EmeraldLive),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Review & Approve PR on GitHub", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            // 3. SECURE SELF-UPDATER (MANDATORY SHA-256 + SIGNING CERT VERIFICATION)
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(22.dp, theme, preset).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.VerifiedUser, null, tint = OmegaColors.EmeraldLive, modifier = Modifier.size(20.dp))
                    Text("Verified Self-Updater (SHA-256 + Cert)", color = theme.primaryText(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Button(
                    onClick = {
                        onSaveSettings { it.copy(githubRepoSlug = repoDraft, githubToken = tokenDraft) }
                        onCheckReleaseUpdate()
                    },
                    enabled = !uiState.isCheckingUpdate,
                    colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.RoyalIndigo),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (uiState.isCheckingUpdate) "Checking Releases..." else "Discover Latest Verified Release", color = Color.White, fontWeight = FontWeight.Bold) }

                if (uiState.updateStatusMessage != null) {
                    Text(uiState.updateStatusMessage, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                val rel = uiState.availableRelease
                if (rel != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(0.06f)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Release: ${rel.tagName}", color = OmegaColors.EmeraldLive, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text(rel.changelog.take(260), color = theme.mutedText(), fontSize = 11.5.sp)
                        Text("SHA-256: ${rel.expectedSha256?.take(24) ?: "MISSING (Blocked)"}...", color = theme.primaryText(), fontSize = 11.sp)
                        Button(
                            onClick = onApproveAndInstallUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.EmeraldLive),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Backup, Verify & Approve Install", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            // 4. AUDIT HISTORY
            Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(22.dp, theme, preset).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Autonomous Audit History", color = theme.primaryText(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (uiState.auditLogs.isEmpty()) {
                    Text("No autonomous actions logged yet.", color = theme.mutedText(), fontSize = 12.sp)
                } else {
                    uiState.auditLogs.take(8).forEach { log ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("[${log.category}] ${log.summary}", color = theme.primaryText(), fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                            Text("${log.status} • ${timeFmt.format(Date(log.timestampMs))}", color = accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
