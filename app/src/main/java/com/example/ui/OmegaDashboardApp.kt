package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.ui.theme.OmegaColors
import com.example.ui.theme.OmegaNavyBackdrop
import com.example.ui.theme.omegaGlassCard

@Composable
fun OmegaDashboardApp(viewModel: PanduViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) viewModel.attachVisionBitmap(bitmap)
    }

    val camPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    val openCameraSafely: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) cameraLauncher.launch(null) else camPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(uiState.cameraLaunchRequestCount) {
        if (uiState.cameraLaunchRequestCount > 0) {
            openCameraSafely()
        }
    }

    OmegaNavyBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Compact Top Identity Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "JARVIS INFINITY",
                        color = OmegaColors.IceWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "${BuildConfig.VERSION_NAME} • ${uiState.aiActivityState.label}",
                        color = OmegaColors.NeonCyan,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val activeNow = uiState.isListening || uiState.isThinking || uiState.isSpeaking ||
                    uiState.liveDuplexState.isConnected || uiState.openAiLiveState.isConnected
                if (activeNow) {
                    IconButton(
                        onClick = { viewModel.stopAllActivity() },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(OmegaColors.CrimsonErr.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Stop All", tint = OmegaColors.CrimsonErr)
                    }
                }
            }

            // Dismissible Status Toast Banner
            uiState.statusBanner?.let { banner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .omegaGlassCard(cornerRadius = 12.dp, highlighted = true)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = banner,
                        color = OmegaColors.IceWhite,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "OK",
                        color = OmegaColors.NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.clearBanner() }
                            .padding(start = 10.dp)
                    )
                }
            }

            // Main Content Area (Strictly 6 Primary Tabs — Section 14)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (uiState.currentTab) {
                    JarvisTab.HOME -> HomeTabScreen(uiState = uiState, viewModel = viewModel, onOpenCamera = openCameraSafely)
                    JarvisTab.DASHBOARD -> DashboardTabScreen(uiState = uiState, viewModel = viewModel)
                    JarvisTab.AI_BRAIN -> AiBrainTabScreen(uiState = uiState, viewModel = viewModel)
                    JarvisTab.VOICE -> VoiceTabScreen(uiState = uiState, viewModel = viewModel)
                    JarvisTab.CONTROL -> ControlTabScreen(uiState = uiState, viewModel = viewModel, onOpenCamera = openCameraSafely)
                    JarvisTab.SETTINGS -> SettingsTabScreen(uiState = uiState, viewModel = viewModel)
                }
            }

            // 6-Item Bottom Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .omegaGlassCard(cornerRadius = 24.dp, highlighted = true)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                JarvisTab.entries.forEach { tab ->
                    val selected = uiState.currentTab == tab
                    val icon = when (tab) {
                        JarvisTab.HOME -> Icons.Rounded.Home
                        JarvisTab.DASHBOARD -> Icons.Rounded.Dashboard
                        JarvisTab.AI_BRAIN -> Icons.Rounded.Memory
                        JarvisTab.VOICE -> Icons.Rounded.GraphicEq
                        JarvisTab.CONTROL -> Icons.Rounded.Tune
                        JarvisTab.SETTINGS -> Icons.Rounded.Settings
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) OmegaColors.NeonBlue.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { viewModel.navigateTo(tab) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.label,
                            tint = if (selected) OmegaColors.NeonCyan else OmegaColors.MutedSlate,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            color = if (selected) OmegaColors.IceWhite else OmegaColors.MutedSlate,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // Explicit Notification Reply Confirmation Dialog (Calls & Messages protection)
    uiState.pendingNotifReply?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingNotifReply() },
            containerColor = OmegaColors.SurfaceNavy,
            title = { Text("Confirm Notification Reply", color = OmegaColors.IceWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("To: ${pending.senderOrApp}", color = OmegaColors.NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(pending.replyText, color = OmegaColors.IceWhite, fontSize = 14.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingNotifReply(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = OmegaColors.NeonBlue)
                ) { Text("Confirm Send") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingNotifReply() }) {
                    Text("Cancel", color = OmegaColors.MutedSlate)
                }
            }
        )
    }
}
