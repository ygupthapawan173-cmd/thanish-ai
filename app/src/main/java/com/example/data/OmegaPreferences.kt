package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class AiProvider(val displayName: String, val defaultChatModel: String) {
    GEMINI("Google Gemini Direct", "gemini-4-hydrogen"),
    OPENAI("OpenAI GPT", "gpt-6.2-sol")
}

enum class IntelligenceMode(val label: String, val description: String) {
    FAST("Fast Intelligence", "Ultra-low-latency direct responses and quick classification"),
    SMART("Smart Intelligence", "Balanced bilingual synthesis, search verification, and tool execution"),
    DEEP("Deep Intelligence", "Extended multi-step reasoning, research synthesis, and STEM analysis");

    companion object {
        fun fromStored(raw: String?): IntelligenceMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: SMART
    }
}

data class OmegaSettings(
    val userName: String = "Boss",
    val assistantName: String = "JARVIS INFINITY",
    val activeProvider: AiProvider = AiProvider.GEMINI,
    val geminiApiKey: String = "",
    val openAiApiKey: String = "",
    val sarvamApiKey: String = "",
    val sarvamSpeaker: String = "shubh_enhi_ads",
    val sarvamModel: String = "bulbul:v4-flash",
    val geminiChatModel: String = "gemini-4-hydrogen",
    val geminiLiveVoiceModel: String = "gemini-3.8-live",
    val openAiLiveVoiceModel: String = "gpt-live-1.1",
    val openAiModel: String = "gpt-6.2-sol",
    val openAiBaseUrl: String = "https://api.openai.com/v1",
    val intelligenceMode: IntelligenceMode = IntelligenceMode.SMART,
    val freeModelPriority: Boolean = true,
    val allowPaidModels: Boolean = false,
    val webSearchGrounding: Boolean = true,
    val deepResearchEnabled: Boolean = true,
    val autoSpeak: Boolean = true,
    val languageTag: String = "auto",
    val wakeWordEnabled: Boolean = false,
    val screenLockWakeEnabled: Boolean = true,
    val onlineWakePriority: Boolean = true,
    val cloudAudioConsentGranted: Boolean = false,
    val useLiveSessionAudio: Boolean = false,
    val micPrivacyMuted: Boolean = false,
    val confirmSensitiveActions: Boolean = true,
    val biometricSafetyLock: Boolean = false,
    val releaseRepoSlug: String = "ygupthapawan173-cmd/thanish-ai",
    val releaseAuthToken: String = "",
    val renderQuality: RenderQuality = RenderQuality.ADAPTIVE
) {
    fun effectiveGeminiKey(): String = geminiApiKey.trim().ifEmpty { BuildConfig.GEMINI_API_KEY.trim() }
    fun effectiveOpenAiKey(): String = openAiApiKey.trim().ifEmpty {
        if (openAiBaseUrl.trim().trimEnd('/') == "https://api.openai.com/v1") "" else BuildConfig.OPENROUTER_API_KEY.trim()
    }
    fun effectiveSarvamKey(): String = sarvamApiKey.trim()

    fun activeChatModel(): String = when (activeProvider) {
        AiProvider.GEMINI -> geminiChatModel.trim().ifEmpty { AiProvider.GEMINI.defaultChatModel }
        AiProvider.OPENAI -> openAiModel.trim().ifEmpty { AiProvider.OPENAI.defaultChatModel }
    }
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

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(changeListener)
    }

    private fun load(): OmegaSettings {
        val rawProvider = prefs.getString(KEY_PROVIDER, AiProvider.GEMINI.name).orEmpty()
        val provider = when {
            rawProvider.equals("OPENAI", true) || rawProvider.equals("OPENROUTER", true) -> AiProvider.OPENAI
            else -> AiProvider.GEMINI
        }
        val legacyGatewayUrl = prefs.getString("gateway_base_url", "")?.trim()?.trimEnd('/').orEmpty()
        val storedOpenAiUrl = prefs.getString(KEY_OPENAI_BASE_URL, "").orEmpty().ifBlank {
            if (legacyGatewayUrl.contains("openai.com")) legacyGatewayUrl else "https://api.openai.com/v1"
        }
        val storedOpenAiKey = prefs.getString(KEY_OPENAI_KEY, "").orEmpty().ifBlank {
            if (legacyGatewayUrl.contains("openai.com")) prefs.getString(KEY_LEGACY_OPENROUTER_KEY, "").orEmpty() else ""
        }

        return OmegaSettings(
            userName = prefs.getString(KEY_USER_NAME, "Boss") ?: "Boss",
            assistantName = "JARVIS INFINITY",
            activeProvider = provider,
            geminiApiKey = KeystoreCryptoManager.decrypt(prefs.getString(KEY_GEMINI_KEY, "").orEmpty()),
            openAiApiKey = KeystoreCryptoManager.decrypt(storedOpenAiKey),
            sarvamApiKey = KeystoreCryptoManager.decrypt(prefs.getString(KEY_SARVAM_KEY, "").orEmpty()),
            sarvamSpeaker = prefs.getString(KEY_SARVAM_SPEAKER, "shubh_enhi_ads")?.ifBlank { "shubh_enhi_ads" } ?: "shubh_enhi_ads",
            sarvamModel = prefs.getString(KEY_SARVAM_MODEL, "bulbul:v4-flash")?.ifBlank { "bulbul:v4-flash" } ?: "bulbul:v4-flash",
            geminiChatModel = prefs.getString(KEY_GEMINI_CHAT_MODEL, "gemini-4-hydrogen")?.ifBlank { "gemini-4-hydrogen" } ?: "gemini-4-hydrogen",
            geminiLiveVoiceModel = prefs.getString(KEY_GEMINI_LIVE_MODEL, "gemini-3.8-live")?.ifBlank { "gemini-3.8-live" } ?: "gemini-3.8-live",
            openAiLiveVoiceModel = prefs.getString("openai_live_model", "gpt-live-1.1").orEmpty().takeIf { it in setOf("gpt-live-1.1", "gpt-realtime-2.1", "gpt-realtime-2.1-mini") } ?: "gpt-live-1.1",
            openAiModel = prefs.getString(KEY_OPENAI_MODEL, "gpt-6.2-sol")?.ifBlank { "gpt-6.2-sol" } ?: "gpt-6.2-sol",
            openAiBaseUrl = storedOpenAiUrl,
            intelligenceMode = IntelligenceMode.fromStored(prefs.getString(KEY_INTEL_MODE, null)),
            freeModelPriority = prefs.getBoolean(KEY_FREE_PRIORITY, true),
            allowPaidModels = prefs.getBoolean(KEY_ALLOW_PAID, false),
            webSearchGrounding = prefs.getBoolean(KEY_WEB_GROUNDING, true),
            deepResearchEnabled = prefs.getBoolean(KEY_DEEP_RESEARCH, true),
            autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, true),
            languageTag = prefs.getString(KEY_LANGUAGE, "auto") ?: "auto",
            wakeWordEnabled = prefs.getBoolean(KEY_WAKE_ENABLED, false),
            screenLockWakeEnabled = prefs.getBoolean(KEY_LOCK_WAKE_ENABLED, true),
            onlineWakePriority = prefs.getBoolean(KEY_ONLINE_WAKE_PRIORITY, true),
            cloudAudioConsentGranted = prefs.getBoolean(KEY_CLOUD_CONSENT, false),
            useLiveSessionAudio = prefs.getBoolean(KEY_LIVE_AUDIO, false),
            micPrivacyMuted = prefs.getBoolean(KEY_MIC_MUTED, false),
            confirmSensitiveActions = prefs.getBoolean(KEY_CONFIRM_SENSITIVE, true),
            biometricSafetyLock = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false),
            releaseRepoSlug = prefs.getString(KEY_RELEASE_REPO, "ygupthapawan173-cmd/thanish-ai") ?: "ygupthapawan173-cmd/thanish-ai",
            releaseAuthToken = KeystoreCryptoManager.decrypt(prefs.getString(KEY_RELEASE_TOKEN, "").orEmpty()),
            renderQuality = RenderQuality.fromStored(prefs.getString(KEY_RENDER_QUALITY, null))
        )
    }

    private fun protectedSecret(secret: String): String {
        if (secret.isBlank()) return ""
        val encrypted = KeystoreCryptoManager.encrypt(secret.trim())
        check(encrypted.startsWith("ENC1:")) { "Android Keystore unavailable; credential was not saved" }
        return encrypted
    }

    private fun keepOrProtect(newVal: String, prevVal: String, prefKey: String): String {
        val stored = prefs.getString(prefKey, "").orEmpty()
        if (newVal == prevVal && (stored.startsWith("ENC1:") || newVal.isBlank())) return stored
        return protectedSecret(newVal)
    }

    @Synchronized
    fun update(transform: (OmegaSettings) -> OmegaSettings) {
        val old = _settings.value
        val u = transform(old).copy(assistantName = "JARVIS INFINITY")
        val geminiEnc = keepOrProtect(u.geminiApiKey, old.geminiApiKey, KEY_GEMINI_KEY)
        val openAiEnc = keepOrProtect(u.openAiApiKey, old.openAiApiKey, KEY_OPENAI_KEY)
        val sarvamEnc = keepOrProtect(u.sarvamApiKey, old.sarvamApiKey, KEY_SARVAM_KEY)
        val releaseTokEnc = keepOrProtect(u.releaseAuthToken, old.releaseAuthToken, KEY_RELEASE_TOKEN)

        val editor = prefs.edit()
            .putString(KEY_USER_NAME, u.userName.trim().ifEmpty { "Boss" })
            .putString(KEY_PROVIDER, u.activeProvider.name)
            .putString(KEY_GEMINI_KEY, geminiEnc)
            .putString(KEY_OPENAI_KEY, openAiEnc)
            .putString(KEY_SARVAM_KEY, sarvamEnc)
            .putString(KEY_SARVAM_SPEAKER, u.sarvamSpeaker.trim().ifEmpty { "shubh_enhi_ads" })
            .putString(KEY_SARVAM_MODEL, u.sarvamModel.trim().ifEmpty { "bulbul:v4-flash" })
            .putString(KEY_GEMINI_CHAT_MODEL, u.geminiChatModel.trim().ifEmpty { "gemini-4-hydrogen" })
            .putString(KEY_GEMINI_LIVE_MODEL, u.geminiLiveVoiceModel.trim().ifEmpty { "gemini-3.8-live" })
            .putString("openai_live_model", u.openAiLiveVoiceModel)
            .putString(KEY_OPENAI_MODEL, u.openAiModel.trim().ifEmpty { "gpt-6.2-sol" })
            .putString(KEY_OPENAI_BASE_URL, u.openAiBaseUrl.trim().trimEnd('/').ifEmpty { "https://api.openai.com/v1" })
            .putString(KEY_INTEL_MODE, u.intelligenceMode.name)
            .putBoolean(KEY_FREE_PRIORITY, u.freeModelPriority)
            .putBoolean(KEY_ALLOW_PAID, u.allowPaidModels)
            .putBoolean(KEY_WEB_GROUNDING, u.webSearchGrounding)
            .putBoolean(KEY_DEEP_RESEARCH, u.deepResearchEnabled)
            .putBoolean(KEY_AUTO_SPEAK, u.autoSpeak)
            .putString(KEY_LANGUAGE, u.languageTag)
            .putBoolean(KEY_WAKE_ENABLED, u.wakeWordEnabled)
            .putBoolean(KEY_LOCK_WAKE_ENABLED, u.screenLockWakeEnabled)
            .putBoolean(KEY_ONLINE_WAKE_PRIORITY, u.onlineWakePriority)
            .putBoolean(KEY_CLOUD_CONSENT, u.cloudAudioConsentGranted)
            .putBoolean(KEY_LIVE_AUDIO, u.useLiveSessionAudio)
            .putBoolean(KEY_MIC_MUTED, u.micPrivacyMuted)
            .putBoolean(KEY_CONFIRM_SENSITIVE, u.confirmSensitiveActions)
            .putBoolean(KEY_BIOMETRIC_LOCK, u.biometricSafetyLock)
            .putString(KEY_RELEASE_REPO, u.releaseRepoSlug.trim().ifEmpty { "ygupthapawan173-cmd/thanish-ai" })
            .putString(KEY_RELEASE_TOKEN, releaseTokEnc)
            .putString(KEY_RENDER_QUALITY, u.renderQuality.name)
            // Clean deprecated preference keys from earlier versions
            .remove("prefer_offline_speech")
            .remove("continuous_conv")
            .remove("tts_speed")
            .remove("tts_pitch")
            .remove("tts_style")
            .remove("bt_routing")
            .remove("trig_bt")
            .remove("wake_word")
            .remove("gemini_live_speaker")
            .remove("offline_fast_cmd")
            .remove("visual_theme")
            .remove("orb_color")
            .remove("local_brain_mode")
            .remove("local_llm_url")
        editor.commit()
        _settings.value = u
    }

    /** Internal encrypted snapshot used strictly before self-update APK verification. */
    fun exportEncryptedBackupJson(): String {
        val s = _settings.value
        val obj = JSONObject().apply {
            put("userName", s.userName)
            put("activeProvider", s.activeProvider.name)
            put("geminiChatModel", s.geminiChatModel)
            put("geminiLiveVoiceModel", s.geminiLiveVoiceModel)
            put("openAiLiveVoiceModel", s.openAiLiveVoiceModel)
            put("openAiModel", s.openAiModel)
            put("sarvamSpeaker", s.sarvamSpeaker)
            put("sarvamModel", s.sarvamModel)
            put("intelligenceMode", s.intelligenceMode.name)
            put("releaseRepoSlug", s.releaseRepoSlug)
            put("languageTag", s.languageTag)
        }
        return KeystoreCryptoManager.encrypt(obj.toString())
    }

    companion object {
        private const val PREFS_NAME = "omega_pandu_v19_auto_prefs"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PROVIDER = "active_provider"
        private const val KEY_GEMINI_KEY = "enc_gemini_key"
        private const val KEY_LEGACY_OPENROUTER_KEY = "enc_openrouter_key"
        private const val KEY_OPENAI_KEY = "enc_openai_key"
        private const val KEY_SARVAM_KEY = "enc_sarvam_key"
        private const val KEY_SARVAM_SPEAKER = "sarvam_speaker"
        private const val KEY_SARVAM_MODEL = "sarvam_model"
        private const val KEY_GEMINI_CHAT_MODEL = "gemini_chat_model"
        private const val KEY_GEMINI_LIVE_MODEL = "gemini_live_model"
        private const val KEY_OPENAI_MODEL = "openrouter_model"
        private const val KEY_OPENAI_BASE_URL = "openai_base_url"
        private const val KEY_INTEL_MODE = "intelligence_mode"
        private const val KEY_FREE_PRIORITY = "free_model_priority"
        private const val KEY_ALLOW_PAID = "allow_paid_models"
        private const val KEY_WEB_GROUNDING = "web_grounding"
        private const val KEY_DEEP_RESEARCH = "deep_research"
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val KEY_LANGUAGE = "language_tag"
        private const val KEY_WAKE_ENABLED = "wake_word_enabled"
        private const val KEY_LOCK_WAKE_ENABLED = "screen_lock_wake_enabled"
        private const val KEY_ONLINE_WAKE_PRIORITY = "online_wake_priority"
        private const val KEY_CLOUD_CONSENT = "cloud_audio_consent"
        private const val KEY_LIVE_AUDIO = "use_live_audio"
        private const val KEY_MIC_MUTED = "mic_muted"
        private const val KEY_CONFIRM_SENSITIVE = "confirm_notif_reply"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock"
        private const val KEY_RELEASE_REPO = "gh_repo_slug"
        private const val KEY_RELEASE_TOKEN = "enc_gh_token"
        private const val KEY_RENDER_QUALITY = "render_quality"
    }
}
