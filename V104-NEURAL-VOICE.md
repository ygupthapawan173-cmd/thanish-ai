# V104 installed neural voice integration + UI contrast

This is an Android source candidate based on V103, not an installed/tested APK.

* The existing Android TextToSpeech path now selects a user-chosen installed engine per reply language (English or Telugu script). It rebinds on engine change, invalidates stale init callbacks, and filters the voice catalog by requested engine and language. Android may fall back to its default engine when binding fails; the requested engine label is not proof of actual engine identity. Preview actual audio before use.
* Voice settings links open the official HayaiTTS release page (English neural models such as Kokoro/Piper) and Hear2Read Android setup page (Telugu voice data). Install the third-party engine and its model yourself; no models are bundled or downloaded by JARVIS. Telugu neural quality depends on the selected engine/model; this is not a guarantee of NG model availability. Third-party model licenses and requirements apply.
* The previous paid cloud voice opt-in is reset by requiring an explicit V104 opt-in. When off, the existing cloud speech policy is denied and system TTS candidates reporting a network requirement are rejected. Engine-reported capability is not a security proof; separately installed engines have their own behavior. Chat and any explicitly enabled Gemini Live connection may still use API credits. Local Brain and offline question recognition remain removed. Existing local wake detector stays intact.
* Voice pack panel explicitly supplies light text, contrast-safe menu surfaces, disabled selection text and readable switch labels. Settings category wake description corrected. No device screenshot verification yet.

## Setup
1. Install a compatible Android TTS engine from its official page and download the desired voice inside it. For English try Kokoro through HayaiTTS. For Telugu select an installed Telugu-capable engine (Hear2Read offers Telugu voice data; listen to a sample first).
2. JARVIS > Voice & Audio > choose English/Telugu > choose installed engine > preview to load its voice catalog > select an installed offline-capable voice > preview again. Do this for both languages. Keep paid cloud voice OFF. Leave Gemini Live OFF if no cloud audio cost is wanted. AICredits remains the online AI brain.
3. Check that the preview plays and that the AICredits dashboard shows no audio/speech request; normal chat usage is still billed. Test English/Telugu alternating, wake acknowledgement, background replies, cancellation and app restart. If no voice is available, install voice data or choose Android default. No cloud fallback is used without explicit opt-in.

## Verification
63/63 pure Kotlin policy assertions; 27/27 new static integration guards; previous source guards passed. Added three JUnit test methods for CI, not run locally. Android compilation, real model installation, UI screenshots, actual speech playback and latency not verified. No claims of bundled native Kokoro, Piper or Indic neural model inference.
