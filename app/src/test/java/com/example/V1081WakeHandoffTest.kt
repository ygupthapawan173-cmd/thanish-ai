package com.example

import com.example.voice.HandsFreeWakePolicy
import org.junit.Test

class V1081WakeHandoffTest {
    @Test fun staleThinkingDoesNotBlockBackgroundWake() {
        check(!HandsFreeWakePolicy.foregroundBusy(false, false, false, true))
        check(HandsFreeWakePolicy.foregroundBusy(true, false, false, true))
    }
    @Test fun realMicrophoneAndTtsOwnersAlwaysBlockWake() {
        for (foreground in listOf(false, true)) {
            check(HandsFreeWakePolicy.foregroundBusy(foreground, true, false, false))
            check(HandsFreeWakePolicy.foregroundBusy(foreground, false, true, false))
            check(!HandsFreeWakePolicy.foregroundBusy(foreground, false, false, false))
        }
    }
}
