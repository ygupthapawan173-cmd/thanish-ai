package com.example.control

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticFeedbackHelper(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun wakePulse() = vibrate(longArrayOf(0, 20, 32, 26), intArrayOf(0, 120, 0, 180))
    fun softTap() = vibrateOneShot(16L, 100)
    fun abortPulse() = vibrate(longArrayOf(0, 42, 28, 42), intArrayOf(0, 210, 0, 210))

    private fun vibrateOneShot(ms: Long, amp: Int) {
        runCatching {
            val v = vibrator ?: return
            if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(ms, amp))
        }
    }

    private fun vibrate(timings: LongArray, amps: IntArray) {
        runCatching {
            val v = vibrator ?: return
            if (v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
        }
    }
}
