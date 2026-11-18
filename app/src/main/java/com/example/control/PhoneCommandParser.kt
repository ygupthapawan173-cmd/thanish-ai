package com.example.control

import java.util.Locale

/**
 * Deterministic parser for Telugu and Roman Telugu (Tenglish) phone control voice commands.
 * Returns null for regular conversational queries so Gemini AI handles them naturally.
 */
object PhoneCommandParser {

    private val openTriggers = listOf(
        "open", "launch", "start", "theruvu", "teruvu", "chey", "cheyi", "cheyyi", "cheyu",
        "ఓపెన్", "తెరువు", "స్టార్ట్", "చెయ్", "చేయ్", "చేయి"
    )

    private val turnOnTriggers = listOf(
        "on", "enable", "start", "వెలిగించు", "ఆన్"
    )

    private val turnOffTriggers = listOf(
        "off", "disable", "stop", "bandh", "aapey", "ఆఫ్", "ఆపు", "ఆపేయ్", "ఆర్పేయ్", "బంద్"
    )

    private val increaseTriggers = listOf(
        "penchu", "ekkuva", "increase", "up", "raise", "high", "పెంచు", "ఎక్కువ"
    )

    private val decreaseTriggers = listOf(
        "tagginchu", "thakkuva", "takkuva", "decrease", "down", "lower", "low", "తగ్గించు", "తక్కువ"
    )

    private val muteTriggers = listOf(
        "mute", "silent", "మ్యూట్", "సైలెంట్"
    )

    private val checkTriggers = listOf(
        "check", "status", "entha", "info", "level", "percent", "percentage", "cheppu",
        "చెక్", "ఎంత", "స్టేటస్", "చెప్పు", "శాతం"
    )

    fun parse(rawInput: String): PhoneControlCommand? {
        val text = rawInput.trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return null

        // Do not intercept creative/conversational requests meant for Gemini AI
        val aiConversationalKeywords = listOf(
            "gurinchi", "gurunchi", "about", "explain", "raasi", "rayi", "write",
            "joke", "story", "meaning", "గురించి", "వివరించు", "రాయు", "రాసివ్వు", "చెప్పగలవా"
        )
        if (aiConversationalKeywords.any { text.contains(it) }) {
            return null
        }

        // Fast YouTube song / artist search commands.
        extractYouTubeQuery(text)?.let { return PhoneControlCommand.SearchYouTube(it) }

        // 1. Device Settings Panels (checked before generic "settings" app open)
        if (containsAny(text, "wifi", "wi-fi", "wi fi", "వైఫై", "వై-ఫై", "వై ఫై")) {
            if (containsAny(text, openTriggers) || containsAny(text, "settings", "సెట్టింగ్స్", "సెట్టింగ్")) {
                return PhoneControlCommand.OpenDeviceSettings(DeviceSettingTarget.WIFI)
            }
        }
        if (containsAny(text, "bluetooth", "blue tooth", "బ్లూటూత్", "బ్లూ టూత్")) {
            if (containsAny(text, openTriggers) || containsAny(text, "settings", "సెట్టింగ్స్", "సెట్టింగ్")) {
                return PhoneControlCommand.OpenDeviceSettings(DeviceSettingTarget.BLUETOOTH)
            }
        }
        if (containsAny(text, "hotspot", "hot spot", "హాట్‌స్పాట్", "హాట్ స్పాట్")) {
            if (containsAny(text, openTriggers) || containsAny(text, "settings", "సెట్టింగ్స్", "సెట్టింగ్")) {
                return PhoneControlCommand.OpenDeviceSettings(DeviceSettingTarget.HOTSPOT)
            }
        }
        if (containsAny(text, "mobile network", "mobile data", "network settings", "నెట్‌వర్క్", "నెట్వర్క్", "మొబైల్ డేటా")) {
            if (containsAny(text, openTriggers) || containsAny(text, "settings", "సెట్టింగ్స్", "సెట్టింగ్")) {
                return PhoneControlCommand.OpenDeviceSettings(DeviceSettingTarget.MOBILE_NETWORK)
            }
        }

        // 2. Flashlight / Torch
        if (containsAny(text, "torch", "flashlight", "flash light", "flash", "టార్చ్", "టార్చి", "ఫ్లాష్")) {
            if (containsAny(text, turnOffTriggers)) {
                return PhoneControlCommand.SetTorch(enabled = false)
            }
            if (containsAny(text, turnOnTriggers) || containsAny(text, openTriggers)) {
                return PhoneControlCommand.SetTorch(enabled = true)
            }
        }

        // 3. Volume Control
        if (containsAny(text, "volume", "sound", "audio", "వాల్యూమ్", "వాల్యూం", "సౌండ్", "శబ్దం")) {
            if (containsAny(text, muteTriggers)) {
                return PhoneControlCommand.MuteVolume
            }
            if (containsAny(text, increaseTriggers)) {
                return PhoneControlCommand.IncreaseVolume
            }
            if (containsAny(text, decreaseTriggers)) {
                return PhoneControlCommand.DecreaseVolume
            }
        }

        // 4. Screen Brightness
        if (containsAny(text, "brightness", "screen light", "బ్రైట్‌నెస్", "బ్రైట్నెస్", "బ్రైట్ నెస్", "వెలుతురు")) {
            if (containsAny(text, increaseTriggers)) {
                return PhoneControlCommand.IncreaseBrightness
            }
            if (containsAny(text, decreaseTriggers)) {
                return PhoneControlCommand.DecreaseBrightness
            }
        }

        // 5. Battery Status
        if (containsAny(text, "battery", "charge", "charging", "బ్యాటరీ", "ఛార్జింగ్", "చార్జింగ్", "చార్జ్", "ఛార్జ్")) {
            if (containsAny(text, checkTriggers) || containsAny(text, openTriggers)) {
                return PhoneControlCommand.CheckBattery
            }
        }

        // 6. Optional Accessibility Global Navigation
        if (containsAny(text, "go back", "back vellu", "వెనక్కి వెళ్ళు")) {
            return PhoneControlCommand.PerformGlobalNav(GlobalNavigationAction.BACK)
        }
        if (containsAny(text, "notification panel", "notifications open", "నోటిఫికేషన్స్ ఓపెన్")) {
            return PhoneControlCommand.PerformGlobalNav(GlobalNavigationAction.NOTIFICATIONS)
        }

        // 7. App Control Commands (all 9 apps)
        if (containsAny(text, openTriggers)) {
            return when {
                containsAny(text, "youtube", "యూట్యూబ్") ->
                    PhoneControlCommand.OpenApp(AppControlTarget.YOUTUBE)
                containsAny(text, "whatsapp", "whats app", "వాట్సాప్", "వాట్స్ అప్", "వాట్సప్") ->
                    PhoneControlCommand.OpenApp(AppControlTarget.WHATSAPP)
                containsAny(text, "instagram", "insta", "ఇన్‌స్టాగ్రామ్", "ఇన్స్టాగ్రామ్", "ఇన్ స్టాగ్రామ్", "ఇన్స్టా") ->
                    PhoneControlCommand.OpenApp(AppControlTarget.INSTAGRAM)
                containsAny(text, "chrome", "browser", "క్రోమ్", "బ్రౌజర్") ->
                    PhoneControlCommand.OpenApp(AppControlTarget.CHROME)
                containsAny(text, "camera", "కెమెరా") ->
                    PhoneControlCommand.OpenApp(AppControlTarget.CAMERA)
                containsAny(text, "gallery", "photos", "గ్యాలరీ", "ఫోటోలు", "ఫోటోస్") ->
                    PhoneControlCommand.OpenApp(AppControlTarget.GALLERY)
                containsAny(text, "phone", "dialer", "call app", "ఫోన్", "డయలర్") ->
                    PhoneControlCommand.OpenApp(AppControlTarget.PHONE)
                containsAny(text, "messages", "message", "sms", "మెసేజెస్", "మెసేజ్", "ఎస్ఎంఎస్") ->
                    PhoneControlCommand.OpenApp(AppControlTarget.MESSAGES)
                containsAny(text, "settings", "సెట్టింగ్స్", "సెట్టింగ్") ->
                    PhoneControlCommand.OpenApp(AppControlTarget.SETTINGS)
                else -> null
            }
        }

        return null
    }

    private fun extractYouTubeQuery(text: String): String? {
        val prefixes = listOf("play ", "search youtube for ", "youtube lo ", "youtube లో ", "ప్లే చేయి ", "ప్లే చెయ్ ", "పాట ప్లే చేయి ", "సాంగ్ ప్లే చేయి ")
        val prefix = prefixes.firstOrNull { text.startsWith(it) } ?: return null
        val query = text.removePrefix(prefix)
            .replace(Regex("\\s+on youtube$"), "")
            .replace(Regex("\\s+పాట(లు)?$"), "")
            .trim()
        if (query.length < 2 || query in setOf("music", "song", "పాట", "సాంగ్")) return null
        return query.take(120)
    }

    private fun containsAny(text: String, vararg needles: String): Boolean {
        return needles.any { text.contains(it) }
    }

    private fun containsAny(text: String, needles: List<String>): Boolean {
        return needles.any { text.contains(it) }
    }
}
