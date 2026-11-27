package com.example.control

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AgentHudState {
    data object Hidden : AgentHudState
    data class Active(
        val statusText: String,
        val isThinking: Boolean = false,
        val highlightBox: Rect? = null,
        val audioAmplitude: Float = 0.2f
    ) : AgentHudState
}

sealed interface HudVisualEvent {
    data class TapRipple(val x: Float, val y: Float) : HudVisualEvent
    data class SwipeTrail(val startX: Float, val startY: Float, val endX: Float, val endY: Float) : HudVisualEvent
}

object AgentHudBus {
    private val _state = MutableStateFlow<AgentHudState>(AgentHudState.Hidden)
    val state: StateFlow<AgentHudState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HudVisualEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<HudVisualEvent> = _events.asSharedFlow()

    private val _abortSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val abortSignal: SharedFlow<Unit> = _abortSignal.asSharedFlow()

    private val _wakeTriggers = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val wakeTriggers: SharedFlow<String> = _wakeTriggers.asSharedFlow()

    private val _isMainMicBusy = MutableStateFlow(false)
    private val micOwners = mutableSetOf<Any>()
    private val speakerOwners = mutableSetOf<Any>()
    private val _isTtsBusy = MutableStateFlow(false)
    val isTtsBusy: StateFlow<Boolean> = _isTtsBusy.asStateFlow()
    val isMainMicBusy: StateFlow<Boolean> = _isMainMicBusy.asStateFlow()


    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    fun updateState(newState: AgentHudState) { _state.value = newState }
    fun emitEvent(event: HudVisualEvent) { _events.tryEmit(event) }
    fun triggerAbort() { _abortSignal.tryEmit(Unit) }
    fun emitWakeTrigger(inlineCommand: String) { _wakeTriggers.tryEmit(inlineCommand) }
    @Synchronized fun setMainMicBusy(busy: Boolean, owner: Any = "manual") {
        if (busy) micOwners.add(owner) else micOwners.remove(owner)
        _isMainMicBusy.value = micOwners.isNotEmpty()
    }
    @Synchronized fun setTtsBusy(owner: Any, busy: Boolean) {
        if (busy) speakerOwners.add(owner) else speakerOwners.remove(owner)
        _isTtsBusy.value = speakerOwners.isNotEmpty()
    }
    fun setAppInForeground(inForeground: Boolean) { _isAppInForeground.value = inForeground }
}
