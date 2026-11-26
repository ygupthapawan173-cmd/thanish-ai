#!/usr/bin/env python3
"""Static review gates. Android unit tests and APK compilation are separate CI steps."""
from pathlib import Path

root = Path("app/src/main")
def read(file):
    return (root / file).read_text(encoding="utf-8")

ai = read("java/com/example/ai/AiProviderSettingsCard.kt")
omega = read("java/com/example/omega/OmegaControlActivity.kt")
wake = read("java/com/example/omega/OmegaWakeService.kt")
voice = read("java/com/example/voice/VoiceInputController.kt")
viewmodel = read("java/com/example/ui/PanduViewModel.kt")
nav = read("java/com/example/ui/components/CyberpunkComponents.kt")
screen = read("java/com/example/ui/PanduDashboardScreen.kt")
strings = read("res/values/strings.xml")
key_store = read("java/com/example/ai/EncryptedApiKeyStore.kt")

assert "4 API SLOTS" not in ai and "AiProvider.values().forEach" not in ai
assert "OMEGA • AI CONNECTION" in ai and "EncryptedApiKeyStore" in ai
assert all(k in key_store for k in ["saveApiKey", "getApiKey", "getActiveProvider"])
assert 'listOf("Master", "Voice", "Memory", "System")' in omega
tabs = read("java/com/example/ui/components/CommandCenterTabs.kt")
orb = read("java/com/example/ui/components/OmegaPlasmaOrb.kt")
master = read("java/com/example/omega/OmegaMasterDashboard.kt")
safe_orb = read("java/com/example/ui/components/OmegaSafeSingularityVisual.kt")
premium_home = read("java/com/example/ui/components/OmegaR104PremiumHome.kt")
assert "OmegaSafeSingularityVisual(" in premium_home and "Canvas(" in safe_orb
assert "JarvisStatus.LISTENING" in safe_orb and "amplitude" in safe_orb
assert "OmegaMasterDashboard(" in omega and "EncryptedApiKeyStore" in master
assert "PanduAccessibilityService.isConnected.collectAsState()" in master
assert "OmegaWakePhrase.entries" in omega and "setWakePhrase" in omega
recognizer = read("java/com/example/voice/VoiceInputController.kt")
assert 'AUTO("te-IN", "TE+EN")' in recognizer and 'locale == SpeechLocaleOption.AUTO' in recognizer
assert "SpeechLocaleOption.AUTO" in viewmodel
repository = read("java/com/example/ai/PanduAiRepository.kt")
assert "AiRetryPolicy.retryDelayMs" in repository and "accumulated.isNotEmpty()" in repository
assert "OmegaContext.recent" in repository and "ReplyPreferences.get" in repository
assert "OmegaWakeEngine.match" in wake and "WakeGate(1600L)" in wake
assert "lastRoute.collectAsState()" in master
premium_home = read("java/com/example/ui/components/OmegaR104PremiumHome.kt")
premium_chrome = read("java/com/example/ui/components/OmegaR104Chrome.kt")
screen = read("java/com/example/ui/PanduDashboardScreen.kt")
assert "OmegaR104PremiumHome(" in tabs
assert "OmegaR104TopBar(" in screen and "OmegaR104BottomNavBar(" in screen
assert "OmegaR104SettingsHeader(" in tabs and "onNavigateOmega" in tabs
assert "lastRoute.collectAsState()" in premium_home and "lastRoute.collectAsState()" in premium_chrome
assert "OmegaWakeService.active.collectAsState()" in premium_home
assert "onClick=onMicClick" in premium_home
assert "onWhatsAppShortcut" in premium_home and "onYouTubeShortcut" in premium_home
assert "onNavigateToApps" in premium_home and "onSpeakBattery" in premium_home
assert "recognitionRestarts >= 2" in wake and "recoverOfflineRecognition" in wake
print("PASS R104 premium Home, neon chrome, real settings status and bounded voice recovery")
print("PASS R100 unified UI, locale, wake and AI safeguards")
assert '"Coding"' not in omega
assert 'OMEGA(R.string.nav_omega' in nav and "com.example.omega.OmegaCenter(" in screen and "embedded = true" in screen
assert 'OMEGA JARVIS' in strings and 'com.example.omega.OmegaWakeService' in screen
assert 'override fun onPartialResult(hypothesis: String?)' in wake
assert 'TextToSpeech.QUEUE_FLUSH' in wake and 'lastWakeAt' in wake
assert 'RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH' in voice
assert 'forcePlatform = true' in voice and 'OmegaVoiceMode.HYBRID' in voice
assert 'setContinuous' in omega and 'scheduleFollowupIfEnabled' in viewmodel
assert 'Lifecycle.Event.ON_STOP' in screen and 'viewModel.cancelVoiceListening()' in screen
permissions = read("java/com/example/ui/components/OmegaPermissionCenter.kt")
manifest = read("AndroidManifest.xml")
tabs = read("java/com/example/ui/components/CommandCenterTabs.kt")
assert 'android.permission.RECORD_AUDIO' in manifest
assert 'android.permission.POST_NOTIFICATIONS' in manifest
assert 'android.permission.FOREGROUND_SERVICE_MICROPHONE' in manifest
assert '.PanduAccessibilityService' in manifest
for required in ["OmegaPermissionCenter", "ActivityResultContracts.RequestPermission",
                 "Settings.ACTION_ACCESSIBILITY_SETTINGS",
                 "Settings.ACTION_MANAGE_WRITE_SETTINGS",
                 "Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
                 "NotificationManagerCompat.from",
                 "Lifecycle.Event.ON_RESUME",
                 "canWrite(context)",
                 "isAccessibilityConnected",
                 "appSettingsIntent"]:
    assert required in permissions, required
assert 'OmegaPermissionCenter(' in tabs
assert 'onMicPermissionChanged' in tabs
assert 'onOpenPermissions = { selectedTab = CyberpunkTab.SETTINGS }' in screen
print("PASS OMEGA permission center wiring + Android manifest checks")
print("PASS OMEGA review UI/slots/branding/voice/privacy regression checks")

assert 'R105 · NEURAL COMMAND' in premium_chrome
assert 'r105_orb' in premium_home and 'requestState.collectAsState()' in premium_home
assert 'recognitionReady' in wake and 'reconnectPending' in wake
assert 'animateScrollToItem(destination)' in tabs
print('PASS R105 source wiring; runtime and visual verification are separate')
