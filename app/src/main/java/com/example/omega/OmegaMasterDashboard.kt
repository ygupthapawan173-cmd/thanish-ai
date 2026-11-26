package com.example.omega

import android.app.ActivityManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.EncryptedApiKeyStore
import com.example.ai.PanduAiRepository
import com.example.control.PanduAccessibilityService

/** Live OS and app data only; stored API key is never presented as ONLINE. */
@Composable
fun OmegaMasterDashboard(
    noteCount: Int,
    onVoice: () -> Unit,
    onMemory: () -> Unit,
    onSystem: () -> Unit,
    onSettings: (() -> Unit)?
) {
    val c = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val awake by OmegaWakeService.active.collectAsState()
    val successfulRoute by PanduAiRepository.lastRoute.collectAsState()
    val wakeState by OmegaWakeService.status.collectAsState()
    val a11y by PanduAccessibilityService.isConnected.collectAsState()
    val vault = remember(c) { EncryptedApiKeyStore(c) }
    val selected = remember(revision) { vault.getActiveProvider() }
    val model = remember(revision) { vault.getModel(selected) }
    val hasKey = remember(revision) { vault.hasApiKey(selected) }
    val battery = remember(revision) {
        c.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    val freeRam = remember(revision) {
        val info = ActivityManager.MemoryInfo()
        c.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        info.availMem / 1048576L
    }
    val microphone = remember(revision) {
        ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("OMEGA · MASTER CONTROL", style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF4FEAFF))
        Text("Actual configuration and device telemetry. No simulated online status.",
            style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CBED2))
        MasterMetric("AI Intelligence", selected.title + " · " + model,
            if (hasKey) "Encrypted key configured · connection not tested here"
            else "API key not configured")
        MasterMetric("Last successful AI route", successfulRoute,
            "Updated only when a provider returns a real completed response")
        MasterMetric("Neural Voice / Wake",
            if (awake) "Mic foreground service running" else "Wake mic stopped", wakeState)
        MasterMetric("Device & Memory",
            "Battery " + (if (battery in 0..100) "$battery%" else "unavailable") +
                " · Available RAM " + freeRam + " MB",
            "$noteCount encrypted notebook entries")
        MasterMetric("Phone Control & Privacy",
            "Microphone " + if (microphone) "allowed" else "not allowed",
            "Accessibility " + if (a11y) "connected" else "not connected")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onVoice, modifier = Modifier.weight(1f)) { Text("Voice") }
            OutlinedButton(onClick = onMemory, modifier = Modifier.weight(1f)) { Text("Memory") }
            OutlinedButton(onClick = onSystem, modifier = Modifier.weight(1f)) { Text("System") }
        }
        if (onSettings != null) {
            Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text("AI, Language & Permission Settings")
            }
        }
        OutlinedButton(onClick = { revision++ }, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh real system status")
        }
    }
}

@Composable
private fun MasterMetric(title: String, value: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF0B233E),
        border = BorderStroke(1.dp, Color(0xFF12CAE6).copy(alpha = .55f))
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color(0xFF42DFFC), style = MaterialTheme.typography.labelLarge)
            Text(value, color = Color(0xFFF3F8FF), style = MaterialTheme.typography.bodyLarge)
            Text(detail, color = Color(0xFFA3BCCE), style = MaterialTheme.typography.bodySmall)
        }
    }
}
