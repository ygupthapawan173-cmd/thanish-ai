package com.example.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.roundToInt

private const val TAG = "BatteryMonitorHelper"

/**
 * Monitors Android device battery percentage, charging status, power source,
 * temperature, voltage, and health using [BatteryManager] and [Intent.ACTION_BATTERY_CHANGED].
 */
class BatteryMonitorHelper(context: Context) {

    private val appContext = context.applicationContext
    private val systemBatteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private val _batteryState = MutableStateFlow(BatteryStatusInfo())
    val batteryState: StateFlow<BatteryStatusInfo> = _batteryState.asStateFlow()

    private val listeners = CopyOnWriteArraySet<(BatteryStatusInfo) -> Unit>()
    private var isMonitoring = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> publish(parseBatteryIntent(intent))
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                BatteryManager.ACTION_CHARGING,
                BatteryManager.ACTION_DISCHARGING -> refresh()
            }
        }
    }

    fun addListener(listener: (BatteryStatusInfo) -> Unit) {
        listeners += listener
        listener(_batteryState.value)
    }

    fun removeListener(listener: (BatteryStatusInfo) -> Unit) {
        listeners -= listener
    }

    fun startMonitoring() {
        if (!isMonitoring) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(BatteryManager.ACTION_CHARGING)
                addAction(BatteryManager.ACTION_DISCHARGING)
            }
            val sticky = runCatching {
                appContext.registerReceiver(batteryReceiver, filter)
            }.onFailure {
                Log.w(TAG, "Unable to register battery monitor receiver", it)
            }.getOrNull()

            isMonitoring = true
            if (sticky != null) {
                publish(parseBatteryIntent(sticky))
                return
            }
        }
        refresh()
    }

    fun stopMonitoring() {
        if (isMonitoring) {
            runCatching { appContext.unregisterReceiver(batteryReceiver) }
                .onFailure { Log.w(TAG, "Unable to unregister battery monitor receiver", it) }
            isMonitoring = false
        }
    }

    fun refresh(): BatteryStatusInfo {
        val stickyIntent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val info = if (stickyIntent != null) {
            parseBatteryIntent(stickyIntent)
        } else {
            readFallbackFromBatteryManager()
        }
        publish(info)
        return info
    }

    internal fun parseBatteryIntent(intent: Intent): BatteryStatusInfo {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val rawPercent = if (level >= 0) ((level * 100f) / scale).roundToInt().coerceIn(0, 100) else -1
        val percent = if (rawPercent >= 0) {
            rawPercent
        } else {
            readCapacityPropertyPercent()
        }

        val status = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val isCharging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> plugged != 0 || (systemBatteryManager?.isCharging == true)
        }

        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Connected" else "On Battery"
        }

        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val tempC = tempTenths / 10f
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val healthCode = intent.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        )
        val healthLabel = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        return BatteryStatusInfo(
            levelPercent = percent,
            isCharging = isCharging,
            isFull = status == BatteryManager.BATTERY_STATUS_FULL || (isCharging && percent >= 100),
            chargingSource = source,
            temperatureCelsius = tempC,
            voltageMillivolts = voltageMv,
            healthLabel = healthLabel
        )
    }

    private fun readFallbackFromBatteryManager(): BatteryStatusInfo {
        val percent = readCapacityPropertyPercent()
        val isCharging = systemBatteryManager?.isCharging == true
        return BatteryStatusInfo(
            levelPercent = percent,
            isCharging = isCharging,
            isFull = isCharging && percent >= 100,
            chargingSource = if (isCharging) "Connected" else "On Battery",
            temperatureCelsius = 0f,
            voltageMillivolts = 0,
            healthLabel = "Normal"
        )
    }

    private fun readCapacityPropertyPercent(): Int {
        val prop = systemBatteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?: return -1
        return if (prop in 1..100) prop else -1
    }

    private fun publish(info: BatteryStatusInfo) {
        if (_batteryState.value != info) {
            _batteryState.value = info
        }
        listeners.forEach { it(info) }
    }
}
