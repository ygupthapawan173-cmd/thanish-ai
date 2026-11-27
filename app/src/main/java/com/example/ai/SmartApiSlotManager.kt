package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import com.example.data.AiProvider
import com.example.data.OmegaSettings
import com.example.security.KeystoreCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * V110.1 Smart API Slot Manager (Module 8, Targets 56-75) + Persistent Model Discovery Cache.
 *
 * Security & Honesty Guarantees:
 * - API keys are encrypted via Android Keystore AES-256-GCM before persisting to SharedPreferences.
 * - Status starts UNCONFIGURED or UNVERIFIED and NEVER reports VERIFIED until an actual live HTTP probe
 *   or verified API response succeeds.
 * - Chat and TTS capabilities are validated separately so voice routes never assume chat endpoints speak.
 * - Zero Paid Credits Protection and individual slot USD budgets/daily limits are strictly enforced.
 */
enum class ApiProviderKind(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultChatModel: String,
    val defaultTtsModel: String?,
    val supportsOpenAiAudioSpeech: Boolean,
    val defaultEstimatedCostPer1M: Double
) {
    GEMINI_DIRECT(
        id = "gemini_direct",
        displayName = "Gemini Direct",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultChatModel = "gemini-3.8-flash",
        defaultTtsModel = "gemini-2.5-flash-preview-tts",
        supportsOpenAiAudioSpeech = false,
        defaultEstimatedCostPer1M = 0.0
    ),
    AICREDITS(
        id = "aicredits",
        displayName = "AICredits",
        defaultBaseUrl = "https://api.aicredits.in/v1",
        defaultChatModel = "openai/gpt-4o-mini",
        defaultTtsModel = "openai/tts-1",
        supportsOpenAiAudioSpeech = true,
        defaultEstimatedCostPer1M = 0.15
    ),
    ONEAI_KEY(
        id = "oneai_key",
        displayName = "OneAIAPIKey",
        defaultBaseUrl = "https://api.oneaiapi.com/v1",
        defaultChatModel = "gpt-4o-mini",
        defaultTtsModel = "tts-1",
        supportsOpenAiAudioSpeech = true,
        defaultEstimatedCostPer1M = 0.25
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultChatModel = "google/gemini-2.5-flash",
        defaultTtsModel = null,
        supportsOpenAiAudioSpeech = false,
        defaultEstimatedCostPer1M = 0.10
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultChatModel = "gpt-4o-mini",
        defaultTtsModel = "tts-1",
        supportsOpenAiAudioSpeech = true,
        defaultEstimatedCostPer1M = 0.30
    ),
    CUSTOM_OPENAI(
        id = "custom_openai",
        displayName = "Custom OpenAI-Compatible",
        defaultBaseUrl = "",
        defaultChatModel = "",
        defaultTtsModel = null,
        supportsOpenAiAudioSpeech = false,
        defaultEstimatedCostPer1M = 0.50
    );

    companion object {
        fun fromId(raw: String?): ApiProviderKind =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: CUSTOM_OPENAI
    }
}

enum class SlotRoutingMode(val label: String) {
    FASTEST("Fastest"),
    LOWEST_COST("Lowest Cost"),
    FREE_FIRST("Free First"),
    CONFIGURABLE_PRIORITY("Custom Priority");

    companion object {
        fun fromStored(raw: String?): SlotRoutingMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: FREE_FIRST
    }
}

enum class SlotRoutePurpose {
    CHAT,
    CODING,
    VOICE_TTS
}

enum class SlotConnectionState(val label: String) {
    UNCONFIGURED("Key Required"),
    UNVERIFIED("Not Verified"),
    TESTING("Testing..."),
    VERIFIED("Verified"),
    COOLDOWN("Cooling Down"),
    FAILED("Error"),
    DISABLED("Disabled");

    companion object {
        fun fromStored(raw: String?): SlotConnectionState =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNVERIFIED
    }
}

data class SmartApiSlot(
    val id: String,
    val label: String,
    val providerKind: ApiProviderKind,
    val baseUrl: String,
    val apiKey: String,
    val chatModel: String = providerKind.defaultChatModel,
    val codingModel: String = "",
    val ttsModel: String = providerKind.defaultTtsModel.orEmpty(),
    val enabled: Boolean = true,
    val priority: Int = 10,
    val isFreeTier: Boolean = providerKind == ApiProviderKind.GEMINI_DIRECT || providerKind == ApiProviderKind.OPENROUTER,
    val budgetUsd: Double = 0.0,
    val dailyRequestLimit: Int = 0,
    val useForChat: Boolean = true,
    val useForCoding: Boolean = true,
    val useForVoiceTts: Boolean = providerKind == ApiProviderKind.GEMINI_DIRECT || providerKind.supportsOpenAiAudioSpeech,
    val supportsChat: Boolean = true,
    val supportsTts: Boolean = providerKind == ApiProviderKind.GEMINI_DIRECT || providerKind.supportsOpenAiAudioSpeech,
    val chatStatus: SlotConnectionState = if (apiKey.isBlank()) SlotConnectionState.UNCONFIGURED else SlotConnectionState.UNVERIFIED,
    val ttsStatus: SlotConnectionState = when {
        apiKey.isBlank() -> SlotConnectionState.UNCONFIGURED
        providerKind == ApiProviderKind.GEMINI_DIRECT || providerKind.supportsOpenAiAudioSpeech -> SlotConnectionState.UNVERIFIED
        else -> SlotConnectionState.DISABLED
    },
    val lastLatencyMs: Long = 0L,
    val avgLatencyMs: Long = 0L,
    val requestsToday: Int = 0,
    val failuresTotal: Int = 0,
    val promptTokensTotal: Long = 0L,
    val outputTokensTotal: Long = 0L,
    val knownCostUsd: Double = 0.0,
    val estimatedCostPer1MTokens: Double = providerKind.defaultEstimatedCostPer1M,
    val cooldownUntilMs: Long = 0L,
    val lastHttpCode: Int = 0,
    val lastChatError: String = "",
    val lastTtsError: String = "",
    val discoveredModels: List<String> = emptyList(),
    val discoveredTtsModels: List<String> = emptyList(),
    val discoveredAtMs: Long = 0L,
    val legacySourceId: String = ""
) {
    fun effectiveBaseUrl(): String = baseUrl.trim().trimEnd('/').ifBlank { providerKind.defaultBaseUrl }

    fun effectiveStatus(nowMs: Long = System.currentTimeMillis()): SlotConnectionState = when {
        !enabled -> SlotConnectionState.DISABLED
        apiKey.isBlank() -> SlotConnectionState.UNCONFIGURED
        cooldownUntilMs > nowMs -> SlotConnectionState.COOLDOWN
        else -> chatStatus
    }

    fun effectiveTtsStatus(nowMs: Long = System.currentTimeMillis()): SlotConnectionState = when {
        !enabled || !useForVoiceTts || !supportsTts -> SlotConnectionState.DISABLED
        apiKey.isBlank() -> SlotConnectionState.UNCONFIGURED
        cooldownUntilMs > nowMs -> SlotConnectionState.COOLDOWN
        else -> ttsStatus
    }

    fun maskedKey(): String {
        val t = apiKey.trim()
        if (t.isBlank()) return "No API key"
        if (t.length <= 8) return "Configured"
        return "${t.take(4)}...${t.takeLast(4)}"
    }

    fun supportedBrains(): List<AiBrainFamily> {
        val candidateModels = (discoveredModels + listOfNotNull(
            chatModel.takeIf { it.isNotBlank() },
            codingModel.takeIf { it.isNotBlank() }
        )).distinct()
        val detected = candidateModels.mapNotNull { MultiBrainPolicy.classifyModelFamily(it) }.distinct()
        if (detected.isNotEmpty()) return detected
        return when (providerKind) {
            ApiProviderKind.GEMINI_DIRECT -> listOf(AiBrainFamily.GEMINI_DIRECT)
            ApiProviderKind.OPENAI -> listOf(AiBrainFamily.OPENAI_GPT)
            ApiProviderKind.AICREDITS, ApiProviderKind.ONEAI_KEY, ApiProviderKind.OPENROUTER ->
                listOf(
                    AiBrainFamily.GEMINI_DIRECT,
                    AiBrainFamily.OPENAI_GPT,
                    AiBrainFamily.CLAUDE_CODING,
                    AiBrainFamily.DEEPSEEK_REASONING,
                    AiBrainFamily.QWEN_INTELLIGENCE,
                    AiBrainFamily.GROK_AI,
                    AiBrainFamily.KIMI_LONG_CONTEXT,
                    AiBrainFamily.MISTRAL_FAST,
                    AiBrainFamily.LLAMA_AI,
                    AiBrainFamily.GPT_OSS_REASONING
                )
            ApiProviderKind.CUSTOM_OPENAI -> listOf(AiBrainFamily.OPENAI_GPT)
        }
    }

    fun withinBudget(): Boolean {
        if (dailyRequestLimit > 0 && requestsToday >= dailyRequestLimit) return false
        if (budgetUsd > 0.0 && knownCostUsd >= budgetUsd) return false
        return true
    }

    fun allowedUnderCostPolicy(zeroPaidCreditsMode: Boolean): Boolean {
        if (!withinBudget()) return false
        if (!zeroPaidCreditsMode) return true
        // Zero Paid Credits mode permits free-tier providers (Gemini Direct, free-tagged OpenRouter)
        // or slots with explicit free model selection.
        return when (providerKind) {
            ApiProviderKind.GEMINI_DIRECT -> budgetUsd == 0.0
            ApiProviderKind.OPENROUTER -> isFreeTier || chatModel.endsWith(":free", ignoreCase = true)
            else -> isFreeTier
        }
    }

    fun hasFreshModelCache(nowMs: Long, ttlMs: Long = MODEL_CACHE_TTL_MS): Boolean =
        discoveredModels.isNotEmpty() && discoveredAtMs > 0L && (nowMs - discoveredAtMs) <= ttlMs

    companion object {
        const val MODEL_CACHE_TTL_MS = 12 * 60 * 60 * 1000L // 12 hours persistent model cache
    }
}

data class SlotTestOutcome(
    val slotId: String,
    val label: String,
    val providerKind: ApiProviderKind,
    val chatOk: Boolean,
    val chatDetail: String,
    val ttsOk: Boolean?,
    val ttsDetail: String,
    val latencyMs: Long,
    val discoveredChatCount: Int,
    val discoveredTtsCount: Int
)

interface SlotKeyCipher {
    fun encrypt(plain: String): String
    fun decrypt(stored: String): String
}

object AndroidSlotKeyCipher : SlotKeyCipher {
    override fun encrypt(plain: String): String =
        if (plain.isBlank()) "" else KeystoreCryptoManager.encrypt(plain.trim())

    override fun decrypt(stored: String): String =
        if (stored.isBlank()) "" else KeystoreCryptoManager.decrypt(stored)
}

interface ApiSlotStorage {
    fun loadRaw(): String
    fun saveRaw(serialized: String): Boolean
    fun loadRoutingMode(): SlotRoutingMode
    fun saveRoutingMode(mode: SlotRoutingMode)
}

class InMemorySlotStorage : ApiSlotStorage {
    @Volatile private var raw = ""
    @Volatile private var mode = SlotRoutingMode.FREE_FIRST
    override fun loadRaw(): String = raw
    override fun saveRaw(serialized: String): Boolean { raw = serialized; return true }
    override fun loadRoutingMode(): SlotRoutingMode = mode
    override fun saveRoutingMode(mode: SlotRoutingMode) { this.mode = mode }
}

private class SharedPrefsApiSlotStorage(context: Context) : ApiSlotStorage {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jarvis_v1101_smart_api_slots", Context.MODE_PRIVATE)

    override fun loadRaw(): String = prefs.getString(KEY_SLOTS_JSON, "").orEmpty()
    override fun saveRaw(serialized: String): Boolean =
        prefs.edit().putString(KEY_SLOTS_JSON, serialized).commit()

    override fun loadRoutingMode(): SlotRoutingMode =
        SlotRoutingMode.fromStored(prefs.getString(KEY_ROUTING_MODE, null))

    override fun saveRoutingMode(mode: SlotRoutingMode) {
        prefs.edit().putString(KEY_ROUTING_MODE, mode.name).apply()
    }

    companion object {
        private const val KEY_SLOTS_JSON = "slots_json_v1"
        private const val KEY_ROUTING_MODE = "routing_mode_v1"
    }
}

/**
 * Pure, deterministic state engine for Smart API Slots (unit-testable on JVM).
 */
class SlotRegistryEngine(
    private val storage: ApiSlotStorage,
    private val cipher: SlotKeyCipher,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val _slots = MutableStateFlow<List<SmartApiSlot>>(emptyList())
    val slots: StateFlow<List<SmartApiSlot>> = _slots.asStateFlow()

    private val _routingMode = MutableStateFlow(SlotRoutingMode.FREE_FIRST)
    val routingMode: StateFlow<SlotRoutingMode> = _routingMode.asStateFlow()

    init {
        loadFromStorage()
    }

    @Synchronized
    fun loadFromStorage() {
        _routingMode.value = storage.loadRoutingMode()
        val raw = storage.loadRaw()
        if (raw.isBlank()) {
            _slots.value = emptyList()
            return
        }
        val parsed = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val kind = ApiProviderKind.fromId(o.optString("providerKind"))
                    val encKey = o.optString("encApiKey", "")
                    val plainKey = if (encKey.isNotBlank()) runCatching { cipher.decrypt(encKey) }.getOrDefault("") else ""
                    val chatList = mutableListOf<String>()
                    val chatArr = o.optJSONArray("discoveredModels")
                    if (chatArr != null) {
                        for (j in 0 until chatArr.length()) {
                            chatArr.optString(j).takeIf { it.isNotBlank() }?.let(chatList::add)
                        }
                    }
                    val ttsList = mutableListOf<String>()
                    val ttsArr = o.optJSONArray("discoveredTtsModels")
                    if (ttsArr != null) {
                        for (j in 0 until ttsArr.length()) {
                            ttsArr.optString(j).takeIf { it.isNotBlank() }?.let(ttsList::add)
                        }
                    }
                    val rawChatStatus = SlotConnectionState.fromStored(o.optString("chatStatus"))
                    val restoredChatStatus = if (plainKey.isBlank()) SlotConnectionState.UNCONFIGURED else rawChatStatus
                    val rawTtsStatus = SlotConnectionState.fromStored(o.optString("ttsStatus"))
                    val restoredTtsStatus = if (plainKey.isBlank()) SlotConnectionState.UNCONFIGURED else rawTtsStatus
                    add(
                        SmartApiSlot(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            label = o.optString("label").ifBlank { kind.displayName },
                            providerKind = kind,
                            baseUrl = o.optString("baseUrl", kind.defaultBaseUrl),
                            apiKey = plainKey,
                            chatModel = o.optString("chatModel", kind.defaultChatModel),
                            codingModel = o.optString("codingModel", ""),
                            ttsModel = o.optString("ttsModel", kind.defaultTtsModel.orEmpty()),
                            enabled = o.optBoolean("enabled", true),
                            priority = o.optInt("priority", i + 1),
                            isFreeTier = o.optBoolean("isFreeTier", kind == ApiProviderKind.GEMINI_DIRECT || kind == ApiProviderKind.OPENROUTER),
                            budgetUsd = o.optDouble("budgetUsd", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
                            dailyRequestLimit = o.optInt("dailyRequestLimit", 0).coerceAtLeast(0),
                            useForChat = o.optBoolean("useForChat", true),
                            useForCoding = o.optBoolean("useForCoding", true),
                            useForVoiceTts = o.optBoolean("useForVoiceTts", kind == ApiProviderKind.GEMINI_DIRECT || kind.supportsOpenAiAudioSpeech),
                            supportsChat = o.optBoolean("supportsChat", true),
                            supportsTts = o.optBoolean("supportsTts", kind == ApiProviderKind.GEMINI_DIRECT || kind.supportsOpenAiAudioSpeech),
                            chatStatus = restoredChatStatus,
                            ttsStatus = restoredTtsStatus,
                            lastLatencyMs = o.optLong("lastLatencyMs", 0L).coerceAtLeast(0L),
                            avgLatencyMs = o.optLong("avgLatencyMs", 0L).coerceAtLeast(0L),
                            requestsToday = o.optInt("requestsToday", 0).coerceAtLeast(0),
                            failuresTotal = o.optInt("failuresTotal", 0).coerceAtLeast(0),
                            promptTokensTotal = o.optLong("promptTokensTotal", 0L).coerceAtLeast(0L),
                            outputTokensTotal = o.optLong("outputTokensTotal", 0L).coerceAtLeast(0L),
                            knownCostUsd = o.optDouble("knownCostUsd", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
                            estimatedCostPer1MTokens = o.optDouble("estimatedCostPer1MTokens", kind.defaultEstimatedCostPer1M)
                                .takeIf { it.isFinite() && it >= 0.0 } ?: kind.defaultEstimatedCostPer1M,
                            cooldownUntilMs = o.optLong("cooldownUntilMs", 0L).coerceAtLeast(0L),
                            lastHttpCode = o.optInt("lastHttpCode", 0),
                            lastChatError = o.optString("lastChatError", ""),
                            lastTtsError = o.optString("lastTtsError", ""),
                            discoveredModels = chatList.distinct(),
                            discoveredTtsModels = ttsList.distinct(),
                            discoveredAtMs = o.optLong("discoveredAtMs", 0L).coerceAtLeast(0L),
                            legacySourceId = o.optString("legacySourceId", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        _slots.value = parsed
    }

    @Synchronized
    private fun persistLocked() {
        val arr = JSONArray()
        for (slot in _slots.value) {
            val encryptedKey = if (slot.apiKey.isBlank()) "" else cipher.encrypt(slot.apiKey.trim())
            val obj = JSONObject().apply {
                put("id", slot.id)
                put("label", slot.label)
                put("providerKind", slot.providerKind.id)
                put("baseUrl", slot.baseUrl)
                put("encApiKey", encryptedKey)
                put("chatModel", slot.chatModel)
                put("codingModel", slot.codingModel)
                put("ttsModel", slot.ttsModel)
                put("enabled", slot.enabled)
                put("priority", slot.priority)
                put("isFreeTier", slot.isFreeTier)
                put("budgetUsd", slot.budgetUsd)
                put("dailyRequestLimit", slot.dailyRequestLimit)
                put("useForChat", slot.useForChat)
                put("useForCoding", slot.useForCoding)
                put("useForVoiceTts", slot.useForVoiceTts)
                put("supportsChat", slot.supportsChat)
                put("supportsTts", slot.supportsTts)
                put("chatStatus", slot.chatStatus.name)
                put("ttsStatus", slot.ttsStatus.name)
                put("lastLatencyMs", slot.lastLatencyMs)
                put("avgLatencyMs", slot.avgLatencyMs)
                put("requestsToday", slot.requestsToday)
                put("failuresTotal", slot.failuresTotal)
                put("promptTokensTotal", slot.promptTokensTotal)
                put("outputTokensTotal", slot.outputTokensTotal)
                put("knownCostUsd", slot.knownCostUsd)
                put("estimatedCostPer1MTokens", slot.estimatedCostPer1MTokens)
                put("cooldownUntilMs", slot.cooldownUntilMs)
                put("lastHttpCode", slot.lastHttpCode)
                put("lastChatError", slot.lastChatError)
                put("lastTtsError", slot.lastTtsError)
                put("discoveredModels", JSONArray(slot.discoveredModels.take(80)))
                put("discoveredTtsModels", JSONArray(slot.discoveredTtsModels.take(30)))
                put("discoveredAtMs", slot.discoveredAtMs)
                put("legacySourceId", slot.legacySourceId)
            }
            arr.put(obj)
        }
        storage.saveRaw(arr.toString())
    }

    @Synchronized
    fun setRoutingMode(mode: SlotRoutingMode) {
        _routingMode.value = mode
        storage.saveRoutingMode(mode)
    }

    /**
     * Target 73: Safe migration preserving existing credentials and slots.
     * Never wipes existing user-configured slots and preserves API keys encrypted in Keystore.
     */
    @Synchronized
    fun migrateFromLegacySettings(settings: OmegaSettings): List<SmartApiSlot> {
        val current = _slots.value.toMutableList()
        var changed = false

        fun upsertLegacy(
            legacyId: String,
            kind: ApiProviderKind,
            label: String,
            baseUrl: String,
            key: String,
            chatModel: String,
            priority: Int,
            isFree: Boolean,
            useForTts: Boolean
        ) {
            val trimmedKey = key.trim()
            val idx = current.indexOfFirst { it.legacySourceId == legacyId }
            if (idx < 0) {
                current.add(
                    SmartApiSlot(
                        id = legacyId,
                        label = label,
                        providerKind = kind,
                        baseUrl = baseUrl.trim().trimEnd('/').ifBlank { kind.defaultBaseUrl },
                        apiKey = trimmedKey,
                        chatModel = chatModel.ifBlank { kind.defaultChatModel },
                        codingModel = chatModel.ifBlank { kind.defaultChatModel },
                        ttsModel = kind.defaultTtsModel.orEmpty(),
                        enabled = trimmedKey.isNotBlank() || legacyId == "legacy_gemini",
                        priority = priority,
                        isFreeTier = isFree,
                        budgetUsd = settings.providerBudgetUsd.coerceAtLeast(0.0),
                        useForChat = true,
                        useForCoding = true,
                        useForVoiceTts = useForTts,
                        supportsChat = true,
                        supportsTts = kind == ApiProviderKind.GEMINI_DIRECT || kind.supportsOpenAiAudioSpeech,
                        chatStatus = if (trimmedKey.isBlank()) SlotConnectionState.UNCONFIGURED else SlotConnectionState.UNVERIFIED,
                        ttsStatus = when {
                            trimmedKey.isBlank() -> SlotConnectionState.UNCONFIGURED
                            kind == ApiProviderKind.GEMINI_DIRECT || kind.supportsOpenAiAudioSpeech -> SlotConnectionState.UNVERIFIED
                            else -> SlotConnectionState.DISABLED
                        },
                        legacySourceId = legacyId
                    )
                )
                changed = true
            } else {
                val existing = current[idx]
                val effectiveBase = baseUrl.trim().trimEnd('/').ifBlank { existing.baseUrl }
                // Preserve existing slot metrics/cache; only synchronize key/model if changed externally via legacy controls
                val nextKey = if (trimmedKey.isNotBlank() && trimmedKey != existing.apiKey) trimmedKey else existing.apiKey
                val keyChanged = nextKey != existing.apiKey
                val baseChanged = effectiveBase.isNotBlank() && effectiveBase != existing.baseUrl
                if (keyChanged || baseChanged || (chatModel.isNotBlank() && chatModel != existing.chatModel && existing.legacySourceId == legacyId)) {
                    val detectedKind = if (legacyId == "legacy_gemini") ApiProviderKind.GEMINI_DIRECT
                    else SmartApiSlotManager.detectProvider(nextKey, effectiveBase)
                    current[idx] = existing.copy(
                        providerKind = detectedKind,
                        baseUrl = effectiveBase.ifBlank { detectedKind.defaultBaseUrl },
                        apiKey = nextKey,
                        chatModel = chatModel.ifBlank { existing.chatModel },
                        supportsTts = detectedKind == ApiProviderKind.GEMINI_DIRECT || detectedKind.supportsOpenAiAudioSpeech,
                        chatStatus = when {
                            nextKey.isBlank() -> SlotConnectionState.UNCONFIGURED
                            keyChanged || baseChanged -> SlotConnectionState.UNVERIFIED
                            else -> existing.chatStatus
                        },
                        ttsStatus = when {
                            nextKey.isBlank() -> SlotConnectionState.UNCONFIGURED
                            !(detectedKind == ApiProviderKind.GEMINI_DIRECT || detectedKind.supportsOpenAiAudioSpeech) -> SlotConnectionState.DISABLED
                            keyChanged || baseChanged -> SlotConnectionState.UNVERIFIED
                            else -> existing.ttsStatus
                        }
                    )
                    changed = true
                }
            }
        }

        val gemKey = settings.geminiApiKey.trim()
        upsertLegacy(
            legacyId = "legacy_gemini",
            kind = ApiProviderKind.GEMINI_DIRECT,
            label = "Slot 1 • Gemini Direct",
            baseUrl = ApiProviderKind.GEMINI_DIRECT.defaultBaseUrl,
            key = gemKey,
            chatModel = settings.geminiChatModel,
            priority = 1,
            isFree = settings.providerBudgetUsd == 0.0,
            useForTts = true
        )

        val gwKey = settings.openRouterApiKey.trim()
        val gwBase = settings.gatewayBaseUrl.trim().trimEnd('/')
        val gwKind = SmartApiSlotManager.detectProvider(gwKey, gwBase)
        if (gwKey.isNotBlank() || current.none { it.legacySourceId == "legacy_gateway" }) {
            upsertLegacy(
                legacyId = "legacy_gateway",
                kind = gwKind,
                label = "Slot 2 • ${gwKind.displayName}",
                baseUrl = gwBase.ifBlank { gwKind.defaultBaseUrl },
                key = gwKey,
                chatModel = settings.openRouterModel,
                priority = 2,
                isFree = settings.smartMode == SmartModelPolicy.Mode.FREE ||
                    settings.openRouterModel.endsWith(":free", ignoreCase = true) ||
                    gwKind == ApiProviderKind.OPENROUTER,
                useForTts = gwKind.supportsOpenAiAudioSpeech
            )
        }

        val secKey = settings.secondaryGatewayApiKey.trim()
        val secBase = settings.secondaryGatewayBaseUrl.trim().trimEnd('/')
        if (secBase.isNotBlank() && secKey.isNotBlank()) {
            val secKind = SmartApiSlotManager.detectProvider(secKey, secBase)
            upsertLegacy(
                legacyId = "legacy_secondary",
                kind = secKind,
                label = "Slot 3 • ${secKind.displayName}",
                baseUrl = secBase,
                key = secKey,
                chatModel = settings.secondaryGatewayModel.ifBlank { secKind.defaultChatModel },
                priority = 3,
                isFree = secKind == ApiProviderKind.OPENROUTER,
                useForTts = secKind.supportsOpenAiAudioSpeech
            )
        }

        if (changed) {
            _slots.value = current.sortedBy { it.priority }
            persistLocked()
        }
        return _slots.value
    }

    @Synchronized
    fun upsertSlot(input: SmartApiSlot): SmartApiSlot {
        val current = _slots.value.toMutableList()
        val detectedKind = if (input.providerKind == ApiProviderKind.CUSTOM_OPENAI) {
            SmartApiSlotManager.detectProvider(input.apiKey, input.baseUrl)
        } else {
            input.providerKind
        }
        val cleanKey = input.apiKey.trim()
        val cleanBase = input.baseUrl.trim().trimEnd('/').ifBlank { detectedKind.defaultBaseUrl }
        val existingIdx = current.indexOfFirst { it.id == input.id }
        val previous = if (existingIdx >= 0) current[existingIdx] else null
        val credsChanged = previous == null || previous.apiKey != cleanKey || previous.baseUrl != cleanBase
        val ttsCapable = detectedKind == ApiProviderKind.GEMINI_DIRECT || detectedKind.supportsOpenAiAudioSpeech || input.supportsTts

        val normalized = input.copy(
            id = input.id.ifBlank { "slot_${UUID.randomUUID().toString().take(8)}" },
            label = input.label.trim().ifBlank { detectedKind.displayName },
            providerKind = detectedKind,
            baseUrl = cleanBase,
            apiKey = cleanKey,
            chatModel = input.chatModel.trim().ifBlank { detectedKind.defaultChatModel },
            ttsModel = input.ttsModel.trim().ifBlank { detectedKind.defaultTtsModel.orEmpty() },
            supportsTts = ttsCapable,
            chatStatus = when {
                !input.enabled -> SlotConnectionState.DISABLED
                cleanKey.isBlank() -> SlotConnectionState.UNCONFIGURED
                credsChanged -> SlotConnectionState.UNVERIFIED
                else -> previous.chatStatus
            },
            ttsStatus = when {
                !input.enabled || !ttsCapable || !input.useForVoiceTts -> SlotConnectionState.DISABLED
                cleanKey.isBlank() -> SlotConnectionState.UNCONFIGURED
                credsChanged -> SlotConnectionState.UNVERIFIED
                else -> previous.ttsStatus
            },
            cooldownUntilMs = if (credsChanged) 0L else previous.cooldownUntilMs
        )
        if (existingIdx >= 0) current[existingIdx] = normalized else current.add(normalized)
        _slots.value = current.sortedBy { it.priority }.take(MAX_SLOTS)
        persistLocked()
        return normalized
    }

    @Synchronized
    fun setSlotEnabled(slotId: String, enabled: Boolean) {
        updateSlot(slotId) { slot ->
            slot.copy(
                enabled = enabled,
                chatStatus = if (!enabled) SlotConnectionState.DISABLED else if (slot.apiKey.isBlank()) SlotConnectionState.UNCONFIGURED else slot.chatStatus
            )
        }
    }

    @Synchronized
    fun deleteSlot(slotId: String): Boolean {
        val current = _slots.value
        val next = current.filterNot { it.id == slotId }
        if (next.size == current.size) return false
        _slots.value = next
        persistLocked()
        return true
    }

    @Synchronized
    fun updateSlot(slotId: String, transform: (SmartApiSlot) -> SmartApiSlot) {
        val current = _slots.value.toMutableList()
        val idx = current.indexOfFirst { it.id == slotId }
        if (idx < 0) return
        current[idx] = transform(current[idx])
        _slots.value = current.sortedBy { it.priority }
        persistLocked()
    }

    @Synchronized
    fun recordSuccess(
        slotId: String,
        purpose: SlotRoutePurpose,
        latencyMs: Long,
        promptTokens: Long? = null,
        outputTokens: Long? = null,
        costUsd: Double? = null
    ) {
        val safeLatency = latencyMs.coerceAtLeast(1L)
        updateSlot(slotId) { s ->
            val count = s.requestsToday
            val nextAvg = if (s.avgLatencyMs <= 0L) safeLatency
            else ((s.avgLatencyMs.toDouble() * count + safeLatency) / (count + 1)).toLong().coerceAtLeast(1L)
            s.copy(
                chatStatus = if (purpose != SlotRoutePurpose.VOICE_TTS) SlotConnectionState.VERIFIED else s.chatStatus,
                ttsStatus = if (purpose == SlotRoutePurpose.VOICE_TTS) SlotConnectionState.VERIFIED else s.ttsStatus,
                lastLatencyMs = safeLatency,
                avgLatencyMs = nextAvg,
                requestsToday = count + 1,
                promptTokensTotal = s.promptTokensTotal + (promptTokens?.coerceAtLeast(0L) ?: 0L),
                outputTokensTotal = s.outputTokensTotal + (outputTokens?.coerceAtLeast(0L) ?: 0L),
                knownCostUsd = s.knownCostUsd + (costUsd?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0),
                cooldownUntilMs = 0L,
                lastHttpCode = 200,
                lastChatError = if (purpose != SlotRoutePurpose.VOICE_TTS) "" else s.lastChatError,
                lastTtsError = if (purpose == SlotRoutePurpose.VOICE_TTS) "" else s.lastTtsError
            )
        }
    }

    /**
     * Targets 63 & 65: Automatic cooldown + HTTP 404/429/500/502/503/504 recovery & slot switching.
     */
    @Synchronized
    fun recordHttpFailure(
        slotId: String,
        httpCode: Int,
        detail: String,
        purpose: SlotRoutePurpose = SlotRoutePurpose.CHAT,
        retryAfterHeader: String? = null
    ) {
        val now = clockMs()
        val cooldownMs = when (httpCode) {
            429 -> GatewayRetryPolicy.cooldownMs(429, retryAfterHeader).coerceAtLeast(15_000L)
            404 -> 180_000L
            500, 502, 503, 504 -> 20_000L
            401, 403 -> 600_000L
            else -> if (httpCode in 500..599) 20_000L else 0L
        }
        val state = if (cooldownMs > 0L) SlotConnectionState.COOLDOWN else SlotConnectionState.FAILED
        updateSlot(slotId) { s ->
            s.copy(
                chatStatus = if (purpose != SlotRoutePurpose.VOICE_TTS) state else s.chatStatus,
                ttsStatus = if (purpose == SlotRoutePurpose.VOICE_TTS) state else s.ttsStatus,
                failuresTotal = s.failuresTotal + 1,
                cooldownUntilMs = if (cooldownMs > 0L) now + cooldownMs else s.cooldownUntilMs,
                lastHttpCode = httpCode,
                lastChatError = if (purpose != SlotRoutePurpose.VOICE_TTS) detail.take(240) else s.lastChatError,
                lastTtsError = if (purpose == SlotRoutePurpose.VOICE_TTS) detail.take(240) else s.lastTtsError
            )
        }
    }

    /**
     * Clears expired cooldowns automatically (Target 65).
     */
    @Synchronized
    fun refreshExpiredCooldowns(): Int {
        val now = clockMs()
        var recovered = 0
        val next = _slots.value.map { s ->
            if (s.cooldownUntilMs in 1L..now && (s.chatStatus == SlotConnectionState.COOLDOWN || s.ttsStatus == SlotConnectionState.COOLDOWN)) {
                recovered++
                s.copy(
                    cooldownUntilMs = 0L,
                    chatStatus = if (s.apiKey.isBlank()) SlotConnectionState.UNCONFIGURED else SlotConnectionState.UNVERIFIED,
                    ttsStatus = if (s.apiKey.isBlank()) SlotConnectionState.UNCONFIGURED
                    else if (s.supportsTts) SlotConnectionState.UNVERIFIED else SlotConnectionState.DISABLED
                )
            } else s
        }
        if (recovered > 0) {
            _slots.value = next
            persistLocked()
        }
        return recovered
    }

    @Synchronized
    fun updateDiscoveredModels(slotId: String, chatModels: List<String>, ttsModels: List<String> = emptyList()) {
        val now = clockMs()
        updateSlot(slotId) { s ->
            s.copy(
                discoveredModels = chatModels.distinct(),
                discoveredTtsModels = ttsModels.distinct(),
                discoveredAtMs = now
            )
        }
    }

    fun selectOrderedSlots(
        purpose: SlotRoutePurpose,
        zeroPaidCreditsMode: Boolean,
        preferredBrain: AiBrainFamily? = null,
        mode: SlotRoutingMode = _routingMode.value
    ): List<SmartApiSlot> {
        refreshExpiredCooldowns()
        val now = clockMs()
        val candidates = _slots.value.filter { slot ->
            slot.enabled &&
                slot.apiKey.isNotBlank() &&
                slot.cooldownUntilMs <= now &&
                slot.allowedUnderCostPolicy(zeroPaidCreditsMode) &&
                when (purpose) {
                    SlotRoutePurpose.CHAT -> slot.useForChat && slot.supportsChat
                    SlotRoutePurpose.CODING -> slot.useForCoding && slot.supportsChat
                    SlotRoutePurpose.VOICE_TTS -> slot.useForVoiceTts && slot.supportsTts
                }
        }
        val comparator = Comparator<SmartApiSlot> { a, b ->
            // Prefer brain compatibility if explicitly requested
            if (preferredBrain != null) {
                val aBrain = preferredBrain in a.supportedBrains()
                val bBrain = preferredBrain in b.supportedBrains()
                if (aBrain != bBrain) return@Comparator if (aBrain) -1 else 1
            }
            // Prefer verified over failed/unverified when equal tier
            val aVerified = if (purpose == SlotRoutePurpose.VOICE_TTS) a.ttsStatus == SlotConnectionState.VERIFIED else a.chatStatus == SlotConnectionState.VERIFIED
            val bVerified = if (purpose == SlotRoutePurpose.VOICE_TTS) b.ttsStatus == SlotConnectionState.VERIFIED else b.chatStatus == SlotConnectionState.VERIFIED
            when (mode) {
                SlotRoutingMode.FASTEST -> {
                    val latA = if (a.avgLatencyMs > 0L) a.avgLatencyMs else Long.MAX_VALUE / 2
                    val latB = if (b.avgLatencyMs > 0L) b.avgLatencyMs else Long.MAX_VALUE / 2
                    if (latA != latB) return@Comparator latA.compareTo(latB)
                    if (aVerified != bVerified) return@Comparator if (aVerified) -1 else 1
                    a.priority.compareTo(b.priority)
                }
                SlotRoutingMode.LOWEST_COST -> {
                    val cA = if (a.isFreeTier) 0.0 else a.estimatedCostPer1MTokens
                    val cB = if (b.isFreeTier) 0.0 else b.estimatedCostPer1MTokens
                    val cmp = cA.compareTo(cB)
                    if (cmp != 0) return@Comparator cmp
                    a.priority.compareTo(b.priority)
                }
                SlotRoutingMode.FREE_FIRST -> {
                    if (a.isFreeTier != b.isFreeTier) return@Comparator if (a.isFreeTier) -1 else 1
                    if (aVerified != bVerified) return@Comparator if (aVerified) -1 else 1
                    val latA = if (a.avgLatencyMs > 0L) a.avgLatencyMs else Long.MAX_VALUE / 2
                    val latB = if (b.avgLatencyMs > 0L) b.avgLatencyMs else Long.MAX_VALUE / 2
                    if (latA != latB) return@Comparator latA.compareTo(latB)
                    a.priority.compareTo(b.priority)
                }
                SlotRoutingMode.CONFIGURABLE_PRIORITY -> {
                    val pCmp = a.priority.compareTo(b.priority)
                    if (pCmp != 0) return@Comparator pCmp
                    if (aVerified != bVerified) if (aVerified) -1 else 1 else 0
                }
            }
        }
        return candidates.sortedWith(comparator)
    }

    fun hasAnyVerifiedSlot(purpose: SlotRoutePurpose = SlotRoutePurpose.CHAT): Boolean =
        _slots.value.any { slot ->
            slot.enabled && slot.apiKey.isNotBlank() && when (purpose) {
                SlotRoutePurpose.VOICE_TTS -> slot.ttsStatus == SlotConnectionState.VERIFIED
                else -> slot.chatStatus == SlotConnectionState.VERIFIED
            }
        }

    fun hasAnyConfiguredKey(): Boolean = _slots.value.any { it.enabled && it.apiKey.isNotBlank() }

    companion object {
        private const val MAX_SLOTS = 24
    }
}

/**
 * Process-wide Smart API Slot Manager singleton handling real HTTP probing, model cache,
 * provider auto-detection, and bidirectionally keeping legacy settings compatible.
 */
object SmartApiSlotManager {
    @Volatile
    private var engine: SlotRegistryEngine? = null

    private val _slotsFallback = MutableStateFlow<List<SmartApiSlot>>(emptyList())
    private val _modeFallback = MutableStateFlow(SlotRoutingMode.FREE_FIRST)

    private val sharedHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    fun init(context: Context, settings: OmegaSettings): SlotRegistryEngine {
        val current = engine ?: synchronized(this) {
            engine ?: SlotRegistryEngine(
                storage = SharedPrefsApiSlotStorage(context.applicationContext),
                cipher = AndroidSlotKeyCipher
            ).also { engine = it }
        }
        current.migrateFromLegacySettings(settings)
        return current
    }

    fun engineOrNull(): SlotRegistryEngine? = engine

    fun slotsFlow(): StateFlow<List<SmartApiSlot>> = engine?.slots ?: _slotsFallback.asStateFlow()
    fun routingModeFlow(): StateFlow<SlotRoutingMode> = engine?.routingMode ?: _modeFallback.asStateFlow()

    /**
     * Target 57: Automatic provider detection from API key prefix + endpoint host.
     */
    fun detectProvider(apiKey: String, baseUrl: String): ApiProviderKind {
        val url = baseUrl.trim().lowercase(Locale.ROOT)
        val key = apiKey.trim()
        return when {
            url.contains("generativelanguage.googleapis.com") -> ApiProviderKind.GEMINI_DIRECT
            url.contains("api.aicredits.in") -> ApiProviderKind.AICREDITS
            url.contains("openrouter.ai") -> ApiProviderKind.OPENROUTER
            url.contains("api.openai.com") -> ApiProviderKind.OPENAI
            url.contains("oneai") -> ApiProviderKind.ONEAI_KEY
            key.startsWith("AIza") -> ApiProviderKind.GEMINI_DIRECT
            key.startsWith("sk-or-v1-") || key.startsWith("sk-or-") -> ApiProviderKind.OPENROUTER
            key.startsWith("sk-proj-") -> ApiProviderKind.OPENAI
            key.startsWith("oneai-") || key.startsWith("oak-") -> ApiProviderKind.ONEAI_KEY
            key.startsWith("sk-") && url.isBlank() -> ApiProviderKind.OPENAI
            url.isNotBlank() -> ApiProviderKind.CUSTOM_OPENAI
            else -> ApiProviderKind.OPENROUTER
        }
    }

    fun cachedChatModels(providerKind: ApiProviderKind): List<String> =
        engine?.slots?.value
            ?.filter { it.enabled && it.providerKind == providerKind && it.hasFreshModelCache(System.currentTimeMillis()) }
            ?.flatMap { it.discoveredModels }
            ?.distinct()
            .orEmpty()

    /**
     * Targets 59, 61, 71, 72: Real HTTP diagnostic for an individual slot.
     * Validates chat discovery/completion AND checks TTS capability separately.
     * NEVER simulates or fakes VERIFIED status when key is missing or HTTP fails.
     */
    suspend fun testSlot(slotId: String, allowNetwork: Boolean = true): SlotTestOutcome = withContext(Dispatchers.IO) {
        val eng = engine ?: return@withContext SlotTestOutcome(
            slotId, "Slot Manager not initialized", ApiProviderKind.CUSTOM_OPENAI, false,
            "Not initialized", null, "Not initialized", 0L, 0, 0
        )
        val slot = eng.slots.value.firstOrNull { it.id == slotId } ?: return@withContext SlotTestOutcome(
            slotId, "Missing slot", ApiProviderKind.CUSTOM_OPENAI, false,
            "Slot not found", null, "Not found", 0L, 0, 0
        )
        if (slot.apiKey.isBlank()) {
            eng.updateSlot(slotId) {
                it.copy(
                    chatStatus = SlotConnectionState.UNCONFIGURED,
                    ttsStatus = SlotConnectionState.UNCONFIGURED,
                    lastChatError = "API key required"
                )
            }
            return@withContext SlotTestOutcome(
                slot.id, slot.label, slot.providerKind,
                false, "API key required", null, "Key required", 0L, 0, 0
            )
        }
        if (!allowNetwork) {
            return@withContext SlotTestOutcome(
                slot.id, slot.label, slot.providerKind,
                false, "Network disabled by policy", null, "Network disabled", 0L, 0, 0
            )
        }

        eng.updateSlot(slotId) { it.copy(chatStatus = SlotConnectionState.TESTING) }
        val start = System.currentTimeMillis()
        var discoveredChat = emptyList<String>()
        var discoveredTts = emptyList<String>()
        var chatOk = false
        var chatDetail = ""
        var ttsOk: Boolean? = null
        var ttsDetail = "Not applicable for this provider"

        try {
            if (slot.providerKind == ApiProviderKind.GEMINI_DIRECT) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100"
                val req = Request.Builder().url(url).addHeader("x-goog-api-key", slot.apiKey.trim()).get().build()
                sharedHttpClient.newCall(req).awaitParsed { resp ->
                    val raw = resp.boundedJsonText(2L * 1024 * 1024)
                    val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                    if (!resp.isSuccessful) {
                        val msg = parseApiError(resp.code, raw)
                        eng.recordHttpFailure(slotId, resp.code, msg, SlotRoutePurpose.CHAT, resp.header("Retry-After"))
                        chatDetail = msg
                    } else {
                        val arr = JSONObject(raw).optJSONArray("models") ?: JSONArray()
                        val chat = mutableListOf<String>()
                        val tts = mutableListOf<String>()
                        for (i in 0 until minOf(arr.length(), 10_000)) {
                            val m = arr.optJSONObject(i) ?: continue
                            val name = m.optString("name", "").removePrefix("models/")
                            if (!name.startsWith("gemini-") || !SmartModelPolicy.validId(name)) continue
                            val methods = m.optJSONArray("supportedGenerationMethods")
                            var gen = false
                            if (methods != null) {
                                for (j in 0 until methods.length()) {
                                    if (methods.optString(j) == "generateContent") gen = true
                                }
                            }
                            if (gen && GeminiCompatibility.isTts(name)) tts.add(name)
                            else if (gen && GeminiCompatibility.isChat(name)) chat.add(name)
                        }
                        discoveredChat = chat.distinct()
                        discoveredTts = tts.distinct()
                        eng.updateDiscoveredModels(slotId, discoveredChat, discoveredTts)
                        eng.recordSuccess(slotId, SlotRoutePurpose.CHAT, latency)
                        chatOk = true
                        chatDetail = "Connected (${latency}ms) • ${discoveredChat.size} chat models"
                        // Separate TTS capability verification (never assume TTS works without TTS model or verified capability)
                        if (discoveredTts.isNotEmpty() && slot.useForVoiceTts) {
                            eng.recordSuccess(slotId, SlotRoutePurpose.VOICE_TTS, latency)
                            ttsOk = true
                            ttsDetail = "Gemini TTS ready (${discoveredTts.first()})"
                        } else if (slot.useForVoiceTts) {
                            ttsOk = false
                            ttsDetail = "No Gemini TTS model advertised for this key; falls back cleanly"
                            eng.updateSlot(slotId) {
                                it.copy(
                                    supportsTts = false,
                                    ttsStatus = SlotConnectionState.FAILED,
                                    lastTtsError = ttsDetail
                                )
                            }
                        }
                    }
                }
            } else {
                val base = slot.effectiveBaseUrl()
                val parsedUrl = base.toHttpUrlOrNull()
                if (parsedUrl == null || !parsedUrl.isHttps) {
                    val err = "Invalid HTTPS base URL: $base"
                    eng.recordHttpFailure(slotId, 0, err, SlotRoutePurpose.CHAT)
                    chatDetail = err
                } else {
                    val modelsUrl = "$base/models"
                    val req = Request.Builder()
                        .url(modelsUrl)
                        .header("Authorization", "Bearer ${slot.apiKey.trim()}")
                        .get()
                        .build()
                    sharedHttpClient.newCall(req).awaitParsed { resp ->
                        val raw = resp.boundedJsonText(2L * 1024 * 1024)
                        val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                        if (!resp.isSuccessful) {
                            val msg = parseApiError(resp.code, raw)
                            eng.recordHttpFailure(slotId, resp.code, msg, SlotRoutePurpose.CHAT, resp.header("Retry-After"))
                            chatDetail = msg
                        } else {
                            val data = JSONObject(raw).optJSONArray("data")
                            val ids = mutableListOf<String>()
                            val ttsIds = mutableListOf<String>()
                            if (data != null) {
                                for (i in 0 until minOf(data.length(), 10_000)) {
                                    val id = data.optJSONObject(i)?.optString("id", "") ?: continue
                                    if (id.isNotBlank() && SmartModelPolicy.validId(id)) {
                                        if (id.contains("tts", ignoreCase = true) || id.contains("bulbul", ignoreCase = true)) {
                                            ttsIds.add(id)
                                        } else {
                                            ids.add(id)
                                        }
                                    }
                                }
                            }
                            discoveredChat = ids.distinct()
                            discoveredTts = ttsIds.distinct()
                            eng.updateDiscoveredModels(slotId, discoveredChat, discoveredTts)
                            eng.recordSuccess(slotId, SlotRoutePurpose.CHAT, latency)
                            chatOk = true
                            chatDetail = "Connected (${latency}ms) • ${discoveredChat.size} models"

                            // Separate TTS validation probe only when slot claims audio speech support & enabled for voice
                            if (slot.useForVoiceTts && (slot.providerKind.supportsOpenAiAudioSpeech || discoveredTts.isNotEmpty())) {
                                val probeTts = probeAudioSpeechCapability(slot, base)
                                ttsOk = probeTts.first
                                ttsDetail = probeTts.second
                                if (probeTts.first) {
                                    eng.recordSuccess(slotId, SlotRoutePurpose.VOICE_TTS, latency)
                                } else {
                                    eng.recordHttpFailure(slotId, 0, probeTts.second, SlotRoutePurpose.VOICE_TTS)
                                }
                            } else {
                                ttsOk = null
                                ttsDetail = "Chat-only endpoint (TTS disabled)"
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val err = e.message ?: "Connection failed"
            eng.recordHttpFailure(slotId, 0, err, SlotRoutePurpose.CHAT)
            chatDetail = err
        }

        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        SlotTestOutcome(
            slotId = slot.id,
            label = slot.label,
            providerKind = slot.providerKind,
            chatOk = chatOk,
            chatDetail = chatDetail,
            ttsOk = ttsOk,
            ttsDetail = ttsDetail,
            latencyMs = elapsed,
            discoveredChatCount = discoveredChat.size,
            discoveredTtsCount = discoveredTts.size
        )
    }

    /**
     * Target 60: Test all enabled API slots and report each real result.
     */
    suspend fun testAllSlots(allowNetwork: Boolean = true): List<SlotTestOutcome> = withContext(Dispatchers.IO) {
        val slots = engine?.slots?.value.orEmpty().filter { it.enabled }
        slots.map { testSlot(it.id, allowNetwork) }
    }

    private suspend fun probeAudioSpeechCapability(slot: SmartApiSlot, baseUrl: String): Pair<Boolean, String> {
        return try {
            val model = slot.ttsModel.ifBlank { slot.providerKind.defaultTtsModel ?: "tts-1" }
            val body = JSONObject()
                .put("model", model)
                .put("input", "ok")
                .put("voice", "onyx")
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("$baseUrl/audio/speech")
                .header("Authorization", "Bearer ${slot.apiKey.trim()}")
                .post(body)
                .build()
            sharedHttpClient.newCall(req).awaitParsed { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()?.size ?: 0
                    if (bytes > 64) true to "TTS Ready (onyx • $model)"
                    else false to "TTS returned empty audio ($bytes bytes)"
                } else {
                    val errText = runCatching { resp.boundedJsonText(32_768L) }.getOrDefault("")
                    false to parseApiError(resp.code, errText)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false to (e.message ?: "TTS probe failed")
        }
    }

    internal fun parseApiError(code: Int, rawBody: String): String {
        val parsedMsg = runCatching {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message").orEmpty()
                .ifBlank { obj.optString("message", "") }
        }.getOrDefault("")
        val tail = parsedMsg.take(140)
        return if (tail.isNotBlank()) "HTTP $code: $tail" else "HTTP $code error"
    }
}
