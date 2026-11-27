# V20.2 voice recovery
Preserves V20.1 interface assets, launcher icon, autonomous modules, AI repository and existing package ID. Version 20.2-voice-recovery (21).
- Manual recognition: bounded retry, stale callback invalidation, Android 14+ language switching request, on-device recognizer fallback; local-brain mode requires on-device speech.
- English offline Vosk wake model provisioned in CI; foreground wake uses one in-process event; background wake posts a user-tapped notification rather than launching an activity behind the lock screen. This does not implement fully hands-free background commands. Speech provider and installed language packs determine Telugu support.
- Shared microphone ownership and TTS guard; Live waits for setupComplete, checks recorder state, sends audio frames and falls back to manual recognition on error. Requires configured supported Live model and valid API key.
- Added recovery policy unit tests. Full Android compilation and real device voice tests not yet executed. Local pure Kotlin recovery policy smoke check passed. No API keys embedded by outer build workflow. Vosk increases APK size.
