package com.example

import com.example.ai.CostProtectionPolicy
import com.example.ai.DeviceContextProvider
import com.example.autonomous.AntigravitySelfCodingAgent
import com.example.autonomous.ReleaseVersionPolicy
import com.example.data.ApiQuotaTracker
import com.example.voice.GeminiSpeechPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class V106MasterUpgradeTest {

    @Test
    fun geminiTtsPreservesAll30VoicesAndBuildsAutoSwitchCandidates() {
        assertEquals(30, GeminiSpeechPolicy.speakers.size)
        assertEquals(30, GeminiSpeechPolicy.speakers.toSet().size)
        assertEquals("Kore", GeminiSpeechPolicy.speaker("unknown", "te"))
        assertEquals("Charon", GeminiSpeechPolicy.speaker("unknown", "en"))

        val candidates = GeminiSpeechPolicy.candidateModels(
            discoveredTtsModels = listOf("gemini-2.5-pro-preview-tts", "gemini-2.5-flash-chat", "bad model"),
            preferredModel = "gemini-2.5-flash-preview-tts"
        )
        assertEquals(
            listOf("gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts"),
            candidates
        )
        assertTrue(GeminiSpeechPolicy.isQuotaError(429, null))
        assertTrue(GeminiSpeechPolicy.isModelSwitchableError(404, "not found"))
        assertEquals(GeminiSpeechPolicy.QUOTA_COOLDOWN_MS, GeminiSpeechPolicy.cooldownFor(429))
    }

    @Test
    fun deviceContextSnapshotFormatsDateTimeBatteryAndNetwork() {
        val snap = DeviceContextProvider.formatSnapshot(
            timestampMs = 1_764_261_200_000L,
            batteryPercent = 82,
            isCharging = true,
            networkLabel = "Wi-Fi",
            locale = Locale.US
        )
        val line = snap.toPromptLine()
        assertTrue(line.contains("Date="))
        assertTrue(line.contains("Time="))
        assertTrue(line.contains("Battery=82% (Charging)"))
        assertTrue(line.contains("Network=Wi-Fi"))
    }

    @Test
    fun antigravityAgentEnforcesRestrictedWorkspaceAndCodeReview() {
        assertTrue(AntigravitySelfCodingAgent.isWithinRestrictedWorkspace("app/src/main/java/com/example/ui/OmegaDashboardApp.kt"))
        assertFalse(AntigravitySelfCodingAgent.isWithinRestrictedWorkspace(".github/workflows/build-apk.yml"))
        assertFalse(AntigravitySelfCodingAgent.isWithinRestrictedWorkspace("../secret/Key.kt"))

        val oldCode = "package com.example.ui\nclass Demo { fun a() = 1 }"
        val goodPatch = "package com.example.ui\nclass Demo { fun a() = 2 }"
        val goodReview = AntigravitySelfCodingAgent.reviewPatch("app/src/main/java/com/example/ui/Demo.kt", oldCode, goodPatch)
        assertTrue(goodReview.passed)
        assertEquals(100, goodReview.score)

        val unsafeShellPatch = "package com.example.ui\nclass Demo { fun run() { Runtime.getRuntime().exec(\"id\") } }"
        val shellReview = AntigravitySelfCodingAgent.reviewPatch("app/src/main/java/com/example/ui/Demo.kt", oldCode, unsafeShellPatch)
        assertFalse(shellReview.passed)

        val silentInstallPatch = "package com.example.ui\nclass Demo { fun install(p: PackageInstaller) {} }"
        val installReview = AntigravitySelfCodingAgent.reviewPatch("app/src/main/java/com/example/ui/Demo.kt", oldCode, silentInstallPatch)
        assertFalse(installReview.passed)

        val plan = AntigravitySelfCodingAgent.buildRegressionPlan("app/src/main/java/com/example/ui/Demo.kt")
        assertTrue(plan.suggestedTestFile.endsWith("DemoRegressionTest.kt"))
        assertTrue(plan.checks.isNotEmpty())
    }

    @Test
    fun costProtectionAndTtsDiagnosticsWorkTogether() {
        assertTrue(CostProtectionPolicy.evaluateRequest(10, 0.05, 1.0, isFreeMode = false).allowed)
        assertFalse(CostProtectionPolicy.evaluateRequest(500, 0.05, 1.0, isFreeMode = false).allowed)
        assertFalse(CostProtectionPolicy.evaluateRequest(10, 1.0, 1.0, isFreeMode = false).allowed)

        ApiQuotaTracker.reset()
        ApiQuotaTracker.recordTtsUsage("gemini-2.5-flash-preview-tts", 120, fallbackUsed = false)
        ApiQuotaTracker.recordTtsUsage("gemini-2.5-flash-preview-tts", 80, fallbackUsed = true)
        val snap = ApiQuotaTracker.snapshot.value
        assertEquals(2, snap.ttsRequests)
        assertEquals(200L, snap.ttsCharacters)
        assertEquals(1, snap.ttsFallbackCount)
        assertTrue(snap.diagnosticSummary().contains("TTS: 2 (Fallbacks: 1)"))
    }

    @Test
    fun releaseVersionPolicySupportsV106TagFormat() {
        assertTrue(ReleaseVersionPolicy.newer("v106", "105.0-dual-api-candidate"))
        assertTrue(ReleaseVersionPolicy.newer("v107", "v106"))
        assertFalse(ReleaseVersionPolicy.newer("v106", "v106"))
    }
}
