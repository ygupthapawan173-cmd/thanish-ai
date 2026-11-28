package com.example.ai

import com.example.autonomous.ReleaseVoiceAction
import com.example.control.DeviceActionExecutor
import com.example.control.NotificationInbox
import com.example.data.AiProvider
import com.example.data.ApiQuotaTracker
import com.example.data.ApiSlot
import com.example.data.ApiSlotManager
import com.example.data.IntelligenceMode
import com.example.data.OmegaSettings
import com.example.data.SlotProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AgentReplyResult(
    val replyText: String,
    val executedTools: List<String> = emptyList(),
    val sources: List<WebSourceReference> = emptyList(),
    val latencyMs: Long = 0L,
    val usedModel: String = "",
    val providerUsed: String = "",
    val domain: KnowledgeDomain = KnowledgeDomain.GENERAL_KNOWLEDGE,
    val intelligenceMode: IntelligenceMode = IntelligenceMode.SMART,
    val releaseAction: ReleaseVoiceAction? = null
)

data class ConnectionDiagnosticResult(
    val isSuccess: Boolean,
    val providerLabel: String,
    val modelId: String,
    val latencyMs: Long,
    val summary: String,
    val httpCode: Int = 0
)

/**
 * V110 Master AI Repository (Sections 3, 4, 5, 10, 11, 15, 16, 17):
 * - Supports ONLY Google Gemini Direct & OpenAI GPT (plus Sarvam Voice slots)
 * - Uses temporary in-memory live-session context ONLY (no persistent Personal Memory/Chat DB bloat)
 * - Real live Internet Search (Gemini Google Search Grounding, Deep Research, OpenAI Web Search)
 * - Safe website extraction with untrusted-content prompt boundaries
 * - Automatic provider switching with strict Free Priority & Paid Authorization enforcement
 */
class PanduAiRepository(
    private val settingsProvider: () -> OmegaSettings,
    private val slotManager: ApiSlotManager,
    private val actionExecutor: DeviceActionExecutor
) {
    private val localRouter = LocalCommandRouter(actionExecutor)
    private val discoveryService = ModelDiscoveryService()
    private val openAiClient = SmartGatewayClient()
    private val webResearch = WebResearchClient()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(50, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    // Temporary live-session context only (explicitly permitted & cleared on exit per Section 16)
    private val liveSessionHistory = mutableListOf<Pair<String, String>>()

    @Volatile var cachedGeminiModels: List<String> = emptyList()
        private set
    @Volatile var cachedGeminiLiveModels: List<String> = emptyList()
        private set
    @Volatile var cachedOpenAiModels: List<String> = emptyList()
        private set
    @Volatile var lastBrainPlan: BrainTaskPlan? = null
        private set
    @Volatile var lastRouteResult: SmartRouteResult? = null
        private set
    @Volatile var lastActivityState: AiActivityState = AiActivityState.STANDBY
        private set

    fun clearSessionContext() {
        synchronized(liveSessionHistory) { liveSessionHistory.clear() }
    }

    private fun rememberSessionTurn(userText: String, replyText: String) {
        synchronized(liveSessionHistory) {
            liveSessionHistory.add("user" to userText.take(1000))
            liveSessionHistory.add("assistant" to replyText.take(1500))
            while (liveSessionHistory.size > 12) liveSessionHistory.removeAt(0)
        }
    }

    suspend fun discoverAllConfiguredModels(): Result<Pair<DiscoveredModelsResult?, DiscoveredModelsResult?>> =
        withContext(Dispatchers.IO) {
            val s = settingsProvider()
            slotManager.syncPrimarySettingsToSlots()
            val geminiKey = s.effectiveGeminiKey().ifBlank {
                slotManager.slots.value.firstOrNull { it.provider == SlotProvider.GEMINI && it.enabled }?.apiKey.orEmpty()
            }
            val openAiSlot = slotManager.slots.value.firstOrNull { it.provider == SlotProvider.OPENAI && it.enabled && it.apiKey.isNotBlank() }
            val openAiKey = s.effectiveOpenAiKey().ifBlank { openAiSlot?.apiKey.orEmpty() }
            val openAiBase = openAiSlot?.baseUrl ?: s.openAiBaseUrl

            if (geminiKey.isBlank() && openAiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Add at least one Gemini or OpenAI API key to discover models."))
            }

            var gemRes: DiscoveredModelsResult? = null
            var oaiRes: DiscoveredModelsResult? = null
            var firstError: Throwable? = null

            if (geminiKey.isNotBlank()) {
                discoveryService.discoverGeminiModels(geminiKey)
                    .onSuccess {
                        cachedGeminiModels = it.chatModels
                        cachedGeminiLiveModels = it.liveVoiceModels
                        gemRes = it
                    }
                    .onFailure { firstError = it }
            }
            if (openAiKey.isNotBlank()) {
                discoveryService.discoverOpenAiModels(openAiKey, openAiBase)
                    .onSuccess {
                        cachedOpenAiModels = it.chatModels
                        oaiRes = it
                    }
                    .onFailure { if (firstError == null) firstError = it }
            }
            if (gemRes == null && oaiRes == null) {
                Result.failure(firstError ?: IOException("Model discovery failed"))
            } else {
                Result.success(gemRes to oaiRes)
            }
        }

    suspend fun testSlotConnection(slot: ApiSlot): ConnectionDiagnosticResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        if (slot.apiKey.isBlank()) {
            return@withContext ConnectionDiagnosticResult(false, slot.provider.label, slot.preferredModel, 0L, "API key is empty.", 0)
        }
        return@withContext when (slot.provider) {
            SlotProvider.GEMINI -> {
                try {
                    val discovered = discoveryService.discoverGeminiModels(slot.apiKey).getOrNull()
                    if (discovered != null) {
                        cachedGeminiModels = discovered.chatModels
                        cachedGeminiLiveModels = discovered.liveVoiceModels
                    }
                    val chain = GeminiModelSelectionPolicy.buildCandidates(slot.preferredModel, cachedGeminiModels)
                    val payload = JSONObject().put(
                        "contents",
                        JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "Reply OK."))))
                    )
                    val (res, used) = postGeminiWithFallback(slot.apiKey, chain, payload)
                    val text = extractGeminiText(res).ifBlank { throw IOException("No text returned by $used") }
                    val latency = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                    slotManager.recordOutcome(slot.id, 200, latency, null)
                    ConnectionDiagnosticResult(true, slot.provider.label, used, latency, "CONNECTED (${latency}ms) • $used • $text", 200)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    val code = parseHttpCode(e.message)
                    val latency = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                    slotManager.recordOutcome(slot.id, code, latency, e.message)
                    ConnectionDiagnosticResult(false, slot.provider.label, slot.preferredModel, latency, e.message ?: "Gemini test failed", code)
                }
            }
            SlotProvider.OPENAI -> {
                try {
                    val reply = openAiClient.answerOpenAi(
                        baseUrl = slot.baseUrl,
                        apiKey = slot.apiKey,
                        preferredModel = slot.preferredModel,
                        intelligenceMode = IntelligenceMode.FAST,
                        freePriority = false,
                        question = "Reply OK.",
                        system = "Be concise."
                    )
                    val latency = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                    slotManager.recordOutcome(slot.id, 200, latency, null)
                    ConnectionDiagnosticResult(true, slot.provider.label, reply.model, latency, "CONNECTED (${latency}ms) • ${reply.model} • ${reply.text}", 200)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    val code = parseHttpCode(e.message)
                    val latency = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                    slotManager.recordOutcome(slot.id, code, latency, e.message)
                    ConnectionDiagnosticResult(false, slot.provider.label, slot.preferredModel, latency, e.message ?: "OpenAI test failed", code)
                }
            }
            SlotProvider.SARVAM -> {
                try {
                    val res = com.example.voice.SarvamVoiceClient.testConnection(slot.apiKey)
                    val latency = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                    res.fold(
                        onSuccess = { info ->
                            slotManager.recordOutcome(slot.id, 200, latency, null)
                            ConnectionDiagnosticResult(true, slot.provider.label, slot.preferredModel, latency, "CONNECTED (${latency}ms) • $info", 200)
                        },
                        onFailure = { err ->
                            val code = parseHttpCode(err.message)
                            slotManager.recordOutcome(slot.id, code, latency, err.message)
                            ConnectionDiagnosticResult(false, slot.provider.label, slot.preferredModel, latency, err.message ?: "Sarvam connection failed", code)
                        }
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    val latency = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                    slotManager.recordOutcome(slot.id, 0, latency, e.message)
                    ConnectionDiagnosticResult(false, slot.provider.label, slot.preferredModel, latency, e.message ?: "Sarvam test failed", 0)
                }
            }
        }
    }

    suspend fun answerBackgroundQuestion(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        CreatorIdentity.reply(prompt)?.let { return@withContext Result.success(it) }
        val s = settingsProvider()
        val plan = MasterAiBrain.classifyAndPlan(
            userMessage = prompt,
            configuredMode = s.intelligenceMode,
            hasImage = false,
            configuredLanguage = s.languageTag,
            webSearchEnabled = s.webSearchGrounding,
            deepResearchEnabled = false
        )
        val slots = slotManager.getOrderedChatSlots()
        val first = slots.firstOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Configure a Gemini or OpenAI API key first."))

        val bgInstruction = "${plan.workflowInstruction} No mobile control tools or private actions run during background Q&A."
        return@withContext runCatching {
            if (plan.requiresWebSearch && first.provider == SlotProvider.GEMINI) {
                webResearch.searchGeminiGrounded(first.apiKey, s.geminiChatModel, prompt, bgInstruction).getOrThrow().answerText
            } else if (first.provider == SlotProvider.GEMINI) {
                val payload = JSONObject()
                    .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", bgInstruction))))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                val (json, _) = postGeminiWithFallback(first.apiKey, GeminiModelSelectionPolicy.buildCandidates(s.geminiChatModel, cachedGeminiModels), payload)
                extractGeminiText(json).ifBlank { throw IOException("Empty reply") }
            } else {
                openAiClient.answerOpenAi(
                    baseUrl = first.baseUrl,
                    apiKey = first.apiKey,
                    preferredModel = s.openAiModel,
                    intelligenceMode = plan.intelligenceMode,
                    freePriority = s.freeModelPriority,
                    question = prompt,
                    system = bgInstruction
                ).text
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun sendMessage(
        userMessage: String,
        attachedImageBase64: String? = null,
        onActivityChange: (AiActivityState, String) -> Unit = { _, _ -> },
        onPartialReply: (String) -> Unit = {}
    ): Result<AgentReplyResult> = withContext(Dispatchers.IO) {
        val s = settingsProvider()
        val started = System.currentTimeMillis()
        val plan = MasterAiBrain.classifyAndPlan(
            userMessage = userMessage,
            configuredMode = s.intelligenceMode,
            hasImage = !attachedImageBase64.isNullOrBlank(),
            configuredLanguage = s.languageTag,
            webSearchEnabled = s.webSearchGrounding,
            deepResearchEnabled = s.deepResearchEnabled
        )
        lastBrainPlan = plan
        val isDirect = attachedImageBase64.isNullOrBlank() && DirectDeviceCommand.parse(userMessage) != null
        lastRouteResult = SmartAiTaskRouter.route(
            plan = plan,
            activeProvider = s.activeProvider,
            hasGeminiKey = s.effectiveGeminiKey().isNotBlank() || slotManager.getOrderedChatSlots().any { it.provider == SlotProvider.GEMINI },
            hasOpenAiKey = s.effectiveOpenAiKey().isNotBlank() || slotManager.slots.value.any { it.provider == SlotProvider.OPENAI && it.enabled && it.apiKey.isNotBlank() },
            freePriority = s.freeModelPriority,
            allowPaidModels = s.allowPaidModels,
            preferredGeminiModel = s.geminiChatModel,
            preferredOpenAiModel = s.openAiModel,
            isDirectCommand = isDirect
        )

        // 1. Self-upgrade / Release Update voice commands (Section 15)
        if (attachedImageBase64.isNullOrBlank() && plan.releaseAction != null) {
            val reply = when (plan.releaseAction) {
                ReleaseVoiceAction.CHECK_FOR_UPDATES -> "Checking verified release updates in Self-Upgrade Center."
                ReleaseVoiceAction.UPGRADE_SELF -> "Checking and verifying the latest signed release for upgrade."
            }
            return@withContext Result.success(
                AgentReplyResult(
                    replyText = reply,
                    latencyMs = 1L,
                    usedModel = "local-self-upgrade",
                    providerUsed = "JARVIS Self-Upgrade",
                    domain = plan.domain,
                    intelligenceMode = plan.intelligenceMode,
                    releaseAction = plan.releaseAction
                )
            )
        }

        // 2. Developer Identity (Section 17)
        if (attachedImageBase64.isNullOrBlank()) {
            CreatorIdentity.reply(userMessage)?.let { reply ->
                rememberSessionTurn(userMessage, reply)
                return@withContext Result.success(
                    AgentReplyResult(
                        replyText = reply,
                        latencyMs = 1L,
                        usedModel = "local-identity",
                        providerUsed = "JARVIS Core",
                        domain = plan.domain,
                        intelligenceMode = plan.intelligenceMode
                    )
                )
            }
        }

        // 3. Direct Android Device Control fast-path (no AI roundtrip latency)
        if (attachedImageBase64.isNullOrBlank()) {
            localRouter.executeExplicit(userMessage)?.let { hit ->
                coroutineContext.ensureActive()
                onActivityChange(AiActivityState.EXECUTING, "Executed: ${hit.executedTool}")
                rememberSessionTurn(userMessage, hit.replyText)
                return@withContext Result.success(
                    AgentReplyResult(
                        replyText = hit.replyText,
                        executedTools = listOf(hit.executedTool),
                        latencyMs = (System.currentTimeMillis() - started).coerceAtLeast(1L),
                        usedModel = "android-direct",
                        providerUsed = "Android Direct Control",
                        domain = plan.domain,
                        intelligenceMode = plan.intelligenceMode
                    )
                )
            }
        }

        // 4. Cost Protection Guard
        val snap = ApiQuotaTracker.snapshot.value
        val costGuard = CostProtectionPolicy.evaluateRequest(
            totalRequests = snap.totalRequests,
            knownCostUsd = snap.knownCostUsd,
            providerBudgetUsd = 0.0,
            isFreeMode = s.freeModelPriority
        )
        if (!costGuard.allowed) {
            return@withContext Result.failure(IllegalStateException(costGuard.reason ?: "Request quota cap reached"))
        }

        val orderedSlots = slotManager.getOrderedChatSlots()
        if (orderedSlots.isEmpty()) {
            val paidLockedHint = if (!s.allowPaidModels && s.effectiveOpenAiKey().isNotBlank() && s.effectiveGeminiKey().isBlank()) {
                "Paid OpenAI models require explicit Paid Models authorization in Settings."
            } else {
                "Configure a Gemini or OpenAI API key in Settings to use AI features."
            }
            return@withContext Result.failure(IllegalStateException(paidLockedHint))
        }

        // 5. Website Extraction (Untrusted content fenced cleanly)
        var effectivePrompt = userMessage
        val extractedSources = mutableListOf<WebSourceReference>()
        if (plan.extractedUrl != null) {
            onActivityChange(AiActivityState.SEARCHING, "Extracting public HTTPS website...")
            webResearch.fetchUntrustedWebsiteSummary(plan.extractedUrl).onSuccess { fenced ->
                effectivePrompt = "$userMessage\n\n$fenced"
                extractedSources.add(WebSourceReference(plan.extractedUrl.removePrefix("https://").substringBefore('/'), plan.extractedUrl, "Website"))
            }.onFailure { err ->
                effectivePrompt = "$userMessage\n[Note: Website fetch blocked or failed: ${err.message}]"
            }
        }

        // 6. Live Internet Search & Deep Research (Section 5)
        if (attachedImageBase64.isNullOrBlank() && plan.requiresWebSearch && plan.extractedUrl == null) {
            val res = runSearchPipeline(orderedSlots, effectivePrompt, plan, s, onActivityChange)
            if (res != null) {
                rememberSessionTurn(userMessage, res.answerText)
                val allSources = (extractedSources + res.sources).distinctBy { it.url }
                return@withContext Result.success(
                    AgentReplyResult(
                        replyText = res.answerText,
                        executedTools = listOf(if (res.usedDeepResearch) "deep_research" else "live_web_search"),
                        sources = allSources,
                        latencyMs = (System.currentTimeMillis() - started).coerceAtLeast(1L),
                        usedModel = res.engineUsed,
                        providerUsed = res.engineUsed,
                        domain = plan.domain,
                        intelligenceMode = plan.intelligenceMode
                    )
                )
            }
        }

        // 7. Execute AI Brain across ordered slots with automatic HTTP recovery & provider switching
        var lastErr: Exception? = null
        for (slot in orderedSlots) {
            coroutineContext.ensureActive()
            val slotStarted = System.currentTimeMillis()
            val attempt = ConnectionRecoveryManager.runWithAutoRecovery(maxAttempts = 2) {
                runCatching {
                    when (slot.provider) {
                        SlotProvider.GEMINI -> executeGeminiTurn(
                            slot = slot,
                            prompt = effectivePrompt,
                            settings = s,
                            plan = plan,
                            imageBase64 = attachedImageBase64,
                            onActivityChange = onActivityChange
                        )
                        SlotProvider.OPENAI -> {
                            onActivityChange(AiActivityState.THINKING, "Thinking (${slot.preferredModel})...")
                            val historyCopy = synchronized(liveSessionHistory) { liveSessionHistory.toList() }
                            val sys = "${plan.workflowInstruction}\nDevice Context: ${actionExecutor.getDeviceContextLine()}"
                            val reply = openAiClient.answerOpenAi(
                                baseUrl = slot.baseUrl,
                                apiKey = slot.apiKey,
                                preferredModel = s.openAiModel.ifBlank { slot.preferredModel },
                                intelligenceMode = plan.intelligenceMode,
                                freePriority = s.freeModelPriority,
                                question = effectivePrompt,
                                system = sys,
                                previous = historyCopy,
                                imageBase64 = attachedImageBase64,
                                onPartial = onPartialReply
                            )
                            AgentReplyResult(
                                replyText = reply.text,
                                sources = extractedSources,
                                latencyMs = System.currentTimeMillis() - started,
                                usedModel = reply.model,
                                providerUsed = "OpenAI GPT (${reply.model})",
                                domain = plan.domain,
                                intelligenceMode = plan.intelligenceMode
                            )
                        }
                        SlotProvider.SARVAM -> throw IOException("Sarvam slot is dedicated to Voice engine")
                    }
                }
            }
            if (attempt.isSuccess) {
                val out = attempt.getOrThrow()
                slotManager.recordOutcome(slot.id, 200, System.currentTimeMillis() - slotStarted, null)
                rememberSessionTurn(userMessage, out.replyText)
                return@withContext Result.success(out)
            }
            val err = attempt.exceptionOrNull() as? Exception
            if (err is CancellationException) throw err
            lastErr = err
            val code = parseHttpCode(err?.message)
            slotManager.recordOutcome(slot.id, code, System.currentTimeMillis() - slotStarted, err?.message)
            if (code == 401 || code == 403 || code == 429 || code in listOf(500, 502, 503, 504)) {
                continue
            }
        }
        Result.failure(lastErr ?: IOException("All configured AI providers failed"))
    }

    private suspend fun runSearchPipeline(
        orderedSlots: List<ApiSlot>,
        query: String,
        plan: BrainTaskPlan,
        s: OmegaSettings,
        onActivityChange: (AiActivityState, String) -> Unit
    ): WebResearchResult? {
        for (slot in orderedSlots) {
            if (slot.provider == SlotProvider.GEMINI) {
                if (plan.prefersDeepResearch && s.deepResearchEnabled) {
                    onActivityChange(AiActivityState.SEARCHING, "Running Gemini Deep Research...")
                    webResearch.runGeminiDeepResearch(slot.apiKey, query).getOrNull()?.let { return it }
                }
                onActivityChange(AiActivityState.SEARCHING, "Searching Google via Gemini...")
                val model = GeminiModelSelectionPolicy.buildCandidates(s.geminiChatModel, cachedGeminiModels).first()
                webResearch.searchGeminiGrounded(slot.apiKey, model, query, plan.workflowInstruction)
                    .getOrNull()?.let { return it }
            } else if (slot.provider == SlotProvider.OPENAI) {
                onActivityChange(AiActivityState.SEARCHING, "Searching web via OpenAI...")
                webResearch.searchOpenAiWeb(slot.apiKey, slot.baseUrl, s.openAiModel.ifBlank { slot.preferredModel }, query, plan.workflowInstruction)
                    .getOrNull()?.let { return it }
            }
        }
        return null
    }

    private suspend fun executeGeminiTurn(
        slot: ApiSlot,
        prompt: String,
        settings: OmegaSettings,
        plan: BrainTaskPlan,
        imageBase64: String?,
        onActivityChange: (AiActivityState, String) -> Unit
    ): AgentReplyResult {
        val started = System.currentTimeMillis()
        val apiKey = slot.apiKey.trim()
        val chain = GeminiModelSelectionPolicy.buildCandidates(
            settings.geminiChatModel.ifBlank { slot.preferredModel },
            cachedGeminiModels
        )
        val executedTools = mutableListOf<String>()
        val collectedSources = mutableListOf<WebSourceReference>()
        var activeModel = chain.first()

        val contents = JSONArray()
        synchronized(liveSessionHistory) {
            liveSessionHistory.forEach { (role, txt) ->
                contents.put(
                    JSONObject()
                        .put("role", if (role == "user") "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", txt)))
                )
            }
        }
        val userParts = JSONArray().put(JSONObject().put("text", prompt))
        if (!imageBase64.isNullOrBlank()) {
            userParts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", imageBase64)))
        }
        contents.put(JSONObject().put("role", "user").put("parts", userParts))

        val systemText = buildSystemPrompt(settings, plan)
        var finalText = ""

        for (hop in 0 until 6) {
            onActivityChange(AiActivityState.THINKING, "Thinking (${activeModel})...")
            val payload = JSONObject()
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemText))))
                .put("contents", contents)
                .put("tools", buildGeminiToolsSchema(includeWebSearch = settings.webSearchGrounding))

            val (resJson, usedModel) = postGeminiWithFallback(apiKey, chain, payload)
            activeModel = usedModel
            val cand = resJson.optJSONArray("candidates")?.optJSONObject(0) ?: break
            collectedSources.addAll(WebResearchClient.parseGeminiGroundingSources(cand.optJSONObject("groundingMetadata")))
            val modelContent = cand.optJSONObject("content") ?: break
            val parts = modelContent.optJSONArray("parts") ?: JSONArray()
            contents.put(modelContent)

            val calls = mutableListOf<JSONObject>()
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.has("functionCall")) calls.add(p.getJSONObject("functionCall"))
                val t = p.optString("text", "")
                if (t.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(t)
                }
            }

            if (calls.isEmpty()) {
                finalText = sb.toString().trim()
                break
            }

            if (!AgentExecutionPolicy.batchAllowed(executedTools.size, calls.size)) {
                finalText = "Action batch limit reached safely."
                break
            }
            val respParts = JSONArray()
            var pendingScreenB64: String? = null
            for (call in calls) {
                val toolName = call.optString("name")
                val args = call.optJSONObject("args") ?: JSONObject()
                executedTools.add(toolName)
                onActivityChange(
                    if (toolName == "live_web_search") AiActivityState.SEARCHING else AiActivityState.EXECUTING,
                    "Running $toolName..."
                )
                val out = actionExecutor.executeTool(
                    name = toolName,
                    args = args,
                    webSearchRunner = { q ->
                        val sr = webResearch.searchGeminiGrounded(apiKey, activeModel, q, plan.workflowInstruction)
                        sr.onSuccess { collectedSources.addAll(it.sources) }
                        sr.getOrElse { "Live search unavailable: ${it.message}" }.let { if (it is WebResearchResult) it.answerText else it.toString() }
                    }
                )
                if (out.capturedImageBase64 != null) pendingScreenB64 = out.capturedImageBase64
                respParts.put(JSONObject().put("functionResponse", JSONObject().put("name", toolName).put("response", out.responseJson)))
            }
            contents.put(JSONObject().put("role", "user").put("parts", respParts))
            if (!pendingScreenB64.isNullOrBlank()) {
                contents.put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", "Captured live screenshot attached for visual verification."))
                            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", pendingScreenB64)))
                    )
                )
            }
        }

        if (finalText.isBlank()) throw IOException("Empty Gemini response from $activeModel")
        return AgentReplyResult(
            replyText = finalText,
            executedTools = executedTools.distinct(),
            sources = collectedSources.distinctBy { it.url },
            latencyMs = (System.currentTimeMillis() - started).coerceAtLeast(1L),
            usedModel = activeModel,
            providerUsed = "Gemini Direct ($activeModel)",
            domain = plan.domain,
            intelligenceMode = plan.intelligenceMode
        )
    }

    private suspend fun postGeminiWithFallback(apiKey: String, chain: List<String>, payload: JSONObject): Pair<JSONObject, String> {
        var lastErr: IOException? = null
        val models = if (cachedGeminiModels.isEmpty() && apiKey.isNotBlank()) {
            val found = discoveryService.discoverGeminiModels(apiKey).getOrNull()?.chatModels.orEmpty()
            if (found.isNotEmpty()) {
                cachedGeminiModels = found
                GeminiModelSelectionPolicy.buildCandidates(chain.firstOrNull().orEmpty(), found)
            } else chain
        } else chain

        for (model in models.map { it.removePrefix("models/") }.distinct()) {
            try {
                return postGemini(apiKey, model, payload) to model
            } catch (e: IOException) {
                lastErr = e
                val msg = e.message.orEmpty()
                if (GeminiCompatibility.canDowngrade(msg, payload.has("tools"), false)) {
                    val stripped = JSONObject(payload.toString()).apply { remove("tools") }
                    try { return postGemini(apiKey, model, stripped) to model } catch (retry: IOException) {
                        lastErr = retry
                        if (!GeminiCompatibility.tryAnotherModel(retry.message.orEmpty())) throw retry
                    }
                } else if (!GeminiCompatibility.tryAnotherModel(msg)) {
                    throw e
                }
            }
        }
        throw lastErr ?: IOException("All Gemini models failed")
    }

    private suspend fun postGemini(apiKey: String, model: String, payload: JSONObject): JSONObject {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val req = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val started = System.currentTimeMillis()
        return try {
            httpClient.newCall(req).awaitParsed { resp ->
                val raw = resp.boundedJsonText(2L * 1024L * 1024L)
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                    throw IOException("HTTP ${resp.code}: ${msg ?: "Gemini request failed"}")
                }
                val json = JSONObject(raw)
                val usage = json.optJSONObject("usageMetadata")
                val p = usage?.optLong("promptTokenCount", -1)?.takeIf { it >= 0 }
                val o = usage?.optLong("candidatesTokenCount", -1)?.takeIf { it >= 0 }
                ApiQuotaTracker.recordSuccess(model, p, o, System.currentTimeMillis() - started)
                json
            }
        } catch (e: IOException) {
            ApiQuotaTracker.recordFailure(model)
            throw e
        }
    }

    private fun buildSystemPrompt(s: OmegaSettings, plan: BrainTaskPlan): String {
        val ctx = actionExecutor.getDeviceContextLine()
        val unread = NotificationInbox.items.value.size
        return "${plan.workflowInstruction}\nUser: ${s.userName} | Assistant: ${s.assistantName} | Device: $ctx | UnreadNotifications=$unread"
    }

    private fun extractGeminiText(json: JSONObject): String {
        val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: return ""
        return (0 until parts.length())
            .mapNotNull { parts.optJSONObject(it)?.optString("text")?.takeIf { t -> t.isNotBlank() } }
            .joinToString("\n")
            .trim()
    }

    private fun buildGeminiToolsSchema(includeWebSearch: Boolean): JSONArray {
        val declarations = JSONArray()
        fun add(name: String, desc: String, props: Map<String, Pair<String, String>> = emptyMap(), req: List<String> = emptyList()) {
            val d = JSONObject().put("name", name).put("description", desc)
            if (props.isNotEmpty()) {
                val pObj = JSONObject()
                props.forEach { (k, v) -> pObj.put(k, JSONObject().put("type", v.first).put("description", v.second)) }
                val params = JSONObject().put("type", "OBJECT").put("properties", pObj)
                if (req.isNotEmpty()) params.put("required", JSONArray(req))
                d.put("parameters", params)
            }
            declarations.put(d)
        }
        if (includeWebSearch) {
            add("live_web_search", "Search live Google Search for current facts, news, weather, prices.", mapOf("query" to ("STRING" to "Search query")), listOf("query"))
        }
        add("open_app", "Launch an installed Android app.", mapOf("app_name" to ("STRING" to "App name")), listOf("app_name"))
        add("toggle_flashlight", "Turn flashlight ON or OFF.", mapOf("enabled" to ("BOOLEAN" to "true or false")), listOf("enabled"))
        add("set_media_volume", "Set media volume percentage 0..100.", mapOf("percent" to ("INTEGER" to "0-100")), listOf("percent"))
        add("adjust_media_volume", "Adjust media volume up or down.", mapOf("direction" to ("STRING" to "up or down")), listOf("direction"))
        add("dial_contact", "Open dialer with contact or number for explicit call confirmation.", mapOf("contact_or_number" to ("STRING" to "Contact or phone number")), listOf("contact_or_number"))
        add("tap_ui_text", "Tap visible UI button via Accessibility automation.", mapOf("target_text" to ("STRING" to "Button text")), listOf("target_text"))
        add("tap_screen_coordinates", "Tap normalized coordinates 0..1000 via Accessibility.", mapOf("x_norm" to ("INTEGER" to "0-1000"), "y_norm" to ("INTEGER" to "0-1000"), "label" to ("STRING" to "Label")), listOf("x_norm", "y_norm"))
        add("type_screen_text", "Type text into focused editable box.", mapOf("text" to ("STRING" to "Text")), listOf("text"))
        add("scroll_screen", "Scroll active screen up or down.", mapOf("direction" to ("STRING" to "up or down")), listOf("direction"))
        add("navigate_device", "Perform back, home, recents, notifications.", mapOf("action" to ("STRING" to "back, home, recents, or notifications")), listOf("action"))
        add("capture_screen_vision", "Capture live screenshot for visual inspection.")
        add("read_notifications", "Read recent smart notifications.", mapOf("limit" to ("INTEGER" to "Max items")))
        add("reply_notification", "Draft confirmed reply to incoming notification.", mapOf("sender_or_app" to ("STRING" to "Sender/app"), "reply_text" to ("STRING" to "Message")), listOf("sender_or_app", "reply_text"))
        add("set_alarm", "Set an alarm.", mapOf("hour_24" to ("INTEGER" to "0-23"), "minute" to ("INTEGER" to "0-59"), "label" to ("STRING" to "Label")), listOf("hour_24", "minute"))
        add("set_timer", "Start countdown timer.", mapOf("seconds" to ("INTEGER" to "Seconds"), "label" to ("STRING" to "Label")), listOf("seconds"))
        add("get_battery_status", "Read phone battery percentage and charging state.")
        add("open_system_settings", "Open Android system settings section.", mapOf("section" to ("STRING" to "Section")), listOf("section"))
        add("run_routine", "Execute a scheduled automation.", mapOf("routine_name" to ("STRING" to "Automation name")), listOf("routine_name"))
        return JSONArray().put(JSONObject().put("function_declarations", declarations))
    }

    private fun parseHttpCode(message: String?): Int {
        if (message.isNullOrBlank()) return 0
        return Regex("HTTP\\s+(\\d{3})").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
}
