package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.control.AgentHudBus
import com.example.ui.JarvisTab
import com.example.ui.OmegaDashboardApp
import com.example.ui.PanduViewModel
import com.example.ui.components.InfinityRenderQuality
import com.example.ui.theme.OmegaColors

class MainActivity : ComponentActivity() {

    private val viewModel: PanduViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        handleLaunchIntent(intent)

        setContent {
            val state by viewModel.uiState.collectAsState()
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = OmegaColors.NeonBlue,
                    onPrimary = OmegaColors.IceWhite,
                    secondary = OmegaColors.NeonCyan,
                    onSecondary = OmegaColors.DeepNavy,
                    background = OmegaColors.DeepNavy,
                    surface = OmegaColors.SurfaceNavy,
                    onBackground = OmegaColors.IceWhite,
                    onSurface = OmegaColors.IceWhite
                )
            ) {
                InfinityRenderQuality(state.settings.renderQuality) {
                    OmegaDashboardApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AgentHudBus.setAppInForeground(true)
        viewModel.restoreWakeIfEnabled()
        viewModel.refreshSystemMetrics()
    }

    override fun onPause() {
        super.onPause()
        AgentHudBus.setAppInForeground(false)
    }

    override fun onStop() {
        viewModel.releaseForegroundVoice()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_ASSIST || intent.action == Intent.ACTION_VOICE_COMMAND) {
            viewModel.startVoiceListening()
            return
        }

        if (intent.getBooleanExtra(EXTRA_START_LISTENING, false)) {
            val inlineCommand = intent.getStringExtra(EXTRA_WAKE_COMMAND).orEmpty()
            if (inlineCommand.isNotBlank()) viewModel.handleInlineWakeCommand(inlineCommand)
            else viewModel.startVoiceListening()
            intent.removeExtra(EXTRA_START_LISTENING)
            intent.removeExtra(EXTRA_WAKE_COMMAND)
        }

        if (intent.getBooleanExtra(EXTRA_OPEN_CAMERA, false)) {
            viewModel.navigateTo(JarvisTab.CONTROL)
            viewModel.requestCameraLaunchFromWidget()
            intent.removeExtra(EXTRA_OPEN_CAMERA)
        }

        if (intent.getBooleanExtra(EXTRA_OPEN_DASHBOARD, false)) {
            viewModel.navigateTo(JarvisTab.DASHBOARD)
            intent.removeExtra(EXTRA_OPEN_DASHBOARD)
        }
    }

    companion object {
        const val EXTRA_START_LISTENING = "extra_start_listening"
        const val EXTRA_WAKE_COMMAND = "extra_wake_command"
        const val EXTRA_OPEN_CAMERA = "extra_open_camera"
        const val EXTRA_OPEN_DASHBOARD = "extra_open_dashboard"
    }
}
