package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.data.RenderQuality

val LocalReducedRendering = staticCompositionLocalOf { false }

@Composable
fun InfinityRenderQuality(quality: RenderQuality, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var saver by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    var low by remember { mutableStateOf(false) }
    var thermal by remember { mutableStateOf(false) }
    DisposableEffect(power) {
        if (android.os.Build.VERSION.SDK_INT >= 29 && power != null) {
            val listener = PowerManager.OnThermalStatusChangedListener { status -> thermal = status >= PowerManager.THERMAL_STATUS_SEVERE }
            thermal = power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
            val added = runCatching { power.addThermalStatusListener(context.mainExecutor,listener) }.isSuccess
            onDispose { if (added) runCatching { power.removeThermalStatusListener(listener) } }
        } else onDispose { }
    }
    DisposableEffect(context, power) {
        fun readBattery(intent: Intent?) {
            val level = intent?.getIntExtra("level", -1) ?: -1
            val scale = intent?.getIntExtra("scale", -1) ?: -1
            low = level >= 0 && scale > 0 && level * 100L <= scale * 20L
            saver = power?.isPowerSaveMode == true
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) readBattery(intent)
                else saver = power?.isPowerSaveMode == true
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply { addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) }
        val registration = runCatching { ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
        readBattery(registration.getOrNull())
        onDispose { if (registration.isSuccess) runCatching { context.unregisterReceiver(receiver) } }
    }
    CompositionLocalProvider(LocalReducedRendering provides quality.reduced(saver, low, thermal), content = content)
}
