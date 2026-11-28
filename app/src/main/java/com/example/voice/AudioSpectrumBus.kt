package com.example.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/** 256-point Hann-window radix-2 FFT, 16 frequency bands; never opens another microphone. */
object AudioSpectrumBus {
    data class Frame(val bands: List<Float> = List(16) { 0f }, val playback: Boolean = false, val time: Long = 0L)
    private val _frame = MutableStateFlow(Frame())
    val frame = _frame.asStateFlow()
    @Synchronized fun feed(pcm: ByteArray, size: Int, rate: Int, playback: Boolean) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - _frame.value.time < 40L && _frame.value.playback == playback) return
        if (!playback && _frame.value.playback && now - _frame.value.time < 180L) return
        val bands = analyze(pcm, size, rate)
        _frame.value = Frame(bands, playback, now)
    }
    fun analyze(pcm: ByteArray, size: Int, rate: Int): List<Float> {
        val n = 256
        val available = minOf(size, pcm.size) / 2
        if (available < n || rate <= 0) return List(16) { 0f }
        val re = DoubleArray(n); val im = DoubleArray(n)
        for (i in 0 until n) {
            val offset = (available - n + i) * 2
            val v = ((pcm[offset+1].toInt() shl 8) or (pcm[offset].toInt() and 255)).toShort() / 32768.0
            val k = Integer.reverse(i) ushr 24
            re[k] = v * (0.5 - 0.5 * cos(2.0 * PI * i / (n-1)))
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            for (i in 0 until n step len) for (j in 0 until half) {
                val a = -2.0 * PI * j / len
                val c = cos(a); val s = sin(a)
                val k = i+j; val l=k+half
                val x=re[l]*c-im[l]*s; val y=re[l]*s+im[l]*c
                re[l]=re[k]-x; im[l]=im[k]-y; re[k]+=x; im[k]+=y
            }
            len *= 2
        }
        return List(16) { b ->
            val lo = (1 + b * 4).coerceAtMost(n/2-1)
            val hi = (lo + 4).coerceAtMost(n/2)
            var energy = 0.0
            for (i in lo until hi) energy += re[i]*re[i]+im[i]*im[i]
            (sqrt(energy) / (n * 0.1)).toFloat().coerceIn(0f, 1f)
        }
    }
}
