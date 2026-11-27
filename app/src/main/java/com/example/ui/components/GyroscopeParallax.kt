package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberParallaxOffset(enabled: Boolean = true, maxDpShift: Float = 12f): State<Offset> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(enabled) {
        if (!enabled) { state.value = Offset.Zero; return@DisposableEffect onDispose {} }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || sensor == null) return@DisposableEffect onDispose {}

        var sx = 0f; var sy = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rx = (-event.values[0] / 9.81f).coerceIn(-1f, 1f) * maxDpShift
                val ry = ((event.values[1] - 5f) / 9.81f).coerceIn(-1f, 1f) * maxDpShift
                sx += (rx - sx) * 0.14f
                sy += (ry - sy) * 0.14f
                state.value = Offset(sx, sy)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}
