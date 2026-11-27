# JARVIS INFINITY V48 — 20-target master patch, PARTIAL implementation candidate
Base: JARVIS-INFINITY-V47-FINAL-ULTIMATE-MASTER.zip from user's library. Its source manifest verified before edits. Source version name and application ID match the supplied V47 APK's reported metadata; reproducible binary equivalence not established. Full updated source tree, not a delta to apply over an APK. Existing file paths retained. App ID unchanged; versionCode 48. No new screen reader or UI redesign.

## Scope matrix (source changes do not imply device acceptance)
| # | Requested target | V48 status |
|---|---|---|
|1|Advanced Telugu recognition|Improved routing: prefer installed Android on-device Telugu recognizer; existing Vosk fallback. No new ASR model; accuracy not measured.|
|2|Telugu/English switching|Final transcript script updates preferred locale, existing Android language detection retained.|
|3|Offline wake word accuracy|Existing confidence gate retained; added bounded romanized Telugu inline commands. No new model or accuracy test.|
|4|Background voice stability|Transient wake failure no longer disables user preference after two retries; exponential delay capped at 32 sec, reset after stable arm. Android runtime pending.|
|5|Locked-screen reliability|Benefits from wake retry; existing offline privacy policy unchanged. Real locked-device tests pending.|
|6|Accessibility auto-OFF|Cancel old scope before reconnect and on unbind; log lifecycle. Root cause not established without device logs.|
|7|Microphone conflict|Bounded four handoff attempts, stale generation check; exclusive lease retained. Runtime pending.|
|8|Gemini Live reconnect|Distinct fallback once for HTTP 400/404, slower 429 retry, no 401/403 retry. Live API not tested.|
|9|Universal single API|Existing HTTPS OpenAI-compatible gateway preserved; no provider-specific compatibility verification.|
|10|Question-based model switching|Expanded Telugu code/reasoning classification; existing ranked routing retained.|
|11|Real model availability|404/model-specific 400 invalidates cached catalog and applies per-credential model cooldown. Existing live catalog API untested.|
|12|400/404/429 recovery|Recognized model-specific 400 only; bounded fallback, per-model cooldown honors numeric Retry-After within 1–300s. No retry of ambiguous network timeout.|
|13|API cost optimization|Avoid repeatedly selecting cooled models; existing budget safeguards retained (official OpenRouter only). No universal cost guarantee.|
|14|Battery optimization|Capped wake retry delay; lower rendering density in saver mode. Battery usage not measured.|
|15|Orb performance|Reduced particle and waveform density in existing saver mode; no FPS benchmark.|
|16|Personal memory|Unrelated memory no longer returned by fallback; cap approximate cosine and one stem bonus per query token.|
|17|Owner voice accuracy|Existing owner enrollment and verification preserved; no recalibration/new model; pending device tests.|
|18|Production security|Explicit cleartext prohibition and non-debug release configuration, corrected stale signing version. Original owner signing key required; no release produced.|
|19|APK size|No reduction performed; existing offline assets retained.|
|20|Device stability|Added regression tests and device checklist. Android 16 hardware execution pending.|

## Build and verification
1. Upload this ZIP into the repo root. Extract `ci/archive-build-workflow.yml` from ZIP into repo `.github/workflows/jarvis-infinity-v48-level6-master.yml`. Do not confuse a source ZIP upload with a completed build.
2. Run workflow via Actions. It verifies source hashes, provisions Vosk/VAD assets, runs neural model CPU check, migration guard, Gradle unit tests, assembles APK, verifies signature/version/assets, publishes `JARVIS-INFINITY-V48-LEVEL6-MASTER-TEST` artifact. For an extracted repo, `.github/workflows/build-apk.yml` also works.
3. No Android SDK/Gradle available in local analysis environment; Android compile, live API tests and APK production were not performed. The local JVM checks exercise selected policy functions only. See `V48-LOCAL-TESTS.txt`.
4. For production use original signing key and repository secrets defined in `ci/sign-release.sh`. A new debug certificate might prevent upgrading an installed APK. Back up data before updates; do not uninstall simply to bypass mismatch.
5. Do not label 20/20 complete; owner accuracy, new Telugu model, size reduction and actual hardware acceptance remain open. Do not automatically merge or release untested source.
