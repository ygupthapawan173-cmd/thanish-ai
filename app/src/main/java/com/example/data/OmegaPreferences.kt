package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.ai.SmartModelPolicy
import com.example.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Native Gemini and a universal HTTPS OpenAI-compatible gateway.
 */
enum class AiProvider(val displayName: String, val defaultChatModel: String) {
    GEMINI("Google Gemini", "gemini-3.8-flash"),
    OPENROUTER("Universal Smart Gateway", "google/gemini-2.5-flash")
}

enum class OmegaVisualTheme(val label: String) {
    AURORA_OBSIDIAN("Pure AMOLED"),
    MIDNIGHT_ROYAL("Velvet Violet"),
    CYBER_HORIZON_WEBGL("Cyber WebGL")
}

data class OmegaSettings(
    val userName: String = "Pandu",
    val assistantName: String = "JARVIS INFINITY",
    val replyStyle: String = "Natural",
    val preferOfflineSpeech: Boolean = false,
    val activeProvider: AiProvider = AiProvider.GEMINI,
    val geminiApiKey: String = "",
    val openRouterApiKey: String = "",
    val geminiChatModel: String = "gemini-3.8-flash",
    val geminiLiveVoiceModel: String = "gemini-3.8-live",
    val geminiLiveSpeaker: String = "",
    val openRouterModel: String = "google/gemini-2.5-flash",
    val gatewayBaseUrl: String = "https://openrouter.ai/api/v1",
    val manualVisionConfirmed: Boolean = false,
    val smartMode: SmartModelPolicy.Mode = SmartModelPolicy.Mode.SMART,
    val providerBudgetUsd: Double = 0.0,
    val zeroPaidCreditsMode: Boolean = true,
    // Slot 3: Optional Secondary OpenAI-compatible Provider (Encrypted in Keystore)
    val secondaryGatewayBaseUrl: String = "",
    val secondaryGatewayApiKey: String = "",
    val secondaryGatewayModel: String = "",
    // V110 Multi-Brain & Personal Assistant controls
    val preferredBrainId: String = "AUTO",
    val brainThinkingMode: String = "AUTO",
    val multiBrainCollaboration: Boolean = false,
    val hallucinationGuardEnabled: Boolean = true,
    val personalLearningEnabled: Boolean = true,
    val proactiveSuggestionsEnabled: Boolean = true,
    val customWakeAck: String = "",
    // Least-privilege GitHub Engineering config (NEVER embedded; stored encrypted in Keystore)
    val githubRepoSlug: String = "",
    val githubToken: String = "",
    val githubBaseBranch: String = "main",
    // Voice & Wake
    val wakeWord: String = "hey jarvis",
    val wakeSensitivity: Float = 0.72f,
    val languageTag: String = "auto",
    val autoSpeak: Boolean = true,
    val continuousConversation: Boolean = false,
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsStyle: TtsEmotionStyle = TtsEmotionStyle.NATURAL,
    val wakeWordEnabled: Boolean = false,
    val bluetoothVoiceRouting: Boolean = true,
    val micPrivacyMuted: Boolean = false,
    val useGeminiLiveAudio: Boolean = false,
    val webSearchGrounding: Boolean = true,
    val biometricSafetyLock: Boolean = false,
    val confirmNotifReply: Boolean = true,
    val gyroscopeParallax: Boolean = true,
    val offlineFastCommands: Boolean = false,
    val triggerBluetoothBriefing: Boolean = true,
    val triggerChargingAlert: Boolean = true,
    val triggerLowBatterySaver: Boolean = true,
    val visualTheme: OmegaVisualTheme = OmegaVisualTheme.AURORA_OBSIDIAN,
    val orbColorPreset: OrbColorPreset = OrbColorPreset.AURORA_CYAN,
    val renderQuality: RenderQuality = RenderQuality.ADAPTIVE
) {
    fun effectiveGeminiKey(): String = if (providerBudgetUsd != 0.0 || !SmartModelPolicy.allowsDirectGemini(smartMode)) "" else geminiApiKey.trim().ifEmpty { BuildConfig.GEMINI_API_KEY.trim() }
    fun effectiveOpenRouterKey(): String = openRouterApiKey.trim().ifEmpty { if (gatewayBaseUrl.trim().trimEnd('/') == "https://openrouter.ai/api/v1") BuildConfig.OPENROUTER_API_KEY.trim() else "" }
    fun effectiveSecondaryGatewayKey(): String = secondaryGatewayApiKey.trim()

    fun activeChatModel(): String = when {
        activeProvider == AiProvider.GEMINI -> geminiChatModel.trim().ifEmpty { AiProvider.GEMINI.defaultChatModel }
        smartMode != SmartModelPolicy.Mode.MANUAL -> "Smart Auto (${smartMode.name})"
        else -> openRouterModel.trim().ifEmpty { AiProvider.OPENROUTER.defaultChatModel }
    }

    fun rmsGateThreshold(): Double = 1150.0 - (wakeSensitivity.coerceIn(0.1f, 1.0f) * 830.0)
}

class OmegaPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<OmegaSettings> = _settings.asStateFlow()

    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = load()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
    }

    fun close() { prefs.unregisterOnSharedPreferenceChangeListener(changeListener) }

    private fun load(): OmegaSettings {
        val provider = runCatching { AiProvider.valueOf(prefs.getString(KEY_PROVIDER, AiProvider.GEMINI.name) ?: "") }.getOrDefault(AiProvider.GEMINI)
        val theme = runCatching { OmegaVisualTheme.valueOf(prefs.getString(KEY_THEME, OmegaVisualTheme.AURORA_OBSIDIAN.name) ?: "") }.getOrDefault(OmegaVisualTheme.AURORA_OBSIDIAN)
        val orbColor = OrbColorPreset.fromStored(prefs.getString(KEY_ORB_COLOR, null))
        val ttsStyle = runCatching { TtsEmotionStyle.valueOf(prefs.getString(KEY_TTS_STYLE, TtsEmotionStyle.NATURAL.name) ?: "") }.getOrDefault(TtsEmotionStyle.NATURAL)

        return OmegaSettings(
            userName = prefs.getString(KEY_USER_NAME, "Pandu") ?: "Pandu",
            assistantName = prefs.getString("assistant_name", null).let { if (it.isNullOrBlank() || it.equals("OMEGA", true) || it.equals("OMEGA PANDU", true)) "JARVIS INFINITY" else it },
            replyStyle = prefs.getString("reply_style", "Natural") ?: "Natural",
            preferOfflineSpeech = false,
            activeProvider = provider,
            geminiApiKey = KeystoreCryptoManager.decrypt(prefs.getString(KEY_GEMINI_KEY, "") ?: ""),
            openRouterApiKey = KeystoreCryptoManager.decrypt(prefs.getString(KEY_OPENROUTER_KEY, "") ?: ""),
            geminiChatModel = prefs.getString(KEY_GEMINI_CHAT_MODEL, "gemini-3.8-flash") ?: "gemini-3.8-flash",
            geminiLiveVoiceModel = (prefs.getString(KEY_GEMINI_LIVE_MODEL, "gemini-3.8-live") ?: "gemini-3.8-live").let { if (it == "gemini-2.5-flash-native-audio-preview") "gemini-3.8-live" else it },
            geminiLiveSpeaker = com.example.voice.LiveVoiceChoices.normalize(prefs.getString("gemini_live_speaker", "")),
            openRouterModel = prefs.getString(KEY_OPENROUTER_MODEL, "google/gemini-2.5-flash") ?: "google/gemini-2.5-flash",
            gatewayBaseUrl = prefs.getString("gateway_base_url", "https://openrouter.ai/api/v1") ?: "https://openrouter.ai/api/v1",
            manualVisionConfirmed = prefs.getBoolean("manual_vision_confirmed", false),
            smartMode = runCatching { SmartModelPolicy.Mode.valueOf(prefs.getString("smart_mode", "SMART") ?: "SMART") }.getOrDefault(SmartModelPolicy.Mode.SMART),
            providerBudgetUsd = Double.fromBits(prefs.getLong("provider_budget_usd", 0L)),
            zeroPaidCreditsMode = prefs.getBoolean("zero_paid_credits_mode", true),
            secondaryGatewayBaseUrl = prefs.getString("secondary_gateway_base_url", "") ?: "",
            secondaryGatewayApiKey = KeystoreCryptoManager.decrypt(prefs.getString(KEY_SECONDARY_GATEWAY_KEY, "") ?: ""),
            secondaryGatewayModel = prefs.getString("secondary_gateway_model", "") ?: "",
            preferredBrainId = prefs.getString("preferred_brain_id", "AUTO") ?: "AUTO",
            brainThinkingMode = prefs.getString("brain_thinking_mode", "AUTO") ?: "AUTO",
            multiBrainCollaboration = prefs.getBoolean("multi_brain_collaboration", false),
            hallucinationGuardEnabled = prefs.getBoolean("hallucination_guard_enabled", true),
            personalLearningEnabled = prefs.getBoolean("personal_learning_enabled", true),
            proactiveSuggestionsEnabled = prefs.getBoolean("proactive_suggestions_enabled", true),
            customWakeAck = prefs.getString("custom_wake_ack", "") ?: "",
            githubRepoSlug = prefs.getString(KEY_GH_REPO, "") ?: "",
            githubToken = KeystoreCryptoManager.decrypt(prefs.getString(KEY_GH_TOKEN, "") ?: ""),
            githubBaseBranch = prefs.getString(KEY_GH_BRANCH, "main") ?: "main",
            wakeWord = prefs.getString(KEY_WAKE_WORD, "hey jarvis") ?: "hey jarvis",
            wakeSensitivity = prefs.getFloat(KEY_WAKE_SENS, 0.72f),
            languageTag = prefs.getString(KEY_LANGUAGE, "auto") ?: "auto",
            autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, true),
            continuousConversation = prefs.getBoolean(KEY_CONTINUOUS, false),
            ttsSpeed = prefs.getFloat(KEY_TTS_SPEED, 1.0f),
            ttsPitch = prefs.getFloat(KEY_TTS_PITCH, 1.0f),
            ttsStyle = ttsStyle,
            wakeWordEnabled = false,
            bluetoothVoiceRouting = prefs.getBoolean(KEY_BT_ROUTING, true),
            micPrivacyMuted = prefs.getBoolean(KEY_MIC_MUTED, false),
            useGeminiLiveAudio = prefs.getString("smart_mode", "SMART") != "FREE" && prefs.getBoolean(KEY_LIVE_AUDIO, false),
            webSearchGrounding = prefs.getBoolean(KEY_WEB_GROUNDING, true),
            biometricSafetyLock = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false),
            confirmNotifReply = prefs.getBoolean(KEY_CONFIRM_REPLY, true),
            gyroscopeParallax = prefs.getBoolean(KEY_PARALLAX, true),
            offlineFastCommands = false,
            triggerBluetoothBriefing = prefs.getBoolean(KEY_TRIG_BT, true),
            triggerChargingAlert = prefs.getBoolean(KEY_TRIG_CHARGE, true),
            triggerLowBatterySaver = prefs.getBoolean(KEY_TRIG_LOWBAT, true),
            visualTheme = theme,
            orbColorPreset = orbColor,
            renderQuality = RenderQuality.fromStored(prefs.getString("render_quality", null))
        )
    }

    private fun protectedApiKey(key: String): String {
        if (key.isBlank()) return ""
        val encrypted = KeystoreCryptoManager.encrypt(key.trim())
        check(encrypted.startsWith("ENC1:")) { "Android Keystore unavailable; API key was not saved" }
        return encrypted
    }

    @Synchronized
    fun update(transform: (OmegaSettings) -> OmegaSettings) {
        val old = _settings.value
        val proposed = transform(old).copy(preferOfflineSpeech = false, wakeWordEnabled = false, offlineFastCommands = false)
        check(com.example.ai.ProviderBudgetPolicy.validCap(proposed.providerBudgetUsd)) { "Invalid API budget" }
        val candidate = when {
            proposed.providerBudgetUsd > 0 -> proposed.copy(activeProvider = AiProvider.OPENROUTER, useGeminiLiveAudio = false)
            proposed.smartMode == SmartModelPolicy.Mode.FREE -> proposed.copy(useGeminiLiveAudio = false)
            else -> proposed
        }
        val changedEndpoint = candidate.gatewayBaseUrl.trim().trimEnd('/') != old.gatewayBaseUrl.trim().trimEnd('/')
        val u = if (changedEndpoint || candidate.openRouterModel != old.openRouterModel || candidate.openRouterApiKey != old.openRouterApiKey) candidate.copy(manualVisionConfirmed = false) else candidate
        fun keepOrProtect(new: String, previous: String, pref: String): String {
            val stored = prefs.getString(pref, "") ?: ""
            if (new == previous && (stored.startsWith("ENC1:") || new.isBlank())) return stored
            return protectedApiKey(new)
        }
        val geminiEncrypted = keepOrProtect(u.geminiApiKey, old.geminiApiKey, KEY_GEMINI_KEY)
        val gatewayEncrypted = keepOrProtect(u.openRouterApiKey, old.openRouterApiKey, KEY_OPENROUTER_KEY)
        val secondaryEncrypted = keepOrProtect(u.secondaryGatewayApiKey, old.secondaryGatewayApiKey, KEY_SECONDARY_GATEWAY_KEY)
        val githubEncrypted = keepOrProtect(u.githubToken, old.githubToken, KEY_GH_TOKEN)
        val editor = prefs.edit()
            .putString(KEY_USER_NAME, u.userName.trim().ifEmpty { "Pandu" })
            .putString("assistant_name",u.assistantName.trim().take(40).ifEmpty { "JARVIS INFINITY" })
            .putString("reply_style",u.replyStyle)
            .putBoolean("prefer_offline_speech",u.preferOfflineSpeech)
            .putString(KEY_PROVIDER, u.activeProvider.name)
            .putString(KEY_GEMINI_KEY, geminiEncrypted)
            .putString(KEY_OPENROUTER_KEY, gatewayEncrypted)
            .putString(KEY_GEMINI_CHAT_MODEL, u.geminiChatModel.trim().ifEmpty { "gemini-3.8-flash" })
            .putString(KEY_GEMINI_LIVE_MODEL, u.geminiLiveVoiceModel.trim().ifEmpty { "gemini-3.8-live" })
            .putString("gemini_live_speaker", com.example.voice.LiveVoiceChoices.normalize(u.geminiLiveSpeaker))
            .putString(KEY_OPENROUTER_MODEL, u.openRouterModel.trim().ifEmpty { "google/gemini-2.5-flash" })
            .putString("gateway_base_url", u.gatewayBaseUrl.trim().trimEnd('/'))
            .putString("smart_mode", u.smartMode.name)
            .putLong("provider_budget_usd", u.providerBudgetUsd.toBits())
            .putBoolean("zero_paid_credits_mode", u.zeroPaidCreditsMode)
            .putString("secondary_gateway_base_url", u.secondaryGatewayBaseUrl.trim().trimEnd('/'))
            .putString(KEY_SECONDARY_GATEWAY_KEY, secondaryEncrypted)
            .putString("secondary_gateway_model", u.secondaryGatewayModel.trim())
            .putString("preferred_brain_id", u.preferredBrainId.trim().ifEmpty { "AUTO" })
            .putString("brain_thinking_mode", u.brainThinkingMode.trim().ifEmpty { "AUTO" })
            .putBoolean("multi_brain_collaboration", u.multiBrainCollaboration)
            .putBoolean("hallucination_guard_enabled", u.hallucinationGuardEnabled)
            .putBoolean("personal_learning_enabled", u.personalLearningEnabled)
            .putBoolean("proactive_suggestions_enabled", u.proactiveSuggestionsEnabled)
            .putString("custom_wake_ack", u.customWakeAck.trim().take(120))
            .putBoolean("manual_vision_confirmed", u.manualVisionConfirmed)
            .remove("local_brain_mode")
            .remove("local_llm_url")
            .putString(KEY_GH_REPO, u.githubRepoSlug.trim())
            .putString(KEY_GH_TOKEN, githubEncrypted)
            .putString(KEY_GH_BRANCH, u.githubBaseBranch.trim().ifEmpty { "main" })
            .putString(KEY_WAKE_WORD, u.wakeWord.trim().lowercase().ifEmpty { "hey jarvis" })
            .putFloat(KEY_WAKE_SENS, u.wakeSensitivity.coerceIn(0.1f, 1.0f))
            .putString(KEY_LANGUAGE, u.languageTag)
            .putBoolean(KEY_AUTO_SPEAK, u.autoSpeak)
            .putBoolean(KEY_CONTINUOUS, u.continuousConversation)
            .putFloat(KEY_TTS_SPEED, u.ttsSpeed.coerceIn(0.6f, 1.6f))
            .putFloat(KEY_TTS_PITCH, u.ttsPitch.coerceIn(0.6f, 1.5f))
            .putString(KEY_TTS_STYLE, u.ttsStyle.name)
            .putBoolean(KEY_WAKE_ENABLED, u.wakeWordEnabled)
            .putBoolean(KEY_BT_ROUTING, u.bluetoothVoiceRouting)
            .putBoolean(KEY_MIC_MUTED, u.micPrivacyMuted)
            .putBoolean(KEY_LIVE_AUDIO, u.useGeminiLiveAudio)
            .putBoolean(KEY_WEB_GROUNDING, u.webSearchGrounding)
            .putBoolean(KEY_BIOMETRIC_LOCK, u.biometricSafetyLock)
            .putBoolean(KEY_CONFIRM_REPLY, u.confirmNotifReply)
            .putBoolean(KEY_PARALLAX, u.gyroscopeParallax)
            .putBoolean(KEY_OFFLINE_FAST, u.offlineFastCommands)
            .putBoolean(KEY_TRIG_BT, u.triggerBluetoothBriefing)
            .putBoolean(KEY_TRIG_CHARGE, u.triggerChargingAlert)
            .putBoolean(KEY_TRIG_LOWBAT, u.triggerLowBatterySaver)
            .putString(KEY_THEME, u.visualTheme.name)
            .putString(KEY_ORB_COLOR, u.orbColorPreset.name)
            .putString("render_quality", u.renderQuality.name)
            .remove("floating_orb_enabled")
        if (u.providerBudgetUsd != old.providerBudgetUsd) {
            check(editor.commit()) { "Credit cap was not saved to disk; check storage and retry" }
        } else editor.apply()
        _settings.value = u
    }

    fun exportEncryptedBackupJson(): String {
        val s = _settings.value
        val obj = JSONObject().apply {
            put("userName", s.userName)
            put("assistantName", s.assistantName)
            put("replyStyle", s.replyStyle)
            put("preferOfflineSpeech", s.preferOfflineSpeech)
            put("activeProvider", s.activeProvider.name)
            put("geminiChatModel", s.geminiChatModel)
            put("geminiLiveVoiceModel", s.geminiLiveVoiceModel)
            put("geminiLiveSpeaker", s.geminiLiveSpeaker)
            put("openRouterModel", s.openRouterModel)
            put("gatewayBaseUrl", s.gatewayBaseUrl)
            put("smartMode", s.smartMode.name)
            put("githubRepoSlug", s.githubRepoSlug)
            put("githubBaseBranch", s.githubBaseBranch)
            put("wakeWord", s.wakeWord)
            put("languageTag", s.languageTag)
            put("visualTheme", s.visualTheme.name)
            put("orbColorPreset", s.orbColorPreset.name)
            put("renderQuality", s.renderQuality.name)
            put("secondaryGatewayBaseUrl", s.secondaryGatewayBaseUrl)
            put("secondaryGatewayModel", s.secondaryGatewayModel)
            put("preferredBrainId", s.preferredBrainId)
            put("brainThinkingMode", s.brainThinkingMode)
            put("hallucinationGuardEnabled", s.hallucinationGuardEnabled)
            put("personalLearningEnabled", s.personalLearningEnabled)
            put("proactiveSuggestionsEnabled", s.proactiveSuggestionsEnabled)
            put("customWakeAck", s.customWakeAck)
        }
        return KeystoreCryptoManager.encrypt(obj.toString())
    }

    fun importEncryptedBackupJson(encryptedBlob: String): Boolean {
        if (!encryptedBlob.trim().startsWith("ENC1:")) return false
        val plain = KeystoreCryptoManager.decrypt(encryptedBlob.trim())
        if (plain.isBlank()) return false
        return runCatching {
            val obj = JSONObject(plain)
            update { cur ->
                val restoredUrl = obj.optString("gatewayBaseUrl", cur.gatewayBaseUrl).trim().trimEnd('/')
                val safeUrl = restoredUrl.takeIf { url -> runCatching { java.net.URI(url).let { it.scheme == "https" && it.host != null && it.userInfo == null && it.query == null && it.fragment == null } }.getOrDefault(false) } ?: cur.gatewayBaseUrl
                val restoredSecondaryUrl = obj.optString("secondaryGatewayBaseUrl", cur.secondaryGatewayBaseUrl).trim().trimEnd('/')
                val safeSecondaryUrl = restoredSecondaryUrl.takeIf { url -> url.isBlank() || runCatching { java.net.URI(url).let { it.scheme == "https" && it.host != null && it.userInfo == null && it.query == null && it.fragment == null } }.getOrDefault(false) } ?: cur.secondaryGatewayBaseUrl
                cur.copy(
                    gatewayBaseUrl = safeUrl,
                    openRouterApiKey = if (safeUrl == cur.gatewayBaseUrl.trim().trimEnd('/')) cur.openRouterApiKey else "",
                    secondaryGatewayBaseUrl = safeSecondaryUrl,
                    secondaryGatewayApiKey = if (safeSecondaryUrl == cur.secondaryGatewayBaseUrl.trim().trimEnd('/')) cur.secondaryGatewayApiKey else "",
                    secondaryGatewayModel = obj.optString("secondaryGatewayModel", cur.secondaryGatewayModel),
                    preferredBrainId = obj.optString("preferredBrainId", cur.preferredBrainId),
                    brainThinkingMode = obj.optString("brainThinkingMode", cur.brainThinkingMode),
                    hallucinationGuardEnabled = obj.optBoolean("hallucinationGuardEnabled", cur.hallucinationGuardEnabled),
                    personalLearningEnabled = obj.optBoolean("personalLearningEnabled", cur.personalLearningEnabled),
                    proactiveSuggestionsEnabled = obj.optBoolean("proactiveSuggestionsEnabled", cur.proactiveSuggestionsEnabled),
                    customWakeAck = obj.optString("customWakeAck", cur.customWakeAck).take(120),
                    smartMode = runCatching { SmartModelPolicy.Mode.valueOf(obj.optString("smartMode", cur.smartMode.name)) }.getOrDefault(cur.smartMode),
                    activeProvider = runCatching { AiProvider.valueOf(obj.optString("activeProvider", cur.activeProvider.name)) }.getOrDefault(cur.activeProvider),
                    manualVisionConfirmed = false,
                    userName = obj.optString("userName", cur.userName),
                    geminiChatModel = obj.optString("geminiChatModel", cur.geminiChatModel),
                    geminiLiveVoiceModel = obj.optString("geminiLiveVoiceModel", cur.geminiLiveVoiceModel),
                    geminiLiveSpeaker = com.example.voice.LiveVoiceChoices.normalize(obj.optString("geminiLiveSpeaker", cur.geminiLiveSpeaker)),
                    openRouterModel = obj.optString("openRouterModel", cur.openRouterModel),
                    githubRepoSlug = obj.optString("githubRepoSlug", cur.githubRepoSlug),
                    githubBaseBranch = obj.optString("githubBaseBranch", cur.githubBaseBranch),
                    wakeWord = obj.optString("wakeWord", cur.wakeWord),
                    assistantName = obj.optString("assistantName",cur.assistantName).take(40),
                    replyStyle = obj.optString("replyStyle",cur.replyStyle),
                    preferOfflineSpeech = obj.optBoolean("preferOfflineSpeech",cur.preferOfflineSpeech),
                    languageTag = obj.optString("languageTag", cur.languageTag),
                    visualTheme = runCatching { OmegaVisualTheme.valueOf(obj.optString("visualTheme", cur.visualTheme.name)) }.getOrDefault(cur.visualTheme),
                    orbColorPreset = OrbColorPreset.fromStored(obj.optString("orbColorPreset", cur.orbColorPreset.name)),
                    renderQuality = RenderQuality.fromStored(obj.optString("renderQuality", cur.renderQuality.name))
                )
            }
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val PREFS_NAME = "omega_pandu_v19_auto_prefs"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PROVIDER = "active_provider"
        private const val KEY_GEMINI_KEY = "enc_gemini_key"
        private const val KEY_OPENROUTER_KEY = "enc_openrouter_key"
        private const val KEY_GEMINI_CHAT_MODEL = "gemini_chat_model"
        private const val KEY_GEMINI_LIVE_MODEL = "gemini_live_model"
        private const val KEY_OPENROUTER_MODEL = "openrouter_model"
        private const val KEY_GH_REPO = "gh_repo_slug"
        private const val KEY_GH_TOKEN = "enc_gh_token"
        private const val KEY_SECONDARY_GATEWAY_KEY = "enc_secondary_gateway_key"
        private const val KEY_GH_BRANCH = "gh_base_branch"
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_WAKE_SENS = "wake_sens"
        private const val KEY_LANGUAGE = "language_tag"
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val KEY_CONTINUOUS = "continuous_conv"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_TTS_STYLE = "tts_style"
        private const val KEY_WAKE_ENABLED = "wake_word_enabled"
        private const val KEY_BT_ROUTING = "bt_routing"
        private const val KEY_MIC_MUTED = "mic_muted"
        private const val KEY_LIVE_AUDIO = "use_live_audio"
        private const val KEY_WEB_GROUNDING = "web_grounding"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock"
        private const val KEY_CONFIRM_REPLY = "confirm_notif_reply"
        private const val KEY_PARALLAX = "gyro_parallax"
        private const val KEY_OFFLINE_FAST = "offline_fast_cmd"
        private const val KEY_TRIG_BT = "trig_bt"
        private const val KEY_TRIG_CHARGE = "trig_charge"
        private const val KEY_TRIG_LOWBAT = "trig_lowbat"
        private const val KEY_THEME = "visual_theme"
        private const val KEY_ORB_COLOR = "orb_color"
    }
}
