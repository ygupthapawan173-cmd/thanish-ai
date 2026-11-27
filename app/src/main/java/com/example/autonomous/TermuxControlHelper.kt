package com.example.autonomous

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class ControlCenterEnvironmentStatus(
    val termuxInstalled: Boolean = false,
    val termuxVersion: String = "Not installed",
    val termuxApiInstalled: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val canInstallPackages: Boolean = false,
    val deviceManufacturer: String = Build.MANUFACTURER ?: "Android"
)

/**
 * V107.2 Real Termux, Installation & Battery Lifecycle Helper.
 * Uses Android PackageManager, PowerManager, and explicit Intents only — no dummy status or shell execution.
 */
object TermuxControlHelper {
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_API_PACKAGE = "com.termux.api"

    fun inspectEnvironment(context: Context): ControlCenterEnvironmentStatus {
        val pm = context.packageManager
        val termuxInfo = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(TERMUX_PACKAGE, 0)
        }.getOrNull()
        val apiInstalled = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(TERMUX_API_PACKAGE, 0)
            true
        }.getOrDefault(false)

        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoringBattery = power?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pm.canRequestPackageInstalls()
        } else true

        return ControlCenterEnvironmentStatus(
            termuxInstalled = termuxInfo != null,
            termuxVersion = termuxInfo?.versionName ?: "Not installed",
            termuxApiInstalled = apiInstalled,
            batteryOptimizationIgnored = ignoringBattery,
            canInstallPackages = canInstall,
            deviceManufacturer = Build.MANUFACTURER ?: "Android"
        )
    }

    fun launchTermux(context: Context): Result<String> = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            ?: throw IllegalStateException("Termux ($TERMUX_PACKAGE) is not installed on this device.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Launched Termux."
    }

    fun copyHandoffAndLaunchTermux(context: Context, cliCommand: String): Result<String> = runCatching {
        require(cliCommand.isNotBlank()) { "Generate a CLI handoff command first." }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("Antigravity CLI Handoff", cliCommand))
        val launchRes = launchTermux(context)
        if (launchRes.isSuccess) "Copied safe CLI command to clipboard and launched Termux."
        else "Copied safe CLI command to clipboard (Termux not installed)."
    }

    fun openBatterySettings(context: Context, targetPackage: String = context.packageName): Result<String> = runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$targetPackage")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opened Android App & Battery settings for $targetPackage."
    }

    fun openInstallUnknownAppsSettings(context: Context): Result<String> = runCatching {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        "Opened Android install-permission settings."
    }
}
