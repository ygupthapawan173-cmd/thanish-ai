# JARVIS INFINITY V42 — Level 6 master integration (NOT a final verified release)

Base: exact V41 source ZIP, SHA-256 `fa4e075487a5f3dc49e7f59e463e41295496438715b1a69b8bef8feee1e67f18`. The user reported V41 CI Green; V42 has not been built on Android here.

## New functional code
- Portable encrypted database-content export and non-destructive merge restore: five existing tables, 30,000-row and 16 MiB plaintext limit, PBKDF2-HMAC-SHA256 600,000 iterations, random salt/IV, AES-256-GCM authenticated header, SAF document picker. No plaintext file created by the app. Credentials, device settings, native model weights and scheduling approvals excluded. Existing IDs / unique-key conflicts skipped (not a complete replacement restore); imported routines not scheduled. On restore sensitive columns encrypted under current device Keystore. Data exists in process memory during operation. Original SQLite file still uses field encryption, not full SQLCipher. UI state may require app restart. Android provider / migration / recovery tests pending.
- Self-coding creates a single tree and commit for selected files, then non-force isolated branch update; snapshots fetched from fixed commit; expected head and blob checked, existing path/package/scan policy retained. Git data API requests not live-tested; branch can exist after failure, and cancellation after dispatch cannot undo GitHub writes. No auto merge. Filters CI results to the extracted source build workflow. Full repository dependency graph remains unimplemented.
- Added Settings > System & Engineering portable backup interface, retained prior 15 menus, existing themes and removals.
- Version code 42, Python 3.12 pinned in both workflow variants to preserve V41 CI fix. Updated release signer version check. Includes extracted source workflow and archive workflow in same ZIP. Archive manifest verifies file consistency, not publisher authenticity; pin ZIP SHA separately when committing workflow.

## Verification here
- 12/12 standalone JVM portable backup cryptography checks: Telugu round-trip, randomized ciphertext, wrong password, tamper detection, malformed/truncated archive and size/password boundaries.
- Five-table desktop SQLite index preservation check, 12 XML files parsed, shell syntax checked. Android JUnit test added, not run here. No Android SDK/Gradle/device or credentials available. No verified APK produced.

## Outstanding blockers and acceptance
- Sherpa-ONNX ASR and neural TTS NOT integrated. Existing Microsoft ONNX Runtime 1.22.0 used by Silero/openWakeWord must remain intact. Sherpa AAR can bundle a conflicting libonnxruntime.so; do not use arbitrary pickFirst. Need a compatible separately built native library, vetted Telugu/English models and runtime tests. Reference: https://github.com/k2-fsa/sherpa-onnx/issues/3261 and https://github.com/k2-fsa/sherpa-onnx/issues/3291 .
- V41 openWakeWord and MediaPipe local model paths remain conditional: optional non-commercial wake test and user-supplied compatible .task LLM; weights not bundled; no mobile inference verification. Vosk and Android TTS remain existing fallback. Silero only in Gemini Live inactivity path.
- Complete unified voice engine, full premium UI and submenu requirements, full SQLite encryption and recovery, complete repository dependency analysis, production signing and full Android 16 tests remain incomplete. Do not claim that all 33 targets are implemented or verified. Prior reports document earlier changes; only checks explicitly recorded here were rerun in this revision.

## Build from one ZIP
1. Upload this ZIP to repo root, unchanged filename. For archive-only repo, copy `ci/archive-build-workflow.yml` from ZIP into `.github/workflows/jarvis-infinity-v42-level6-master.yml`. For extracted repo, use `.github/workflows/build-apk.yml` included in ZIP instead.
2. Run workflow; mandatory model CPU checks and Gradle tests must pass before debug APK artifact. Neural wake test is optional non-commercial only. Owner production signing requires original keystore and secrets and is disabled by default.
3. Device test: upgrade from same-certificate V41, chat/memory preservation, file backup round-trip and wrong-password rejection, Telugu+English capture, background wake with lock screen, TTS, offline model, Silero, persistent task cancellation/reboot, all themes/settings, accessibility and crash/battery profiling. Do not uninstall the existing app before safely exporting data; debug CI keys may differ.
