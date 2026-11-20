package com.example.ui

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.AiStreamEvent
import com.example.ai.PanduAiRepository
import com.example.control.AppControlTarget
import com.example.control.CommandExecutionResult
import com.example.control.DeviceControlState
import com.example.control.DeviceSettingTarget
import com.example.control.GlobalNavigationAction
import com.example.control.PendingSensitiveAction
import com.example.control.PhoneCommandParser
import com.example.control.PhoneControlCommand
import com.example.control.PhoneControlManager
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.data.ChatRepository
import com.example.data.TtsPreferencesRepository
import com.example.voice.SpeechLocaleOption
import com.example.voice.TeluguTtsConfig
import com.example.voice.TeluguTtsController
import com.example.voice.TeluguVoiceOption
import com.example.voice.TtsPersonalityPreset
import com.example.voice.TtsReadiness
import com.example.voice.VoiceInputController
import com.example.voice.VoiceInputState
import kotlinx.coroutines.CancellationException
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
    private val _speechLocale = MutableStateFlow(SpeechLocaleOption.TELUGU)
    private val _hasMicPermission = MutableStateFlow(false)
    private val _showPermissionRationale = MutableStateFlow(false)
    private val _permissionPermanentlyDenied = MutableStateFlow(false)
    private val _bannerError = MutableStateFlow<String?>(null)
    private val _lastFailedPrompt = MutableStateFlow<String?>(null)
    private val _inputText = MutableStateFlow("")
    private val _pendingSensitiveAction = MutableStateFlow<PendingSensitiveAction?>(null)

    private var activeGenerationJob: Job? = null
    private var currentTtsConfig = TeluguTtsConfig()
    private var pendingFromVoiceOrChat = false

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

    fun startVoiceListening() {
        ttsController.stop()
        _bannerError.value = null
        if (_aiGenState.value is AiGenState.Error) {
            _aiGenState.value = AiGenState.Idle
        }
        voiceInputController.startListening(_speechLocale.value)
    }

    fun stopVoiceListening() {
        voiceInputController.stopListening()
    }

    fun cancelVoiceListening() {
        voiceInputController.cancel()
    }

    fun toggleSpeechLocale() {
        _speechLocale.update { current ->
            if (current == SpeechLocaleOption.TELUGU) {
                SpeechLocaleOption.ENGLISH_INDIA
            } else {
                SpeechLocaleOption.TELUGU
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

    fun createAccessibilitySettingsIntent(): Intent {
        return phoneControlManager.createAccessibilitySettingsIntent()
    }

    fun confirmPendingAction() {
        val pending = _pendingSensitiveAction.value ?: return
        _pendingSensitiveAction.value = null
        val fromVoice = pendingFromVoiceOrChat
        pendingFromVoiceOrChat = false

        val result = when (pending) {
            is PendingSensitiveAction.ConfirmAppLaunch -> phoneControlManager.openApp(pending.target)
            is PendingSensitiveAction.ConfirmGlobalNav -> phoneControlManager.performGlobalNavigation(pending.action)
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

        activeGenerationJob?.cancel()
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
                aiRepository.streamReply(historySnapshot, trimmed).collect { event ->
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
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceInputController.release()
        ttsController.release()
        phoneControlManager.release()
    }
}
