package com.example.data

import android.content.Context
import com.example.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SlotProvider(val label: String, val defaultBaseUrl: String, val defaultModel: String, val defaultPaid: Boolean) {
    GEMINI("Google Gemini Direct", "https://generativelanguage.googleapis.com/v1beta", "gemini-3.5-flash-lite", false),
    OPENAI("OpenAI GPT", "https://api.openai.com/v1", "gpt-6.2-sol", true),
    SARVAM("Sarvam AI Voice", "https://api.sarvam.ai", "bulbul:v3", true);

    companion object {
        fun fromStored(raw: String?): SlotProvider =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: GEMINI
    }
}

enum class SlotStatus(val label: String) {
    UNVERIFIED("Not Verified"),
    TESTING("Testing..."),
    VERIFIED("Verified"),
    INVALID_AUTH("Invalid Key (401/403)"),
    RATE_LIMITED("Rate Limited (429)"),
    UNREACHABLE("Connection Error");

    companion object {
        fun fromStored(raw: String?): SlotStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNVERIFIED
    }
}

data class ApiSlot(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val provider: SlotProvider,
    val baseUrl: String = provider.defaultBaseUrl,
    val apiKey: String = "",
    val preferredModel: String = provider.defaultModel,
    val enabled: Boolean = true,
    val isPaidTier: Boolean = provider.defaultPaid,
    val priority: Int = 10,
    val status: SlotStatus = SlotStatus.UNVERIFIED,
    val lastHttpCode: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastError: String = "",
    val cooldownUntilMs: Long = 0L
) {
    fun maskedKey(): String {
        val k = apiKey.trim()
        return when {
            k.isEmpty() -> "No Key"
            k.length <= 8 -> "****"
            else -> "${k.take(4)}...${k.takeLast(4)}"
        }
    }

    fun isCoolingDown(nowMs: Long = System.currentTimeMillis()): Boolean = cooldownUntilMs > nowMs
}

/**
 * Pure slot ordering & HTTP recovery policy (testable without Android Context).
 * Never rotates credentials on 429 to bypass provider quotas, and never activates
 * paid slots without explicit user authorization.
 */
object ApiSlotSelectionPolicy {
    fun selectChatSlots(
        slots: List<ApiSlot>,
        preferredProvider: AiProvider,
        freePriority: Boolean,
        allowPaidModels: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): List<ApiSlot> {
        val eligible = slots.filter { slot ->
            slot.enabled &&
                slot.apiKey.isNotBlank() &&
                (slot.provider == SlotProvider.GEMINI || slot.provider == SlotProvider.OPENAI) &&
                slot.status != SlotStatus.INVALID_AUTH &&
                !slot.isCoolingDown(nowMs) &&
                (!slot.isPaidTier || allowPaidModels)
        }
        return eligible.sortedWith(
            compareBy<ApiSlot> { slot ->
                if (freePriority && slot.isPaidTier) 1 else 0
            }.thenBy { slot ->
                val matchesActive = (preferredProvider == AiProvider.GEMINI && slot.provider == SlotProvider.GEMINI) ||
                    (preferredProvider == AiProvider.OPENAI && slot.provider == SlotProvider.OPENAI)
                if (matchesActive) 0 else 1
            }.thenBy { it.priority }
                .thenBy { if (it.lastLatencyMs > 0L) it.lastLatencyMs else Long.MAX_VALUE }
        )
    }

    fun selectSarvamSlot(
        slots: List<ApiSlot>,
        allowPaidModels: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): ApiSlot? = slots.filter { slot ->
        slot.enabled &&
            slot.apiKey.isNotBlank() &&
            slot.provider == SlotProvider.SARVAM &&
            slot.status != SlotStatus.INVALID_AUTH &&
            !slot.isCoolingDown(nowMs) &&
            (!slot.isPaidTier || allowPaidModels)
    }.minByOrNull { it.priority }

    fun backoffMsForHttp(code: Int, attempt: Int): Long = when (code) {
        429 -> 45_000L * (attempt.coerceIn(1, 3))
        500, 502, 503, 504 -> 1_500L * (1 shl attempt.coerceIn(0, 2))
        else -> 0L
    }
}

class ApiSlotManager(context: Context, private val preferences: OmegaPreferences) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _slots = MutableStateFlow(loadSlots())
    val slots: StateFlow<List<ApiSlot>> = _slots.asStateFlow()

    @Synchronized
    fun reloadFromSettingsIfNeeded() {
        if (_slots.value.isEmpty()) {
            syncPrimarySettingsToSlots()
        }
    }

    @Synchronized
    fun syncPrimarySettingsToSlots() {
        val s = preferences.settings.value
        val cur = _slots.value.toMutableList()
        var changed = false

        fun ensurePrimarySlot(id: String, label: String, prov: SlotProvider, key: String, model: String, url: String) {
            if (key.isBlank()) return
            val idx = cur.indexOfFirst { it.id == id }
            if (idx < 0) {
                cur.add(ApiSlot(id = id, label = label, provider = prov, baseUrl = url, apiKey = key, preferredModel = model, isPaidTier = prov != SlotProvider.GEMINI, priority = 1))
                changed = true
            } else if (cur[idx].apiKey != key || cur[idx].preferredModel != model || cur[idx].baseUrl != url) {
                cur[idx] = cur[idx].copy(apiKey = key, preferredModel = model, baseUrl = url, status = SlotStatus.UNVERIFIED)
                changed = true
            }
        }

        ensurePrimarySlot("primary_gemini", "Primary Gemini", SlotProvider.GEMINI, s.effectiveGeminiKey(), s.geminiChatModel, SlotProvider.GEMINI.defaultBaseUrl)
        ensurePrimarySlot("primary_openai", "Primary OpenAI GPT", SlotProvider.OPENAI, s.effectiveOpenAiKey(), s.openAiModel, s.openAiBaseUrl)
        ensurePrimarySlot("primary_sarvam", "Primary Sarvam Voice", SlotProvider.SARVAM, s.effectiveSarvamKey(), s.sarvamModel, SlotProvider.SARVAM.defaultBaseUrl)

        if (changed) saveSlots(cur)
    }

    @Synchronized
    fun upsertSlot(slot: ApiSlot) {
        val list = _slots.value.toMutableList()
        val idx = list.indexOfFirst { it.id == slot.id }
        val normalized = slot.copy(
            label = slot.label.trim().ifEmpty { slot.provider.label },
            baseUrl = slot.baseUrl.trim().trimEnd('/').ifEmpty { slot.provider.defaultBaseUrl },
            preferredModel = slot.preferredModel.trim().ifEmpty { slot.provider.defaultModel }
        )
        if (idx >= 0) list[idx] = normalized else list.add(normalized)
        saveSlots(list)
    }

    @Synchronized
    fun toggleSlot(id: String, enabled: Boolean) {
        val list = _slots.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        saveSlots(list)
    }

    @Synchronized
    fun deleteSlot(id: String) {
        saveSlots(_slots.value.filterNot { it.id == id })
    }

    @Synchronized
    fun recordOutcome(id: String, httpCode: Int, latencyMs: Long, error: String? = null) {
        val now = System.currentTimeMillis()
        val list = _slots.value.map { slot ->
            if (slot.id != id) slot
            else when {
                httpCode in 200..299 && error == null -> slot.copy(
                    status = SlotStatus.VERIFIED,
                    lastHttpCode = httpCode,
                    lastLatencyMs = latencyMs,
                    lastError = "",
                    cooldownUntilMs = 0L
                )
                httpCode == 401 || httpCode == 403 -> slot.copy(
                    status = SlotStatus.INVALID_AUTH,
                    lastHttpCode = httpCode,
                    lastLatencyMs = latencyMs,
                    lastError = error ?: "Authentication rejected (HTTP $httpCode)",
                    cooldownUntilMs = 0L
                )
                httpCode == 429 -> slot.copy(
                    status = SlotStatus.RATE_LIMITED,
                    lastHttpCode = 429,
                    lastLatencyMs = latencyMs,
                    lastError = error ?: "Provider rate limit reached (HTTP 429)",
                    cooldownUntilMs = now + 45_000L
                )
                else -> slot.copy(
                    status = if (httpCode == 400 || httpCode == 404) SlotStatus.UNVERIFIED else SlotStatus.UNREACHABLE,
                    lastHttpCode = httpCode,
                    lastLatencyMs = latencyMs,
                    lastError = error ?: "HTTP $httpCode"
                )
            }
        }
        saveSlots(list)
    }

    fun getOrderedChatSlots(): List<ApiSlot> {
        syncPrimarySettingsToSlots()
        val s = preferences.settings.value
        val baseList = _slots.value.toMutableList()
        if (baseList.none { it.provider == SlotProvider.GEMINI } && s.effectiveGeminiKey().isNotBlank()) {
            baseList.add(ApiSlot("primary_gemini", "Primary Gemini", SlotProvider.GEMINI, apiKey = s.effectiveGeminiKey(), preferredModel = s.geminiChatModel, isPaidTier = false, priority = 0))
        }
        if (baseList.none { it.provider == SlotProvider.OPENAI } && s.effectiveOpenAiKey().isNotBlank()) {
            baseList.add(ApiSlot("primary_openai", "Primary OpenAI GPT", SlotProvider.OPENAI, baseUrl = s.openAiBaseUrl, apiKey = s.effectiveOpenAiKey(), preferredModel = s.openAiModel, isPaidTier = true, priority = 1))
        }
        return ApiSlotSelectionPolicy.selectChatSlots(
            slots = baseList,
            preferredProvider = s.activeProvider,
            freePriority = s.freeModelPriority,
            allowPaidModels = s.allowPaidModels
        )
    }

    fun getActiveSarvamKeyAndUrl(): Pair<String, String> {
        val s = preferences.settings.value
        if (s.effectiveSarvamKey().isNotBlank()) return s.effectiveSarvamKey() to SlotProvider.SARVAM.defaultBaseUrl
        val slot = ApiSlotSelectionPolicy.selectSarvamSlot(_slots.value, allowPaidModels = true)
        return (slot?.apiKey.orEmpty()) to (slot?.baseUrl ?: SlotProvider.SARVAM.defaultBaseUrl)
    }

    private fun saveSlots(list: List<ApiSlot>) {
        val arr = JSONArray()
        list.forEach { slot ->
            val encKey = if (slot.apiKey.isBlank()) "" else KeystoreCryptoManager.encrypt(slot.apiKey.trim())
            arr.put(
                JSONObject().apply {
                    put("id", slot.id)
                    put("label", slot.label)
                    put("provider", slot.provider.name)
                    put("baseUrl", slot.baseUrl)
                    put("apiKey", encKey)
                    put("preferredModel", slot.preferredModel)
                    put("enabled", slot.enabled)
                    put("isPaidTier", slot.isPaidTier)
                    put("priority", slot.priority)
                    put("status", slot.status.name)
                    put("lastHttpCode", slot.lastHttpCode)
                    put("lastLatencyMs", slot.lastLatencyMs)
                    put("lastError", slot.lastError)
                    put("cooldownUntilMs", slot.cooldownUntilMs)
                }
            )
        }
        prefs.edit().putString(KEY_SLOTS_JSON, arr.toString()).commit()
        _slots.value = list
    }

    private fun loadSlots(): List<ApiSlot> {
        val raw = prefs.getString(KEY_SLOTS_JSON, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ApiSlot(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    label = o.optString("label"),
                    provider = SlotProvider.fromStored(o.optString("provider")),
                    baseUrl = o.optString("baseUrl"),
                    apiKey = KeystoreCryptoManager.decrypt(o.optString("apiKey")),
                    preferredModel = o.optString("preferredModel"),
                    enabled = o.optBoolean("enabled", true),
                    isPaidTier = o.optBoolean("isPaidTier", false),
                    priority = o.optInt("priority", 10),
                    status = SlotStatus.fromStored(o.optString("status")),
                    lastHttpCode = o.optInt("lastHttpCode", 0),
                    lastLatencyMs = o.optLong("lastLatencyMs", 0L),
                    lastError = o.optString("lastError"),
                    cooldownUntilMs = o.optLong("cooldownUntilMs", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "jarvis_v110_api_slots"
        private const val KEY_SLOTS_JSON = "slots_json"
    }
}
