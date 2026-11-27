package com.example.omega

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueRunPolicyTest {
    @Test fun queueApproval() {
        val day = "2026-11-27"
        assertEquals(true, QueueRunPolicy.allowed("a", "a", 1, 1, day, day, false))
        assertEquals(false, QueueRunPolicy.allowed("a", "b", 1, 1, day, day, false))
        assertEquals(false, QueueRunPolicy.allowed("a", null, 1, 1, day, day, false))
        assertEquals(false, QueueRunPolicy.allowed("a", "a", 1, 2, day, day, false))
        assertEquals(false, QueueRunPolicy.allowed("a", "a", 1, 1, day, "2026-11-28", false))
        assertEquals(false, QueueRunPolicy.allowed("a", "a", 1, 1, day, day, true))
        assertEquals(false, QueueRunPolicy.allowed("", "", 1, 1, day, day, false))
        assertEquals(false, QueueRunPolicy.allowed("a", "a", -1, -1, day, day, false))
        assertEquals(false, QueueRunPolicy.allowed("a", "a", 1, 1, "", "", false))
    }
}
