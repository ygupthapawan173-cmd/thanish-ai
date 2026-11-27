from pathlib import Path

root = Path(".")
gradle = (root / "app/build.gradle.kts").read_text()
p = root / "app/src/main/java/com/example"

def r(rel: str) -> str:
    return (p / rel).read_text()

checks = [
    'applicationId = "com.aistudio.pandubabu.omega.v19ultra"' in gradle,
    'versionCode = 119' in gradle,
    'versionName = "V110.1 PREMIUM SPEED MASTER"' in gradle,
    # Module 1 & 8: Smart API Slot Manager + latency cache + streaming + dedupe
    'class SlotRegistryEngine' in r("ai/SmartApiSlotManager.kt"),
    'object SmartApiSlotManager' in r("ai/SmartApiSlotManager.kt"),
    all(k in r("ai/SmartApiSlotManager.kt") for k in (
        "GEMINI_DIRECT", "AICREDITS", "ONEAI_KEY", "OPENROUTER", "OPENAI", "CUSTOM_OPENAI"
    )),
    'MODEL_CACHE_TTL_MS' in r("ai/SmartApiSlotManager.kt"),
    'postGeminiStreaming(' in r("ai/PanduAiRepository.kt"),
    'tryAdditionalGatewaySlots(' in r("ai/PanduAiRepository.kt"),
    # Module 2: Premium Voice Studio + Onyx + actual error surfacing
    'PREMIUM VOICE STUDIO' in r("ui/VoicePackPanel.kt"),
    'VoiceStudioTab' in r("ui/VoicePackPanel.kt"),
    'SUPPORTED_TTS_BASES' in r("voice/CloudVoicePolicy.kt"),
    'selectForBase(' in r("voice/CloudVoicePolicy.kt"),
    'providerMsg' in r("voice/CloudVoiceClient.kt"),
    'lastCloudError = e' in r("voice/TeluguTtsController.kt"),
    # Module 3: Wake word preserves screen + floating animated HUD + lock-screen local reminder
    'preserveScreen: Boolean' in r("ui/PanduViewModel.kt"),
    'JarvisFloatingVoiceOverlay(' in r("ui/OmegaDashboardApp.kt"),
    'showFloatingVoiceHud' in r("ui/OmegaDashboardApp.kt"),
    'lockedReminder(' in r("omega/OnlineBackgroundVoiceService.kt"),
    # Module 4 & 5: Auto-save + honest verification status + compact Automation/Lab/Settings
    'first_launch_perms_done' in r("ui/OmegaDashboardApp.kt"),
    'val lastVerificationBadge: String = "Key Required"' in r("ui/PanduViewModel.kt"),
    'SmartApiSlotManagerPanel(' in r("ui/SettingsScreen.kt"),
    'Auto-Save' in r("ui/SettingsScreen.kt"),
    'AutomationTab' in r("ui/RoutinesAndMemoryScreen.kt"),
    'LabTab' in r("ui/AutonomousLabScreen.kt"),
    # Module 7: Design system palette
    'MidnightNavy = Color(0xFF080E1D)' in r("ui/theme/OmegaTheme.kt"),
    'DeepBlue = Color(0xFF12233F)' in r("ui/theme/OmegaTheme.kt"),
    'ElectricBlue = Color(0xFF2563FF)' in r("ui/theme/OmegaTheme.kt"),
    'NeonCyan = Color(0xFF00E5FF)' in r("ui/theme/OmegaTheme.kt"),
    'RoyalViolet = Color(0xFF8B5CF6)' in r("ui/theme/OmegaTheme.kt"),
    'IceWhite = Color(0xFFE8F4FF)' in r("ui/theme/OmegaTheme.kt"),
    not (p / "ai/LocalBrainEngine.kt").exists(),
]

for i, ok in enumerate(checks, 1):
    assert ok, f"V110.1 check #{i} failed"

print(f"{len(checks)}/{len(checks)} V110.1 PREMIUM SPEED MASTER source checks passed")
