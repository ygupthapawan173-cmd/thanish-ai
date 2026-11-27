package com.example

import com.example.ai.ZeroPaidCreditsPolicy
import com.example.autonomous.AntigravityTaskHandoff
import com.example.autonomous.CodingModelSwitchPolicy
import com.example.autonomous.CrashBugFixPlanner
import com.example.autonomous.VoiceUpgradeIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V107AutonomousUpgradeTest {

    @Test
    fun zeroPaidCreditsPolicyBlocksPaidCallsWhenEnabled() {
        assertFalse(ZeroPaidCreditsPolicy.allowsPaidCloudVoice(zeroPaidCreditsMode = true))
        assertTrue(ZeroPaidCreditsPolicy.allowsPaidCloudVoice(zeroPaidCreditsMode = false))
        assertFalse(ZeroPaidCreditsPolicy.allowsPaidGateway(zeroPaidCreditsMode = true, isFreeMode = false))
        assertTrue(ZeroPaidCreditsPolicy.allowsPaidGateway(zeroPaidCreditsMode = true, isFreeMode = true))
        assertTrue(ZeroPaidCreditsPolicy.allowsPaidGateway(zeroPaidCreditsMode = false, isFreeMode = false))
    }

    @Test
    fun codingModelSwitchPolicyRotatesAndStopsWhenExhausted() {
        val policy = CodingModelSwitchPolicy(cooldownMs = 60_000L)
        val candidates = policy.candidateModels(listOf("gemini-2.5-flash", "gemini-2.5-pro-tts"), "gemini-2.5-flash")
        assertTrue("gemini-2.5-flash" in candidates)
        assertFalse(candidates.any { "tts" in it })

        val t0 = 1_000_000L
        for (m in candidates) policy.markQuotaExhausted("scope1", m, t0)
        assertTrue(policy.nextAvailable("scope1", candidates, t0 + 1000L).isEmpty())
        assertEquals(candidates, policy.nextAvailable("scope1", candidates, t0 + 61_000L))
    }

    @Test
    fun manualAntigravityHandoffEnforcesUseG1CreditsFalseAndWorkspaceBounds() {
        val ok = AntigravityTaskHandoff.buildHandoff(
            featureRequest = "Optimize chat header spacing",
            targetPathsRaw = "app/src/main/java/com/example/ui/OmegaDashboardApp.kt"
        )
        assertTrue(ok.isSuccess)
        val bundle = ok.getOrThrow()
        assertTrue(bundle.cliCommand.contains("useG1Credits"))
        assertTrue(bundle.cliCommand.contains("--sandbox"))

        val badPath = AntigravityTaskHandoff.buildHandoff(
            featureRequest = "Edit workflow",
            targetPathsRaw = ".github/workflows/build-apk.yml"
        )
        assertTrue(badPath.isFailure)
    }

    @Test
    fun voiceUpgradeIntentParserAndCrashPlannerRequireConfirmation() {
        val upgrade = VoiceUpgradeIntentParser.parse("Hey JARVIS self upgrade add latency badge")
        assertNotNull(upgrade)
        assertFalse(upgrade!!.isCrashFix)
        assertTrue(upgrade.featureRequest.contains("add latency badge"))

        assertNull(VoiceUpgradeIntentParser.parse("What is the weather today?"))

        val crashSample = """
            === CRASH [2026-11-27 17:00:00] Thread=main ===
            NullPointerException: Attempt to invoke virtual method
            at com.example.ui.OmegaDashboardApp.ChatScreen(OmegaDashboardApp.kt:195)
        """.trimIndent()
        val crashPlan = CrashBugFixPlanner.buildFixProposal(crashSample)
        assertNotNull(crashPlan)
        assertTrue(crashPlan!!.isCrashFix)
        assertEquals("app/src/main/java/com/example/ui/OmegaDashboardApp.kt", crashPlan.suggestedTargetPath)
        assertNull(CrashBugFixPlanner.buildFixProposal("No crashes recorded in this log."))
        assertEquals(3, com.example.autonomous.AutonomousUpgradeOrchestrator.MAX_REPAIR_ATTEMPTS)
    }

    @Test
    fun connectionRecoveryManagerDetectsTransientAndQuotaErrors() {
        assertTrue(com.example.ai.ConnectionRecoveryManager.isTransientError("HTTP 503 Service Unavailable"))
        assertTrue(com.example.ai.ConnectionRecoveryManager.isTransientError("Socket timeout"))
        assertFalse(com.example.ai.ConnectionRecoveryManager.isTransientError("HTTP 401 Unauthorized"))
        assertTrue(com.example.ai.ConnectionRecoveryManager.isQuotaError("HTTP 429 RESOURCE_EXHAUSTED"))
        assertEquals(1_000L, com.example.ai.ConnectionRecoveryManager.backoffForAttempt(0))
        assertEquals(3_000L, com.example.ai.ConnectionRecoveryManager.backoffForAttempt(1))
        assertEquals(8_000L, com.example.ai.ConnectionRecoveryManager.backoffForAttempt(2))
    }
}
