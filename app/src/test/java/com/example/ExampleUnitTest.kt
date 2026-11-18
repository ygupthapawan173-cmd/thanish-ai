package com.example

import com.example.ai.PanduAiRepository
import com.example.control.AppControlTarget
import com.example.control.DeviceSettingTarget
import com.example.control.GlobalNavigationAction
import com.example.control.PhoneCommandParser
import com.example.control.PhoneControlCommand
import com.example.data.ChatMessageEntity
import com.example.voice.TeluguSpeechFormatter
import com.example.voice.TtsPersonalityPreset
import com.google.firebase.ai.type.TextPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `telugu speech formatter preserves telugu script while stripping markdown and emojis`() {
        val raw = "**హలో బాస్!** 😎 నేను పాండు బాబు — జార్విస్ ఎడిషన్... ఏం చేద్దాం?"
        val formatted = TeluguSpeechFormatter.formatForTeluguSpeech(raw)

        assertTrue(formatted.contains("హలో బాస్!"))
        assertTrue(formatted.contains("నేను పాండు బాబు"))
        assertFalse(formatted.contains("*"))
        assertFalse(formatted.contains("😎"))
        assertTrue(formatted.contains(","))
    }

    @Test
    fun `tts personality presets resolve and calibrate witty defaults`() {
        val witty = TtsPersonalityPreset.fromId("witty_jarvis")
        assertEquals(TtsPersonalityPreset.WITTY_JARVIS, witty)
        assertTrue(witty.pitch > 1.0f)
        assertTrue(witty.speechRate > 1.0f)

        val calm = TtsPersonalityPreset.fromId("calm_jarvis")
        assertEquals(TtsPersonalityPreset.CALM_JARVIS, calm)
        assertTrue(calm.pitch < 1.0f)

        assertEquals(TtsPersonalityPreset.WITTY_JARVIS, TtsPersonalityPreset.fromId("unknown"))
    }

    @Test
    fun `phone command parser handles roman telugu and telugu app launch commands`() {
        assertEquals(
            PhoneControlCommand.OpenApp(AppControlTarget.YOUTUBE),
            PhoneCommandParser.parse("YouTube open chey")
        )
        assertEquals(
            PhoneControlCommand.OpenApp(AppControlTarget.WHATSAPP),
            PhoneCommandParser.parse("WhatsApp open chey")
        )
        assertEquals(
            PhoneControlCommand.OpenApp(AppControlTarget.WHATSAPP),
            PhoneCommandParser.parse("వాట్సప్ ఓపెన్ చెయ్")
        )
        assertEquals(
            PhoneControlCommand.OpenApp(AppControlTarget.INSTAGRAM),
            PhoneCommandParser.parse("Instagram start cheyi")
        )
        assertEquals(
            PhoneControlCommand.OpenApp(AppControlTarget.INSTAGRAM),
            PhoneCommandParser.parse("ఇన్స్టాగ్రామ్ ఓపెన్ చెయ్")
        )
        assertEquals(
            PhoneControlCommand.OpenApp(AppControlTarget.CHROME),
            PhoneCommandParser.parse("Chrome open cheyyi")
        )
        assertEquals(
            PhoneControlCommand.OpenApp(AppControlTarget.CAMERA),
            PhoneCommandParser.parse("Camera open chey")
        )
        assertEquals(
            PhoneControlCommand.OpenApp(AppControlTarget.GALLERY),
            PhoneCommandParser.parse("Gallery teruvu")
        )
        assertEquals(
            PhoneControlCommand.OpenApp(AppControlTarget.PHONE),
            PhoneCommandParser.parse("ఫోన్ ఓపెన్ చెయ్")
        )
        assertEquals(
            PhoneControlCommand.OpenApp(AppControlTarget.MESSAGES),
            PhoneCommandParser.parse("Messages open chey")
        )
        assertEquals(
            PhoneControlCommand.OpenApp(AppControlTarget.SETTINGS),
            PhoneCommandParser.parse("Settings open chey")
        )
    }

    @Test
    fun `phone command parser handles device control and settings commands`() {
        assertEquals(
            PhoneControlCommand.SetTorch(true),
            PhoneCommandParser.parse("Torch on chey")
        )
        assertEquals(
            PhoneControlCommand.SetTorch(true),
            PhoneCommandParser.parse("టార్చ్ ఆన్ చెయ్")
        )
        assertEquals(
            PhoneControlCommand.SetTorch(false),
            PhoneCommandParser.parse("Torch off chey")
        )
        assertEquals(
            PhoneControlCommand.SetTorch(false),
            PhoneCommandParser.parse("టార్చ్ ఆర్పేయ్")
        )
        assertEquals(
            PhoneControlCommand.IncreaseVolume,
            PhoneCommandParser.parse("Volume penchu")
        )
        assertEquals(
            PhoneControlCommand.IncreaseVolume,
            PhoneCommandParser.parse("వాల్యూం పెంచు")
        )
        assertEquals(
            PhoneControlCommand.DecreaseVolume,
            PhoneCommandParser.parse("Volume tagginchu")
        )
        assertEquals(
            PhoneControlCommand.MuteVolume,
            PhoneCommandParser.parse("Sound mute chey")
        )
        assertEquals(
            PhoneControlCommand.DecreaseBrightness,
            PhoneCommandParser.parse("Brightness tagginchu")
        )
        assertEquals(
            PhoneControlCommand.DecreaseBrightness,
            PhoneCommandParser.parse("బ్రైట్ నెస్ తగ్గించు")
        )
        assertEquals(
            PhoneControlCommand.IncreaseBrightness,
            PhoneCommandParser.parse("Brightness penchu")
        )
        assertEquals(
            PhoneControlCommand.OpenDeviceSettings(DeviceSettingTarget.WIFI),
            PhoneCommandParser.parse("Wi-Fi settings open chey")
        )
        assertEquals(
            PhoneControlCommand.OpenDeviceSettings(DeviceSettingTarget.WIFI),
            PhoneCommandParser.parse("వై ఫై సెట్టింగ్స్ ఓపెన్ చెయ్")
        )
        assertEquals(
            PhoneControlCommand.OpenDeviceSettings(DeviceSettingTarget.BLUETOOTH),
            PhoneCommandParser.parse("Bluetooth settings open chey")
        )
        assertEquals(
            PhoneControlCommand.OpenDeviceSettings(DeviceSettingTarget.BLUETOOTH),
            PhoneCommandParser.parse("బ్లూ టూత్ సెట్టింగ్స్ ఓపెన్ చెయ్")
        )
        assertEquals(
            PhoneControlCommand.OpenDeviceSettings(DeviceSettingTarget.HOTSPOT),
            PhoneCommandParser.parse("Hotspot settings open chey")
        )
        assertEquals(
            PhoneControlCommand.OpenDeviceSettings(DeviceSettingTarget.MOBILE_NETWORK),
            PhoneCommandParser.parse("Mobile network settings open chey")
        )
        assertEquals(
            PhoneControlCommand.CheckBattery,
            PhoneCommandParser.parse("బ్యాటరీ ఎంత ఉంది")
        )
        assertEquals(
            PhoneControlCommand.CheckBattery,
            PhoneCommandParser.parse("బ్యాటరీ ఎంత శాతం")
        )
        assertEquals(
            PhoneControlCommand.PerformGlobalNav(GlobalNavigationAction.BACK),
            PhoneCommandParser.parse("back vellu")
        )
    }

    @Test
    fun `phone command parser returns null for conversational ai queries`() {
        assertNull(PhoneCommandParser.parse("YouTube గురించి చెప్పు"))
        assertNull(PhoneCommandParser.parse("WhatsApp కోసం జోక్ చెప్పు"))
        assertNull(PhoneCommandParser.parse("Explain how Wi-Fi works"))
        assertNull(PhoneCommandParser.parse("ఒక సూపర్ తెలుగు జోక్ చెప్పు"))
        assertNull(PhoneCommandParser.parse("హలో పాండు బాబు"))
    }

    @Test
    fun `normalizeChatHistory enforces valid gemini user-model alternation`() {
        val repo = PanduAiRepository()
        val rawHistory = listOf(
            ChatMessageEntity(id = 1, role = ChatMessageEntity.ROLE_ASSISTANT, text = "Orphan welcome"),
            ChatMessageEntity(id = 2, role = ChatMessageEntity.ROLE_USER, text = "   "),
            ChatMessageEntity(id = 3, role = ChatMessageEntity.ROLE_USER, text = "First user"),
            ChatMessageEntity(id = 4, role = ChatMessageEntity.ROLE_USER, text = "Second user"),
            ChatMessageEntity(id = 5, role = ChatMessageEntity.ROLE_ASSISTANT, text = "Model reply 1"),
            ChatMessageEntity(id = 6, role = ChatMessageEntity.ROLE_ASSISTANT, text = "Model reply 2"),
            ChatMessageEntity(id = 7, role = ChatMessageEntity.ROLE_USER, text = "Trailing orphan user")
        )

        val normalized = repo.normalizeChatHistory(rawHistory)

        assertEquals(2, normalized.size)
        assertEquals("user", normalized[0].role)
        assertEquals("First user\nSecond user", (normalized[0].parts.first() as TextPart).text)
        assertEquals("model", normalized[1].role)
        assertEquals("Model reply 1\nModel reply 2", (normalized[1].parts.first() as TextPart).text)

        assertTrue(
            repo.normalizeChatHistory(
                listOf(ChatMessageEntity(id = 1, role = ChatMessageEntity.ROLE_ASSISTANT, text = "Only model"))
            ).isEmpty()
        )
        assertTrue(
            repo.normalizeChatHistory(
                listOf(ChatMessageEntity(id = 1, role = ChatMessageEntity.ROLE_USER, text = "Only user"))
            ).isEmpty()
        )
    }
}
