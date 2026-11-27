package com.example.omega

import java.time.Instant
import java.time.ZoneId
import java.time.LocalTime
import java.security.MessageDigest

/** Local wall-clock schedules; missed days are not replayed. */
object DailySchedulePolicy {
    fun nextMillis(nowMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val time = LocalTime.of(hour, minute)
        var next = now.toLocalDate().atTime(time).atZone(zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        return next.toInstant().toEpochMilli()
    }
    fun fingerprint(steps: String): String = MessageDigest.getInstance("SHA-256")
        .digest(steps.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun eligible(tool: String, value: Any?): Boolean = when (tool) {
        "toggle_flashlight" -> value is Boolean
        "set_media_volume" -> value is Int && value in 0..100
        else -> false
    }
}
