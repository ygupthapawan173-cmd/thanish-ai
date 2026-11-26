# OMEGA native master candidate
Base: Pandu Babu Kotlin/Compose project, 5883e5a7c0a647c5ac3fe400c3a7cb761e984623. No recovered APK or smali patches.
- Existing four providers, TTS, chat history and phone control are retained.
- New Vosk Android 0.3.75 and bundled English model, foreground microphone service, wake phrase and one local command capture. User must start listening and review command before sending. No boot start.
- Selectable Android recognition mode, local availability selection, stale callback rejection and timeout. Local language pack errors require manual retry; offline Telugu depends on Android.
- Encrypted local notebook and task list, actual provider tests, system measurements, reviewed feature and bug-fix drafts.
- HTTP redirect blocking, model validation, provider cooldown and streaming backpressure.
- Not delivered: Porcupine/Sherpa-ONNX, Vosk Telugu, continuous follow-up, Aider/multi-agent, automated PR and tested repairs, secure installer, vision/document AI, new 3D renderer, full workflow scheduler, full battery tuning and original feature parity.
- APK build and device tests required. Existing Pandu application ID retained; do not uninstall a working app if signing conflicts.

Build hardening: official Vosk small English 0.15 archive pinned to SHA256 observed in successful provisioning run 45789879372. Maven model placeholder AAR removed. CI retries transient dependency errors, verifies signature, packaged model and ARM64 libraries. APK runtime remains unverified. Android Studio local builds must provision the same model in app/src/main/assets/model-en-us (CI workflow documents extraction).
