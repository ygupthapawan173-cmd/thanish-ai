package com.example.ui

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.ai.AiActivityState
import com.example.ai.ConnectionDiagnosticResult
import com.example.ai.GeminiLiveDuplexEngine
import com.example.ai.KnowledgeDomain
import com.example.ai.LiveDuplexState
import com.example.ai.ModelCatalogPolicy
import com.example.ai.OpenAiRealtimeClient
import com.example.ai.PanduAiRepository
import com.example.ai.ResolvedBrainModelStatus
import com.example.ai.SmartActionPolicy
import com.example.ai.WebSourceReference
import com.example.autonomous.ReleaseUpdateInfo
import com.example.autonomous.ReleaseVoiceAction
import com.example.autonomous.SecureSelfUpdater
import com.example.control.AgentHudBus
import com.example.control.BiometricSafetyGate
import com.example.control.DeviceActionExecutor
import com.example.control.HapticFeedbackHelper
import com.example.control.NotificationInbox
import com.example.control.PanduAccessibilityService
import com.example.control.PendingNotificationReply
import com.example.control.VisionHelper
import com.example.data.AiProvider
import com.example.data.ApiQuotaTracker
import com.example.data.ApiSlot
import com.example.data.ApiSlotManager
import com.example.data.ApiSlotSelectionPolicy
import com.example.data.ApiTelemetrySnapshot
import com.example.data.AppDatabase
import com.example.data.AuditLogEntry
import com.example.data.IntelligenceMode
import com.example.data.OmegaPreferences
import com.example.data.OmegaSettings
import com.example.data.SlotProvider
import com.example.data.SlotStatus
import com.example.data.VoiceRoutine
import com.example.omega.OnlineBackgroundVoiceService
import com.example.omega.SmartTriggerBus
import com.example.omega.TimeAutomationScheduler
import com.example.voice.PreloadedAckPlayer
import com.example.voice.SarvamVoiceClient
import com.example.voice.StreamingVoiceEngineKind
import com.example.voice.TeluguTtsController
import com.example.voice.TeluguVoiceReplies
import com.example.voice.VoiceEngineBenchmark
import com.example.voice.VoiceEngineSelector
import com.example.voice.VoiceInputController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class JarvisTab(val label: String) {
    HOME("Home"),
    DASHBOARD("Dashboard"),
    AI_BRAIN("AI Brain"),
    VOICE("Voice"),
    CONTROL("Control"),
    SETTINGS("Settings")
}

data class DashboardIndicator(
    val id: String,
    val title: String,
    val value: String,
    val detail: String,
    val healthy: Boolean,
    val targetTab: JarvisTab,
    val settingsPage: Int = -1
)

data class PanduUiState(
    val currentTab: JarvisTab = JarvisTab.HOME,
    val settingsCategoryPage: Int = -1,
    val settings: OmegaSettings = OmegaSettings(),
    val slots: List<ApiSlot> = emptyList(),
    val catalogStatuses: List<ResolvedBrainModelStatus> = emptyList(),
    val indicators: List<DashboardIndicator> = emptyList(),
    val aiActivityState: AiActivityState = AiActivityState.STANDBY,
    val lastBrainRouteSummary: String = "Master AI Brain Ready",
    val lastQuery: String = "",
    val lastAiReply: String = "",
    val streamingReply: String = "",
    val lastSources: List<WebSourceReference> = emptyList(),
    val lastDomainLabel: String = KnowledgeDomain.GENERAL_KNOWLEDGE.label,
    val lastModelUsed: String = "",
    val lastLatencyMs: Long = 0L,
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val liveTranscript: String = "",
    val audioAmplitude: Float = 0f,
    val voiceDiagNote: String = "Voice Engine Ready",
    val ttsStatusNote: String = "Sarvam Telugu Voice Ready",
    val bgServiceStatus: String = "Idle",
    val voiceBenchmark: VoiceEngineBenchmark = VoiceEngineBenchmark(),
    val measuredAckLatencyMs: Long = 0L,
    val ackUsingSarvamCache: Boolean = false,
    val liveDuplexState: LiveDuplexState = LiveDuplexState(),
    val openAiLiveState: LiveDuplexState = LiveDuplexState(),
    val discoveredGeminiModels: List<String> = emptyList(),
    val discoveredGeminiLiveModels: List<String> = emptyList(),
    val discoveredOpenAiModels: List<String> = emptyList(),
    val telemetry: ApiTelemetrySnapshot = ApiTelemetrySnapshot(),
    val isTestingConnection: Boolean = false,
    val isDiscoveringModels: Boolean = false,
    val diagnosticResult: ConnectionDiagnosticResult? = null,
    val voiceTestStatus: String? = null,
    val isCheckingUpdate: Boolean = false,
    val availableRelease: ReleaseUpdateInfo? = null,
    val updateStatusMessage: String? = null,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val mediaVolumePercent: Int = 50,
    val flashlightOn: Boolean = false,
    val installedApps: List<SmartActionPolicy.App> = emptyList(),
    val ramFreeMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val internetValidated: Boolean = false,
    val networkLabel: String = "Checking...",
    val accessibilityConnected: Boolean = false,
    val notificationAccessGranted: Boolean = false,
    val unreadNotifCount: Int = 0,
    val pendingNotifReply: PendingNotificationReply? = null,
    val routines: List<VoiceRoutine> = emptyList(),
    val auditLogs: List<AuditLogEntry> = emptyList(),
    val visionBitmap: Bitmap? = null,
    val cameraLaunchRequestCount: Int = 0,
    val batteryOptIgnored: Boolean = false,
    val micPermGranted: Boolean = false,
    val notifPermGranted: Boolean = false,
    val cameraPermGranted: Boolean = false,
    val contactsPermGranted: Boolean = false,
    val phonePermGranted: Boolean = false,
    val calendarPermGranted: Boolean = false,
    val statusBanner: String? = null
)

class PanduViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = OmegaPreferences(appContext)
    private val slotManager = ApiSlotManager(appContext, preferences)
    private val database = AppDatabase(appContext)
    private val actionExecutor = DeviceActionExecutor(appContext, database, preferences)
    private val repository = PanduAiRepository({ preferences.settings.value }, slotManager, actionExecutor)
    private val updater = SecureSelfUpdater(appContext, preferences)
    private val haptics = runCatching { HapticFeedbackHelper(appContext) }.getOrNull()

    private val ttsController = TeluguTtsController(
        context = appContext,
        backgroundOwned = false,
        cloudSettings = { preferences.settings.value },
        slotManager = slotManager
    )

    private val voiceInputController = VoiceInputController(
        context = appContext,
        owner = "foreground-main",
        sarvamKeyProvider = { effectiveSarvamKey() },
        geminiKeyProvider = { effectiveGeminiKey() },
        openAiKeyProvider = { effectiveOpenAiKey() },
        cloudConsentProvider = { preferences.settings.value.cloudAudioConsentGranted }
    ) { spokenText ->
        askAssistant(spokenText)
    }

    private val geminiLiveEngine = GeminiLiveDuplexEngine(
        context = appContext,
        apiKeyProvider = { effectiveGeminiKey() },
        liveModelProvider = { preferences.settings.value.geminiLiveVoiceModel },
        backgroundOwned = false,
        allowedProvider = { preferences.settings.value.cloudAudioConsentGranted && !preferences.settings.value.micPrivacyMuted && AgentHudBus.isAppInForeground.value }
    )

    private val openAiRealtime = OpenAiRealtimeClient(
        apiKeyProvider = { effectiveOpenAiKey() },
        modelProvider = { preferences.settings.value.openAiLiveVoiceModel },
        allowedProvider = { preferences.settings.value.cloudAudioConsentGranted && !preferences.settings.value.micPrivacyMuted && AgentHudBus.isAppInForeground.value }
    )

    private val _uiState = MutableStateFlow(PanduUiState())
    val uiState: StateFlow<PanduUiState> = _uiState.asStateFlow()

    private var activeQueryJob: Job? = null

    init {
        slotManager.syncPrimarySettingsToSlots()
        PreloadedAckPlayer.warmUpSarvamAckCache(
            appContext,
            effectiveSarvamKey(),
            preferences.settings.value.sarvamSpeaker,
            preferences.settings.value.sarvamModel
        )

        viewModelScope.launch {
            preferences.settings.collect { s ->
                _uiState.update { it.copy(settings = s) }
                refreshDerivedMetrics()
            }
        }
        viewModelScope.launch {
            slotManager.slots.collect { list ->
                _uiState.update { it.copy(slots = list) }
                refreshDerivedMetrics()
            }
        }
        viewModelScope.launch {
            voiceInputController.state.collect { st ->
                _uiState.update { cur ->
                    val activity = when {
                        st.isListening -> AiActivityState.LISTENING
                        cur.isThinking -> cur.aiActivityState
                        cur.isSpeaking -> AiActivityState.SPEAKING
                        else -> AiActivityState.STANDBY
                    }
                    cur.copy(
                        isListening = st.isListening,
                        liveTranscript = st.partialTranscript,
                        audioAmplitude = maxOf(st.audioAmplitude, cur.liveDuplexState.amplitude, cur.openAiLiveState.amplitude),
                        voiceDiagNote = st.diagnosticsNote,
                        statusBanner = st.errorMessage ?: cur.statusBanner,
                        aiActivityState = activity
                    )
                }
                refreshDerivedMetrics()
            }
        }
        viewModelScope.launch {
            ttsController.isSpeaking.collect { speaking ->
                _uiState.update { cur ->
                    val nextState = when {
                        speaking -> AiActivityState.SPEAKING
                        cur.isListening -> AiActivityState.LISTENING
                        cur.isThinking -> cur.aiActivityState
                        else -> AiActivityState.STANDBY
                    }
                    cur.copy(isSpeaking = speaking, aiActivityState = nextState)
                }
                refreshDerivedMetrics()
            }
        }
        viewModelScope.launch {
            ttsController.ttsStatus.collect { note ->
                _uiState.update { it.copy(ttsStatusNote = note) }
                refreshDerivedMetrics()
            }
        }
        viewModelScope.launch {
            OnlineBackgroundVoiceService.status.collect { st ->
                _uiState.update { it.copy(bgServiceStatus = st) }
                if (st.startsWith("Wake start failed") || st.startsWith("Wake library unavailable") || st.startsWith("Wake service failed") || st.startsWith("Stopped by privacy") || st.startsWith("Android foreground") || st.startsWith("Grant Microphone")) {
                    if (preferences.settings.value.wakeWordEnabled) preferences.update { it.copy(wakeWordEnabled = false) }
                }
                refreshDerivedMetrics()
            }
        }
        viewModelScope.launch {
            geminiLiveEngine.state.collect { live ->
                _uiState.update { cur ->
                    val nextState = when {
                        live.isAiSpeaking -> AiActivityState.SPEAKING
                        live.isConnected -> AiActivityState.LISTENING
                        else -> cur.aiActivityState
                    }
                    cur.copy(liveDuplexState = live, aiActivityState = nextState, audioAmplitude = if (live.isConnected) live.amplitude else 0f)
                }
                refreshDerivedMetrics()
            }
        }
        viewModelScope.launch {
            openAiRealtime.state.collect { oai ->
                _uiState.update { cur -> cur.copy(openAiLiveState = oai, audioAmplitude = if (oai.isConnected) oai.amplitude else cur.audioAmplitude,
                    aiActivityState = when { oai.isAiSpeaking -> AiActivityState.SPEAKING; oai.isConnected -> AiActivityState.LISTENING; else -> cur.aiActivityState }) }
                refreshDerivedMetrics()
            }
        }
        viewModelScope.launch {
            PanduAccessibilityService.isConnected.collect { connected ->
                _uiState.update { it.copy(accessibilityConnected = connected) }
                refreshDerivedMetrics()
            }
        }
        viewModelScope.launch {
            NotificationInbox.items.collect { items ->
                _uiState.update { it.copy(unreadNotifCount = items.size) }
                refreshDerivedMetrics()
            }
        }
        viewModelScope.launch {
            NotificationInbox.pendingReply.collect { pending ->
                _uiState.update { it.copy(pendingNotifReply = pending) }
            }
        }
        viewModelScope.launch {
            ApiQuotaTracker.snapshot.collect { snap ->
                _uiState.update { it.copy(telemetry = snap) }
            }
        }
        viewModelScope.launch {
            SmartTriggerBus.events.collect { ev ->
                _uiState.update { it.copy(statusBanner = "${ev.title}: ${ev.message}") }
            }
        }
        viewModelScope.launch {
            AgentHudBus.wakeTriggers.collect { inlineCmd ->
                handleInlineWakeCommand(inlineCmd)
            }
        }

        refreshSystemMetrics()
    }

    private fun effectiveGeminiKey(): String {
        val direct = preferences.settings.value.effectiveGeminiKey()
        if (direct.isNotBlank()) return direct
        return slotManager.slots.value.firstOrNull { it.provider == SlotProvider.GEMINI && it.enabled && it.apiKey.isNotBlank() }?.apiKey.orEmpty()
    }

    private fun effectiveOpenAiKey(): String {
        val direct = preferences.settings.value.effectiveOpenAiKey()
        if (direct.isNotBlank()) return direct
        return slotManager.slots.value.firstOrNull { it.provider == SlotProvider.OPENAI && it.enabled && it.apiKey.isNotBlank() }?.apiKey.orEmpty()
    }

    private fun effectiveSarvamKey(): String {
        val direct = preferences.settings.value.effectiveSarvamKey()
        if (direct.isNotBlank()) return direct
        return slotManager.getActiveSarvamKeyAndUrl().first
    }

    fun navigateTo(tab: JarvisTab, settingsCategory: Int = -1) {
        _uiState.update { it.copy(currentTab = tab, settingsCategoryPage = settingsCategory) }
    }

    fun openSettingsPage(pageIndex: Int) {
        _uiState.update { it.copy(currentTab = JarvisTab.SETTINGS, settingsCategoryPage = pageIndex) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    fun updateSettings(transform: (OmegaSettings) -> OmegaSettings) {
        preferences.update(transform)
        slotManager.syncPrimarySettingsToSlots()
        val s = preferences.settings.value
        val key = effectiveSarvamKey()
        if (key.isNotBlank()) {
            PreloadedAckPlayer.warmUpSarvamAckCache(appContext, key, s.sarvamSpeaker, s.sarvamModel)
        }
        refreshDerivedMetrics()
    }

    fun upsertSlot(slot: ApiSlot) {
        slotManager.upsertSlot(slot)
        if (slot.provider == SlotProvider.GEMINI && slot.enabled && slot.apiKey.isNotBlank() && preferences.settings.value.geminiApiKey.isBlank()) {
            preferences.update { it.copy(geminiApiKey = slot.apiKey, geminiChatModel = slot.preferredModel) }
        } else if (slot.provider == SlotProvider.OPENAI && slot.enabled && slot.apiKey.isNotBlank() && preferences.settings.value.openAiApiKey.isBlank()) {
            preferences.update { it.copy(openAiApiKey = slot.apiKey, openAiModel = slot.preferredModel, openAiBaseUrl = slot.baseUrl) }
        } else if (slot.provider == SlotProvider.SARVAM && slot.enabled && slot.apiKey.isNotBlank() && preferences.settings.value.sarvamApiKey.isBlank()) {
            preferences.update { it.copy(sarvamApiKey = slot.apiKey) }
            PreloadedAckPlayer.warmUpSarvamAckCache(appContext, slot.apiKey, preferences.settings.value.sarvamSpeaker, preferences.settings.value.sarvamModel)
        }
        refreshDerivedMetrics()
    }

    fun toggleSlot(id: String, enabled: Boolean) {
        slotManager.toggleSlot(id, enabled)
        refreshDerivedMetrics()
    }

    fun deleteSlot(id: String) {
        slotManager.deleteSlot(id)
        refreshDerivedMetrics()
    }

    fun testSlotConnection(slot: ApiSlot) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, diagnosticResult = null) }
            val result = repository.testSlotConnection(slot)
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    diagnosticResult = result,
                    discoveredGeminiModels = repository.cachedGeminiModels,
                    discoveredGeminiLiveModels = repository.cachedGeminiLiveModels,
                    discoveredOpenAiModels = repository.cachedOpenAiModels,
                    statusBanner = result.summary
                )
            }
            refreshDerivedMetrics()
        }
    }

    fun discoverAllModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDiscoveringModels = true) }
            val res = repository.discoverAllConfiguredModels()
            _uiState.update {
                it.copy(
                    isDiscoveringModels = false,
                    discoveredGeminiModels = repository.cachedGeminiModels,
                    discoveredGeminiLiveModels = repository.cachedGeminiLiveModels,
                    discoveredOpenAiModels = repository.cachedOpenAiModels,
                    statusBanner = res.fold(
                        onSuccess = { (g, o) ->
                            "Discovered ${g?.chatModels?.size ?: 0} Gemini & ${o?.chatModels?.size ?: 0} OpenAI models"
                        },
                        onFailure = { err -> err.message ?: "Model discovery failed" }
                    )
                )
            }
            refreshDerivedMetrics()
        }
    }

    fun askAssistant(rawPrompt: String, overrideBitmap: Bitmap? = null) {
        val text = rawPrompt.trim()
        if (text.isBlank()) return
        val s = preferences.settings.value
        val imgBitmap = overrideBitmap ?: _uiState.value.visionBitmap
        val imgBase64 = imgBitmap?.let { runCatching { VisionHelper.bitmapToBase64Jpeg(it) }.getOrNull() }

        activeQueryJob?.cancel()
        ttsController.stop()
        voiceInputController.stopListening()

        activeQueryJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    lastQuery = text,
                    isThinking = true,
                    streamingReply = "",
                    aiActivityState = AiActivityState.THINKING,
                    visionBitmap = null
                )
            }

            try {
                val res = repository.sendMessage(
                    userMessage = text,
                    attachedImageBase64 = imgBase64,
                    onActivityChange = { actState, detail ->
                        _uiState.update { it.copy(aiActivityState = actState, lastBrainRouteSummary = detail) }
                    },
                    onPartialReply = { partial ->
                        _uiState.update { it.copy(streamingReply = partial) }
                    }
                )
                coroutineContext.ensureActive()

                res.onSuccess { reply ->
                    val routeSummary = repository.lastRouteResult?.reason ?: reply.providerUsed
                    _uiState.update {
                        it.copy(
                            isThinking = false,
                            aiActivityState = AiActivityState.STANDBY,
                            lastAiReply = reply.replyText,
                            streamingReply = "",
                            lastSources = reply.sources,
                            lastDomainLabel = reply.domain.label,
                            lastModelUsed = "${reply.providerUsed} (${reply.usedModel})",
                            lastLatencyMs = reply.latencyMs,
                            lastBrainRouteSummary = routeSummary
                        )
                    }
                    when (reply.releaseAction) {
                        ReleaseVoiceAction.CHECK_FOR_UPDATES -> {
                            openSettingsPage(6)
                            checkSignedUpdates()
                        }
                        ReleaseVoiceAction.UPGRADE_SELF -> {
                            openSettingsPage(6)
                            checkSignedUpdates(autoInstallIfNewer = true)
                        }
                        null -> Unit
                    }
                    if (s.autoSpeak) {
                        ttsController.configure(TeluguVoiceReplies.effectiveLocale(s.languageTag, utterance = reply.replyText))
                        ttsController.speak(reply.replyText)
                    }
                    refreshSystemMetrics()
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isThinking = false,
                            aiActivityState = AiActivityState.STANDBY,
                            lastAiReply = err.message ?: "Request failed",
                            streamingReply = "",
                            statusBanner = err.message ?: "Request failed"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun clearSessionContext() {
        repository.clearSessionContext()
        _uiState.update {
            it.copy(
                lastQuery = "",
                lastAiReply = "",
                streamingReply = "",
                lastSources = emptyList(),
                statusBanner = "Live session context cleared"
            )
        }
    }

    fun startVoiceListening() {
        if (preferences.settings.value.micPrivacyMuted) {
            _uiState.update { it.copy(statusBanner = "Microphone privacy mute is ON") }
            return
        }
        ttsController.stop()
        voiceInputController.startListening(preferences.settings.value.languageTag)
    }

    fun toggleVoiceListening() {
        if (_uiState.value.isListening) voiceInputController.stopListening()
        else startVoiceListening()
    }

    fun handleInlineWakeCommand(inlineCmd: String) {
        val ackAt = android.os.SystemClock.elapsedRealtime()
        PreloadedAckPlayer.playImmediateAck(appContext, preferences.settings.value.languageTag, ackAt) {
            _uiState.update {
                it.copy(
                    measuredAckLatencyMs = PreloadedAckPlayer.lastMeasuredAckMs,
                    ackUsingSarvamCache = PreloadedAckPlayer.usingCachedSarvamAudio
                )
            }
            if (inlineCmd.isNotBlank()) askAssistant(inlineCmd) else startVoiceListening()
        }
    }

    fun testYesBossAck() {
        val started = android.os.SystemClock.elapsedRealtime()
        PreloadedAckPlayer.playImmediateAck(appContext, preferences.settings.value.languageTag, started) {
            _uiState.update {
                it.copy(
                    measuredAckLatencyMs = PreloadedAckPlayer.lastMeasuredAckMs,
                    ackUsingSarvamCache = PreloadedAckPlayer.usingCachedSarvamAudio,
                    statusBanner = "Yes Boss ack played in ${PreloadedAckPlayer.lastMeasuredAckMs}ms (${if (PreloadedAckPlayer.usingCachedSarvamAudio) "Cached Sarvam" else "Instant Chime"})"
                )
            }
            refreshDerivedMetrics()
        }
    }

    fun testSarvamVoice() {
        viewModelScope.launch {
            val key = effectiveSarvamKey()
            val speaker = preferences.settings.value.sarvamSpeaker
            _uiState.update { it.copy(voiceTestStatus = "Testing Sarvam ($speaker)...") }
            val result = SarvamVoiceClient.testConnection(key, speaker)
            result.onSuccess { msg ->
                _uiState.update { it.copy(voiceTestStatus = msg, statusBanner = msg) }
                ttsController.configure("te-IN")
                ttsController.speak("నమస్కారం బాస్! నేను జార్విస్ ఇన్ఫినిటీ. సర్వమ్ తెలుగు వాయిస్ సిద్ధంగా ఉంది.")
            }.onFailure { err ->
                _uiState.update { it.copy(voiceTestStatus = err.message ?: "Sarvam test failed", statusBanner = err.message) }
            }
        }
    }

    fun toggleLiveSession(useOpenAi: Boolean = false) {
        if (useOpenAi) {
            if (_uiState.value.openAiLiveState.isConnected || _uiState.value.openAiLiveState.isConnecting) {
                openAiRealtime.disconnect()
            } else {
                geminiLiveEngine.disconnect()
                voiceInputController.stopListening()
                ttsController.stop()
                OnlineBackgroundVoiceService.yieldToForeground(appContext)
                openAiRealtime.connect()
            }
        } else {
            if (_uiState.value.liveDuplexState.isConnected || _uiState.value.liveDuplexState.isConnecting) {
                geminiLiveEngine.disconnect()
            } else {
                openAiRealtime.disconnect()
                voiceInputController.stopListening()
                ttsController.stop()
                OnlineBackgroundVoiceService.yieldToForeground(appContext)
                geminiLiveEngine.connect()
            }
        }
    }

    fun toggleBackgroundWake(enable: Boolean) {
        if (enable) {
            // Persist only after requesting service startup; do not auto-restart a crashing native engine.
            startBackgroundWakeService()
        } else {
            preferences.update { it.copy(wakeWordEnabled = false) }
            stopBackgroundVoiceService()
        }
    }

    fun startBackgroundWakeService() {
        val ok = OnlineBackgroundVoiceService.startWake(appContext)
        preferences.update { it.copy(wakeWordEnabled = ok) }
        _uiState.update {
            it.copy(statusBanner = if (ok) "Starting Hey JARVIS — check live service status" else "Allow Microphone & Notifications first")
        }
    }

    fun stopBackgroundVoiceService() {
        OnlineBackgroundVoiceService.stop(appContext)
        _uiState.update { it.copy(statusBanner = "Background voice service stopped") }
    }

    fun restoreWakeIfEnabled() {
        // Android can kill a microphone process after a native fault. Require a fresh user tap
        // after a cold process start rather than entering a crash/restart loop.
        if (preferences.settings.value.wakeWordEnabled && OnlineBackgroundVoiceService.status.value == "Idle") {
            preferences.update { it.copy(wakeWordEnabled = false) }
            _uiState.update { it.copy(statusBanner = "Tap Hey JARVIS to restart wake listening") }
        }
    }

    fun releaseForegroundVoice() {
        voiceInputController.stopListening()
        geminiLiveEngine.disconnect()
        openAiRealtime.disconnect()
        ttsController.stop()
    }

    fun stopAllActivity() {
        activeQueryJob?.cancel()
        activeQueryJob = null
        releaseForegroundVoice()
        AgentHudBus.triggerAbort()
        haptics?.abortPulse()
        _uiState.update {
            it.copy(
                isListening = false,
                isThinking = false,
                isSpeaking = false,
                streamingReply = "",
                aiActivityState = AiActivityState.STANDBY,
                statusBanner = "Stopped all voice & AI tasks"
            )
        }
    }

    // ---- Device Control, Notifications, Vision, Scheduled Automation ----
    fun launchAppByName(name: String) {
        _uiState.update { it.copy(statusBanner = actionExecutor.phoneControl.openAppByName(name)) }
    }

    fun toggleFlashlight(enable: Boolean) {
        val msg = actionExecutor.phoneControl.setFlashlight(enable)
        _uiState.update { it.copy(flashlightOn = enable && !msg.contains("error", true), statusBanner = msg) }
    }

    fun setMediaVolume(percent: Int) {
        val msg = actionExecutor.phoneControl.setMediaVolume(percent)
        _uiState.update { it.copy(mediaVolumePercent = actionExecutor.phoneControl.getMediaVolumePercent(), statusBanner = msg) }
    }

    fun dialContact(target: String) {
        _uiState.update { it.copy(statusBanner = actionExecutor.phoneControl.dialForConfirmation(target)) }
    }

    fun openSmsDraft(target: String, message: String) {
        _uiState.update { it.copy(statusBanner = actionExecutor.phoneControl.sendSmsDraft(target, message)) }
    }

    fun openSystemSettingsSection(section: String) {
        actionExecutor.phoneControl.openSystemSetting(section)
    }

    fun openAccessibilitySettings() {
        actionExecutor.phoneControl.openSystemSetting("accessibility")
    }

    fun openNotificationListenerSettings() {
        runCatching {
            appContext.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openBatteryOptimizationSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${appContext.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }.onFailure { actionExecutor.phoneControl.openSystemSetting("battery") }
    }

    fun openAppDetailsSettings() {
        runCatching {
            appContext.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${appContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun requestCameraLaunchFromWidget() {
        _uiState.update { it.copy(cameraLaunchRequestCount = it.cameraLaunchRequestCount + 1) }
    }

    fun attachVisionBitmap(bitmap: Bitmap) {
        _uiState.update { it.copy(visionBitmap = bitmap, statusBanner = "Photo ready — type or speak a Vision question on Home") }
        navigateTo(JarvisTab.HOME)
    }

    fun clearVisionBitmap() {
        _uiState.update { it.copy(visionBitmap = null) }
    }

    fun captureScreenVisionAndAsk(question: String = "What is shown on my screen right now? Summarize clearly.") {
        val svc = PanduAccessibilityService.instance
        if (svc == null) {
            _uiState.update { it.copy(statusBanner = "Enable Accessibility Service first for screen capture") }
            return
        }
        viewModelScope.launch {
            val b64 = svc.captureScreenBase64Jpeg()
            if (b64.isNullOrBlank()) {
                _uiState.update { it.copy(statusBanner = "Screen capture failed or blocked") }
            } else {
                navigateTo(JarvisTab.HOME)
                _uiState.update { it.copy(aiActivityState = AiActivityState.THINKING, isThinking = true, lastQuery = question) }
                repository.sendMessage(question, attachedImageBase64 = b64)
                    .onSuccess { reply ->
                        _uiState.update {
                            it.copy(
                                isThinking = false,
                                aiActivityState = AiActivityState.STANDBY,
                                lastAiReply = reply.replyText,
                                lastModelUsed = "${reply.providerUsed} (${reply.usedModel})",
                                lastLatencyMs = reply.latencyMs
                            )
                        }
                    }
                    .onFailure { err ->
                        _uiState.update { it.copy(isThinking = false, aiActivityState = AiActivityState.STANDBY, statusBanner = err.message) }
                    }
            }
        }
    }

    fun confirmPendingNotifReply(activityContext: android.content.Context) {
        val pending = _uiState.value.pendingNotifReply ?: return
        val runAction = {
            val msg = NotificationInbox.sendDirectReply(appContext, pending.senderOrApp, pending.replyText)
            _uiState.update { it.copy(statusBanner = msg) }
        }
        if (preferences.settings.value.biometricSafetyLock) {
            BiometricSafetyGate.authenticate(activityContext, onSuccess = runAction, onError = { err ->
                _uiState.update { it.copy(statusBanner = err) }
            })
        } else runAction()
    }

    fun cancelPendingNotifReply() = NotificationInbox.clearPendingReply()

    fun saveFlashlightSchedule(name: String, hour24: Int, minute: Int, turnOn: Boolean) {
        viewModelScope.launch {
            val clean = name.trim().ifBlank { "Scheduled Flashlight" }
            val steps = """[{"tool":"toggle_flashlight","args":{"enabled":$turnOn}}]"""
            database.saveRoutine(
                VoiceRoutine(
                    name = clean,
                    triggerPhrase = clean.lowercase(),
                    actionsSummary = if (turnOn) "Turn flashlight ON daily" else "Turn flashlight OFF daily",
                    stepsJson = steps
                )
            )
            val status = runCatching { TimeAutomationScheduler.scheduleDailyRoutine(appContext, clean, hour24, minute) }
                .getOrElse { it.message ?: "Saved routine" }
            refreshSystemMetrics()
            _uiState.update { it.copy(statusBanner = status) }
        }
    }

    fun cancelDailySchedule(name: String) {
        val msg = runCatching { TimeAutomationScheduler.cancel(appContext, name) }.getOrElse { it.message ?: "Cancelled" }
        refreshSystemMetrics()
        _uiState.update { it.copy(statusBanner = msg) }
    }

    fun deleteRoutine(id: Long, name: String) {
        runCatching { TimeAutomationScheduler.cancel(appContext, name) }
        database.deleteRoutine(id)
        refreshSystemMetrics()
    }

    fun runRoutineByName(name: String) {
        viewModelScope.launch {
            val msg = actionExecutor.executeRoutineByName(name)
            refreshSystemMetrics()
            _uiState.update { it.copy(statusBanner = msg) }
        }
    }

    // ---- Self-Upgrade Center (Section 15) ----
    fun checkSignedUpdates(autoInstallIfNewer: Boolean = false) {
        viewModelScope.launch {
            val s = preferences.settings.value
            _uiState.update { it.copy(isCheckingUpdate = true, updateStatusMessage = "Checking verified release...") }
            val res = updater.checkLatestRelease(s.releaseRepoSlug, s.releaseAuthToken)
            res.onSuccess { info ->
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        availableRelease = info,
                        updateStatusMessage = if (info.isNewerThanInstalled) {
                            "Signed release ${info.tagName} available (${info.expectedSha256?.take(10) ?: "No hash"})"
                        } else {
                            "Already up to date (${BuildConfig.VERSION_NAME})"
                        }
                    )
                }
                if (autoInstallIfNewer && info.isNewerThanInstalled) downloadVerifyAndInstall()
            }.onFailure { err ->
                _uiState.update { it.copy(isCheckingUpdate = false, updateStatusMessage = err.message ?: "Release check failed") }
            }
            refreshDerivedMetrics()
        }
    }

    fun downloadVerifyAndInstall() {
        val rel = _uiState.value.availableRelease ?: return
        viewModelScope.launch {
            updater.downloadVerifyAndPromptInstall(rel) { progress ->
                _uiState.update { it.copy(updateStatusMessage = progress) }
            }.onSuccess { msg ->
                _uiState.update { it.copy(updateStatusMessage = msg, statusBanner = msg) }
            }.onFailure { err ->
                _uiState.update { it.copy(updateStatusMessage = err.message ?: "Update verification failed") }
            }
        }
    }

    fun refreshSystemMetrics() {
        val pm = appContext.getSystemService(PowerManager::class.java)
        val bm = appContext.getSystemService(BatteryManager::class.java)
        val am = appContext.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mem)

        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        val net = cm?.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val netType = when {
            !validated -> "Offline"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi Validated"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile 5G/4G Validated"
            else -> "Online Validated"
        }

        val notifListenerEnabled = Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners")
            ?.contains(appContext.packageName) == true

        val micPerm = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notifPerm = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val camPerm = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val contactPerm = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val phonePerm = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val calPerm = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

        _uiState.update {
            it.copy(
                batteryPercent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 100,
                isCharging = bm?.isCharging == true,
                mediaVolumePercent = actionExecutor.phoneControl.getMediaVolumePercent(),
                installedApps = actionExecutor.phoneControl.getInstalledLauncherApps(),
                ramFreeMb = mem.availMem / (1024L * 1024L),
                ramTotalMb = mem.totalMem / (1024L * 1024L),
                internetValidated = validated,
                networkLabel = netType,
                notificationAccessGranted = notifListenerEnabled,
                routines = database.getAllRoutines(),
                auditLogs = database.getRecentAuditLogs(15),
                batteryOptIgnored = pm?.isIgnoringBatteryOptimizations(appContext.packageName) == true,
                micPermGranted = micPerm,
                notifPermGranted = notifPerm,
                cameraPermGranted = camPerm,
                contactsPermGranted = contactPerm,
                phonePermGranted = phonePerm,
                calendarPermGranted = calPerm
            )
        }
        refreshDerivedMetrics()
    }

    private fun refreshDerivedMetrics() {
        val cur = _uiState.value
        val s = cur.settings
        val hasGem = effectiveGeminiKey().isNotBlank()
        val hasOai = effectiveOpenAiKey().isNotBlank()
        val hasSarvam = effectiveSarvamKey().isNotBlank()
        val catalog = ModelCatalogPolicy.evaluateCatalog(
            discoveredGemini = cur.discoveredGeminiModels,
            discoveredOpenAi = cur.discoveredOpenAiModels,
            hasGeminiKey = hasGem,
            hasOpenAiKey = hasOai
        )

        val locked = runCatching { appContext.getSystemService(KeyguardManager::class.java).isDeviceLocked }.getOrDefault(false)
        val voiceBench = VoiceEngineSelector.selectBest(
            hasInternet = cur.internetValidated,
            isLocked = locked,
            cloudConsentGranted = s.cloudAudioConsentGranted,
            hasSarvamKey = hasSarvam,
            hasGeminiKey = hasGem
        )

        val enabledSlots = cur.slots.filter { it.enabled && it.apiKey.isNotBlank() }
        val nowMs = System.currentTimeMillis()
        val healthySlots = enabledSlots.count { it.status == SlotStatus.VERIFIED || (it.status != SlotStatus.INVALID_AUTH && !it.isCoolingDown(nowMs)) }
        val armedSchedules = cur.routines.count { TimeAutomationScheduler.status(appContext, it.name) != null }
        val permsReadyCount = listOf(cur.micPermGranted, cur.notifPermGranted, cur.cameraPermGranted, cur.contactsPermGranted, cur.accessibilityConnected).count { it }
        val liveConnected = cur.liveDuplexState.isConnected || cur.openAiLiveState.isConnected

        val indicators = listOf(
            DashboardIndicator("1", "Active Provider", s.activeProvider.displayName, if (hasGem || hasOai) "Authenticated key present" else "Add key in Settings", hasGem || hasOai, JarvisTab.AI_BRAIN),
            DashboardIndicator("2", "Brain Model", s.activeChatModel(), cur.lastModelUsed.ifBlank { "Click Discover to verify" }, true, JarvisTab.AI_BRAIN),
            DashboardIndicator("3", "Discovered Catalog", "${cur.discoveredGeminiModels.size + cur.discoveredOpenAiModels.size} live", "${cur.discoveredGeminiModels.size} Gemini • ${cur.discoveredOpenAiModels.size} OpenAI", cur.discoveredGeminiModels.isNotEmpty() || cur.discoveredOpenAiModels.isNotEmpty(), JarvisTab.AI_BRAIN),
            DashboardIndicator("4", "Free Model Priority", if (s.freeModelPriority) "Free First" else "Balanced", if (s.allowPaidModels) "Paid tier authorized" else "Paid blocked without consent", true, JarvisTab.SETTINGS, 2),
            DashboardIndicator("5", "API Multi-Slots", "$healthySlots/${enabledSlots.size} Ready", "Automatic 401/429 slot failover", healthySlots > 0, JarvisTab.SETTINGS, 0),
            DashboardIndicator("6", "Master Intelligence", "${s.intelligenceMode.label} Mode", cur.lastDomainLabel, true, JarvisTab.AI_BRAIN),
            DashboardIndicator("7", "Live Internet Search", if (s.webSearchGrounding) "Grounding ON" else "Grounding OFF", if (s.deepResearchEnabled) "Deep Research Ready" else "Standard Search", s.webSearchGrounding, JarvisTab.AI_BRAIN),
            DashboardIndicator("8", "Streaming Voice STT", voiceBench.selectedEngine.label, if (voiceBench.sarvamLatencyMs > 0L) "Saaras V4 ${voiceBench.sarvamLatencyMs}ms" else voiceBench.reason, voiceBench.selectedEngine != StreamingVoiceEngineKind.LOCAL_KEYWORD_WAKE || !cur.internetValidated, JarvisTab.VOICE),
            DashboardIndicator("9", "Wake Engine (FGS)", if (s.wakeWordEnabled) "Enabled" else "Off", cur.bgServiceStatus, s.wakeWordEnabled, JarvisTab.VOICE),
            DashboardIndicator("10", "Yes Boss Ack Latency", if (PreloadedAckPlayer.lastMeasuredAckMs > 0L) "${PreloadedAckPlayer.lastMeasuredAckMs}ms" else "Tap Test Ack", if (PreloadedAckPlayer.usingCachedSarvamAudio) "Preloaded Sarvam cache" else "Instant local tone", PreloadedAckPlayer.lastMeasuredAckMs in 1L..500L, JarvisTab.VOICE),
            DashboardIndicator("11", "Sarvam Telugu Voice", if (hasSarvam) s.sarvamSpeaker else "Key Required", cur.ttsStatusNote, hasSarvam, JarvisTab.VOICE),
            DashboardIndicator("12", "Live Duplex Audio", if (liveConnected) "Connected" else "Standby", if (cur.liveDuplexState.isConnected) cur.liveDuplexState.activeModel else s.geminiLiveVoiceModel, liveConnected || hasGem, JarvisTab.VOICE),
            DashboardIndicator("13", "Internet Health", cur.networkLabel, if (cur.internetValidated) "Ready for live research" else "Offline fallback mode", cur.internetValidated, JarvisTab.SETTINGS, 2),
            DashboardIndicator("14", "Battery Telemetry", "${cur.batteryPercent}%", if (cur.isCharging) "Charging now" else "Discharging", cur.batteryPercent > 20 || cur.isCharging, JarvisTab.CONTROL),
            DashboardIndicator("15", "Memory Available", "${cur.ramFreeMb} MB free", "Total ${cur.ramTotalMb} MB RAM", cur.ramFreeMb > 350L, JarvisTab.SETTINGS, 3),
            DashboardIndicator("16", "Accessibility Control", if (cur.accessibilityConnected) "Connected" else "Disabled", "Cross-app taps & screen vision", cur.accessibilityConnected, JarvisTab.CONTROL),
            DashboardIndicator("17", "Smart Notifications", if (cur.notificationAccessGranted) "${cur.unreadNotifCount} cached" else "Permission Needed", if (cur.notificationAccessGranted) "Reader active" else "Tap to grant listener", cur.notificationAccessGranted, JarvisTab.CONTROL),
            DashboardIndicator("18", "Scheduled Automation", "$armedSchedules armed", "${cur.routines.size} saved routines", cur.routines.isNotEmpty(), JarvisTab.CONTROL),
            DashboardIndicator("19", "Permissions Health", "$permsReadyCount/5 Ready", if (cur.micPermGranted) "Mic granted" else "Mic permission needed", cur.micPermGranted && cur.notifPermGranted, JarvisTab.SETTINGS, 4),
            DashboardIndicator("20", "Self-Upgrade Build", BuildConfig.VERSION_NAME, cur.availableRelease?.let { if (it.isNewerThanInstalled) "Update ${it.tagName} ready" else "Signed v${BuildConfig.VERSION_CODE}" } ?: "Build #${BuildConfig.VERSION_CODE}", true, JarvisTab.SETTINGS, 6)
        )

        _uiState.update {
            it.copy(
                catalogStatuses = catalog,
                voiceBenchmark = voiceBench,
                measuredAckLatencyMs = PreloadedAckPlayer.lastMeasuredAckMs,
                ackUsingSarvamCache = PreloadedAckPlayer.usingCachedSarvamAudio,
                indicators = indicators
            )
        }
    }

    override fun onCleared() {
        voiceInputController.release()
        ttsController.release()
        geminiLiveEngine.release()
        openAiRealtime.release()
        preferences.close()
        database.close()
        super.onCleared()
    }
}
