# V111 final candidate qualification
Baseline: 7bfc046 (previous signed debug CI success). New builds must pass source audit, unit tests, packaging, signature verification and artifact upload. Do not label a candidate device verified on the basis of CI alone.

## Required physical device checks (Android 16 ARM64)
1. Fresh install and update from a same-certificate V111 candidate. Verify launch, all six tabs, permission requests and settings persistence.
2. Enable wake service while app is foreground. Check notification, microphone privacy indicator, and STOP action. Test 20 positive "Hey JARVIS" attempts and 20 negative utterances in foreground, minimized and screen locked. Record misses, false wakes, acknowledgement p50/p95; repeat with network disabled. No cloud audio while locked. Lock-screen reply requires unlock to continue.
3. Repeat in quiet and moderate noise; test Telugu-accented English. Test Sherpa and separately Porcupine after providing a valid AccessKey and Android .ppn. Do not mark Porcupine ready just because the files exist.
4. Grant cloud consent, configure owned API keys, test Saaras, Gemini Transcribe Live and GPT-Live-Transcribe independently, then revoke consent mid-capture. No further audio may be transmitted. Confirm Telugu, English and mixed utterances; record real endpoint-to-transcript timing.
5. For each conversation model (Gemini 3.8 Live, Extended Thinking, GPT-Live 1.1, Realtime 2.1 and 2.1 Mini), verify setup acknowledgement, spoken replies, playback and disconnect; report HTTP/API failures. Do not assume key entitlement.
6. Test Telugu and English Sarvam replies, change speaker and rewarm acknowledgement cache. Validate FFT bars against captured audio, silence, and playback; Android Visualizer access may be unavailable.
7. Run for 30 minutes screen unlocked/locked, switch apps, revoke microphone permission, turn network off/on, and inspect crashes, memory, battery usage, audio lease handoff. Do not uninstall an existing APK if signatures differ unless data is backed up.

## Explicitly unavailable until real prerequisites are obtained
- VoxRT custom Hey JARVIS: authorized vendor-trained .vxrt and licensing required; public Hey Assistant model is not equivalent.
- LiveKit WakeWord 0.2.0: trained Hey JARVIS stateful ONNX classifier and tested Android JNI runtime.
- NVIDIA MatchboxNet INT8: labeled positive/negative training samples, trained and calibrated classifier, frontend contract and validation.
- LiteRT + MediaTek: compatible model, device-side runtime/compiler delegate and verified accelerator execution; CPU fallback is not proof of NPU support.

Do not claim unsupported engines active or fabricate measured latency. No new cloud credentials are stored in the repository.
