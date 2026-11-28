package com.example

import com.example.omega.DailySchedulePolicy
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReleasePolicyTest {
    @Test fun dailyWallClockAcrossDst() {
        val z = ZoneId.of("America/New_York")
        val now = LocalDateTime.parse("2026-03-07T08:00").atZone(z).toInstant().toEpochMilli()
        val next = LocalDateTime.parse("2026-03-08T07:30").atZone(z).toInstant().toEpochMilli()
        assertEquals(next, DailySchedulePolicy.nextMillis(now, 7, 30, z))
    }

    @Test fun onlySafeBackgroundArguments() {
        assertTrue(DailySchedulePolicy.eligible("toggle_flashlight", false))
        assertFalse(DailySchedulePolicy.eligible("set_media_volume", 101))
        assertFalse(DailySchedulePolicy.eligible("open_app", "YouTube"))
    }
}
