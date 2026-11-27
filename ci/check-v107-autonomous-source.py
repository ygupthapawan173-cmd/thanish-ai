from pathlib import Path

root = Path(".")
gradle = (root / "app/build.gradle.kts").read_text()
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
p = root / "app/src/main/java/com/example"

def r(rel: str) -> str:
    return (p / rel).read_text()

zero_policy = r("ai/ZeroPaidCreditsPolicy.kt")
coding_switch = r("autonomous/CodingModelSwitchPolicy.kt")
handoff = r("autonomous/AntigravityTaskHandoff.kt")
voice_parser = r("autonomous/VoiceUpgradeIntentParser.kt")
prefs = r("data/OmegaPreferences.kt")
vm = r("ui/PanduViewModel.kt")
lab = r("ui/AutonomousLabScreen.kt")
app_ui = r("ui/OmegaDashboardApp.kt")
ai_repo = r("ai/PanduAiRepository.kt")
tts = r("voice/TeluguTtsController.kt")

checks = [
    # App ID, version, SDK compatibility
    'applicationId = "com.aistudio.pandubabu.omega.v19ultra"' in gradle,
    'minSdk = 24' in gradle,
    'targetSdk = 36' in gradle,
    ('versionCode = 113' in gradle or 'versionCode = 114' in gradle),
    ('versionName = "v107.1"' in gradle or 'versionName = "v107.2"' in gradle),
    'android:usesCleartextTraffic="false"' in manifest,

    # V107.1 Auto-connect, recovery, and live indicators
    'runWithAutoRecovery(' in r("ai/ConnectionRecoveryManager.kt"),
    'autoConnectAndRecover()' in vm,
    'Live AI Connection' in lab,

    # Zero additional paid credits mode
    'allowsPaidGateway(' in zero_policy and 'allowsPaidCloudVoice(' in zero_policy,
    'val zeroPaidCreditsMode: Boolean = true' in prefs,
    'ZeroPaidCreditsPolicy.allowsPaidCloudVoice(' in tts,
    'ZeroPaidCreditsPolicy.allowsPaidGateway(' in ai_repo,

    # Automatic coding model switching
    'markQuotaExhausted(' in coding_switch and 'nextAvailable(' in coding_switch,
    'codingModelPolicy' in ai_repo,

    # Manual CLI task handoff (ToS compliant, no OAuth extraction, useG1Credits:false)
    'useG1Credits' in handoff and '--sandbox' in handoff,
    'generateAntigravityHandoff(' in vm,
    'Generate Manual Antigravity CLI Handoff' in lab,

    # Voice-based self-upgrade, crash-fix planner & 3 repair attempts without repeated coding dialogs
    'VoiceUpgradeIntentParser.parse(' in vm,
    'analyzeLastCrashForFix(' in vm,
    'MAX_REPAIR_ATTEMPTS = 3' in r("autonomous/AutonomousUpgradeOrchestrator.kt"),
    'Fix Last Crash' in lab,
]

for i, ok in enumerate(checks, 1):
    assert ok, f"V107 check #{i} failed"

print(f"{len(checks)}/{len(checks)} V107 Autonomous Self Development source checks passed")
