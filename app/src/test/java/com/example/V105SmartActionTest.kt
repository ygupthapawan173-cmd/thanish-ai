package com.example

import com.example.ai.SmartActionPolicy
import org.junit.Assert.*
import org.junit.Test

class V105SmartActionTest {
    @Test fun validPlanAndUnsafePlan() {
        assertEquals(2, SmartActionPolicy.parse("ACTION_V1|open_app|YouTube\nACTION_V1|adjust_media_volume|up")?.size)
        assertNull(SmartActionPolicy.parse("ACTION_V1|open_app|YouTube\nACTION_V1|make_phone_call|12345"))
        assertNull(SmartActionPolicy.parse("ACTION_V1|set_media_volume|101"))
        assertNull(SmartActionPolicy.parse("ACTION_V1|dial_contact|*123#"))
    }
    @Test fun ambiguousAppsAreNotSelectedSilently() {
        val apps = listOf(SmartActionPolicy.App("WhatsApp", "com.whatsapp"), SmartActionPolicy.App("WhatsApp Business", "com.whatsapp.w4b"))
        assertEquals("com.whatsapp", SmartActionPolicy.matchApp("Whats App", apps).single().packageId)
        assertEquals(2, SmartActionPolicy.matchApp("whats", apps).size)
        assertTrue(SmartActionPolicy.matchApp("unknown", apps).isEmpty())
    }
    @Test fun clearNegationAndProtocolRequestsAreBlocked() {
        assertFalse(SmartActionPolicy.permits("Do not open Camera"))
        assertFalse(SmartActionPolicy.permits("ACTION_V1|open_app|Camera"))
        assertTrue(SmartActionPolicy.permits("YouTube pettu"))
    }
}
