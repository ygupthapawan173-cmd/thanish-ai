# Consolidated V100 source — audio continuation, not a final release

Base: JARVIS-INFINITY-V100-CONSOLIDATED-WORKING-SOURCE.zip; verified the original source manifest. All earlier changes and reports are preserved. No changes to version code, package name, repository or signing identity. This is not the final 1000-target delivery and is not for upload/build yet.

## Integrated changes
- Live Gemini capture: validate minimum recording buffer, reject negative, oversized or unaligned PCM reads, cap consecutive zero reads at 100 with 20 ms waits, report capture exceptions through existing session recovery, propagate coroutine cancellation and release echo/noise effects independently.
- Offline wake: clamp nonfinite sensitivity to a safe default, reject nonfinite/out-of-range confidence, apply consistent bounds to Vosk and optional neural wake. When owner filter is enabled, missing or unverified speaker vectors reject wake rather than proceeding to command capture. This is intentionally stricter; short phrases may now be rejected more often. Real owner false-accept/false-reject measurements and calibration required. Voice biometrics are not strong authentication.
- Neural wake capture: prevent duplicate worker starts for the same capture instance; allow retry if lease acquisition or thread startup fails.
- Bluetooth: report routing request/rejection rather than claiming physical microphone route is confirmed. Actual headset routing requires Android testing.
- Standalone audio gate (no active call site found in current source): reject invalid recording config/read size, bound empty reads, preserve cancellation, release effects/record independently; no claim of runtime benefit unless used.

## Tests run
- New pure JVM audio policy checks: 56/56 passed; matching JUnit test included for eventual Android CI.
- Previous continuation pure JVM checks: 50/50 passed.
- Previous V100 pure JVM checks: 221/221 passed.
- Source guards: V47 7 plus desktop SQLite collision example, V50 14/14, V100 15/15 passed.
- SQLite index SQL: five rows retained (desktop test, not Android migration).

No Android SDK/Gradle build or device test performed. These are policy checks and static guards, not features or measured speech accuracy. Remaining work includes actual language recognition, owner voice, background screen-lock, Bluetooth, live API, UI, agents, self-coding, self-update, performance and full integration. The 1,000 count remains an incomplete planning allocation. The inherited archive CI workflow still needs its final ZIP name and digest regenerated. Do not upload this working checkpoint; consolidate further work on it.
