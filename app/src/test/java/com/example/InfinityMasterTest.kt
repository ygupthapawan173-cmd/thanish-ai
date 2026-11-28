package com.example

import com.example.ai.CreatorIdentity
import com.example.data.RenderQuality
import org.junit.Assert.*
import org.junit.Test

class InfinityMasterTest {
    @Test fun renderQualityPolicy() {
        assertFalse(RenderQuality.ADAPTIVE.reduced(false, false))
        assertTrue(RenderQuality.ADAPTIVE.reduced(false, true))
        assertTrue(RenderQuality.HIGH.reduced(true, false))
        assertFalse(RenderQuality.HIGH.reduced(false, true))
        assertTrue(RenderQuality.SAVER.reduced(false, false))
        assertEquals(RenderQuality.ADAPTIVE, RenderQuality.fromStored(null))
        assertEquals(RenderQuality.SAVER, RenderQuality.fromStored("SAVER"))
    }

    @Test fun creatorAppVariants() {
        assertNotNull(CreatorIdentity.reply("Who developed this app?"))
        assertNotNull(CreatorIdentity.reply("Hey Jarvis, ninnu evaru create chesav?"))
        assertNull(CreatorIdentity.reply("who made this app and send message"))
    }

    @Test fun creatorEnglish() {
        val reply = CreatorIdentity.reply("Hey JARVIS, who created you?")!!
        assertTrue(reply.contains("Guptha"))
    }

    @Test fun creatorTelugu() {
        val reply = CreatorIdentity.reply("నిన్ను ఎవరు సృష్టించారు?")!!
        assertTrue(reply.contains("గుప్తా"))
    }

    @Test fun creatorTenglish() {
        assertNotNull(CreatorIdentity.reply("ninnu evaru create chesaru"))
    }

    @Test fun noFalseIdentity() {
        assertNull(CreatorIdentity.reply("who created Gemini?"))
        assertNull(CreatorIdentity.reply("open flashlight"))
        assertNull(CreatorIdentity.reply("who created you and who created Gemini?"))
    }
}
