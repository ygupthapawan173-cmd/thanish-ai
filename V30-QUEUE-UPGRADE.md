# Level 6 queue integration preview (versionCode 40)
Base: Level 6 Neural & Security, versionCode 39 (user reported CI green; no independent run ID).

## Actual changes
- Daily scheduled torch/volume actions now enqueue Android WorkManager persistent unique work. Alarm receiver arms the next day, submits a bounded queue operation and releases its pending broadcast. Jobs check schedule token, stop epoch, local day and cancellation before device actions. A durable per-day claim prevents blind replay of partially executed routines; interrupted tasks require review. Alarm and WorkManager scheduling are inexact and subject to Android restrictions. No automatic replay of missed days. Existing schedule names remain local plaintext metadata; WorkManager input contains routine name/token/day/epoch, not API secrets or routine payloads.
- Emergency stop invalidates queued work through persistent epoch and cancels WorkManager jobs. A device action already underway may finish. Cancellation and schedule replacement invalidate previous tokens. Existing biometric and allowlist checks retained.
- Settings grid adapts to narrow screens and larger font scale; cards expand rather than using fixed height. Existing 15 menus, theme and home navigation retained.
- Android unit test added for queue policy. Existing model provisioning and APK checks retained.

## Verified locally
- 9/9 queue approval policy assertions via Kotlin/JVM; 12 XML parse checks; SQLite desktop index migration preservation check. These do not exercise Android WorkManager, Keystore or ONNX. Existing prior test results are historical.
- Android SDK/Gradle unavailable locally; Android compilation, real task recovery/stop, hardware testing and signing need CI/device verification.

## Not complete
OpenWakeWord custom model and Android integration, Sherpa-ONNX ASR, neural TTS, embedded offline LLM, full premium UI, whole database encryption and recovery, full project dependency graph, device integration for all voice engines, complete Android 16 hardware tests. The existing Silero integration and encrypted text columns are retained. WorkManager integration is code-level, not a verified Android runtime result. No full Level 6 completion claim.
