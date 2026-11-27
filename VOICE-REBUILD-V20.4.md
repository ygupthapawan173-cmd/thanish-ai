# V20.4 Voice Engine Rebuild
Based on V20.3 SHA-256 49bbed2cbd856041484cdc08a56deec2898ca48c93a810031c9923aef0eebf2e.
- Permission callback starts user-enabled foreground wake service after grant; resume restores opt-in.
- Exclusive in-process mic lease covers Vosk wake/command, Android recognizer, Gemini Live and optional VAD.
- Bounded retries, language-aware error routing, recognition support telemetry and conservative AUTO fallback.
- Wake grammar includes safe English inline commands; final results must pass confidence. Other commands require a second utterance. Partial matches are diagnostic only.
- Short wake cue; stop/release wake recorder before command capture.
- TTS audio focus and success/error/stop callbacks; missing Telugu TTS voice explicitly reported.
- Background safe local commands only on locked screen; no cloud question while locked or local-only.
- Sticky service restart is best effort; Android/OEM limits, permission removal and force-stop remain.
- Migrate obsolete default Gemini Live model while keeping custom settings; existing text provider fallback preserved.
- Structured OmegaVoice log tags without raw transcripts or keys; four added JUnit tests.
Local pure Kotlin smoke test passed. Android build and phone tests not run locally. Bundled Vosk model is English only; Telugu depends on Android recognition/TTS support, not a native Telugu model.
