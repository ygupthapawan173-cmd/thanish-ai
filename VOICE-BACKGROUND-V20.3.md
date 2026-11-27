# V20.3 Background Voice Master patch
Base: V20.2 Voice Recovery. Existing visual resources, icon and autonomous modules unchanged.

- Wake foreground service now handles background commands directly after Vosk wake recognition; no notification tap required while service remains running.
- Android speech recognition uses selected language, AUTO detection/switch and bounded retry; full-grammar English Vosk is a fallback when system recognition fails. Telugu offline model is not bundled.
- Session and capture owners block wake rearm during speech, AI, TTS. Manual/Live mic preempts the background session; token checks invalidate stale callbacks.
- Safe exact local commands: clock, date, battery, flashlight, greeting, stop. Background questions use tool-free configured Gemini/OpenRouter text request, no private memory/notification/tool context. In local mode no cloud request; general offline LLM is not implemented by this patch. Background full-duplex Gemini Live is not implemented.
- TTS reply and opt-in maximum three-turn follow-up; notification end, restart and stop controls; privacy mute cancels the session. No forced Activity launch, no lock bypass.
- Voice status on notification and existing HUD; no visual design changes. Edge overlay requires existing Accessibility setup, OS restrictions apply.
- Start wake service while app visible and permission granted. Android may terminate microphone foreground service; it is not automatically started after force-stop/reboot.
- Pure Kotlin policy smoke test passed (exact commands, bounded retries and follow-ups, locale). Full Android compilation, device tests, battery/locked-screen behavior not yet verified.
