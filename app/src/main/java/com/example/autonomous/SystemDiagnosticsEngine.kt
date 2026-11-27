package com.example.autonomous

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.control.PanduAccessibilityService
import com.example.data.ApiQuotaTracker
import com.example.data.OmegaSettings
import com.example.security.CrashRecoveryHandler

data class DiagnosticIssue(
    val severity: String, // "CRITICAL" | "WARN" | "INFO"
    val category: String,
    val title: String,
    val repairSuggestion: String
)

data class SystemHealthReport(
    val score: Int,
    val batteryPercent: Int,
    val batteryTempC: Float,
    val ramUsedPercent: Int,
    val avgApiLatencyMs: Long,
    val crashCount: Int,
    val issues: List<DiagnosticIssue>,
    val generatedAtMs: Long = System.currentTimeMillis()
)

class SystemDiagnosticsEngine(private val context: Context) {

    fun runFullDiagnostics(settings: OmegaSettings, ttsStatus: String, voiceDiag: String): SystemHealthReport {
        val issues = mutableListOf<DiagnosticIssue>()
        var score = 100

        // 1. Crash Log Analysis
        val rawLogs = CrashRecoveryHandler.readLogs(context)
        val crashCount = Regex("""=== CRASH \[""").findAll(rawLogs).count()
        if (crashCount > 0) {
            score -= (crashCount * 8).coerceAtMost(24)
            val hint = when {
                rawLogs.contains("NullPointerException") -> "Null safety check needed in recent crash trace."
                rawLogs.contains("OutOfMemoryError") -> "Reduce bitmap/cache allocation."
                rawLogs.contains("SecurityException") -> "Missing runtime permission guard."
                else -> "Inspect crash stack trace in System Logs or run Self-Coding bug-fix."
            }
            issues.add(DiagnosticIssue("WARN", "Crash Analyzer", "$crashCount crash(es) recorded", hint))
        }

        // 2. Voice Engine Health
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            score -= 18
            issues.add(DiagnosticIssue("CRITICAL", "Voice", "Microphone permission missing", "Grant RECORD_AUDIO in System > Permissions."))
        }
        if (settings.micPrivacyMuted) {
            score -= 10
            issues.add(DiagnosticIssue("WARN", "Voice", "Microphone Privacy Mute is ON", "Turn off Mic Mute so voice & Hey Jarvis can listen."))
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            score -= 12
            issues.add(DiagnosticIssue("WARN", "Voice", "SpeechRecognizer service unavailable", "Enable Google Speech Services in Android Settings."))
        }
        if (ttsStatus.contains("unavailable", ignoreCase = true)) {
            score -= 10
            issues.add(DiagnosticIssue("WARN", "Voice", "TTS speaker engine unavailable", "Install/enable Android Text-to-Speech output."))
        }

        // 3. API Error & Model Diagnosis
        val hasGemini = settings.effectiveGeminiKey().isNotBlank()
        val hasOr = settings.effectiveOpenRouterKey().isNotBlank() || settings.effectiveSecondaryGatewayKey().isNotBlank()
        if (!hasGemini && !hasOr) {
            score -= 20
            issues.add(DiagnosticIssue("CRITICAL", "AI API", "No cloud API key configured", "Add Gemini or Smart Gateway key."))
        }
        val certSha = com.example.security.VerifiedApkUpdater.installedSigningCertSha256(context)
        if (certSha != null) {
            issues.add(DiagnosticIssue("INFO", "APK Signing", "Signing cert SHA-256: ${certSha.take(16)}...", "Pin this SHA-256 in RELEASE_CERT_SHA256 for conflict-free updates."))
        }
        if (settings.openRouterModel.contains(":batch", ignoreCase = true)) {
            score -= 12
            issues.add(DiagnosticIssue("CRITICAL", "AI API", "Async ':batch' gateway model selected", "Select a discovered synchronous chat model."))
        }
        val telemetry = ApiQuotaTracker.snapshot.value
        if (telemetry.totalRequests >= 3 && telemetry.failedRequests * 2 >= telemetry.totalRequests) {
            score -= 10
            issues.add(DiagnosticIssue("WARN", "AI API", "High API failure rate (${telemetry.failedRequests}/${telemetry.totalRequests})", "Run Ping test or tap Fetch Models in AI & Keys."))
        }

        // 4. Permission Audit
        val hasAcc = PanduAccessibilityService.instance != null
        val hasNotif = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (!hasAcc) {
            issues.add(DiagnosticIssue("INFO", "Permissions", "Cross-App Automation Service OFF", "Enable in Accessibility for HUD & screen actions."))
        }
        if (!hasNotif) {
            issues.add(DiagnosticIssue("INFO", "Permissions", "Notification Listener OFF", "Enable to read/reply to WhatsApp & SMS hands-free."))
        }

        // 5. Battery, Thermal & Memory Bottlenecks
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batPct = FinalIntegrationPolicy.battery(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        val batIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempC = FinalIntegrationPolicy.temperature(batIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE))

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val ramUsedPct = FinalIntegrationPolicy.ram(memInfo.totalMem, memInfo.availMem)
        if (batPct < 0 || !tempC.isFinite() || ramUsedPct < 0) issues.add(DiagnosticIssue("INFO", "Device metrics", "Some hardware readings are unavailable", "Review Android system diagnostics; unavailable values are not treated as healthy."))

        if (tempC >= 43f) {
            score -= 8
            issues.add(DiagnosticIssue("WARN", "Thermal", "Device running warm (${tempC}°C)", "Pause voice sessions until temperature drops."))
        }
        if (ramUsedPct >= 88) {
            score -= 8
            issues.add(DiagnosticIssue("WARN", "Memory", "High system RAM usage ($ramUsedPct%)", "Clear chat history or close heavy background apps."))
        }
        if (telemetry.averageLatencyMs > 4500L) {
            score -= 5
            issues.add(DiagnosticIssue("INFO", "Performance", "Slow API latency (${telemetry.averageLatencyMs}ms)", "Switch to a faster cloud model."))
        }

        if (issues.isEmpty()) {
            issues.add(DiagnosticIssue("INFO", "Health", "No issues detected in this snapshot", "This is a limited diagnostic snapshot, not a live functional test."))
        }

        return SystemHealthReport(
            score = score.coerceIn(10, 100),
            batteryPercent = batPct,
            batteryTempC = tempC,
            ramUsedPercent = ramUsedPct,
            avgApiLatencyMs = telemetry.averageLatencyMs,
            crashCount = crashCount,
            issues = issues
        )
    }
}
