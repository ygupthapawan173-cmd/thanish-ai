from pathlib import Path
p=Path('app/src/main/java/com/example')
s=(p/'omega/OmegaWakeService.kt').read_text(); vm=(p/'ui/PanduViewModel.kt').read_text(); a=(p/'MainActivity.kt').read_text()
assert 'handler.postDelayed(rearmWatchdog, 2500L)' in s
assert 'rearmPolicy.reserve(canListen(),model!=null,speech!=null || neuralWake!=null)' in s
assert 'handler.removeCallbacks(rearmAttempt); rearmPolicy.cancel()' in s
assert 'if (canListen() && speech == null && neuralWake == null) arm()' in s
assert 'if (!VoiceMic.lease.acquire(wakeLease))' in s
assert 'override fun onStop()' in a and 'viewModel.releaseForegroundVoice()' in a
f=vm.split('fun releaseForegroundVoice() {',1)[1].split('fun stopVoiceListening()',1)[0]
assert 'voiceInput.stopListening(); liveDuplex.disconnect(); ttsController.stop()' in f and 'stopBackgroundConversation' not in f
assert 'followUpJob?.cancel()' in f and 'liveFallbackArmed=false' in f
assert 'OmegaWakeService.stop(appContext)' in vm.split('fun restoreWakeIfEnabled() {',1)[1].split('fun setWakeWordEnabled',1)[0]
assert 'setMainMicBusy(true, this)' in (p/'voice/VoiceInputController.kt').read_text()
assert 'setMainMicBusy(true, this)' in (p/'ai/GeminiLiveDuplexEngine.kt').read_text()
assert 'mutableSetOf<Any>()' in (p/'control/AgentHudBus.kt').read_text()
print('12/12 V100.1 integration source guards passed (static checks)')
