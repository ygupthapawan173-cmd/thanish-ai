package com.example

import com.example.ai.CostProtectionPolicy
import com.example.ai.DeviceContextProvider
import com.example.autonomous.ReleaseVersionPolicy
import com.example.data.ApiQuotaTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class V106MasterUpgradeTest {

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
    fun costProtectionAndTtsDiagnosticsWorkTogether() {
        assertTrue(CostProtectionPolicy.evaluateRequest(10, 0.05, 1.0, isFreeMode = false).allowed)
        assertFalse(CostProtectionPolicy.evaluateRequest(500, 0.05, 1.0, isFreeMode = false).allowed)
        assertFalse(CostProtectionPolicy.evaluateRequest(10, 1.0, 1.0, isFreeMode = false).allowed)

        ApiQuotaTracker.reset()
        ApiQuotaTracker.recordTtsUsage("bulbul:v3", 120, fallbackUsed = false)
        ApiQuotaTracker.recordTtsUsage("bulbul:v3", 80, fallbackUsed = true)
        val snap = ApiQuotaTracker.snapshot.value
        assertEquals(2, snap.ttsRequests)
        assertEquals(200L, snap.ttsCharacters)
        assertEquals(1, snap.ttsFallbackCount)
        assertTrue(snap.diagnosticSummary().contains("TTS: 2 (Fallbacks: 1)"))
    }

    @Test
    fun releaseVersionPolicySupportsV110Formats() {
        assertTrue(ReleaseVersionPolicy.newer("v110", "v109"))
        assertTrue(ReleaseVersionPolicy.newer("v111", "V110 MASTER INTELLIGENCE"))
        assertFalse(ReleaseVersionPolicy.newer("v110", "V110 MASTER INTELLIGENCE"))
    }
}
