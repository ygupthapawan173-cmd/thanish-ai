package com.example.omega

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import com.example.control.AgentHudBus
import com.example.control.AgentHudState
import com.example.control.NotificationInbox
import com.example.control.PhoneControlManager
import com.example.data.OmegaPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class SmartTriggerEvent(
    val title: String,
    val announcement: String,
    val timestampMs: Long = System.currentTimeMillis()
)

object SmartTriggerBus {
    private val _events = MutableSharedFlow<SmartTriggerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SmartTriggerEvent> = _events.asSharedFlow()
    fun emit(event: SmartTriggerEvent) { _events.tryEmit(event) }
}

class SmartTriggerManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = OmegaPreferences(appContext)
    private val phoneControl = PhoneControlManager(appContext)
    private var isRegistered = false
    private companion object { val debounce = TriggerDebouncePolicy() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val settings = preferences.settings.value
            val now = SystemClock.elapsedRealtime()

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (!settings.triggerBluetoothBriefing) return
                    if (!debounce.claim(action, now)) return
                    val unread = NotificationInbox.items.value.size
                    val msg = if (unread > 0) "Bluetooth connected. You have $unread unread notifications."
                    else "Bluetooth audio connected. JARVIS INFINITY is ready hands-free."
                    fireTrigger("Bluetooth Connected", msg)
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    if (intent.getIntExtra("state", -1) == 1 && settings.triggerBluetoothBriefing) {
                        if (!debounce.claim(action, now)) return
                        val unread = NotificationInbox.items.value.size
                        fireTrigger("Headphones Connected", if (unread > 0) "Headphones ready. $unread unread messages." else "Headphones connected.")
                    }
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    if (!settings.triggerChargingAlert) return
                    if (!debounce.claim(action, now)) return
                    fireTrigger("Charger Connected", "Power connected. Battery at ${readBatteryPercent()} percent.")
                }
                Intent.ACTION_BATTERY_LOW -> {
                    if (!settings.triggerLowBatterySaver) return
                    if (!debounce.claim(action, now)) return
                    if (settings.biometricSafetyLock) {
                        fireTrigger("Low Battery Auto-Saver", "Battery low. Automatic device actions blocked by biometric protection.")
                        return
                    }
                    val torch = phoneControl.setFlashlight(false)
                    val volume = phoneControl.setMediaVolume(20, showSystemUi = false)
                    fireTrigger("Low Battery Auto-Saver", "Battery low at ${readBatteryPercent()} percent. $torch $volume")
                }
            }
        }
    }

    fun register() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
        isRegistered = true
    }

    fun unregister() {
        if (!isRegistered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        isRegistered = false
    }

    fun triggerManualTest(type: String) {
        when (type) {
            "bluetooth" -> {
                val unread = NotificationInbox.items.value.size
                fireTrigger("Bluetooth Briefing", if (unread > 0) "Bluetooth ready. $unread unread messages waiting." else "Bluetooth audio ready. No unread notifications.")
            }
            "low_battery" -> {
                if (preferences.settings.value.biometricSafetyLock) {
                    fireTrigger("Low Battery Saver", "Device actions blocked by biometric protection.")
                    return
                }
                val torch = phoneControl.setFlashlight(false)
                val volume = phoneControl.setMediaVolume(20)
                fireTrigger("Low Battery Saver", "$torch $volume")
            }
            else -> fireTrigger("Power Status", "Battery is at ${readBatteryPercent()} percent.")
        }
    }

    private fun fireTrigger(title: String, announcement: String) {
        AgentHudBus.updateState(AgentHudState.Active(title, audioAmplitude = 0.45f))
        SmartTriggerBus.emit(SmartTriggerEvent(title, announcement))
    }

    private fun readBatteryPercent(): Int {
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
