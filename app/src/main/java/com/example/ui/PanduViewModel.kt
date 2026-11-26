package com.example.ui

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.AiStreamEvent
import com.example.ai.AgentAction
import com.example.ai.PanduAiRepository
import com.example.control.AppControlTarget
import com.example.control.CommandExecutionResult
import com.example.control.DeviceControlState
import com.example.control.DeviceSettingTarget
import com.example.control.GlobalNavigationAction
import com.example.control.PendingSensitiveAction
import com.example.control.PanduAccessibilityService
import com.example.control.PhoneCommandParser
import com.example.control.PhoneControlCommand
import com.example.control.PhoneControlManager
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.data.ChatRepository
import com.example.data.TtsPreferencesRepository
import com.example.omega.OmegaVoicePreferences
import com.example.omega.OmegaWakeService
import com.example.omega.OmegaAssistantCore
import com.example.omega.MicOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.example.voice.SpeechLocaleOption
import com.example.voice.TeluguTtsConfig
import com.example.voice.TeluguTtsController
import com.example.voice.TeluguVoiceOption
import com.example.voice.TtsPersonalityPreset
import com.example.voice.TtsReadiness
import com.example.voice.VoiceInputController
import com.example.voice.VoiceInputState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "PanduViewModel"

sealed interface AiGenState {
    data object Idle : AiGenState
    data object Thinking : AiGenState
    data class Streaming(val partialText: String) : AiGenState
    data class Error(val message: String) : AiGenState
}

enum class JarvisStatus {
    ONLINE,
    LISTENING,
    THINKING,
    SPEAKING,
    ALERT
}

data class PanduUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val aiGenState: AiGenState = AiGenState.Idle,
    val voiceInputState: VoiceInputState = VoiceInputState.Idle,
    val isSpeaking: Boolean = false,
    val speakingMessageId: Long? = null,
    val ttsReadiness: TtsReadiness = TtsReadiness.INITIALIZING,
    val ttsConfig: TeluguTtsConfig = TeluguTtsConfig(),
    val availableTeluguVoices: List<TeluguVoiceOption> = emptyList(),
    val speechLocale: SpeechLocaleOption = SpeechLocaleOption.TELUGU,
    val hasMicPermission: Boolean = false,
    val showPermissionRationale: Boolean = false,
    val permissionPermanentlyDenied: Boolean = false,
    val bannerError: String? = null,
    val lastFailedPrompt: String? = null,
    val inputText: String = "",
    val isRecognitionAvailable: Boolean = true,
    val deviceControlState: DeviceControlState = DeviceControlState(),
    val pendingSensitiveAction: PendingSensitiveAction? = null
) {
    val autoSpeakEnabled: Boolean
        get() = ttsConfig.autoSpeakEnabled

    val jarvisStatus: JarvisStatus
        get() = when {
            voiceInputState is VoiceInputState.Listening -> JarvisStatus.LISTENING
            aiGenState is AiGenState.Thinking || aiGenState is AiGenState.Streaming -> JarvisStatus.THINKING
            isSpeaking -> JarvisStatus.SPEAKING
            bannerError != null || aiGenState is AiGenState.Error -> JarvisStatus.ALERT
            else -> JarvisStatus.ONLINE
        }
}

class PanduViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val chatRepository = ChatRepository(database.chatMessageDao())
    private val ttsPrefsRepository = TtsPreferencesRepository(application)
    private val aiRepository = PanduAiRepository(application)
    private val ttsController = TeluguTtsController(application)
    private val phoneControlManager = PhoneControlManager(application)

    private lateinit var voiceInputController: VoiceInputController

    private val _aiGenState = MutableStateFlow<AiGenState>(AiGenState.Idle)
    private val _speechLocale = MutableStateFlow(SpeechLocaleOption.AUTO)
    private val _hasMicPermission = MutableStateFlow(false)
    private val _showPermissionRationale = MutableStateFlow(false)
    private val _permissionPermanentlyDenied = MutableStateFlow(false)
    private val _bannerError = MutableStateFlow<String?>(null)
    private val _lastFailedPrompt = MutableStateFlow<String?>(null)
    private val _inputText = MutableStateFlow("")
    private val _pendingSensitiveAction = MutableStateFlow<PendingSensitiveAction?>(null)

    private var activeGenerationJob: Job? = null
    private var followupJob: Job? = null
    private var manualStartJob: Job? = null
    private var foregroundVisible = false
    private var currentTtsConfig = TeluguTtsConfig()
    private var pendingFromVoiceOrChat = false

    private data class AgentSession(
        val goal: String,
        val step: Int,
        val history: List<String>
    )

    private var agentSession: AgentSession? = null

    init {
        voiceInputController = VoiceInputController(
            context = application,
            onFinalResult = { transcript ->
                sendMessageInternal(
                    prompt = transcript,
                    isVoiceInput = true,
                    saveUserTurn = true
                )
            },
            onError = { errorMsg ->
                _bannerError.value = errorMsg
            }
        )

        viewModelScope.launch {
            ttsPrefsRepository.ttsConfig.collect { config ->
                currentTtsConfig = config
                ttsController.applyConfig(config)
            }
        }

        // Refresh dashboard state immediately as Android binds or unbinds the Accessibility service.
        viewModelScope.launch {
            PanduAccessibilityService.isConnected.collect {
                phoneControlManager.refreshState()
            }
        }
    }

    private data class PermissionSlice(
        val locale: SpeechLocaleOption,
        val hasMicPermission: Boolean,
        val showRationale: Boolean,
        val permanentlyDenied: Boolean
    )

    private data class InteractionSlice(
        val aiGenState: AiGenState,
        val bannerError: String?,
        val lastFailedPrompt: String?,
        val inputText: String,
        val deviceControlState: DeviceControlState,
        val pendingSensitiveAction: PendingSensitiveAction?
    )

    private data class TtsSlice(
        val isSpeaking: Boolean,
        val speakingMessageId: Long?,
        val readiness: TtsReadiness,
        val config: TeluguTtsConfig,
        val availableVoices: List<TeluguVoiceOption>
    )

    private val permissionFlow = combine(
        _speechLocale,
        _hasMicPermission,
        _showPermissionRationale,
        _permissionPermanentlyDenied
    ) { locale, hasMic, showRationale, permanentlyDenied ->
        PermissionSlice(
            locale = locale,
            hasMicPermission = hasMic,
            showRationale = showRationale,
            permanentlyDenied = permanentlyDenied
        )
    }

    private val controlFlow = combine(
        phoneControlManager.state,
        _pendingSensitiveAction
    ) { deviceState, pending ->
        deviceState to pending
    }

    private val interactionFlow = combine(
        _aiGenState,
        _bannerError,
        _lastFailedPrompt,
        _inputText,
        controlFlow
    ) { aiGen, banner, failedPrompt, input, controlPair ->
        InteractionSlice(
            aiGenState = aiGen,
            bannerError = banner,
            lastFailedPrompt = failedPrompt,
            inputText = input,
            deviceControlState = controlPair.first,
            pendingSensitiveAction = controlPair.second
        )
    }

    private val ttsFlow = combine(
        ttsController.isSpeaking,
        ttsController.speakingMessageId,
        ttsController.readiness,
        ttsPrefsRepository.ttsConfig,
        ttsController.availableTeluguVoices
    ) { speaking, speakingId, readiness, config, voices ->
        TtsSlice(
            isSpeaking = speaking,
            speakingMessageId = speakingId,
            readiness = readiness,
            config = config,
            availableVoices = voices
        )
    }

    val uiState: StateFlow<PanduUiState> = combine(
        chatRepository.messages,
        voiceInputController.state,
        ttsFlow,
        permissionFlow,
        interactionFlow
    ) { messages, voiceState, ttsSlice, permissions, interaction ->
        PanduUiState(
            messages = messages,
            aiGenState = interaction.aiGenState,
            voiceInputState = voiceState,
            isSpeaking = ttsSlice.isSpeaking,
            speakingMessageId = ttsSlice.speakingMessageId,
            ttsReadiness = ttsSlice.readiness,
            ttsConfig = ttsSlice.config,
            availableTeluguVoices = ttsSlice.availableVoices,
            speechLocale = permissions.locale,
            hasMicPermission = permissions.hasMicPermission,
            showPermissionRationale = permissions.showRationale,
            permissionPermanentlyDenied = permissions.permanentlyDenied,
            bannerError = interaction.bannerError,
            lastFailedPrompt = interaction.lastFailedPrompt,
            inputText = interaction.inputText,
            isRecognitionAvailable = voiceInputController.isRecognitionAvailable,
            deviceControlState = interaction.deviceControlState,
            pendingSensitiveAction = interaction.pendingSensitiveAction
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PanduUiState()
    )

    fun updateMicPermissionStatus(granted: Boolean, permanentlyDenied: Boolean = false) {
        _hasMicPermission.value = granted
        if (granted) {
            _showPermissionRationale.value = false
            _permissionPermanentlyDenied.value = false
        } else {
            _permissionPermanentlyDenied.value = permanentlyDenied
        }
    }

    fun onMicPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        _hasMicPermission.value = granted
        if (granted) {
            _showPermissionRationale.value = false
            _permissionPermanentlyDenied.value = false
            _bannerError.value = null
            startVoiceListening()
        } else {
            _showPermissionRationale.value = true
            _permissionPermanentlyDenied.value = !shouldShowRationale
        }
    }

    fun dismissPermissionCard() {
        _showPermissionRationale.value = false
    }

    fun onInputTextChanged(newText: String) {
        _inputText.value = newText
    }

    fun sendTypedMessage() {
        val trimmed = _inputText.value.trim()
        if (trimmed.isBlank()) return
        _inputText.value = ""
        sendMessageInternal(prompt = trimmed, isVoiceInput = false, saveUserTurn = true)
    }

    fun sendStarterPrompt(prompt: String) {
        sendMessageInternal(prompt = prompt, isVoiceInput = false, saveUserTurn = true)
    }

    fun retryLastFailed() {
        val prompt = _lastFailedPrompt.value ?: return
        sendMessageInternal(prompt = prompt, isVoiceInput = false, saveUserTurn = false)
    }

    fun setForegroundVisible(visible: Boolean) {
        foregroundVisible = visible
        if (!visible) cancelVoiceListening()
    }
    fun startVoiceListening() {
        if (!foregroundVisible) return
        followupJob?.cancel()
        manualStartJob?.cancel()
        ttsController.stop()
        _bannerError.value = null
        if (_aiGenState.value is AiGenState.Error) {
            _aiGenState.value = AiGenState.Idle
        }
        manualStartJob = viewModelScope.launch {
            if (OmegaWakeService.active.value || OmegaAssistantCore.sessions.owner.value == MicOwner.WAKE) {
                OmegaWakeService.stop(getApplication())
                val released = withTimeoutOrNull(4000L) { OmegaAssistantCore.sessions.owner.first { it != MicOwner.WAKE } } != null
                if (!released) { _bannerError.value = "Wake microphone did not release; retry."; return@launch }
            }
            if (foregroundVisible && _hasMicPermission.value) voiceInputController.startListening(_speechLocale.value)
        }
    }

    fun stopVoiceListening() {
        manualStartJob?.cancel()
        followupJob?.cancel()
        voiceInputController.stopListening()
    }

    fun cancelVoiceListening() {
        manualStartJob?.cancel()
        followupJob?.cancel()
        voiceInputController.cancel()
    }

    fun toggleSpeechLocale() {
        _speechLocale.update { current ->
            when (current) {
                SpeechLocaleOption.AUTO -> SpeechLocaleOption.TELUGU
                SpeechLocaleOption.TELUGU -> SpeechLocaleOption.ENGLISH_INDIA
                SpeechLocaleOption.ENGLISH_INDIA -> SpeechLocaleOption.AUTO
            }
        }
    }

    fun toggleAutoSpeak() {
        val next = !currentTtsConfig.autoSpeakEnabled
        if (!next) {
            ttsController.stop()
        }
        viewModelScope.launch {
            ttsPrefsRepository.setAutoSpeak(next)
        }
    }

    fun selectTtsPreset(preset: TtsPersonalityPreset) {
        viewModelScope.launch {
            ttsPrefsRepository.applyPreset(preset)
        }
    }

    fun updateTtsPitch(pitch: Float) {
        viewModelScope.launch {
            ttsPrefsRepository.updatePitch(pitch)
        }
    }

    fun updateTtsSpeechRate(rate: Float) {
        viewModelScope.launch {
            ttsPrefsRepository.updateSpeechRate(rate)
        }
    }

    fun selectTeluguVoice(voiceName: String?) {
        viewModelScope.launch {
            ttsPrefsRepository.updateSelectedVoice(voiceName)
        }
    }

    fun testTeluguVoice() {
        val sample = getApplication<Application>().getString(R.string.tts_sample_phrase)
        if (!ttsController.speak(sample)) {
            _bannerError.value = getApplication<Application>().getString(R.string.tts_status_unavailable)
        } else {
            _bannerError.value = null
        }
    }

    fun refreshTtsVoices() {
        ttsController.refreshVoices()
    }

    fun createInstallTeluguVoiceIntent(): Intent {
        return ttsController.createInstallVoiceDataIntent()
    }

    // --- Phone Control Center Actions ---

    fun refreshDeviceControls() {
        phoneControlManager.refreshState()
    }

    fun onAppControlClicked(target: AppControlTarget) {
        if (target.requiresConfirmation) {
            pendingFromVoiceOrChat = false
            _pendingSensitiveAction.value = PendingSensitiveAction.ConfirmAppLaunch(
                target = target,
                title = "${target.displayName} ఓపెన్ చేయాలా బాస్?",
                description = "కాల్ / మెసేజ్ సంబంధిత యాప్ (${target.displayName}) ఓపెన్ చేయడానికి మీ కన్ఫర్మేషన్ అవసరం."
            )
        } else {
            applyControlResult(phoneControlManager.openApp(target))
        }
    }

    fun setTorchEnabled(enabled: Boolean) {
        applyControlResult(phoneControlManager.setTorchEnabled(enabled))
    }

    fun adjustVolume(delta: Int) {
        applyControlResult(phoneControlManager.adjustVolume(delta))
    }

    fun setVolumeLevel(level: Int) {
        applyControlResult(phoneControlManager.setVolumeLevel(level))
    }

    fun setSystemBrightnessPercent(percent: Int) {
        applyControlResult(phoneControlManager.setSystemBrightnessPercent(percent))
    }

    fun adjustBrightness(deltaPercent: Int) {
        applyControlResult(phoneControlManager.adjustBrightness(deltaPercent))
    }

    fun createWriteSettingsPermissionIntent(): Intent {
        return phoneControlManager.createWriteSettingsPermissionIntent()
    }

    fun checkBatteryInfo() {
        val res = phoneControlManager.readBatterySummary()
        applyControlResult(res)
        if (res.succeeded) {
            if (!ttsController.speak(res.teluguResponse)) {
                _bannerError.value = getApplication<Application>().getString(R.string.tts_status_unavailable)
            }
        }
    }

    fun openDeviceSetting(target: DeviceSettingTarget) {
        applyControlResult(phoneControlManager.openDeviceSetting(target))
    }

    fun onGlobalNavClicked(action: GlobalNavigationAction) {
        pendingFromVoiceOrChat = false
        _pendingSensitiveAction.value = PendingSensitiveAction.ConfirmGlobalNav(
            action = action,
            title = "${action.displayName} యాక్షన్ చేయాలా బాస్?",
            description = "Accessibility Service ద్వారా సిస్టమ్ '${action.displayName}' నావిగేషన్ యాక్షన్ అమలు చేయబడుతుంది."
        )
    }

    /**
     * Starts a user-approved screen agent session. Each phone action is shown in
     * the existing confirmation dialog before it can execute.
     */
    fun startAgentTask(goal: String) {
        val cleanGoal = goal.trim().take(500)
        if (cleanGoal.isBlank()) {
            _bannerError.value = "బాస్, Agent task కోసం ఒక goal ఇవ్వండి."
            return
        }
        if (!phoneControlManager.state.value.isAccessibilityConnected) {
            _bannerError.value = "ముందుగా Apps tab లో Accessibility Service enable చేయండి."
            return
        }

        stopAgentTask(showMessage = false)
        PanduAccessibilityService.setAgentScreenCaptureConsent(true)
        val session = AgentSession(cleanGoal, 0, emptyList())
        agentSession = session
        advanceAgentTask(session)
    }

    fun stopAgentTask() {
        stopAgentTask(showMessage = true)
    }

    private fun stopAgentTask(showMessage: Boolean) {
        agentSession = null
        PanduAccessibilityService.setAgentScreenCaptureConsent(false)
        if (_pendingSensitiveAction.value is PendingSensitiveAction.ConfirmAgentAction) {
            _pendingSensitiveAction.value = null
        }
        _aiGenState.value = AiGenState.Idle
        if (showMessage) _bannerError.value = "AI Phone Agent task ఆపేశాను బాస్."
    }

    private fun finishAgentTask(message: String) {
        agentSession = null
        PanduAccessibilityService.setAgentScreenCaptureConsent(false)
        _aiGenState.value = AiGenState.Idle
        viewModelScope.launch {
            val finalMessage = message.ifBlank { "Agent task పూర్తయింది బాస్." }
            val savedId = chatRepository.saveAssistantMessage(finalMessage)
            if (currentTtsConfig.autoSpeakEnabled) ttsController.speak(finalMessage, savedId)
        }
    }

    private fun advanceAgentTask(session: AgentSession) {
        if (agentSession != session) return
        if (session.step >= 12) {
            finishAgentTask("బాస్, 12 steps పూర్తి చేశాను. మిగతా పని మీరు చెక్ చేయండి.")
            return
        }

        _aiGenState.value = AiGenState.Thinking
        viewModelScope.launch {
            try {
                val screen = PanduAccessibilityService.captureAgentScreen()
                    ?: error("ఈ app screen చదవలేకపోయాను. Accessibility Service status చెక్ చేయండి లేదా supported app తెరవండి.")
                val action: AgentAction = aiRepository.suggestAgentAction(
                    goal = session.goal,
                    screen = screen,
                    step = session.step,
                    history = session.history
                )
                if (agentSession != session) return@launch

                when (action.name) {
                    "finish" -> finishAgentTask(action.args.optString("message"))
                    "speak" -> {
                        val message = action.args.optString("text")
                        val savedId = chatRepository.saveAssistantMessage(message)
                        if (currentTtsConfig.autoSpeakEnabled) ttsController.speak(message, savedId)
                        val next = session.copy(
                            step = session.step + 1,
                            history = (session.history + "speak: ${message.take(120)}").takeLast(10)
                        )
                        _aiGenState.value = AiGenState.Idle
                        delay(300)
                        advanceAgentTask(next)
                    }
                    "wait" -> {
                        val next = session.copy(
                            step = session.step + 1,
                            history = (session.history + "wait").takeLast(10)
                        )
                        _aiGenState.value = AiGenState.Idle
                        delay(900)
                        advanceAgentTask(next)
                    }
                    else -> {
                        val description = PanduAccessibilityService.describeAgentAction(action.name, action.args)
                            ?: error("AI proposed an action for a screen item that is no longer available.")
                        _aiGenState.value = AiGenState.Idle
                        _pendingSensitiveAction.value = PendingSensitiveAction.ConfirmAgentAction(
                            actionName = action.name,
                            argsJson = action.args.toString(),
                            goal = session.goal,
                            step = session.step,
                            history = session.history,
                            title = "AI proposes: $description",
                            description = "ఈ ఒక్క action మాత్రమే execute అవుతుంది. Continue చేయడానికి Confirm నొక్కండి; వద్దంటే Cancel చేయండి."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Phone agent failed", e)
                stopAgentTask(showMessage = false)
                _bannerError.value = "Agent ఆగిపోయింది: ${e.localizedMessage ?: "AI లేదా screen access error"}"
            }
        }
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return phoneControlManager.createAccessibilitySettingsIntent()
    }

    fun confirmPendingAction() {
        val pending = _pendingSensitiveAction.value ?: return
        _pendingSensitiveAction.value = null
        val fromVoice = pendingFromVoiceOrChat
        pendingFromVoiceOrChat = false

        if (pending is PendingSensitiveAction.ConfirmAgentAction) {
            viewModelScope.launch {
                val result = PanduAccessibilityService.executeAgentAction(
                    pending.actionName,
                    pending.argsJson
                )
                applyControlResult(result)
                if (!result.succeeded) {
                    stopAgentTask(showMessage = false)
                    _bannerError.value = result.teluguResponse
                    return@launch
                }
                val next = AgentSession(
                    goal = pending.goal,
                    step = pending.step + 1,
                    history = (pending.history + "${pending.actionName}: ${pending.argsJson.take(160)}").takeLast(10)
                )
                agentSession = next
                delay(if (pending.actionName == "open_app") 1_500L else 700L)
                if (agentSession == next) advanceAgentTask(next)
            }
            return
        }

        val result = when (pending) {
            is PendingSensitiveAction.ConfirmAppLaunch -> phoneControlManager.openApp(pending.target)
            is PendingSensitiveAction.ConfirmGlobalNav -> phoneControlManager.performGlobalNavigation(pending.action)
            is PendingSensitiveAction.ConfirmAgentAction -> CommandExecutionResult(
                succeeded = false,
                teluguResponse = "Agent action మళ్ళీ confirm చేయాలి బాస్."
            )
        }

        applyControlResult(result)

        if (fromVoice) {
            viewModelScope.launch {
                val savedId = chatRepository.saveAssistantMessage(result.teluguResponse)
                if (currentTtsConfig.autoSpeakEnabled) {
                    ttsController.speak(result.teluguResponse, savedId)
                }
            }
        }
    }

    private fun applyControlResult(result: CommandExecutionResult) {
        _bannerError.value = if (result.succeeded) null else result.teluguResponse
    }

    fun dismissPendingAction() {
        val pending = _pendingSensitiveAction.value ?: return
        _pendingSensitiveAction.value = null
        if (pending is PendingSensitiveAction.ConfirmAgentAction) {
            stopAgentTask(showMessage = true)
            return
        }
        val fromVoice = pendingFromVoiceOrChat
        pendingFromVoiceOrChat = false

        if (fromVoice) {
            val cancelMsg = "సరే బాస్, ${pending.title.substringBefore(" ")} యాక్షన్ క్యాన్సిల్ చేశాను."
            viewModelScope.launch {
                val savedId = chatRepository.saveAssistantMessage(cancelMsg)
                if (currentTtsConfig.autoSpeakEnabled) {
                    ttsController.speak(cancelMsg, savedId)
                }
            }
        }
    }

    fun speakMessage(message: ChatMessageEntity) {
        if (ttsController.isSpeaking.value && ttsController.speakingMessageId.value == message.id) {
            ttsController.stop()
        } else if (!ttsController.speak(message.text, message.id)) {
            _bannerError.value = getApplication<Application>().getString(R.string.tts_status_unavailable)
        }
    }

    fun stopSpeaking() {
        ttsController.stop()
    }

    fun dismissError() {
        _bannerError.value = null
        if (_aiGenState.value is AiGenState.Error) {
            _aiGenState.value = AiGenState.Idle
        }
    }

    fun clearChatHistory() {
        activeGenerationJob?.cancel()
        ttsController.stop()
        voiceInputController.cancel()
        _aiGenState.value = AiGenState.Idle
        _bannerError.value = null
        _lastFailedPrompt.value = null
        viewModelScope.launch {
            chatRepository.clearChat()
        }
    }

    private fun sendMessageInternal(
        prompt: String,
        isVoiceInput: Boolean,
        saveUserTurn: Boolean
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return
        if (OmegaAssistantCore.sessions.backgroundBusy.value) {
            _bannerError.value = "Background AI reply is in progress. Stop wake or wait for reply."
            return
        }

        activeGenerationJob?.cancel()
        followupJob?.cancel()
        ttsController.stop()
        _bannerError.value = null
        _lastFailedPrompt.value = null

        val parsedCommand = PhoneCommandParser.parse(trimmed)
        if (parsedCommand != null) {
            handlePhoneControlCommand(
                prompt = trimmed,
                command = parsedCommand,
                isVoiceInput = isVoiceInput,
                saveUserTurn = saveUserTurn
            )
            return
        }

        val turnToken = OmegaAssistantCore.sessions.beginForeground() ?: return
        activeGenerationJob = viewModelScope.launch {
            val historySnapshot = uiState.value.messages.let { list ->
                if (!saveUserTurn && list.lastOrNull()?.role == ChatMessageEntity.ROLE_USER) {
                    list.dropLast(1)
                } else {
                    list
                }
            }

            if (saveUserTurn) {
                chatRepository.saveUserMessage(trimmed, isVoiceInput)
            }

            _aiGenState.value = AiGenState.Thinking
            try {
                val prompt = OmegaAssistantCore.promptWithMemory(getApplication(), trimmed)
                aiRepository.streamReply(historySnapshot, prompt).collect { event ->
                    when (event) {
                        is AiStreamEvent.Chunk -> {
                            _aiGenState.value = AiGenState.Streaming(event.accumulatedText)
                        }
                        is AiStreamEvent.Completed -> {
                            val savedId = chatRepository.saveAssistantMessage(event.fullText)
                            _aiGenState.value = AiGenState.Idle
                            if (currentTtsConfig.autoSpeakEnabled) {
                                ttsController.speak(event.fullText, savedId)
                            }
                            scheduleFollowupIfEnabled(isVoiceInput)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI reply failed", e)
                val msg = e.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: "అయ్యో బాస్, ఏదో చిన్న సమస్య వచ్చింది. మళ్ళీ ప్రయత్నించండి!"
                _lastFailedPrompt.value = trimmed
                _aiGenState.value = AiGenState.Error(msg)
                _bannerError.value = msg
            }
        }
        activeGenerationJob?.invokeOnCompletion { OmegaAssistantCore.sessions.endForeground(turnToken) }
    }

    private fun handlePhoneControlCommand(
        prompt: String,
        command: PhoneControlCommand,
        isVoiceInput: Boolean,
        saveUserTurn: Boolean
    ) {
        viewModelScope.launch {
            if (saveUserTurn) {
                chatRepository.saveUserMessage(prompt, isVoiceInput)
            }

            when (command) {
                is PhoneControlCommand.OpenApp -> {
                    if (command.target.requiresConfirmation) {
                        pendingFromVoiceOrChat = true
                        _pendingSensitiveAction.value = PendingSensitiveAction.ConfirmAppLaunch(
                            target = command.target,
                            title = "${command.target.displayName} ఓపెన్ చేయాలా బాస్?",
                            description = "కాల్ / మెసేజ్ సంబంధిత యాప్ (${command.target.displayName}) ఓపెన్ చేయడానికి మీ కన్ఫర్మేషన్ అవసరం."
                        )
                        val askMsg = "${command.target.displayName} ఓపెన్ చేయాలా బాస్? స్క్రీన్ మీద కన్ఫర్మ్ చేయండి."
                        if (currentTtsConfig.autoSpeakEnabled) {
                            ttsController.speak(askMsg)
                        }
                        return@launch
                    }
                }

                is PhoneControlCommand.PerformGlobalNav -> {
                    if (phoneControlManager.state.value.isAccessibilityConnected) {
                        pendingFromVoiceOrChat = true
                        _pendingSensitiveAction.value = PendingSensitiveAction.ConfirmGlobalNav(
                            action = command.action,
                            title = "${command.action.displayName} యాక్షన్ చేయాలా బాస్?",
                            description = "Accessibility Service ద్వారా '${command.action.displayName}' నావిగేషన్ యాక్షన్ అమలు చేయబడుతుంది."
                        )
                        return@launch
                    }
                }

                else -> Unit
            }

            val result: CommandExecutionResult = when (command) {
                is PhoneControlCommand.OpenApp -> phoneControlManager.openApp(command.target)
                is PhoneControlCommand.SetTorch -> phoneControlManager.setTorchEnabled(command.enabled)
                is PhoneControlCommand.IncreaseVolume -> phoneControlManager.adjustVolume(+1)
                is PhoneControlCommand.DecreaseVolume -> phoneControlManager.adjustVolume(-1)
                is PhoneControlCommand.MuteVolume -> phoneControlManager.setVolumeLevel(0)
                is PhoneControlCommand.IncreaseBrightness -> phoneControlManager.adjustBrightness(+15)
                is PhoneControlCommand.DecreaseBrightness -> phoneControlManager.adjustBrightness(-15)
                is PhoneControlCommand.CheckBattery -> phoneControlManager.readBatterySummary()
                is PhoneControlCommand.OpenDeviceSettings -> phoneControlManager.openDeviceSetting(command.target)
                is PhoneControlCommand.PerformGlobalNav -> phoneControlManager.performGlobalNavigation(command.action)
            }

            if (!result.succeeded) {
                _bannerError.value = result.teluguResponse
            }

            val savedId = chatRepository.saveAssistantMessage(result.teluguResponse)
            if (currentTtsConfig.autoSpeakEnabled) {
                ttsController.speak(result.teluguResponse, savedId)
            }
            scheduleFollowupIfEnabled(isVoiceInput)
        }
    }

    /** Opt-in only. Schedule one follow-up after speech ends; lifecycle ON_STOP cancels. */
    private fun scheduleFollowupIfEnabled(fromVoice: Boolean) {
        if (!foregroundVisible || !fromVoice || !OmegaVoicePreferences.continuous(getApplication())) return
        followupJob?.cancel()
        followupJob = viewModelScope.launch {
            delay(1200)
            var ticks = 0
            while (ttsController.isSpeaking.value && ticks < 160) {
                delay(125)
                ticks++
            }
            if (ticks >= 160) return@launch
            delay(500)
            if (foregroundVisible && OmegaVoicePreferences.continuous(getApplication()) &&
                _hasMicPermission.value &&
                !OmegaWakeService.active.value &&
                _pendingSensitiveAction.value == null &&
                _aiGenState.value is AiGenState.Idle) {
                voiceInputController.startListening(_speechLocale.value)
            }
        }
    }

    override fun onCleared() {
        manualStartJob?.cancel()
        followupJob?.cancel()
        super.onCleared()
        voiceInputController.release()
        ttsController.release()
        phoneControlManager.release()
    }
}
