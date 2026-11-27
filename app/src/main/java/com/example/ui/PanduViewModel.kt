package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ConnectionDiagnosticResult
import com.example.ai.GeminiLiveDuplexEngine
import com.example.ai.LiveDuplexState
import com.example.ai.PanduAiRepository
import com.example.autonomous.AutonomousUpgradeOrchestrator
import com.example.autonomous.AutonomousUpgradeState
import com.example.autonomous.GitHubEngineeringClient
import com.example.autonomous.ReleaseUpdateInfo
import com.example.autonomous.SecureSelfUpdater
import com.example.autonomous.SelfCodingEngine
import com.example.autonomous.SystemDiagnosticsEngine
import com.example.autonomous.SystemHealthReport
import com.example.control.AgentHudBus
import com.example.control.AgentHudState
import com.example.control.DeviceActionExecutor
import com.example.control.HapticFeedbackHelper
import com.example.control.NotificationInbox
import com.example.control.PendingNotificationReply
import com.example.control.VisionHelper
import com.example.data.AiProvider
import com.example.data.ApiQuotaTracker
import com.example.data.ApiTelemetrySnapshot
import com.example.data.AppDatabase
import com.example.data.AuditLogEntry
import com.example.data.ChatMessageEntity
import com.example.data.LocationReminder
import com.example.data.MemoryFact
import com.example.data.OmegaPreferences
import com.example.data.OmegaSettings
import com.example.data.VoiceRoutine
import com.example.omega.OmegaWakeService
import com.example.omega.SmartTriggerBus
import com.example.omega.SmartTriggerManager
import com.example.omega.TimeAutomationScheduler
import com.example.security.CrashRecoveryHandler
import com.example.voice.BluetoothAudioRouter
import com.example.voice.TeluguTtsController
import com.example.voice.VoiceInputController
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OmegaScreen { HOME, LISTENING, CHAT, ROUTINES, AUTONOMOUS_LAB, CONTROL, SETTINGS }

data class PanduUiState(
    val currentScreen: OmegaScreen = OmegaScreen.HOME,
    val navigationBackStack: List<OmegaScreen> = emptyList(),
    val voiceSession: Boolean = false,
    val initialSettingsPage: Int = -1,
    val settings: OmegaSettings = OmegaSettings(),
    val messages: List<ChatMessageEntity> = emptyList(),
    val memories: List<MemoryFact> = emptyList(),
    val routines: List<VoiceRoutine> = emptyList(),
    val locationReminders: List<LocationReminder> = emptyList(),
    val auditLogs: List<AuditLogEntry> = emptyList(),
    val healthReport: SystemHealthReport? = null,
    val upgradeState: AutonomousUpgradeState = AutonomousUpgradeState(),
    val connectionHealth: com.example.ai.ConnectionHealthSnapshot = com.example.ai.ConnectionHealthSnapshot(),
    val controlEnv: com.example.autonomous.ControlCenterEnvironmentStatus = com.example.autonomous.ControlCenterEnvironmentStatus(),
    val recentBuilds: List<com.example.autonomous.WorkflowRunStatus> = emptyList(),
    val rollbackSnapshots: List<com.example.autonomous.RollbackSnapshot> = emptyList(),
    val controlCenterStatus: String? = null,
    val lastBrainRouteSummary: String = "Master AI Brain Ready",
    val activeBrainLabel: String = "Gemini Direct Brain",
    val activePipelineStage: com.example.ai.BrainPipelineStage = com.example.ai.BrainPipelineStage.RESPOND,
    val lastVerificationBadge: String = "Key Required",
    val brainInventory: List<com.example.ai.BrainStatusSnapshot> = emptyList(),
    val apiSlots: List<com.example.ai.SmartApiSlot> = emptyList(),
    val slotRoutingMode: com.example.ai.SlotRoutingMode = com.example.ai.SlotRoutingMode.FREE_FIRST,
    val slotTestResults: Map<String, com.example.ai.SlotTestOutcome> = emptyMap(),
    val isTestingAllSlots: Boolean = false,
    val showVoiceOverlay: Boolean = false,
    val proactiveSuggestions: List<com.example.autonomous.ProactiveSuggestion> = emptyList(),
    val routineSuggestions: List<com.example.autonomous.RoutineSuggestion> = emptyList(),
    val conditionalRules: List<com.example.autonomous.ConditionalAutomationRule> = listOf(
        com.example.autonomous.ConditionalAutomationRule("cond_low_bat", com.example.autonomous.AutomationConditionType.BATTERY_BELOW_15, "Lower volume & turn off flashlight", true),
        com.example.autonomous.ConditionalAutomationRule("cond_bt", com.example.autonomous.AutomationConditionType.BLUETOOTH_CONNECTED, "Announce unread notifications", true),
        com.example.autonomous.ConditionalAutomationRule("cond_charge", com.example.autonomous.AutomationConditionType.CHARGER_CONNECTED, "Announce live battery status", true),
        com.example.autonomous.ConditionalAutomationRule("cond_notifs", com.example.autonomous.AutomationConditionType.UNREAD_NOTIFS_HIGH, "Offer notification digest", true)
    ),
    val signingCertSha256: String? = null,
    val pendingVoiceUpgrade: com.example.autonomous.VoiceUpgradeProposal? = null,
    val handoffBundle: com.example.autonomous.AntigravityHandoffBundle? = null,
    val isCheckingUpdate: Boolean = false,
    val availableRelease: ReleaseUpdateInfo? = null,
    val updateStatusMessage: String? = null,
    val pendingNotifReply: PendingNotificationReply? = null,
    val unreadNotifCount: Int = 0,
    val lastTriggerBanner: String? = null,
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val liveDuplexState: LiveDuplexState = LiveDuplexState(),
    val liveToolStatus: String? = null,
    val streamingReply: String = "",
    val liveTranscript: String = "",
    val voiceDiagNote: String = "Voice Ready",
    val ttsStatusNote: String = "TTS Ready",
    val ttsVoiceChoices: List<com.example.voice.TtsVoiceChoice> = emptyList(),
    val lastAiReply: String = "",
    val lastLatencyMs: Long = 0L,
    val batteryPercent: Int = 100,
    val audioAmplitude: Float = 0f,
    val voiceError: String? = null,
    val isTestingConnection: Boolean = false,
    val diagnosticResult: ConnectionDiagnosticResult? = null,
    val isDiscoveringModels: Boolean = false,
    val discoveredChatModels: List<String> = emptyList(),
    val discoveredLiveModels: List<String> = emptyList(),
    val discoveredTtsModels: List<String> = emptyList(),
    val discoveryStatus: String? = null,
    val apiTelemetry: ApiTelemetrySnapshot = ApiTelemetrySnapshot(),
    val crashLogText: String = "",
    val backupStatusText: String? = null,
    val pendingWidgetCameraLaunch: Boolean = false
)

class PanduViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = OmegaPreferences(appContext)
    private val database = AppDatabase(appContext)
    private val actionExecutor = DeviceActionExecutor(appContext, database, preferences)
    private val haptics = HapticFeedbackHelper(appContext)
    private val smartTriggers = SmartTriggerManager(appContext)
    private val btRouter = BluetoothAudioRouter(appContext)
    private val diagnosticsEngine = SystemDiagnosticsEngine(appContext)

    private val aiRepository = PanduAiRepository(
        settingsProvider = { preferences.settings.value },
        actionExecutor = actionExecutor,
        database = database
    )

    private val githubClient = GitHubEngineeringClient()
    private val selfCodingEngine = SelfCodingEngine(aiRepository)
    private val upgradeOrchestrator = AutonomousUpgradeOrchestrator(preferences, database, selfCodingEngine, githubClient)
    private val secureUpdater = SecureSelfUpdater(appContext, preferences)

    private val liveDuplex = GeminiLiveDuplexEngine(
        context = appContext,
        apiKeyProvider = { preferences.settings.value.effectiveGeminiKey() },
        liveModelProvider = { preferences.settings.value.geminiLiveVoiceModel },
        liveSpeakerProvider = { preferences.settings.value.geminiLiveSpeaker },
        bluetoothRoutingEnabledProvider = { preferences.settings.value.bluetoothVoiceRouting },
        allowedProvider = { !preferences.settings.value.micPrivacyMuted && preferences.settings.value.effectiveGeminiKey().isNotBlank() && !preferences.settings.value.preferOfflineSpeech && !appContext.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked }
    )

    private val ttsController = TeluguTtsController(appContext, cloudSettings = { preferences.settings.value })
    private var activeJob: Job? = null
    private var followUpJob: Job? = null
    private var emergencyStopped = false
    private var liveFallbackArmed = false

    private val _uiState = MutableStateFlow(
        PanduUiState(
            settings = preferences.settings.value,
            lastVerificationBadge = if (preferences.settings.value.effectiveGeminiKey().isNotBlank() || preferences.settings.value.effectiveOpenRouterKey().isNotBlank()) "Not Verified" else "Key Required",
            batteryPercent = actionExecutor.getBatteryPercent(),
            crashLogText = CrashRecoveryHandler.readLogs(appContext),
            signingCertSha256 = com.example.security.VerifiedApkUpdater.installedSigningCertSha256(appContext)
        )
    )
    val uiState: StateFlow<PanduUiState> = _uiState.asStateFlow()

    private var wakeAckPending = false
    private var wakeAckGeneration = 0L
    private val voiceInput = VoiceInputController(appContext) { finalTranscript ->
        handlePotentialAutonomousVoiceCommand(finalTranscript)
    }

    init {
        val slotEngine = com.example.ai.SmartApiSlotManager.init(appContext, preferences.settings.value)
        aiRepository.seedHistoryFromDatabase()
        smartTriggers.register()
        refreshDbState()
        runSystemDiagnostics()

        viewModelScope.launch {
            com.example.ai.SmartApiSlotManager.slotsFlow().collect { slots ->
                _uiState.update { cur ->
                    val hasVerifiedSlot = slots.any { it.enabled && it.apiKey.isNotBlank() && it.chatStatus == com.example.ai.SlotConnectionState.VERIFIED }
                    val hasAnyKey = slots.any { it.enabled && it.apiKey.isNotBlank() }
                    val nextBadge = when {
                        hasVerifiedSlot -> "Verified"
                        !hasAnyKey -> "Key Required"
                        cur.lastVerificationBadge == "Verified" -> "Verified"
                        else -> "Not Verified"
                    }
                    cur.copy(apiSlots = slots, lastVerificationBadge = nextBadge)
                }
            }
        }
        viewModelScope.launch {
            com.example.ai.SmartApiSlotManager.routingModeFlow().collect { mode ->
                _uiState.update { it.copy(slotRoutingMode = mode) }
            }
        }

        ttsController.setOnSpeechFinished {
            if (emergencyStopped) return@setOnSpeechFinished
            if(wakeAckPending) {
                wakeAckPending=false; ++wakeAckGeneration
                if(AgentHudBus.isAppInForeground.value && !_uiState.value.settings.micPrivacyMuted) {
                    startVoiceListening(preserveScreen = true)
                }
                return@setOnSpeechFinished
            }
            if (_uiState.value.settings.continuousConversation &&
                _uiState.value.voiceSession &&
                !_uiState.value.liveDuplexState.isConnected && !_uiState.value.liveDuplexState.isConnecting &&
                !_uiState.value.settings.micPrivacyMuted
            ) {
                followUpJob?.cancel()
                followUpJob = viewModelScope.launch {
                    delay(380L)
                    if (emergencyStopped) return@launch
                    if (_uiState.value.voiceSession && !_uiState.value.isThinking && !_uiState.value.settings.micPrivacyMuted && AgentHudBus.isAppInForeground.value && !AgentHudBus.isMainMicBusy.value) {
                        voiceInput.startListening(_uiState.value.settings.languageTag, preferOffline = _uiState.value.settings.preferOfflineSpeech, bluetoothEnabled = _uiState.value.settings.bluetoothVoiceRouting)
                    }
                }
            } else {
                _uiState.update { it.copy(showVoiceOverlay = false) }
            }
        }

        if (_uiState.value.messages.isEmpty() && !database.unreadableEncryptedRows) {
            database.insertMessage(
                ChatMessageEntity(
                    text = "Welcome to JARVIS INFINITY. Open Diagnostics to test your engines and configure voice and AI in Settings.",
                    isFromUser = false
                )
            )
            refreshDbState()
        }

        viewModelScope.launch {
            autoConnectAndRecover()
        }
        viewModelScope.launch {
            preferences.settings.collect { s ->
                if (s.preferOfflineSpeech && !_uiState.value.settings.preferOfflineSpeech) {
                    voiceInput.stopListening()
                }
                if(s.micPrivacyMuted) { liveFallbackArmed=false; voiceInput.stopListening(); liveDuplex.disconnect(); ttsController.stop() }
                else if (s.preferOfflineSpeech || s.effectiveGeminiKey().isBlank() || !s.useGeminiLiveAudio) { liveFallbackArmed=false; if(liveDuplex.state.value.isConnected || liveDuplex.state.value.isConnecting) liveDuplex.disconnect() }
                ttsController.configure(s.languageTag, s.ttsSpeed, s.ttsPitch, s.ttsStyle)
                val configChanged = !sameApiConfig(itSettingsSnapshot(), s)
                _uiState.update { if (!configChanged) it.copy(settings = s) else it.copy(settings = s, diagnosticResult = null, discoveredChatModels = emptyList(), discoveredLiveModels = emptyList(), discoveredTtsModels = emptyList(), discoveryStatus = null) }
                if (configChanged) autoConnectAndRecover()
            }
        }
        viewModelScope.launch { upgradeOrchestrator.state.collect { st -> _uiState.update { it.copy(upgradeState = st) }; refreshDbState() } }
        viewModelScope.launch { NotificationInbox.items.collect { list -> _uiState.update { it.copy(unreadNotifCount = list.size) } } }
        viewModelScope.launch { NotificationInbox.pendingReply.collect { p -> _uiState.update { it.copy(pendingNotifReply = p) } } }
        viewModelScope.launch { ApiQuotaTracker.snapshot.collect { snap -> _uiState.update { it.copy(apiTelemetry = snap) } } }
        viewModelScope.launch { ttsController.voices.collect { choices -> _uiState.update { it.copy(ttsVoiceChoices=choices) } } }
        viewModelScope.launch { ttsController.ttsStatus.collect { st -> _uiState.update { it.copy(ttsStatusNote = st) } } }
        viewModelScope.launch {
            SmartTriggerBus.events.collect { ev ->
                _uiState.update { it.copy(lastTriggerBanner = "${ev.title}: ${ev.announcement}") }
                if (_uiState.value.settings.autoSpeak && AgentHudBus.isAppInForeground.value && !AgentHudBus.isMainMicBusy.value) ttsController.speak(ev.announcement)
            }
        }
        viewModelScope.launch {
            voiceInput.state.collect { v ->
                _uiState.update { cur ->
                    cur.copy(
                        isListening = v.isListening,
                        liveTranscript = if (v.partialTranscript.isNotBlank()) v.partialTranscript else cur.liveTranscript,
                        audioAmplitude = v.audioAmplitude,
                        voiceError = v.errorMessage,
                        voiceDiagNote = v.diagnosticsNote
                    )
                }
                if (v.isListening) {
                    AgentHudBus.updateState(AgentHudState.Active(v.partialTranscript.ifBlank { "Listening..." }, audioAmplitude = v.audioAmplitude))
                } else if (!_uiState.value.isThinking && !_uiState.value.liveDuplexState.isConnected) {
                    AgentHudBus.updateState(AgentHudState.Hidden)
                }
            }
        }
        viewModelScope.launch {
            liveDuplex.state.collect { ls ->
                _uiState.update { it.copy(liveDuplexState = ls) }
                if (ls.isConnected) AgentHudBus.updateState(AgentHudState.Active(ls.statusText, audioAmplitude = ls.amplitude))
                if(ls.error != null && liveFallbackArmed && AgentHudBus.isAppInForeground.value && _uiState.value.voiceSession && !_uiState.value.settings.micPrivacyMuted) {
                    liveFallbackArmed=false; liveDuplex.disconnect(); _uiState.update { it.copy(voiceError="Live unavailable; switching to speech recognition") }; voiceInput.startListening(_uiState.value.settings.languageTag, preferOffline = _uiState.value.settings.preferOfflineSpeech, bluetoothEnabled = _uiState.value.settings.bluetoothVoiceRouting)
                }
            }
        }
        viewModelScope.launch { ttsController.isSpeaking.collect { sp -> _uiState.update { it.copy(isSpeaking = sp) } } }
        viewModelScope.launch { OmegaWakeService.status.collect { st -> if(!AgentHudBus.isMainMicBusy.value || OmegaWakeService.isBackgroundConversationActive()) _uiState.update { it.copy(voiceDiagNote=st) } } }
        viewModelScope.launch { AgentHudBus.abortSignal.collect { stopAllActivity() } }
        viewModelScope.launch {
            AgentHudBus.wakeTriggers.collect { cmd ->
                if (!AgentHudBus.isAppInForeground.value || _uiState.value.settings.micPrivacyMuted) return@collect
                emergencyStopped = false
                if (cmd.isNotBlank()) handleInlineWakeCommand(cmd) else if(_uiState.value.settings.autoSpeak) {
                    wakeAckPending=true; val token=++wakeAckGeneration
                    val s = _uiState.value.settings
                    val ackText = s.customWakeAck.trim().ifEmpty { com.example.voice.TeluguVoiceReplies.wakeAck(s.languageTag) }
                    _uiState.update { it.copy(showVoiceOverlay = it.currentScreen != OmegaScreen.HOME) }
                    ttsController.configure(
                        com.example.voice.TeluguVoiceReplies.wakeAckLocale(s.languageTag),
                        s.ttsSpeed,
                        s.ttsPitch,
                        s.ttsStyle,
                        localOnly = true
                    )
                    ttsController.speak(ackText)
                    viewModelScope.launch {
                        delay(4000)
                        if(wakeAckPending && token==wakeAckGeneration) {
                            wakeAckPending=false; ttsController.stop()
                            if(AgentHudBus.isAppInForeground.value && !_uiState.value.settings.micPrivacyMuted) startVoiceListening(preserveScreen = true)
                        }
                    }
                } else startVoiceListening(preserveScreen = true)
            }
        }
    }

    private fun refreshDbState() {
        val s = preferences.settings.value
        val messages = database.getRecentMessages(80)
        val memories = database.getAllMemories()
        val routines = database.getAllRoutines()
        val battery = actionExecutor.getBatteryPercent()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        _uiState.update {
            it.copy(
                messages = messages,
                memories = memories,
                routines = routines,
                locationReminders = database.getLocationReminders(),
                auditLogs = database.getRecentAuditLogs(25),
                batteryPercent = battery,
                brainInventory = aiRepository.getBrainInventory(),
                proactiveSuggestions = com.example.autonomous.PersonalAssistantEngine.proactiveSuggestions(
                    enabled = s.proactiveSuggestionsEnabled,
                    batteryPercent = battery,
                    unreadNotifCount = it.unreadNotifCount,
                    hourOfDay = hour,
                    memoriesCount = memories.size
                ),
                routineSuggestions = com.example.autonomous.PersonalAssistantEngine.suggestRoutinesFromHistory(
                    learningEnabled = s.personalLearningEnabled,
                    messages = messages,
                    existingRoutines = routines
                ),
                voiceError = if (database.unreadableEncryptedRows) "Some encrypted records could not be read. Original data retained; restore a trusted backup." else it.voiceError
            )
        }
    }

    private fun handlePotentialAutonomousVoiceCommand(spoken: String) {
        if(com.example.voice.BackgroundVoicePolicy.classify(spoken)==com.example.voice.BackgroundVoicePolicy.Action.STOP) { stopAllActivity(); return }
        val q = spoken.lowercase()
        when {
            q.contains("system health") || q.contains("diagnostics") || q.contains("health report") -> {
                runSystemDiagnostics()
                val score = _uiState.value.healthReport?.score
                val reply = if (score != null) "Diagnostics complete. Heuristic health score: $score out of 100." else "Diagnostics report unavailable."
                database.insertMessage(ChatMessageEntity(text = spoken, isFromUser = true, isVoiceInput = true))
                database.insertMessage(ChatMessageEntity(text = reply, isFromUser = false, executedTools = listOf("self_diagnostics")))
                refreshDbState()
                _uiState.update { it.copy(lastAiReply = reply) }
                if (_uiState.value.settings.autoSpeak) ttsController.speak(reply)
            }
            q.contains("check for update") || q.contains("self update") -> {
                _uiState.update { it.copy(currentScreen = OmegaScreen.AUTONOMOUS_LAB) }
                checkReleaseUpdate()
            }
            else -> dispatchToAi(spoken, isVoiceInput = true)
        }
    }

    fun runSystemDiagnostics() {
        val report = diagnosticsEngine.runFullDiagnostics(_uiState.value.settings, _uiState.value.ttsStatusNote, _uiState.value.voiceDiagNote)
        runCatching { database.logAudit("DIAGNOSTICS", "Health score ${report.score}/100 (${report.issues.size} items)", "OK") }.onFailure { _uiState.update { u -> u.copy(voiceError="Diagnostics audit not saved; check encrypted storage") } }
        _uiState.update { it.copy(healthReport = report) }
        refreshDbState()
    }

    private fun itSettingsSnapshot(): OmegaSettings = _uiState.value.settings

    fun autoConnectAndRecover() {
        viewModelScope.launch {
            val s = preferences.settings.value
            val hasGemini = s.effectiveGeminiKey().isNotBlank()
            val hasGateway = s.effectiveOpenRouterKey().isNotBlank()
            val providerLabel = com.example.ai.ConnectionRecoveryManager.initialProviderLabel(s.activeProvider, hasGemini, hasGateway)
            val hasAnySlotKey = com.example.ai.SmartApiSlotManager.engineOrNull()?.hasAnyConfiguredKey() == true
            if (!hasGemini && !hasGateway && !hasAnySlotKey) {
                _uiState.update {
                    it.copy(
                        lastVerificationBadge = "Key Required",
                        connectionHealth = com.example.ai.ConnectionHealthSnapshot(
                            status = com.example.ai.ConnectionStatus.DISCONNECTED,
                            providerLabel = providerLabel,
                            activeChatModel = s.activeChatModel(),
                            detail = "Configure Gemini or Smart API Slot key in Settings"
                        )
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    connectionHealth = it.connectionHealth.copy(
                        status = com.example.ai.ConnectionStatus.CONNECTING,
                        providerLabel = providerLabel,
                        activeChatModel = s.activeChatModel(),
                        detail = "Auto-connecting & verifying models..."
                    )
                )
            }

            val start = System.currentTimeMillis()
            val res = com.example.ai.ConnectionRecoveryManager.runWithAutoRecovery(
                maxAttempts = 3,
                onRecovering = { attempt, waitMs ->
                    _uiState.update {
                        it.copy(
                            connectionHealth = it.connectionHealth.copy(
                                status = com.example.ai.ConnectionStatus.RECOVERING,
                                recoveryAttempt = attempt,
                                detail = "Transient error; recovering in ${waitMs / 1000}s (attempt $attempt/3)"
                            )
                        )
                    }
                }
            ) {
                aiRepository.discoverModelsForActiveProvider()
            }

            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            res.onSuccess { found ->
                if (found.ttsModels.isNotEmpty()) {
                    com.example.voice.GeminiSpeechPreferences.saveDiscoveredModels(appContext, found.ttsModels)
                }
                val codingModel = found.chatModels.firstOrNull { !it.contains("tts", ignoreCase = true) } ?: s.geminiChatModel
                val ttsModel = found.ttsModels.firstOrNull() ?: com.example.voice.GeminiSpeechPreferences.lastActiveModel(appContext)
                _uiState.update {
                    it.copy(
                        discoveredChatModels = found.chatModels,
                        discoveredLiveModels = found.liveVoiceModels,
                        discoveredTtsModels = found.ttsModels,
                        discoveryStatus = found.message,
                        lastVerificationBadge = "Verified",
                        brainInventory = aiRepository.getBrainInventory(),
                        connectionHealth = com.example.ai.ConnectionHealthSnapshot(
                            status = com.example.ai.ConnectionStatus.CONNECTED,
                            providerLabel = providerLabel,
                            activeChatModel = s.activeChatModel(),
                            activeCodingModel = codingModel,
                            activeTtsModel = ttsModel,
                            quotaState = "OK",
                            latencyMs = latency,
                            recoveryAttempt = 0,
                            detail = "Connected • ${found.chatModels.size} chat, ${found.ttsModels.size} TTS models"
                        )
                    )
                }
            }.onFailure { err ->
                val msg = err.message.orEmpty()
                val isQuota = com.example.ai.ConnectionRecoveryManager.isQuotaError(msg)
                _uiState.update {
                    it.copy(
                        lastVerificationBadge = if (isQuota) "Quota Limited" else "Error",
                        connectionHealth = com.example.ai.ConnectionHealthSnapshot(
                            status = if (isQuota) com.example.ai.ConnectionStatus.QUOTA_LIMITED else com.example.ai.ConnectionStatus.DISCONNECTED,
                            providerLabel = providerLabel,
                            activeChatModel = s.activeChatModel(),
                            quotaState = if (isQuota) "429 Cooldown" else "Check Connection",
                            latencyMs = latency,
                            detail = msg.ifBlank { "Auto-connect failed" }
                        )
                    )
                }
            }
        }
    }

    fun verifyGitHubConnection(repoSlug: String, baseBranch: String, token: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(controlCenterStatus = "Verifying GitHub repo & branch...") }
            val res = githubClient.verifyConnection(repoSlug, baseBranch, token)
            _uiState.update {
                it.copy(controlCenterStatus = res.getOrElse { e -> "GitHub verification failed: ${e.message}" })
            }
            if (res.isSuccess) refreshGitHubBuilds()
        }
    }

    fun startSelfUpgrade(featureRequest: String, targetFilePath: String) {
        upgradeOrchestrator.startSelfUpgrade(featureRequest, targetFilePath)
    }

    fun confirmVoiceUpgrade() {
        val proposal = _uiState.value.pendingVoiceUpgrade ?: return
        _uiState.update { it.copy(pendingVoiceUpgrade = null, currentScreen = OmegaScreen.AUTONOMOUS_LAB) }
        upgradeOrchestrator.startSelfUpgrade(proposal.featureRequest, proposal.suggestedTargetPath)
    }

    fun dismissVoiceUpgrade() {
        _uiState.update { it.copy(pendingVoiceUpgrade = null) }
    }

    fun analyzeLastCrashForFix() {
        val logs = CrashRecoveryHandler.readLogs(appContext)
        val proposal = com.example.autonomous.CrashBugFixPlanner.buildFixProposal(logs)
        if (proposal != null) {
            _uiState.update { it.copy(currentScreen = OmegaScreen.AUTONOMOUS_LAB) }
            upgradeOrchestrator.startSelfUpgrade(proposal.featureRequest, proposal.suggestedTargetPath)
        } else {
            _uiState.update { it.copy(updateStatusMessage = "No recorded crashes found to analyze.") }
        }
    }

    fun generateAntigravityHandoff(
        featureRequest: String,
        targetFilePath: String,
        preferredModel: String = "gemini-4-hydrogen-medium",
        thinkingEffort: String = "medium"
    ) {
        val res = com.example.autonomous.AntigravityTaskHandoff.buildHandoff(
            featureRequest,
            targetFilePath,
            preferredModel = preferredModel,
            thinkingEffort = thinkingEffort
        )
        res.onSuccess { bundle ->
            _uiState.update { it.copy(handoffBundle = bundle, updateStatusMessage = "Manual Antigravity CLI Handoff ready ($preferredModel • $thinkingEffort • useG1Credits:false).") }
        }.onFailure { err ->
            _uiState.update { it.copy(updateStatusMessage = err.message ?: "Unable to build Antigravity CLI Handoff.") }
        }
    }

    fun autoDownloadAndVerifyUpdate() {
        val s = preferences.settings.value
        if (s.githubRepoSlug.isBlank()) {
            _uiState.update { it.copy(updateStatusMessage = "Configure GitHub Repo (owner/repo) first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true, updateStatusMessage = "Checking latest verified release...") }
            val check = secureUpdater.checkLatestRelease(s.githubRepoSlug, s.githubToken)
            check.onSuccess { rel ->
                _uiState.update { it.copy(isCheckingUpdate = false, availableRelease = rel) }
                if (!rel.isNewerThanInstalled) {
                    _uiState.update { it.copy(updateStatusMessage = "Already on latest version (${rel.tagName}).") }
                    return@onSuccess
                }
                if (!com.example.autonomous.UpdateTransferPolicy.validHash(rel.expectedSha256)) {
                    _uiState.update { it.copy(updateStatusMessage = "Update blocked: release ${rel.tagName} missing mandatory SHA-256.") }
                    return@onSuccess
                }
                prepareRollbackSnapshot()
                approveAndInstallRelease()
            }.onFailure { err ->
                _uiState.update { it.copy(isCheckingUpdate = false, updateStatusMessage = "Update check failed: ${err.message}") }
            }
        }
    }

    fun refreshControlCenter() {
        val env = com.example.autonomous.TermuxControlHelper.inspectEnvironment(appContext)
        val snaps = com.example.autonomous.RollbackPreparationManager.listSnapshots(appContext)
        _uiState.update { it.copy(controlEnv = env, rollbackSnapshots = snaps) }
        refreshGitHubBuilds()
    }

    fun refreshGitHubBuilds() {
        val s = preferences.settings.value
        if (s.githubRepoSlug.isBlank() || s.githubToken.isBlank()) return
        viewModelScope.launch {
            githubClient.listRecentWorkflowRuns(s.githubRepoSlug, s.githubToken, limit = 5)
                .onSuccess { runs -> _uiState.update { it.copy(recentBuilds = runs) } }
                .onFailure { err -> _uiState.update { it.copy(controlCenterStatus = "GitHub build monitor: ${err.message}") } }
        }
    }

    fun launchTermuxApp() {
        val res = com.example.autonomous.TermuxControlHelper.launchTermux(appContext)
        _uiState.update { it.copy(controlCenterStatus = res.getOrElse { e -> e.message }) }
    }

    fun launchTermuxWithHandoff() {
        val cmd = _uiState.value.handoffBundle?.cliCommand.orEmpty()
        val res = com.example.autonomous.TermuxControlHelper.copyHandoffAndLaunchTermux(appContext, cmd)
        _uiState.update { it.copy(controlCenterStatus = res.getOrElse { e -> e.message }) }
    }

    fun openBatteryOptimization(packageName: String = appContext.packageName) {
        val res = com.example.autonomous.TermuxControlHelper.openBatterySettings(appContext, packageName)
        _uiState.update { it.copy(controlCenterStatus = res.getOrElse { e -> e.message }) }
    }

    fun openInstallPermissionSettings() {
        val res = com.example.autonomous.TermuxControlHelper.openInstallUnknownAppsSettings(appContext)
        _uiState.update { it.copy(controlCenterStatus = res.getOrElse { e -> e.message }) }
    }

    fun prepareRollbackSnapshot() {
        val res = com.example.autonomous.RollbackPreparationManager.prepareSnapshot(appContext, preferences)
        res.onSuccess { snap ->
            database.logAudit("ROLLBACK_PREP", "Prepared ${snap.versionName} (${snap.sha256.take(12)})", "VERIFIED")
            refreshDbState()
            _uiState.update {
                it.copy(
                    rollbackSnapshots = com.example.autonomous.RollbackPreparationManager.listSnapshots(appContext),
                    controlCenterStatus = "Prepared verified rollback snapshot ${snap.id}"
                )
            }
        }.onFailure { err ->
            _uiState.update { it.copy(controlCenterStatus = "Rollback prep failed: ${err.message}") }
        }
    }

    fun restoreRollbackSnapshot(snapshotId: String) {
        val res = com.example.autonomous.RollbackPreparationManager.restoreSnapshot(appContext, preferences, snapshotId)
        res.onSuccess { msg ->
            database.logAudit("ROLLBACK_RESTORE", msg, "SUCCESS")
            refreshDbState()
            _uiState.update { it.copy(controlCenterStatus = msg) }
        }.onFailure { err ->
            _uiState.update { it.copy(controlCenterStatus = "Restore failed: ${err.message}") }
        }
    }

    fun emergencyStopUpgrade() {
        upgradeOrchestrator.emergencyStop()
    }

    fun checkReleaseUpdate() {
        val s = _uiState.value.settings
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true, updateStatusMessage = "Checking GitHub Releases...") }
            val res = secureUpdater.checkLatestRelease(s.githubRepoSlug, s.githubToken)
            res.onSuccess { info ->
                database.logAudit("SELF_UPDATE", "Discovered release ${info.tagName}", "VERIFIED_META")
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        availableRelease = info,
                        updateStatusMessage = "Found ${info.tagName}. SHA-256 ${if (info.expectedSha256 != null) "present" else "missing"}."
                    )
                }
                refreshDbState()
            }.onFailure { err ->
                _uiState.update { it.copy(isCheckingUpdate = false, updateStatusMessage = "Update check failed: ${err.message}") }
            }
        }
    }

    fun approveAndInstallRelease() {
        val rel = _uiState.value.availableRelease ?: return
        viewModelScope.launch {
            val res = secureUpdater.downloadVerifyAndPromptInstall(rel) { step ->
                _uiState.update { it.copy(updateStatusMessage = step) }
            }
            res.onSuccess { msg ->
                database.logAudit("SELF_UPDATE", "Verified & launched installer for ${rel.tagName}", "APPROVED")
                _uiState.update { it.copy(updateStatusMessage = msg) }
                refreshDbState()
            }.onFailure { err ->
                database.logAudit("SELF_UPDATE", "Blocked: ${err.message}", "REJECTED")
                _uiState.update { it.copy(updateStatusMessage = "Blocked: ${err.message}") }
                refreshDbState()
            }
        }
    }

    fun refreshVoiceCatalog() { ttsController.refreshVoices() }
    fun previewVoice(locale: String) {
        if (AgentHudBus.isMainMicBusy.value || _uiState.value.liveDuplexState.isConnected || _uiState.value.liveDuplexState.isConnecting) return
        val s = preferences.settings.value
        ttsController.configure(locale,s.ttsSpeed,s.ttsPitch,s.ttsStyle)
        ttsController.speak(if(locale.startsWith("te")) "నమస్కారం బాస్! నేను మీ జార్విస్ ఇన్ఫినిటీ సహాయకుడిని." else "Hello Boss. I am your JARVIS INFINITY voice assistant.")
    }

    fun testSpeakerVoice() {
        haptics.softTap()
        ttsController.speak("Hello ${_uiState.value.settings.userName}! This is your JARVIS INFINITY voice test.")
    }

    fun unmuteMicAndListen() {
        preferences.update { it.copy(micPrivacyMuted = false) }
        startVoiceListening()
    }


    fun confirmPendingNotifReply() {
        val pending = _uiState.value.pendingNotifReply ?: return
        val result = NotificationInbox.sendDirectReply(appContext, pending.senderOrApp, pending.replyText)
        database.insertMessage(ChatMessageEntity(text = result, isFromUser = false, executedTools = listOf("reply_notification")))
        refreshDbState()
    }

    fun cancelPendingNotifReply() = NotificationInbox.clearPendingReply()
    fun dismissTriggerBanner() = _uiState.update { it.copy(lastTriggerBanner = null) }
    fun testSmartTrigger(type: String) { haptics.softTap(); smartTriggers.triggerManualTest(type) }
    fun requestCameraLaunchFromWidget() = _uiState.update { it.copy(pendingWidgetCameraLaunch = true) }
    fun consumeWidgetCameraLaunch() = _uiState.update { it.copy(pendingWidgetCameraLaunch = false) }

    fun navigateTo(screen: OmegaScreen, settingsPage: Int = -1) {
        haptics.softTap()
        val normalizedTarget = if (screen == OmegaScreen.LISTENING) OmegaScreen.HOME else screen
        val current = if (_uiState.value.currentScreen == OmegaScreen.LISTENING) OmegaScreen.HOME else _uiState.value.currentScreen
        if (normalizedTarget != OmegaScreen.HOME) {
            followUpJob?.cancel(); followUpJob = null
            liveFallbackArmed=false
            voiceInput.stopListening()
            btRouter.clearRouting()
            if (_uiState.value.liveDuplexState.isConnected || _uiState.value.liveDuplexState.isConnecting) liveDuplex.disconnect()
        }
        if (normalizedTarget == OmegaScreen.AUTONOMOUS_LAB || normalizedTarget == OmegaScreen.CONTROL) runSystemDiagnostics()
        _uiState.update { cur ->
            val updatedStack = if (normalizedTarget == current) cur.navigationBackStack
            else (cur.navigationBackStack + current).takeLast(16)
            cur.copy(
                currentScreen = normalizedTarget,
                navigationBackStack = updatedStack,
                initialSettingsPage = settingsPage,
                voiceSession = if (normalizedTarget == OmegaScreen.HOME) cur.voiceSession else false,
                voiceError = null,
                batteryPercent = actionExecutor.getBatteryPercent(),
                brainInventory = aiRepository.getBrainInventory(),
                crashLogText = if (normalizedTarget == OmegaScreen.SETTINGS) CrashRecoveryHandler.readLogs(appContext) else cur.crashLogText
            )
        }
    }

    fun navigateBack(): Boolean {
        haptics.softTap()
        val stack = _uiState.value.navigationBackStack
        if (stack.isNotEmpty()) {
            val prev = stack.last()
            val rest = stack.dropLast(1)
            if (prev != OmegaScreen.HOME) {
                followUpJob?.cancel(); followUpJob = null
                liveFallbackArmed = false
                voiceInput.stopListening()
                btRouter.clearRouting()
                if (_uiState.value.liveDuplexState.isConnected || _uiState.value.liveDuplexState.isConnecting) liveDuplex.disconnect()
            }
            _uiState.update {
                it.copy(
                    currentScreen = prev,
                    navigationBackStack = rest,
                    initialSettingsPage = -1,
                    voiceError = null,
                    batteryPercent = actionExecutor.getBatteryPercent()
                )
            }
            return true
        }
        if (_uiState.value.currentScreen != OmegaScreen.HOME) {
            _uiState.update { it.copy(currentScreen = OmegaScreen.HOME, initialSettingsPage = -1, voiceError = null) }
            return true
        }
        return false
    }

    private fun sameApiConfig(a: OmegaSettings, b: OmegaSettings): Boolean =
        a.activeProvider == b.activeProvider && a.gatewayBaseUrl == b.gatewayBaseUrl &&
            a.geminiApiKey == b.geminiApiKey && a.openRouterApiKey == b.openRouterApiKey &&
            a.geminiChatModel == b.geminiChatModel && a.geminiLiveVoiceModel == b.geminiLiveVoiceModel &&
            a.openRouterModel == b.openRouterModel && a.smartMode == b.smartMode &&
            a.providerBudgetUsd == b.providerBudgetUsd && a.manualVisionConfirmed == b.manualVisionConfirmed &&
            a.secondaryGatewayBaseUrl == b.secondaryGatewayBaseUrl && a.secondaryGatewayApiKey == b.secondaryGatewayApiKey &&
            a.secondaryGatewayModel == b.secondaryGatewayModel && a.zeroPaidCreditsMode == b.zeroPaidCreditsMode &&
            a.preferredBrainId == b.preferredBrainId && a.brainThinkingMode == b.brainThinkingMode

    fun updateSettings(transform: (OmegaSettings) -> OmegaSettings): Boolean {
        val old = preferences.settings.value
        try { preferences.update(transform) } catch (e: IllegalStateException) {
            _uiState.update { it.copy(discoveryStatus = e.message ?: "Settings not saved") }; return false
        }
        val next = preferences.settings.value
        com.example.ai.SmartApiSlotManager.engineOrNull()?.migrateFromLegacySettings(next)
        if (!sameApiConfig(old, next)) _uiState.update { it.copy(discoveredChatModels = emptyList(), discoveredLiveModels = emptyList(), discoveredTtsModels = emptyList(), discoveryStatus = null, diagnosticResult = null) }
        return true
    }

    fun upsertApiSlot(slot: com.example.ai.SmartApiSlot) {
        val eng = com.example.ai.SmartApiSlotManager.engineOrNull() ?: return
        val saved = eng.upsertSlot(slot)
        // Bidirectional sync if user edited legacy slot 1/2/3 via Slot Manager UI
        when (saved.legacySourceId) {
            "legacy_gemini" -> preferences.update { cur ->
                cur.copy(geminiApiKey = saved.apiKey, geminiChatModel = saved.chatModel.ifBlank { cur.geminiChatModel })
            }
            "legacy_gateway" -> preferences.update { cur ->
                cur.copy(
                    gatewayBaseUrl = saved.effectiveBaseUrl(),
                    openRouterApiKey = saved.apiKey,
                    openRouterModel = saved.chatModel.ifBlank { cur.openRouterModel }
                )
            }
            "legacy_secondary" -> preferences.update { cur ->
                cur.copy(
                    secondaryGatewayBaseUrl = saved.effectiveBaseUrl(),
                    secondaryGatewayApiKey = saved.apiKey,
                    secondaryGatewayModel = saved.chatModel
                )
            }
        }
    }

    fun toggleApiSlot(slotId: String, enabled: Boolean) {
        com.example.ai.SmartApiSlotManager.engineOrNull()?.setSlotEnabled(slotId, enabled)
    }

    fun deleteApiSlot(slotId: String) {
        com.example.ai.SmartApiSlotManager.engineOrNull()?.deleteSlot(slotId)
    }

    fun setSlotRoutingMode(mode: com.example.ai.SlotRoutingMode) {
        com.example.ai.SmartApiSlotManager.engineOrNull()?.setRoutingMode(mode)
    }

    fun testApiSlot(slotId: String) {
        viewModelScope.launch {
            val outcome = com.example.ai.SmartApiSlotManager.testSlot(slotId)
            _uiState.update { cur ->
                cur.copy(
                    slotTestResults = cur.slotTestResults + (slotId to outcome),
                    lastLatencyMs = outcome.latencyMs,
                    brainInventory = aiRepository.getBrainInventory()
                )
            }
        }
    }

    fun testAllApiSlots() {
        if (_uiState.value.isTestingAllSlots) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingAllSlots = true) }
            val results = com.example.ai.SmartApiSlotManager.testAllSlots()
            _uiState.update { cur ->
                cur.copy(
                    isTestingAllSlots = false,
                    slotTestResults = cur.slotTestResults + results.associateBy { it.slotId },
                    brainInventory = aiRepository.getBrainInventory()
                )
            }
        }
    }

    fun restoreWakeIfEnabled() {
        // Online-only edition: do not restart the former offline microphone service.
        OmegaWakeService.stop(appContext)
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        OmegaWakeService.stop(appContext)
        if (enabled) _uiState.update { it.copy(voiceError="Background wake is disabled in Online Edition. Tap the mic to speak.") }
    }

    fun runConnectionTest(provider: AiProvider) {
        if (_uiState.value.isTestingConnection) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, diagnosticResult = null) }
            val tested = preferences.settings.value
            val result = aiRepository.runConnectionDiagnostic(provider)
            if (!sameApiConfig(tested, preferences.settings.value)) { _uiState.update { it.copy(isTestingConnection = false) }; return@launch }

            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    diagnosticResult = result,
                    lastLatencyMs = result.latencyMs,
                    lastVerificationBadge = if (result.isSuccess) "Verified" else "Failed"
                )
            }
        }
    }

    fun discoverRealModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDiscoveringModels = true, discoveredChatModels = emptyList(), discoveredLiveModels = emptyList(), discoveredTtsModels = emptyList(), discoveryStatus = "Querying live API models...") }
            val tested = preferences.settings.value
            val res = aiRepository.discoverModelsForActiveProvider()
            if (!sameApiConfig(tested, preferences.settings.value)) { _uiState.update { it.copy(isDiscoveringModels = false) }; return@launch }
            res.onSuccess { found ->
                if (found.ttsModels.isNotEmpty()) {
                    com.example.voice.GeminiSpeechPreferences.saveDiscoveredModels(appContext, found.ttsModels)
                }
                _uiState.update {
                    it.copy(isDiscoveringModels = false, discoveredChatModels = found.chatModels, discoveredLiveModels = found.liveVoiceModels, discoveredTtsModels = found.ttsModels, discoveryStatus = found.message, brainInventory = aiRepository.getBrainInventory())
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isDiscoveringModels = false, discoveryStatus = "Discovery failed: ${err.message}") }
            }
        }
    }

    fun exportEncryptedBackup(): String {
        val blob = runCatching { preferences.exportEncryptedBackupJson() }.getOrElse {
            _uiState.update { u -> u.copy(backupStatusText = "Encrypted backup failed: ${it.message}") }; return ""
        }
        _uiState.update { it.copy(backupStatusText = "Encrypted backup copied (${blob.length} chars).") }
        return blob
    }

    fun importEncryptedBackup(blob: String) {
        val ok = preferences.importEncryptedBackupJson(blob)
        _uiState.update { it.copy(backupStatusText = if (ok) "Settings restored!" else "Invalid backup payload.") }
    }

    fun clearCrashLogs() {
        CrashRecoveryHandler.clearLogs(appContext)
        _uiState.update { it.copy(crashLogText = CrashRecoveryHandler.readLogs(appContext)) }
    }

    fun scheduleDailyRoutine(routineName: String, hour24: Int, minute: Int) {
        val msg = runCatching { TimeAutomationScheduler.scheduleDailyRoutine(appContext, routineName, hour24, minute) }.getOrElse { "Schedule not saved: ${it.message}" }
        _uiState.update { it.copy(lastTriggerBanner = msg) }
    }

    fun cancelDailyRoutine(name: String) {
        val msg = runCatching { TimeAutomationScheduler.cancel(appContext, name) }.getOrElse { "Cancellation failed: ${it.message}" }
        _uiState.update { it.copy(lastTriggerBanner = msg) }
    }

    fun runDailyBriefing() {
        val s = preferences.settings.value
        val cal = actionExecutor.readCalendarSummary(1)
        val notifs = NotificationInbox.formatForAgent(5)
        val prompt = com.example.autonomous.PersonalAssistantEngine.buildDailyPlanPrompt(
            batteryPercent = actionExecutor.getBatteryPercent(),
            calendarSummary = cal,
            reminders = database.getLocationReminders(),
            routines = database.getAllRoutines(),
            unreadNotifsSummary = notifs,
            languageTag = s.languageTag
        )
        sendTextPrompt(prompt, openChat = true)
    }

    fun toggleConditionalRule(ruleId: String) {
        _uiState.update { cur ->
            cur.copy(conditionalRules = cur.conditionalRules.map { if (it.id == ruleId) it.copy(enabled = !it.enabled) else it })
        }
    }

    fun acceptRoutineSuggestion(s: com.example.autonomous.RoutineSuggestion) {
        saveCustomRoutine(s.suggestedName, s.triggerPhrase, s.summary, s.stepsJson)
    }

    fun runProactiveSuggestion(s: com.example.autonomous.ProactiveSuggestion) {
        sendTextPrompt(s.promptToRun, openChat = true)
    }

    fun analyzeDocumentUri(uri: android.net.Uri, question: String = "") {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val extracted = com.example.control.DocumentIntelligenceHelper.extractFromUri(appContext, uri)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                extracted.onSuccess { doc ->
                    val ask = question.trim().ifEmpty {
                        "Analyze '${doc.displayName}' (${doc.pageCount} page(s)), summarize key points, and highlight action items."
                    }
                    if (doc.renderedPageBase64 != null) {
                        _uiState.update { it.copy(currentScreen = OmegaScreen.CHAT) }
                        dispatchToAi(ask, isVoiceInput = false, imageBase64 = doc.renderedPageBase64, isDocumentTask = true)
                    } else if (!doc.textContent.isNullOrBlank()) {
                        val fullPrompt = "$ask\n\nDOCUMENT (${doc.displayName}):\n${doc.textContent}"
                        _uiState.update { it.copy(currentScreen = OmegaScreen.CHAT) }
                        dispatchToAi(fullPrompt, isVoiceInput = false, isDocumentTask = true)
                    }
                }.onFailure { err ->
                    _uiState.update { it.copy(voiceError = "Document error: ${err.message}") }
                }
            }
        }
    }

    fun interruptAndListen(preserveScreen: Boolean = _uiState.value.currentScreen != OmegaScreen.HOME) {
        haptics.wakePulse()
        followUpJob?.cancel(); followUpJob = null
        wakeAckPending = false; ++wakeAckGeneration
        ttsController.stop()
        startVoiceListening(preserveScreen = preserveScreen)
    }

    fun dismissVoiceOverlay() {
        _uiState.update { it.copy(showVoiceOverlay = false) }
    }

    fun summarizeConversation() {
        sendTextPrompt("Summarize our recent conversation in 3 short bullet points and save key facts to memory.", openChat = false)
    }

    fun startVoiceListening(preserveScreen: Boolean = false) {
        com.example.omega.OnlineBackgroundVoiceService.yieldToForeground(appContext)
        emergencyStopped = false
        wakeAckPending=false; ++wakeAckGeneration
        val s = _uiState.value.settings
        if (s.micPrivacyMuted) {
            _uiState.update { cur ->
                val targetScreen = if (preserveScreen) cur.currentScreen else OmegaScreen.HOME
                cur.copy(
                    currentScreen = targetScreen,
                    showVoiceOverlay = preserveScreen && targetScreen != OmegaScreen.HOME,
                    voiceSession = false,
                    voiceError = "Microphone Privacy Mute is ON. Tap the orb to unmute."
                )
            }
            return
        }
        haptics.wakePulse()
        liveFallbackArmed=false; voiceInput.stopListening(); liveDuplex.disconnect(); ttsController.stop()
        _uiState.update { cur ->
            val targetScreen = if (preserveScreen) cur.currentScreen else OmegaScreen.HOME
            cur.copy(
                currentScreen = targetScreen,
                showVoiceOverlay = preserveScreen && targetScreen != OmegaScreen.HOME,
                voiceSession = true,
                liveTranscript = "",
                liveToolStatus = null,
                voiceError = null
            )
        }

        val canUseLiveWs = s.useGeminiLiveAudio && !s.preferOfflineSpeech && !appContext.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked && s.effectiveGeminiKey().isNotBlank()
        if (canUseLiveWs) {
            liveFallbackArmed=true; liveDuplex.connect()
        } else {
            voiceInput.startListening(s.languageTag, preferOffline = s.preferOfflineSpeech, bluetoothEnabled = s.bluetoothVoiceRouting)
        }
    }

    /** Hand the microphone back to the service when the UI leaves the screen. */
    fun releaseForegroundVoice() {
        followUpJob?.cancel(); followUpJob = null
        wakeAckPending=false; ++wakeAckGeneration
        liveFallbackArmed=false
        _uiState.update { it.copy(voiceSession=false) }
        voiceInput.stopListening(); liveDuplex.disconnect(); ttsController.stop()
        OmegaWakeService.requestIdleRearm()
        com.example.omega.OnlineBackgroundVoiceService.foregroundReleased()
    }

    fun stopVoiceListening() {
        com.example.omega.OnlineBackgroundVoiceService.stop(appContext)
        _uiState.update { it.copy(voiceSession = false) }; followUpJob?.cancel()
        wakeAckPending=false; ++wakeAckGeneration
        OmegaWakeService.stopBackgroundConversation()
        haptics.softTap()
        btRouter.clearRouting()
        liveFallbackArmed=false; voiceInput.stopListening(); liveDuplex.disconnect()
    }

    fun toggleLiveDuplexMode() {
        val next = !_uiState.value.settings.useGeminiLiveAudio
        stopAllActivity()
        preferences.update { it.copy(useGeminiLiveAudio = next) }
    }

    fun toggleAutoSpeak() {
        val next = !_uiState.value.settings.autoSpeak
        if (!next) ttsController.stop()
        preferences.update { it.copy(autoSpeak = next) }
    }

    fun cycleVoiceLanguage() {
        val next = when (_uiState.value.settings.languageTag) { "auto" -> "te-IN"; "te-IN" -> "en-IN"; else -> "auto" }
        preferences.update { it.copy(languageTag = next) }
    }

    fun handleInlineWakeCommand(command: String) {
        haptics.wakePulse()
        ttsController.stop()
        _uiState.update { cur ->
            cur.copy(
                showVoiceOverlay = cur.currentScreen != OmegaScreen.HOME,
                voiceSession = true,
                liveTranscript = command,
                voiceError = null
            )
        }
        handlePotentialAutonomousVoiceCommand(command)
    }

    fun analyzeCameraBitmap(bitmap: Bitmap, prompt: String = "Describe what you see and extract any useful text.") {
        val b64 = VisionHelper.bitmapToBase64Jpeg(bitmap)
        _uiState.update { it.copy(currentScreen = OmegaScreen.CHAT) }
        dispatchToAi(prompt, isVoiceInput = false, imageBase64 = b64)
    }

    fun sendTextPrompt(prompt: String, openChat: Boolean = true) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return
        if (com.example.voice.BackgroundVoicePolicy.classify(trimmed) == com.example.voice.BackgroundVoicePolicy.Action.STOP) { stopAllActivity(); return }
        haptics.softTap()
        ttsController.stop()
        if (openChat) _uiState.update { it.copy(currentScreen = OmegaScreen.CHAT) }
        dispatchToAi(trimmed, isVoiceInput = false)
    }

    fun runRoutineByName(name: String) { database.findRoutineByNameOrTrigger(name)?.let { runRoutineNow(it) } }

    fun runRoutineNow(routine: VoiceRoutine) {
        haptics.wakePulse()
        emergencyStopped = false
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isThinking = true, liveToolStatus = "Running ${routine.name}...") }
            val summary = try {
                actionExecutor.executeRoutineByName(routine.name) { st -> _uiState.update { it.copy(liveToolStatus = st) } }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { "Routine failed: ${e.message ?: "unknown error"}" }
            coroutineContext.ensureActive()
            database.insertMessage(ChatMessageEntity(text = summary, isFromUser = false, executedTools = listOf("routine:${routine.name}")))
            refreshDbState()
            _uiState.update { it.copy(isThinking = false, liveToolStatus = null, lastAiReply = summary) }
            AgentHudBus.updateState(AgentHudState.Hidden)
            if (_uiState.value.settings.autoSpeak) ttsController.speak(summary)
        }
    }

    fun saveCustomRoutine(name: String, trigger: String, summary: String, stepsJson: String) {
        if (name.isBlank() || trigger.isBlank()) return
        database.saveRoutine(VoiceRoutine(name = name, triggerPhrase = trigger, actionsSummary = summary, stepsJson = stepsJson))
        refreshDbState()
    }

    fun deleteRoutine(id: Long) {
        val routine = database.getAllRoutines().firstOrNull { it.id == id } ?: return
        val result = runCatching { TimeAutomationScheduler.cancel(appContext, routine.name); database.deleteRoutine(id) }
        if (result.isFailure) _uiState.update { it.copy(lastTriggerBanner = "Delete failed: ${result.exceptionOrNull()?.message}") }
        refreshDbState()
    }
    fun deleteMemory(key: String) { database.deleteMemory(key); refreshDbState() }
    fun speakMessage(text: String) { ttsController.speak(text) }

    fun clearChatHistory() {
        aiRepository.clearHistory()
        database.clearMessages()
        database.insertMessage(ChatMessageEntity(text = "Chat cleared. Encrypted memories remain safe.", isFromUser = false))
        refreshDbState()
        _uiState.update { it.copy(lastAiReply = "") }
    }

    fun stopAllActivity() {
        com.example.omega.OnlineBackgroundVoiceService.stop(appContext)
        runCatching { TimeAutomationScheduler.stopRunning(appContext) }.onFailure { android.util.Log.e("InfinityQueue", "Queue stop not confirmed", it) }
        _uiState.update { it.copy(voiceSession = false) }
        emergencyStopped = true
        followUpJob?.cancel(); followUpJob = null
        activeJob?.cancel(); activeJob = null
        wakeAckPending=false; ++wakeAckGeneration
        OmegaWakeService.stopBackgroundConversation()
        haptics.abortPulse()
        liveFallbackArmed=false
        voiceInput.stopListening()
        btRouter.clearRouting()
        liveDuplex.disconnect()
        ttsController.stop()
        upgradeOrchestrator.emergencyStop()
        _uiState.update { it.copy(isThinking = false, isListening = false, showVoiceOverlay = false, liveToolStatus = null, audioAmplitude = 0f) }
        AgentHudBus.updateState(AgentHudState.Hidden)
    }

    private fun dispatchToAi(prompt: String, isVoiceInput: Boolean, imageBase64: String? = null, isDocumentTask: Boolean = false) {
        if (imageBase64 == null && !isDocumentTask) {
            com.example.autonomous.VoiceUpgradeIntentParser.parse(prompt)?.let { proposal ->
                val resolved = if (proposal.isCrashFix) {
                    com.example.autonomous.CrashBugFixPlanner.buildFixProposal(CrashRecoveryHandler.readLogs(appContext)) ?: proposal
                } else proposal
                database.insertMessage(ChatMessageEntity(text = prompt, isFromUser = true, isVoiceInput = isVoiceInput))
                database.insertMessage(ChatMessageEntity(text = "Routing to Autonomous Self-Upgrade (${resolved.suggestedTargetPath}). Monitoring build & tests in Control Center.", isFromUser = false, executedTools = listOf("self_upgrade")))
                refreshDbState()
                _uiState.update { it.copy(currentScreen = OmegaScreen.AUTONOMOUS_LAB) }
                upgradeOrchestrator.startSelfUpgrade(resolved.featureRequest, resolved.suggestedTargetPath)
                return
            }
        }
        com.example.omega.OnlineBackgroundVoiceService.yieldToForeground(appContext)
        emergencyStopped = false
        followUpJob?.cancel(); followUpJob = null
        val dur = String.format("0:%02d", (prompt.split(" ").size / 2).coerceIn(2, 59))
        database.insertMessage(
            ChatMessageEntity(text = prompt, isFromUser = true, isVoiceInput = isVoiceInput, audioDuration = dur, hasImageAttachment = imageBase64 != null)
        )
        refreshDbState()
        _uiState.update { it.copy(isThinking = true, liveToolStatus = null, voiceError = null, streamingReply = "") }
        AgentHudBus.updateState(AgentHudState.Active(if (imageBase64 != null) "Analyzing vision..." else "JARVIS thinking...", isThinking = true, audioAmplitude = 0.45f))

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val requestJob = coroutineContext[Job]
            val res = aiRepository.sendMessage(
                prompt,
                imageBase64,
                onPartialReply = { partial ->
                    if (requestJob?.isActive == true) _uiState.update { it.copy(streamingReply = partial) }
                },
                onStageChange = { stage ->
                    if (requestJob?.isActive == true) _uiState.update { it.copy(activePipelineStage = stage) }
                },
                isDocumentTask = isDocumentTask
            ) { status -> _uiState.update { it.copy(liveToolStatus = status) } }
            val routeSummary = aiRepository.lastBrainPlan?.let { p ->
                val target = aiRepository.lastRouteResult?.target?.name ?: "AUTO"
                "${p.category.name} (${p.effort.name} • $target)"
            }
            if (routeSummary != null) _uiState.update { it.copy(lastBrainRouteSummary = routeSummary) }
            coroutineContext.ensureActive()
            res.onSuccess { reply ->
                database.insertMessage(ChatMessageEntity(text = reply.replyText, isFromUser = false, executedTools = reply.executedTools))
                refreshDbState()
                _uiState.update {
                    it.copy(
                        isThinking = false,
                        liveToolStatus = null,
                        streamingReply = "",
                        lastAiReply = reply.replyText,
                        lastLatencyMs = reply.latencyMs,
                        activeBrainLabel = reply.activeBrain.displayName,
                        lastVerificationBadge = reply.verificationStatus.badge,
                        activePipelineStage = com.example.ai.BrainPipelineStage.RESPOND,
                        brainInventory = aiRepository.getBrainInventory()
                    )
                }
                if (_uiState.value.settings.autoSpeak) ttsController.speak(reply.replyText)
            }.onFailure { err ->
                val msg = err.message ?: "Request failed."
                database.insertMessage(ChatMessageEntity(text = msg, isFromUser = false, isError = true))
                refreshDbState()
                _uiState.update { it.copy(isThinking = false, liveToolStatus = null, streamingReply = "", voiceError = msg) }
            }
            AgentHudBus.updateState(AgentHudState.Hidden)
        }
    }

    override fun onCleared() {
        activeJob?.cancel(); followUpJob?.cancel()
        smartTriggers.unregister()
        upgradeOrchestrator.emergencyStop()
        preferences.close()
        voiceInput.release(); btRouter.clearRouting(); liveDuplex.release(); ttsController.release(); database.close()
        super.onCleared()
    }
}
