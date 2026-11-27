from pathlib import Path

root = Path(".")
gradle = (root / "app/build.gradle.kts").read_text()
p = root / "app/src/main/java/com/example"

def r(rel: str) -> str:
    return (p / rel).read_text()

brain = r("ai/MasterAiBrain.kt")
router = r("ai/SmartAiTaskRouter.kt")
cache = r("voice/VoiceResponseCache.kt")
gemini_client = r("voice/GeminiSpeechClient.kt")
handoff = r("autonomous/AntigravityTaskHandoff.kt")
ai_repo = r("ai/PanduAiRepository.kt")
vm = r("ui/PanduViewModel.kt")
lab = r("ui/AutonomousLabScreen.kt")

checks = [
    'applicationId = "com.aistudio.pandubabu.omega.v19ultra"' in gradle,
    ('versionCode = 115' in gradle or 'versionCode = 116' in gradle),
    ('versionName = "V108 ULTIMATE MASTER AI"' in gradle or 'versionName = "V108.1 WAKE RECOVERY"' in gradle),
    'classifyAndPlan(' in brain and 'detectResponseLanguage(' in brain,
    'SmartAiTaskRouter' in router and 'ANDROID_DIRECT_EXECUTION' in router and 'BLOCKED_ZERO_CREDITS' in router,
    'MasterAiBrain.classifyAndPlan(' in ai_repo and 'SmartAiTaskRouter.route(' in ai_repo,
    'VoiceResponseCache' in cache and 'VoiceResponseCache.get(' in gemini_client,
    'THINKING_EFFORTS' in handoff and 'MODEL_CATALOG' in handoff,
    'autoDownloadAndVerifyUpdate(' in vm and 'lastBrainRouteSummary' in vm,
    'Auto-Detect, Backup & Stage Verified APK' in lab and 'Thinking Effort' in lab,
    not (p / "ai/LocalBrainEngine.kt").exists(),
]

for i, ok in enumerate(checks, 1):
    assert ok, f"V108 check #{i} failed"

print(f"{len(checks)}/{len(checks)} V108 ULTIMATE MASTER AI source checks passed")
