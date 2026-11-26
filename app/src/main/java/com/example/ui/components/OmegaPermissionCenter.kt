package com.example.ui.components

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.control.DeviceControlState
import com.example.omega.OmegaWakeService
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudOnlineGreen
import com.example.ui.theme.HudWarningAmber
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.SlateCyan

/**
 * Real Android permission and special-access status. Every action is user-triggered.
 * App launching Camera/Dialer/Messages does not need CAMERA/CALL_PHONE/READ_SMS.
 * No fake permission "connected" statuses and no attempt to silently grant access.
 */
@Composable
fun OmegaPermissionCenter(
    controlState: DeviceControlState,
    onMicPermissionChanged: (Boolean) -> Unit,
    onRefreshDeviceState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var updateCount by remember { mutableIntStateOf(0) }
    var showAccessibilityConsent by remember { mutableStateOf(false) }
    var notificationHint by remember { mutableStateOf("") }
    val wakeActive by OmegaWakeService.active.collectAsState()

    fun openSystemSettings(intent: Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure { 
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"))
                    )
                }
            }
    }
    fun appSettingsIntent() = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )

    fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 26) {
            openSystemSettings(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        } else openSystemSettings(appSettingsIntent())
    }

    val micRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onMicPermissionChanged(granted)
        updateCount++
    }
    val notificationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationHint = if (granted) "Notification permission granted." else
            "Notifications denied. Open Android notification settings if needed."
        updateCount++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateCount++
                onRefreshDeviceState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { onRefreshDeviceState() }

    // updateCount is deliberately read so the UI checks permissions again
    // when the user returns from Settings or completes the runtime dialog.
    val refreshToken = updateCount
    val micAllowed = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    val notificationsAllowed = (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    val brightnessAllowed = Settings.System.canWrite(context)
    val batteryManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val batteryUnrestricted = Build.VERSION.SDK_INT < 23 ||
        batteryManager.isIgnoringBatteryOptimizations(context.packageName)
    val accessibilityStatus = when {
        controlState.isAccessibilityConnected -> "Connected to running app"
        controlState.isAccessibilityEnabled -> "Enabled in Android, not connected"
        else -> "Not enabled"
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("omega_permission_center"),
        shape = MaterialTheme.shapes.large,
        color = JarvisSurface,
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = .65f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("OMEGA • PERMISSION CENTER",
                style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
            Text("Actual Android permission status. Choose what to allow; no access is granted automatically.",
                style = MaterialTheme.typography.bodySmall, color = SlateCyan)
            Text("Status refreshed on return from Android Settings ($refreshToken)",
                style = MaterialTheme.typography.labelSmall, color = SlateCyan)

            PermissionStatusRow(
                title = "Microphone",
                detail = "Required for voice recognition and Hey JARVIS.",
                status = if (micAllowed) "Allowed" else "Not allowed",
                allowed = micAllowed,
                action = if (micAllowed) "Manage" else "Allow",
                onAction = {
                    if (micAllowed) openSystemSettings(appSettingsIntent())
                    else micRequest.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
            PermissionStatusRow(
                title = "Notifications",
                detail = "Required for background listening controls on Android 13+.",
                status = if (notificationsAllowed) "Allowed" else "Blocked / not allowed",
                allowed = notificationsAllowed,
                action = if (notificationsAllowed) "Manage" else "Allow",
                onAction = {
                    if (notificationsAllowed || Build.VERSION.SDK_INT < 33) {
                        openNotificationSettings()
                    } else notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
            if (notificationHint.isNotBlank()) {
                Text(notificationHint, style = MaterialTheme.typography.bodySmall,
                    color = SlateCyan)
                OutlinedButton(onClick = { openNotificationSettings() }) {
                    Text("Android notification settings")
                }
            }
            PermissionStatusRow(
                title = "Accessibility • Phone Navigation",
                detail = "Optional. Android must enable this service. AI Phone Agent uses separate consent.",
                status = accessibilityStatus,
                allowed = controlState.isAccessibilityConnected,
                action = "Open Settings",
                onAction = { showAccessibilityConsent = true }
            )
            PermissionStatusRow(
                title = "Modify System Settings",
                detail = "Optional special access for changing screen brightness.",
                status = if (brightnessAllowed) "Allowed" else "Not allowed",
                allowed = brightnessAllowed,
                action = "Open Settings",
                onAction = {
                    openSystemSettings(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${context.packageName}"))
                    )
                }
            )
            PermissionStatusRow(
                title = "Battery Optimization",
                detail = "Optional setting for background operation; Android may still stop microphone sessions.",
                status = if (batteryUnrestricted) "Unrestricted / exempt" else "Optimized",
                allowed = batteryUnrestricted,
                action = "Battery Settings",
                onAction = {
                    openSystemSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            )
            PermissionStatusRow(
                title = "Offline wake foreground service",
                detail = "User-started microphone service. Android 16 background behavior needs phone testing.",
                status = if (wakeActive) "Running" else "Stopped",
                allowed = wakeActive,
                action = "App Info",
                onAction = { openSystemSettings(appSettingsIntent()) }
            )
            OutlinedButton(
                onClick = { 
                    updateCount++
                    onRefreshDeviceState()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Refresh actual permission status") }

            Text(
                "Camera, Phone and Messages shortcuts open other apps; they do not require direct " +
                    "camera, call or SMS data access. Accessibility can only be enabled by you in Android Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateCyan
            )
        }
    }

    if (showAccessibilityConsent) {
        AlertDialog(
            onDismissRequest = { showAccessibilityConsent = false },
            title = { Text("Enable optional Accessibility Service?") },
            text = {
                Text("This service can inspect visible interface labels to support an explicitly approved " +
                    "AI Phone Agent task, and can perform navigation after confirmation. " +
                    "Passwords and selected sensitive apps are excluded by the app. " +
                    "Only Android Settings can enable it. Continue?")
            },
            confirmButton = {
                Button(onClick = {
                    showAccessibilityConsent = false
                    openSystemSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Open Android Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityConsent = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    detail: String,
    status: String,
    allowed: Boolean,
    action: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = JarvisSurfaceElevated
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = SlateCyan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(status,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (allowed) HudOnlineGreen else HudWarningAmber
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onAction) {
                    Text(action)
                }
            }
        }
    }
}
