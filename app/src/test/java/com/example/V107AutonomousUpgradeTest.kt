package com.example

import com.example.ai.ConnectionRecoveryManager
import com.example.ai.ZeroPaidCreditsPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun connectionRecoveryManagerDetectsTransientAndQuotaErrors() {
        assertTrue(ConnectionRecoveryManager.isTransientError("HTTP 503 Service Unavailable"))
        assertTrue(ConnectionRecoveryManager.isTransientError("Socket timeout"))
        assertFalse(ConnectionRecoveryManager.isTransientError("HTTP 401 Unauthorized"))
        assertTrue(ConnectionRecoveryManager.isQuotaError("HTTP 429 RESOURCE_EXHAUSTED"))
        assertEquals(1_000L, ConnectionRecoveryManager.backoffForAttempt(0))
        assertEquals(3_000L, ConnectionRecoveryManager.backoffForAttempt(1))
        assertEquals(8_000L, ConnectionRecoveryManager.backoffForAttempt(2))
    }
}
