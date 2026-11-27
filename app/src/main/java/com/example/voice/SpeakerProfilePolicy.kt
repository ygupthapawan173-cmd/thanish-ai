package com.example.voice

import kotlin.math.sqrt

internal object SpeakerProfilePolicy {
    const val sampleCount = 3
    const val minFrames = 80
    const val threshold = 0.72 // provisional; calibrate on real phone, not a security boundary
    fun similarity(a: DoubleArray, b: DoubleArray): Double {
        if (a.isEmpty() || a.size != b.size || a.any { !it.isFinite() } || b.any { !it.isFinite() }) return -1.0
        val scaleA = a.maxOf { kotlin.math.abs(it) }; val scaleB = b.maxOf { kotlin.math.abs(it) }
        if (scaleA == 0.0 || scaleB == 0.0) return -1.0
        var dot=0.0; var aa=0.0; var bb=0.0
        for(i in a.indices) { val x=a[i]/scaleA; val y=b[i]/scaleB; dot+=x*y; aa+=x*x; bb+=y*y }
        return (dot/(sqrt(aa)*sqrt(bb))).takeIf { it.isFinite() }?.coerceIn(-1.0,1.0) ?: -1.0
    }
    fun matches(samples: List<DoubleArray>, candidate: DoubleArray): Boolean =
        samples.size == sampleCount && samples.count { similarity(it,candidate)>=threshold } >= 2
}
