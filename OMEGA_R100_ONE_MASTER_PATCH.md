# OMEGA JARVIS · R100 Single Master Patch

## Rule
**One master upgrade branch, one combined commit, one draft review PR, no merge.**
Ten workstreams are integrated into a *single patch artifact* rather than ten
separate PRs. The list below is a QA acceptance checklist, **not a claim that
100 separate implementations passed**.

## Core changes included in the combined source patch
- Live Canvas liquid-plasma-inspired orb, driven by real VoiceInputState/JarvisStatus.
  GPU-rendered 2D effect; not a true 3D renderer.
- New OMEGA Master tab with local key status, selected model, Android mic,
  accessibility, RAM, battery, encrypted-note count, real wake state and refresh.
  Saved keys are shown as configured, NOT proven online.
- AUTO, Telugu, English speech input selection. AUTO asks Android 14+ language
  switching where supported by the installed SpeechRecognizer. Older Android
  versions remain Telugu-first.
- User-selectable English Vosk wake phrases Hey Jarvis / Hey Omega / Omega.
  No trained custom Telugu wake model is bundled. Android foreground restrictions remain.
- AI 503 bounded retry (only before response text; no partial response replay).
- Existing encrypted keys/models remain intact; new unsaved Gemini configurations
  default to the user's confirmed working gemini-3.5-flash-lite.
- Existing consent-based phone automation, notebook, notifications, permissions,
  diagnostics and two optional APK artifact modes remain.

## 100 Acceptance targets grouped as ten workstreams
Every numbered item remains **subject to code review and device verification**.
Features present in the base application are not counted as newly built.

### Master Workstream 1: Plasma Orb & Home
1. Master home — acceptance pending
2. Liquid plasma canvas — acceptance pending
3. Omega brand core — acceptance pending
4. Idle glow — acceptance pending
5. RMS audio reaction — acceptance pending
6. Thinking state — acceptance pending
7. Speaking state — acceptance pending
8. Particle accents — acceptance pending
9. Performance profiling — acceptance pending
10. Responsive layouts — acceptance pending

### Master Workstream 2: Neural Voice Interface
11. Waveform — acceptance pending
12. Transcript — acceptance pending
13. Mic start — acceptance pending
14. Mic stop — acceptance pending
15. Speech timeout — acceptance pending
16. TTS control — acceptance pending
17. Audio focus — acceptance pending
18. Interrupt reply — acceptance pending
19. Voice diagnostics — acceptance pending
20. Speech session status — acceptance pending

### Master Workstream 3: Ultra AI Dashboard
21. Model selection — acceptance pending
22. Key configuration status — acceptance pending
23. Wake state — acceptance pending
24. Battery state — acceptance pending
25. RAM monitor — acceptance pending
26. Local memory count — acceptance pending
27. Accessibility state — acceptance pending
28. Permission shortcut — acceptance pending
29. Error diagnostics — acceptance pending
30. Real status refresh — acceptance pending

### Master Workstream 4: Autonomous Phone Controls
31. App open — acceptance pending
32. Flashlight — acceptance pending
33. Volume — acceptance pending
34. Brightness — acceptance pending
35. Phone settings — acceptance pending
36. Approved accessibility gestures — acceptance pending
37. Safe agent planning — acceptance pending
38. Consent barriers — acceptance pending
39. Task progress — acceptance pending
40. Error recovery — acceptance pending

### Master Workstream 5: Cinematic Navigation
41. Neon palette — acceptance pending
42. Premium bottom bar — acceptance pending
43. Home cards — acceptance pending
44. Chat cards — acceptance pending
45. Settings dashboard — acceptance pending
46. Language setting — acceptance pending
47. Voice engine settings — acceptance pending
48. Wake settings — acceptance pending
49. AI connection settings — acceptance pending
50. Privacy center — acceptance pending

### Master Workstream 6: Telugu English Engine
51. Telugu recognition — acceptance pending
52. English recognition — acceptance pending
53. Auto language selection — acceptance pending
54. Service-dependent bilingual switch — acceptance pending
55. Hybrid routing — acceptance pending
56. On-device-only mode — acceptance pending
57. Telugu TTS — acceptance pending
58. English TTS — acceptance pending
59. Mixed transcripts — acceptance pending
60. Continuous conversation opt-in — acceptance pending

### Master Workstream 7: Offline Wake Word
61. Vosk English asset — acceptance pending
62. Hey Jarvis — acceptance pending
63. Hey Omega — acceptance pending
64. Omega keyword — acceptance pending
65. Partial wake handling — acceptance pending
66. False-positive suppression — acceptance pending
67. Wake acknowledgement — acceptance pending
68. Foreground mic notification — acceptance pending
69. Lockscreen device tests — acceptance pending
70. Battery endurance tests — acceptance pending

### Master Workstream 8: AI Intelligence & Memory
71. Gemini configured model — acceptance pending
72. OpenAI fallback — acceptance pending
73. OpenRouter fallback — acceptance pending
74. Groq fallback — acceptance pending
75. Encrypted provider vault — acceptance pending
76. 503 backoff retry — acceptance pending
77. 429 rate-limit handling — acceptance pending
78. Context-aware replies — acceptance pending
79. Encrypted notebook — acceptance pending
80. Tool-call approvals — acceptance pending

### Master Workstream 9: Android 16 & Security
81. Microphone permission — acceptance pending
82. Notification permission — acceptance pending
83. Accessibility consent — acceptance pending
84. Write-settings access — acceptance pending
85. Battery Settings access — acceptance pending
86. Foreground service lifecycle — acceptance pending
87. Audio cleanup — acceptance pending
88. Safe network retries — acceptance pending
89. Privacy scrub — acceptance pending
90. Crash recovery — acceptance pending

### Master Workstream 10: Testing & Delivery
91. Static check — acceptance pending
92. Unit tests — acceptance pending
93. Compile — acceptance pending
94. Sign APK — acceptance pending
95. Bundled Vosk — acceptance pending
96. Phone install — acceptance pending
97. Voice language test — acceptance pending
98. Wake lock screen test — acceptance pending
99. Permissions verification — acceptance pending
100. User review before merge — acceptance pending

## Explicitly unverified before release
- Full screenshot pixel-for-pixel match; design is an in-house implementation.
- Continuous on-device Telugu recognition (Telugu Vosk model not bundled).
- Custom wake-word acoustic models and low-power always-on microphone.
- Locked-screen, background recording, OEM battery restrictions on Android 16.
- Exact device GPU performance and animation frame-rate.
- Accuracy, model access, request quota and Gemini API test on the user's phone.
- Accessibility auto-OFF behavior and long-session reliability on user's device.
- Actual phone app install/update and signature compatibility.

## Release checklist
1. CI static gate + Android unit tests + full Kotlin compile.
2. Signed APK and bundled model verification.
3. Isolated diagnostic package for testing without uninstalling existing app.
4. User tests UI, bilingual recognition, voice replies, wake word, permissions,
   saved API key and phone controls.
5. Only after explicit approval consider PR merge. Do not silently self-update.
