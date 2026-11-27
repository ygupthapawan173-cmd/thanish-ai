# JARVIS INFINITY V47 — final-scope stability candidate (NOT a verified final release)

Base: exact V46 source archive (user reported Green; actual V46 APK provided in conversation). Application ID and SDK 36 retained. Version code 47. No old overlay or screen-reader class restored. All original V46 archive file paths retained, with selected edits. This package consolidates the changes below; it does not complete all 20 requested targets.

## Seven audited logic issues: source changes
1. Offline image privacy: reject image requests before cloud routing, guard the camera submission, and gate Gemini and Smart Gateway network calls, cloud diagnostics and engineering prompts in local mode. On entry into local mode cancel the active foreground request, voice and engineering job. Already-transmitted bytes cannot be recalled; this is not an OS network firewall. Explicit non-AI internet functions are not governed by this switch.
2. Online ASR screen privacy: cancel network recognizer when screen becomes non-interactive or device locked, poll at 150 ms and guard partial/final/launch paths. Offline capture and existing locked-screen Vosk remain. Gemini Live is disallowed when offline speech is selected. Device transition testing required.
3. TTS errors: deliver terminal completion on timeout, initialization failure, unavailable voice and other errors; existing follow-up checks and explicit stop remain. Avoid automatic full-text retry after a completed chunk.
4. Local model: load and run smoke inference from temporary .task file, validate storage/RAM, require non-empty response, check cancellation, then atomically rename. Previous model remains on ordinary validation failure. Native crashes/OOM and device model compatibility require testing.
5. Encrypted DB read failure: isolate unreadable rows rather than crash due to a decrypt exception or inject placeholders into AI context or executable routines. Preserve ciphertext; warn in UI, do not create new key on decrypt; migration preserves existing ENC1 memory ciphertext. Not a general database corruption/keystore failure recovery system. Backup export remains strict and fails if ciphertext cannot be decrypted.
6. Long TTS replies: split at platform length limit without splitting surrogate pairs, queue distinct utterance IDs and complete only after final callback or terminal failure. JVM tests validate chunk invariants; actual Android engine behavior requires testing.
7. Backup: exact-content duplicate detection, fresh local IDs for distinct records, preserve existing natural-key conflicts, transaction retained. Imported schedules and approvals remain excluded. Android restore, cancellation and repeated restore tests still required.

## Remaining integration / acceptance blockers
- Sherpa-ONNX ASR and bundled neural TTS: not integrated. Need vetted native runtime coexistence with existing ONNX Runtime, model licensing and real Android ASR/TTS tests. Existing Vosk and Android TTS remain; no fake engine entries.
- Premium UI/settings and unified voice controller: existing implementation plus recovery changes retained; complete redesign and visual acceptance not verified.
- Full SQLite file encryption and recovery: existing field encryption, portable encrypted backup and isolation improved. SQLCipher migration/full file encryption not implemented; metadata remains visible on disk.
- OpenWakeWord model is optional non-commercial test input, not bundled by default; Silero and local LLM code retained, actual inference/audio testing pending. Compatible LLM model must be provided by user.
- Background/locked-screen, Accessibility/mic recovery, CPU/RAM/battery profiling, device feature and regression testing remain open; no hardware performance claims.
- Production APK signing requires original owner keystore/secrets. Debug CI signature does not prove upgrade compatibility. Back up before attempting update, never uninstall just to bypass signature mismatch.
- Android compilation for V47 not run locally (no SDK/Gradle); no V47 APK produced. CI workflow retains mandatory build, unit tests, neural CPU smoke tests and signatures. Do not label V47 a final verified master until all open items are complete.

## Build instructions
Upload only JARVIS-INFINITY-V47-FINAL-ULTIMATE-MASTER.zip. Copy ci/archive-build-workflow.yml from ZIP into .github/workflows/jarvis-infinity-v47-level6-master.yml in repository, optionally pin independent SHA-256 before extraction. ZIP integrity manifest checks each file. For extracted source use .github/workflows/build-apk.yml. CI uses Python 3.12, JDK 17, Gradle 8.11.1. Optional neural wake input is non-commercial only; optional release signing uses owner secrets. Run Android 16 acceptance tests on actual hardware after Green. 
