package com.example.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build

/**
 * Bluetooth Headset Voice Routing + Hardware Echo Cancellation & Noise Suppression (v18 Supreme).
 */
class BluetoothAudioRouter(context: Context) {

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun routeToBluetoothIfAvailable(enabled: Boolean): String {
        if (!enabled) {
            clearRouting()
            return "Phone mic active"
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val btDevice = audioManager.availableCommunicationDevices.firstOrNull { dev ->
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (btDevice != null) {
                    if (audioManager.setCommunicationDevice(btDevice)) "Bluetooth routing requested (${btDevice.productName})"
                    else "Bluetooth route rejected; check audio device"
                } else "Phone mic active"
            } else {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothScoAvailableOffCall) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    "Bluetooth SCO requested"
                } else "Phone mic active"
            }
        } catch (_: Exception) {
            "Phone mic active"
        }
    }

    fun clearRouting() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            }
        }
    }

    companion object {
        fun attachAudioEffects(audioSessionId: Int): Pair<AcousticEchoCanceler?, NoiseSuppressor?> {
            val aec = runCatching {
                if (AcousticEchoCanceler.isAvailable()) {
                    AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
                } else null
            }.getOrNull()

            val ns = runCatching {
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
                } else null
            }.getOrNull()

            return aec to ns
        }
    }
}
