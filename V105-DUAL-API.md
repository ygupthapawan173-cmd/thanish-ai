# V105 Dual API voice candidate

- Source base: V104 Neural Voice & UI. Existing gateway chat and actions remain unchanged.
- Save your Google key in the existing encrypted Gemini slot; keep Smart Gateway selected and Gemini Live duplex OFF. Enable Gemini TTS under Voice & Audio. Choose separate Telugu and English speakers from the 30 documented Gemini TTS voices.
- Direct Gemini 2.5 Flash Preview TTS HTTPS generateContent request uses the Google key in a header, does not redirect or retry, bounds response data, converts returned 24 kHz mono PCM to WAV, and reuses existing cancellable MediaPlayer playback.
- Enabling Gemini TTS disables paid AICredits speech. Failure before playback uses installed TTS, never another paid speech request. Existing 30-second cloud cooldown and privacy checks remain. Replies over 1000 characters use installed TTS; speech after partial playback failure is not repeated.
- Google may bill a paid project; free-tier quota is account dependent. Text to be spoken is sent to Google. The app cannot verify the billing tier. AICredits chat usage still costs credits. No voice models are bundled.
- Existing V104 UI contrast changes and V103 hands-free wake are preserved; no Local Brain. Background/screen-lock limitations are unchanged.
- Verification: 397/397 pure JVM policy/WAV assertions and 26/26 V105 static guards passed. Earlier static guards passed with updated version expectations. Three new JUnit tests added for CI. No live Google request, Android compilation or physical-device playback/UI test performed locally.
- Docs: https://ai.google.dev/gemini-api/docs/speech-generation and https://ai.google.dev/gemini-api/docs/pricing/
