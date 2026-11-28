package com.example

import com.example.ai.SmartModelPolicy
import com.example.autonomous.ReleaseVersionPolicy
import com.example.autonomous.UpdateTransferPolicy
import com.example.data.RenderQuality
import com.example.security.DiagnosticRedaction
import com.example.voice.LiveSilencePolicy
import com.example.voice.VoiceHealthPolicy
import com.example.voice.VoiceRecoveryPolicy

class V100PolicyRegressionTest {
    @org.junit.Test
    fun policies() {
        var n = 0
        fun t(ok: Boolean) { check(ok) { "V100 check ${n + 1} failed" }; n++ }
        val task = SmartModelPolicy.Task.CHAT
        for (id in listOf("", " ", "bad id", "bad\nmodel", "a".repeat(513))) t(!SmartModelPolicy.eligible(SmartModelPolicy.Model(id), task))
        t(SmartModelPolicy.eligible(SmartModelPolicy.Model("gemini-4-hydrogen"), task))
        for (a in -3..5) for (code in listOf(1, 2, 3, 5, 8, 11, 12, 13)) t(VoiceRecoveryPolicy.retryable(code, true, a) == (a in 0..1))
        for (x in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -2f, 0f)) {
            val s = LiveSilencePolicy()
            repeat(12) { t(!s.update(x)) }
            repeat(2) { t(!s.update(.9f)) }
            t(s.update(.9f))
        }
        t(!VoiceHealthPolicy.stalled(-1, 0, 0))
        t(!VoiceHealthPolicy.stalled(100, 200, 200))
        t(VoiceHealthPolicy.stalled(9000, 0, 9000))
        for (q in RenderQuality.entries) {
            for (power in listOf(false, true)) {
                for (low in listOf(false, true)) {
                    for (thermal in listOf(false, true)) {
                        t(q.reduced(power, low, thermal) == (q == RenderQuality.SAVER || power || thermal || q == RenderQuality.ADAPTIVE && low))
                    }
                }
            }
        }
        for (s in listOf("v99", "100.0", "v100.0.0", "latest", "", "v100.0-beta", "v9999999999")) t(!ReleaseVersionPolicy.newer(s, "100.0-hyper-integration-candidate"))
        for (s in listOf("v101", "v100.1", "100.0.1", "V200.0.0")) t(ReleaseVersionPolicy.newer(s, "100.0-hyper-integration-candidate"))
        val base = "https://github.com/owner/repo/releases/download/v101/app.apk"
        t(UpdateTransferPolicy.allowedAsset(base))
        for (s in listOf("?token=a", "#section")) t(!UpdateTransferPolicy.allowedAsset(base + s))
        t(!UpdateTransferPolicy.allowedAsset("https://github.com/owner/repo/releases/download/v101/../app.apk"))
        for (secret in listOf("Bearer test-token", "ghp_" + "a".repeat(30), "sk-proj-" + "b".repeat(30), "AIza" + "c".repeat(30))) {
            t(DiagnosticRedaction.clean("test $secret") == "test [REDACTED]")
        }
        t(DiagnosticRedaction.clean("ordinary error") == "ordinary error")
        println("$n/$n V100 pure JVM policy checks passed")
    }
}
