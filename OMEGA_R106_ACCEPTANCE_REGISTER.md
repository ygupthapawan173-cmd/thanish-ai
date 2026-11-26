# OMEGA R106 — Individual R100 acceptance register

Baseline: R105 b65b9acaaccc9786ebb6abe20904b688a95705bf. The 100 original titles are retained exactly. SOURCE = source marker observed in audited source (not behavior PASS); RECHECK = specific source candidate needs targeted verification; GAP/DEVICE = missing implementation or manual test. These are acceptance procedures, not 100 executed automated tests. No target is marked accepted for R106 yet.

| ID | Original target | Evidence or source candidate | State | Acceptance procedure |
|---:|---|---|---|---|
| 1 | Master home | [OmegaR104PremiumHome.kt:50](app/src/main/java/com/example/ui/components/OmegaR104PremiumHome.kt#L50) · `OmegaR104PremiumHome` | SOURCE | Open Home and check orb and controls |
| 2 | Liquid plasma canvas | [OmegaSafeSingularityVisual.kt:4](app/src/main/java/com/example/ui/components/OmegaSafeSingularityVisual.kt#L4) · `Canvas` | SOURCE | Inspect plasma visual; true GPU mesh not delivered |
| 3 | Omega brand core | [OmegaR104Chrome.kt:77](app/src/main/java/com/example/ui/components/OmegaR104Chrome.kt#L77) · `OMEGA` | SOURCE | Check branding on header and launch screen |
| 4 | Idle glow | [OmegaSafeSingularityVisual.kt:40](app/src/main/java/com/example/ui/components/OmegaSafeSingularityVisual.kt#L40) · `breathe` | SOURCE | Observe idle animation |
| 5 | RMS audio reaction | [VoiceInputController.kt:108](app/src/main/java/com/example/voice/VoiceInputController.kt#L108) · `onRmsChanged` | SOURCE | Vary speaking level; compare waveform |
| 6 | Thinking state | [OmegaR104PremiumHome.kt:80](app/src/main/java/com/example/ui/components/OmegaR104PremiumHome.kt#L80) · `THINKING` | SOURCE | Send question and check thinking indicator |
| 7 | Speaking state | [OmegaR104PremiumHome.kt:81](app/src/main/java/com/example/ui/components/OmegaR104PremiumHome.kt#L81) · `SPEAKING` | SOURCE | Play reply; check speaking indicator |
| 8 | Particle accents | [OmegaSafeSingularityVisual.kt:49](app/src/main/java/com/example/ui/components/OmegaSafeSingularityVisual.kt#L49) · `drawCircle` | SOURCE | Observe particles |
| 9 | Performance profiling | — | GAP/DEVICE | Measure frame times on device |
| 10 | Responsive layouts | [OmegaR104PremiumHome.kt:11](app/src/main/java/com/example/ui/components/OmegaR104PremiumHome.kt#L11) · `LazyColumn` | SOURCE | Test small display and large font |
| 11 | Waveform | [OmegaR104PremiumHome.kt:119](app/src/main/java/com/example/ui/components/OmegaR104PremiumHome.kt#L119) · `R104Waveform` | SOURCE | Confirm waveform reacts to microphone |
| 12 | Transcript | [VoiceInputController.kt:167](app/src/main/java/com/example/voice/VoiceInputController.kt#L167) · `onPartialResults` | SOURCE | Confirm partial and final transcript |
| 13 | Mic start | [VoiceInputController.kt:58](app/src/main/java/com/example/voice/VoiceInputController.kt#L58) · `startListening` | SOURCE | Start microphone; verify recognition |
| 14 | Mic stop | [VoiceInputController.kt:239](app/src/main/java/com/example/voice/VoiceInputController.kt#L239) · `stopListening` | SOURCE | Stop session and check microphone release |
| 15 | Speech timeout | [VoiceInputController.kt:224](app/src/main/java/com/example/voice/VoiceInputController.kt#L224) · `35000` | SOURCE | Verify timeout and release |
| 16 | TTS control | [PanduViewModel.kt:329](app/src/main/java/com/example/ui/PanduViewModel.kt#L329) · `stopVoice` | SOURCE | Start/stop TTS |
| 17 | Audio focus | — | GAP/DEVICE | Measure audio focus interruptions |
| 18 | Interrupt reply | [PanduViewModel.kt:321](app/src/main/java/com/example/ui/PanduViewModel.kt#L321) · `ttsController.stop()` | SOURCE | Tap mic during reply |
| 19 | Voice diagnostics | [OmegaControlActivity.kt:277](app/src/main/java/com/example/omega/OmegaControlActivity.kt#L277) · `Speech error:` | SOURCE | Induce error and inspect diagnostics |
| 20 | Speech session status | [VoiceInputController.kt:44](app/src/main/java/com/example/voice/VoiceInputController.kt#L44) · `engineStatus` | SOURCE | Check session and failure states |
| 21 | Model selection | [AiProviderSettingsCard.kt](app/src/main/java/com/example/ai/AiProviderSettingsCard.kt) · `testProvider` | SOURCE | Select working model and persist |
| 22 | Key configuration status | [EncryptedApiKeyStore.kt](app/src/main/java/com/example/ai/EncryptedApiKeyStore.kt) · `hasApiKey` | SOURCE | Confirm configuration without exposing secret |
| 23 | Wake state | [OmegaWakeService.kt:31](app/src/main/java/com/example/omega/OmegaWakeService.kt#L31) · `recognitionReady` | SOURCE | Start and stop Vosk |
| 24 | Battery state | [BatteryMonitorHelper.kt](app/src/main/java/com/example/control/BatteryMonitorHelper.kt) · `batteryState` | SOURCE | Compare battery percentage |
| 25 | RAM monitor | [OmegaMasterDashboard.kt](app/src/main/java/com/example/omega/OmegaMasterDashboard.kt) · `freeRam` | SOURCE | Compare RAM |
| 26 | Local memory count | [OmegaMasterDashboard.kt](app/src/main/java/com/example/omega/OmegaMasterDashboard.kt) · `noteCount` | SOURCE | Add note and check count |
| 27 | Accessibility state | [PanduAccessibilityService.kt:183](app/src/main/java/com/example/control/PanduAccessibilityService.kt#L183) · `isConnected` | SOURCE | Compare enabled and bound states |
| 28 | Permission shortcut | [OmegaPermissionCenter.kt](app/src/main/java/com/example/ui/components/OmegaPermissionCenter.kt) · `ACTION_ACCESSIBILITY_SETTINGS` | SOURCE | Open Android settings |
| 29 | Error diagnostics | [OmegaControlActivity.kt:277](app/src/main/java/com/example/omega/OmegaControlActivity.kt#L277) · `Speech error:` | SOURCE | Induce recognition error |
| 30 | Real status refresh | [OmegaMasterDashboard.kt](app/src/main/java/com/example/omega/OmegaMasterDashboard.kt) · `Refresh real system status` | SOURCE | Refresh metrics |
| 31 | App open | [PhoneControlManager.kt](app/src/main/java/com/example/control/PhoneControlManager.kt) · `openApp` | SOURCE | Open app; missing app fails |
| 32 | Flashlight | [PhoneControlManager.kt](app/src/main/java/com/example/control/PhoneControlManager.kt) · `setTorchEnabled` | SOURCE | Toggle flash with hardware |
| 33 | Volume | [PhoneControlManager.kt](app/src/main/java/com/example/control/PhoneControlManager.kt) · `adjustVolume` | SOURCE | Verify OS volume |
| 34 | Brightness | [PhoneControlManager.kt](app/src/main/java/com/example/control/PhoneControlManager.kt) · `setSystemBrightnessPercent` | SOURCE | Verify permission and brightness |
| 35 | Phone settings | [PhoneControlManager.kt](app/src/main/java/com/example/control/PhoneControlManager.kt) · `openDeviceSetting` | SOURCE | Open settings |
| 36 | Approved accessibility gestures | [PanduAccessibilityService.kt:271](app/src/main/java/com/example/control/PanduAccessibilityService.kt#L271) · `performGlobalNav` | SOURCE | Test consented back/home |
| 37 | Safe agent planning | [PanduAiRepository.kt](app/src/main/java/com/example/ai/PanduAiRepository.kt) · `propose` | RECHECK | Inspect proposed action; never auto-execute |
| 38 | Consent barriers | [PanduViewModel.kt:82](app/src/main/java/com/example/ui/PanduViewModel.kt#L82) · `pendingSensitiveAction` | SOURCE | Cancel and approve sensitive action |
| 39 | Task progress | [PanduViewModel.kt:129](app/src/main/java/com/example/ui/PanduViewModel.kt#L129) · `agentSession` | SOURCE | Check task progress |
| 40 | Error recovery | [PanduViewModel.kt:314](app/src/main/java/com/example/ui/PanduViewModel.kt#L314) · `retryLastFailed` | SOURCE | Induce failure and retry |
| 41 | Neon palette | [Color.kt](app/src/main/java/com/example/ui/theme/Color.kt) · `ElectricCyan` | SOURCE | Inspect visual palette |
| 42 | Premium bottom bar | [OmegaR104Chrome.kt:117](app/src/main/java/com/example/ui/components/OmegaR104Chrome.kt#L117) · `OmegaR104BottomNavBar` | SOURCE | Navigate every tab |
| 43 | Home cards | [OmegaR104PremiumHome.kt:165](app/src/main/java/com/example/ui/components/OmegaR104PremiumHome.kt#L165) · `R104Quick` | SOURCE | Use shortcuts |
| 44 | Chat cards | [ChatBubble.kt](app/src/main/java/com/example/ui/components/ChatBubble.kt) · `ChatMessageBubble` | SOURCE | Check message cards |
| 45 | Settings dashboard | [CommandCenterTabs.kt](app/src/main/java/com/example/ui/components/CommandCenterTabs.kt) · `animateScrollToItem` | RECHECK | Check settings section navigation |
| 46 | Language setting | [PanduDashboardScreen.kt](app/src/main/java/com/example/ui/PanduDashboardScreen.kt) · `onToggleLocale` | RECHECK | Cycle locales |
| 47 | Voice engine settings | [OmegaControlActivity.kt:136](app/src/main/java/com/example/omega/OmegaControlActivity.kt#L136) · `OmegaVoiceMode.entries` | SOURCE | Switch voice mode |
| 48 | Wake settings | [OmegaVoiceMode.kt](app/src/main/java/com/example/omega/OmegaVoiceMode.kt) · `setWakePhrase` | RECHECK | Set phrase and test |
| 49 | AI connection settings | [AiProviderSettingsCard.kt](app/src/main/java/com/example/ai/AiProviderSettingsCard.kt) · `AiProviderSettingsCard` | SOURCE | Test configured provider |
| 50 | Privacy center | [OmegaPermissionCenter.kt](app/src/main/java/com/example/ui/components/OmegaPermissionCenter.kt) · `OmegaPermissionCenter` | SOURCE | Review controls; full privacy center still partial |
| 51 | Telugu recognition | [VoiceInputController.kt:34](app/src/main/java/com/example/voice/VoiceInputController.kt#L34) · `TELUGU` | SOURCE | Recognize Telugu on phone |
| 52 | English recognition | [VoiceInputController.kt:35](app/src/main/java/com/example/voice/VoiceInputController.kt#L35) · `ENGLISH_INDIA` | SOURCE | Recognize English on phone |
| 53 | Auto language selection | [VoiceInputController.kt:33](app/src/main/java/com/example/voice/VoiceInputController.kt#L33) · `AUTO` | SOURCE | Select AUTO |
| 54 | Service-dependent bilingual switch | [VoiceInputController.kt:177](app/src/main/java/com/example/voice/VoiceInputController.kt#L177) · `onLanguageDetection` | SOURCE | Inspect actual language callback |
| 55 | Hybrid routing | [VoiceInputController.kt:136](app/src/main/java/com/example/voice/VoiceInputController.kt#L136) · `OmegaRecoveryPolicy.platformFallback` | SOURCE | Induce local model failure and test fallback |
| 56 | On-device-only mode | [VoiceInputController.kt:69](app/src/main/java/com/example/voice/VoiceInputController.kt#L69) · `OmegaVoiceMode.LOCAL` | SOURCE | Airplane mode; no cloud fallback |
| 57 | Telugu TTS | [TeluguTtsController.kt](app/src/main/java/com/example/voice/TeluguTtsController.kt) · `configureTeluguLocaleAndVoices` | SOURCE | Hear Telugu reply |
| 58 | English TTS | [TeluguTtsController.kt](app/src/main/java/com/example/voice/TeluguTtsController.kt) · `Locale.forLanguageTag("en-IN")` | SOURCE | Hear English reply |
| 59 | Mixed transcripts | [VoiceInputController.kt:204](app/src/main/java/com/example/voice/VoiceInputController.kt#L204) · `EXTRA_ENABLE_LANGUAGE_SWITCH` | SOURCE | Speak mixed sentence |
| 60 | Continuous conversation opt-in | [PanduViewModel.kt:743](app/src/main/java/com/example/ui/PanduViewModel.kt#L743) · `scheduleFollowupIfEnabled` | SOURCE | Test opt-in follow-up and cancel |
| 61 | Vosk English asset | [build-apk.yml:126](.github/workflows/build-apk.yml#L126) · `model-en-us/am/final.mdl` | SOURCE | Inspect bundled model and ARM64 libs |
| 62 | Hey Jarvis | [OmegaVoiceMode.kt](app/src/main/java/com/example/omega/OmegaVoiceMode.kt) · `HEY_JARVIS` | RECHECK | Speak Hey Jarvis |
| 63 | Hey Omega | [OmegaVoiceMode.kt](app/src/main/java/com/example/omega/OmegaVoiceMode.kt) · `HEY_OMEGA` | RECHECK | Speak Hey Omega |
| 64 | Omega keyword | [OmegaVoiceMode.kt](app/src/main/java/com/example/omega/OmegaVoiceMode.kt) · `OMEGA` | RECHECK | Speak Omega |
| 65 | Partial wake handling | [OmegaWakeService.kt:174](app/src/main/java/com/example/omega/OmegaWakeService.kt#L174) · `onPartialResult` | SOURCE | Test partial hypothesis wake |
| 66 | False-positive suppression | [OmegaWakeEngine.kt](app/src/main/java/com/example/omega/OmegaWakeEngine.kt) · `WakeGate` | RECHECK | Test false positives and cooldown |
| 67 | Wake acknowledgement | [OmegaWakeService.kt:66](app/src/main/java/com/example/omega/OmegaWakeService.kt#L66) · `acknowledgeWake` | SOURCE | Test acknowledgement and echo |
| 68 | Foreground mic notification | [OmegaWakeService.kt:38](app/src/main/java/com/example/omega/OmegaWakeService.kt#L38) · `startForeground` | SOURCE | Verify notification and stop |
| 69 | Lockscreen device tests | — | GAP/DEVICE | Physical locked screen wake test |
| 70 | Battery endurance tests | — | GAP/DEVICE | Measure 30-minute battery drain |
| 71 | Gemini configured model | [EncryptedApiKeyStore.kt](app/src/main/java/com/example/ai/EncryptedApiKeyStore.kt) · `getModel` | SOURCE | Test configured Gemini model |
| 72 | OpenAI fallback | [PanduAiRepository.kt](app/src/main/java/com/example/ai/PanduAiRepository.kt) · `AiProvider.OPENAI` | RECHECK | Test configured OpenAI fallback |
| 73 | OpenRouter fallback | [PanduAiRepository.kt](app/src/main/java/com/example/ai/PanduAiRepository.kt) · `AiProvider.OPENROUTER` | RECHECK | Test configured OpenRouter fallback |
| 74 | Groq fallback | [PanduAiRepository.kt](app/src/main/java/com/example/ai/PanduAiRepository.kt) · `AiProvider.GROQ` | RECHECK | Test configured Groq fallback |
| 75 | Encrypted provider vault | [EncryptedApiKeyStore.kt](app/src/main/java/com/example/ai/EncryptedApiKeyStore.kt) · `AndroidKeyStore` | SOURCE | Verify encrypted key vault |
| 76 | 503 backoff retry | [AiRetryPolicy.kt](app/src/main/java/com/example/ai/AiRetryPolicy.kt) · `retryDelayMs` | SOURCE | Inject HTTP 503 before streaming |
| 77 | 429 rate-limit handling | [AiRetryPolicy.kt](app/src/main/java/com/example/ai/AiRetryPolicy.kt) · `429` | SOURCE | Inject HTTP 429 |
| 78 | Context-aware replies | [PanduAiRepository.kt](app/src/main/java/com/example/ai/PanduAiRepository.kt) · `OmegaContext.recent` | RECHECK | Ask contextual follow-up |
| 79 | Encrypted notebook | [OmegaNotebook.kt](app/src/main/java/com/example/omega/OmegaNotebook.kt) · `Cipher` | SOURCE | Save/read encrypted notebook |
| 80 | Tool-call approvals | [PanduViewModel.kt:82](app/src/main/java/com/example/ui/PanduViewModel.kt#L82) · `pendingSensitiveAction` | SOURCE | Decline proposed action; verify no execution |
| 81 | Microphone permission | [OmegaPermissionCenter.kt](app/src/main/java/com/example/ui/components/OmegaPermissionCenter.kt) · `RECORD_AUDIO` | SOURCE | Deny/grant microphone |
| 82 | Notification permission | [OmegaPermissionCenter.kt](app/src/main/java/com/example/ui/components/OmegaPermissionCenter.kt) · `POST_NOTIFICATIONS` | SOURCE | Deny/grant notification |
| 83 | Accessibility consent | [PanduAccessibilityService.kt:28](app/src/main/java/com/example/control/PanduAccessibilityService.kt#L28) · `agentScreenCaptureGranted` | SOURCE | Check explicit consent |
| 84 | Write-settings access | [OmegaPermissionCenter.kt](app/src/main/java/com/example/ui/components/OmegaPermissionCenter.kt) · `ACTION_MANAGE_WRITE_SETTINGS` | SOURCE | Deny/grant write settings |
| 85 | Battery Settings access | [OmegaPermissionCenter.kt](app/src/main/java/com/example/ui/components/OmegaPermissionCenter.kt) · `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` | SOURCE | Open battery setup |
| 86 | Foreground service lifecycle | [OmegaWakeService.kt:247](app/src/main/java/com/example/omega/OmegaWakeService.kt#L247) · `OmegaRecoveryPolicy.keepListeningAfterCapture` | SOURCE | Capture review and next wake |
| 87 | Audio cleanup | [OmegaWakeService.kt:252](app/src/main/java/com/example/omega/OmegaWakeService.kt#L252) · `onDestroy` | SOURCE | Verify recorder release |
| 88 | Safe network retries | [AiRetryPolicy.kt](app/src/main/java/com/example/ai/AiRetryPolicy.kt) · `retryDelayMs` | SOURCE | Test bounded retry |
| 89 | Privacy scrub | [PanduAccessibilityService.kt:38](app/src/main/java/com/example/control/PanduAccessibilityService.kt#L38) · `isPassword` | SOURCE | Verify password exclusion; broader privacy review |
| 90 | Crash recovery | — | GAP/DEVICE | Induce process crash; verify recovery |
| 91 | Static check | [build-apk.yml:93](.github/workflows/build-apk.yml#L93) · `omega_review_checks.py` | SOURCE | Run static gate |
| 92 | Unit tests | [build-apk.yml:99](.github/workflows/build-apk.yml#L99) · `testDebugUnitTest` | SOURCE | Run existing and new JVM tests |
| 93 | Compile | [build-apk.yml:112](.github/workflows/build-apk.yml#L112) · `assembleDebug` | SOURCE | Compile APK |
| 94 | Sign APK | [build-apk.yml:122](.github/workflows/build-apk.yml#L122) · `apksigner` | SOURCE | Verify signature |
| 95 | Bundled Vosk | [build-apk.yml:132](.github/workflows/build-apk.yml#L132) · `Offline model and ARM64 libraries verified` | SOURCE | Inspect bundled offline assets |
| 96 | Phone install | — | GAP/DEVICE | Install exact APK on physical phone |
| 97 | Voice language test | — | GAP/DEVICE | Physical Telugu-English test matrix |
| 98 | Wake lock screen test | — | GAP/DEVICE | Physical lockscreen wake test |
| 99 | Permissions verification | — | GAP/DEVICE | Physical permission test matrix |
| 100 | User review before merge | — | GAP/DEVICE | Obtain user review before merge |

Summary: 78 source markers observed; 12 require targeted source verification; 10 gap/device rows. R106 adds actual speech diagnostics, bounded hybrid fallback, opt-in ongoing wake with one pending-command guard, accessibility setting-versus-binding diagnostics, and four JVM policy tests. Full Telugu offline acoustic model, real GPU mesh, full interior UI overhaul, battery/locked-screen endurance, physical accessibility root cause and additional functional acceptance automation remain open. R105 phone feedback (4/7) is not R106 validation.
