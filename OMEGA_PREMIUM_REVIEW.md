# OMEGA Native Master — Premium review branch

Base: `omega-native-master`. Target: no automatic merge to main.

## User-selected removals (R1–R6, plus provider slots)

- R1 Coding self-evolution tab hidden from OMEGA navigation; no code execution or automatic updates.
- R2 Duplicate per-provider test cards removed from the OMEGA control surface; AI API configuration is consolidated.
- R3 Existing Android permission flows preserved; this review does not bypass permissions.
- R4 Basic UI refreshed with cyan/blue/violet holo core and OMEGA branding; not a true 3D renderer.
- R5 OMEGA controls embedded into the main bottom navigation instead of requiring a second screen.
- R6 Visible Pandu Babu branding replaced in header, app label, chat persona, phone-control labels and core badge. Kotlin package and application ID intentionally unchanged to preserve Android data.
- Four simultaneous AI API slot cards replaced by **one** AI connection panel with a provider choice menu. This is not deletion of the encrypted provider vault. Existing encrypted credentials and model selection/fallback remain usable.

## Requested additions

- A1: Animated premium cyan/blue/violet orb refresh. Not a 3D engine.
- A2: Offline Vosk wake with partial hypothesis detection; platform speech service supports optional online recognition when the user selects hybrid mode.
- A3: Android 14+ speech recognizer language detection / switch hints for Telugu and English. Requires device speech engine and packs; does not bundle a Telugu Vosk model.
- A4: Opt-in continuous follow-up listening after one assistant reply while foreground and permitted, with cancellation on screen exit/Stop. Long endurance still unverified.
- A5: Existing foreground microphone service and Android 16 manifest retained. Starting the service from the visible app is required; locked-screen endurance remains a device test.
- A6: Offline wake acknowledgement via Android TTS: “Yes, Sir!”; requires installed TTS.
- A9: Existing chat-microphone live RMS waveform retained; Vosk wake service does NOT provide live measured waveform amplitude.
- A10: One secure AI Connection section and OMEGA Voice, Memory, System tabs.
- A11: Live wake state, partial hypothesis, error, last-wake time, and system metrics.

## Verification needed

1. GitHub CI unit tests and Android debug APK compile.
2. Install without removing the working app where possible; debug signing certificates can differ across CI runs.
3. Android 16 wake, screen lock, mic Stop, TTS echo, Telugu/English, fallback privacy, battery drain.
4. Verify provider keys, memory, chat history, phone controls and accessibility preserved.

Do not merge this draft until the APK has passed real-device tests.
