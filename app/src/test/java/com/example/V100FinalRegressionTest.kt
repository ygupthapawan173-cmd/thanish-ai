package com.example

import com.example.control.VisionEncodingPolicy as V
import com.example.ui.SettingsSearchPolicy as S

class V100FinalRegressionTest {
    @org.junit.Test
    fun policies() {
        var n = 0
        fun t(ok: Boolean) { check(ok) { "Integration case ${n + 1}" }; n++ }
        for ((w, h) in listOf(1 to 1, 100 to 200, 4000 to 3000, 1 to 10000, 10000 to 1, Int.MAX_VALUE to Int.MAX_VALUE)) {
            for (m in listOf(0, 128, 1024, 2048, 9999)) {
                val (x, y) = V.dimensions(w, h, m)
                t(x in 1..minOf(w, m.coerceIn(128, 2048)) && y in 1..minOf(h, m.coerceIn(128, 2048)))
            }
        }
        t(V.dimensions(4000, 2000, 1024) == (1024 to 512))
        t(runCatching { V.dimensions(0, 1, 128) }.isFailure)
        for (s in listOf(-1, 0, 1, V.MAX_JPEG_BYTES, V.MAX_JPEG_BYTES + 1)) t(V.acceptsSize(s) == (s in 1..V.MAX_JPEG_BYTES))
        for (q in listOf("", " ", "VOICE", "voice  engine", "speech\nengine", "microphone", "unknown")) {
            t(S.matches(q, "Voice Engine", "Speech", "microphone") == (q != "unknown"))
        }
        println("$n/$n vision and search policy checks passed")
    }
}
