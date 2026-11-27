package com.example.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceContextSnapshot(
    val dateLabel: String,
    val timeLabel: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val networkLabel: String
) {
    fun toPromptLine(): String {
        val chargeTag = if (isCharging) "Charging" else "Discharging"
        return "Date=$dateLabel | Time=$timeLabel | Battery=$batteryPercent% ($chargeTag) | Network=$networkLabel"
    }
}

/**
 * Provides live device date, time, battery, and network context (minSdk 24 compatible).
 */
object DeviceContextProvider {

    fun capture(context: Context): DeviceContextSnapshot {
        val (batteryPct, charging) = readBattery(context)
        val network = readNetwork(context)
        return formatSnapshot(
            timestampMs = System.currentTimeMillis(),
            batteryPercent = batteryPct,
            isCharging = charging,
            networkLabel = network
        )
    }

    fun formatSnapshot(
        timestampMs: Long,
        batteryPercent: Int,
        isCharging: Boolean,
        networkLabel: String,
        locale: Locale = Locale.getDefault()
    ): DeviceContextSnapshot {
        val date = Date(timestampMs)
        val dateLabel = SimpleDateFormat("yyyy-MM-dd (EEEE)", locale).format(date)
        val timeLabel = SimpleDateFormat("hh:mm a z", locale).format(date)
        return DeviceContextSnapshot(
            dateLabel = dateLabel,
            timeLabel = timeLabel,
            batteryPercent = batteryPercent.coerceIn(0, 100),
            isCharging = isCharging,
            networkLabel = networkLabel.ifBlank { "Unknown" }
        )
    }

    private fun readBattery(context: Context): Pair<Int, Boolean> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val fallbackPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return (pct ?: fallbackPct) to charging
    }

    private fun readNetwork(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "Unknown"
        val network = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(network) ?: return "Offline"
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Online"
        }
        return if (validated) transport else "$transport (Unvalidated)"
    }
}
