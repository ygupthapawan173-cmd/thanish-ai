from pathlib import Path

root = Path(".")
gradle = (root / "app/build.gradle.kts").read_text()
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
p = root / "app/src/main/java/com/example"

def r(rel: str) -> str:
    return (p / rel).read_text()

gemini_policy = r("voice/GeminiSpeechPolicy.kt")
gemini_client = r("voice/GeminiSpeechClient.kt")
tts_ctrl = r("voice/TeluguTtsController.kt")
device_ctx = r("ai/DeviceContextProvider.kt")
ai_repo = r("ai/PanduAiRepository.kt")
dashboard = r("ui/OmegaDashboardApp.kt")
agent = r("autonomous/AntigravitySelfCodingAgent.kt")
self_coding = r("autonomous/SelfCodingEngine.kt")
cost_policy = r("ai/CostProtectionPolicy.kt")
quota_tracker = r("data/ApiQuotaTracker.kt")
updater = r("security/VerifiedApkUpdater.kt")

checks = [
    # App ID, version, SDK compatibility
    'applicationId = "com.aistudio.pandubabu.omega.v19ultra"' in gradle,
    'minSdk = 24' in gradle,
    'targetSdk = 36' in gradle,
    ('versionCode = 111' in gradle or 'versionCode = 112' in gradle or 'versionCode = 113' in gradle or 'versionCode = 114' in gradle or 'versionCode = 115' in gradle or 'versionCode = 116' in gradle or 'versionCode = 117' in gradle or 'versionCode = 118' in gradle or 'versionCode = 119' in gradle),
    ('versionName = "v106"' in gradle or 'versionName = "v107"' in gradle or 'versionName = "v107.1"' in gradle or 'versionName = "v107.2"' in gradle or 'versionName = "V108 ULTIMATE MASTER AI"' in gradle or 'versionName = "V108.1"' in gradle or 'versionName = "V108.2"' in gradle or 'versionName = "V110 ULTRA INTELLIGENCE MASTER"' in gradle or 'versionName = "V110.1 PREMIUM SPEED MASTER"' in gradle),
    'val geminiApiKey = ""' in gradle and 'val openRouterApiKey = ""' in gradle,
    'android:usesCleartextTraffic="false"' in manifest,
    'android:enableOnBackInvokedCallback="true"' in manifest,
    'android.permission.REQUEST_INSTALL_PACKAGES' in manifest,

    # 1-3: Gemini Free TTS auto-switching, 30 voices, quota recovery & offline fallback
    'candidateModels(' in gemini_policy,
    'isQuotaError(' in gemini_policy,
    len(gemini_policy.split('val speakers = listOf(')[1].split(')')[0].split(',')) == 30,
    'synthesizeWithAutoSwitch(' in gemini_client,
    'synthesizeWithAutoSwitch(' in tts_ctrl,
    'QUOTA_COOLDOWN_MS' in tts_ctrl or 'QUOTA_COOLDOWN_MS' in gemini_policy,
    'speakInternal(clean)' in tts_ctrl,

    # 4: Live device date, time, battery, network context
    'Date=' in device_ctx and 'Time=' in device_ctx and 'Battery=' in device_ctx and 'Network=' in device_ctx,
    'getDeviceContextLine()' in ai_repo,

    # 5: Chat header overlap fix
    'Modifier.weight(1f)' in dashboard and 'TextOverflow.Ellipsis' in dashboard,

    # 6-8: Antigravity Self Coding Agent, user-approved self-upgrade, auto code review & regression
    'isWithinRestrictedWorkspace(' in agent,
    'reviewPatch(' in agent,
    'buildRegressionPlan(' in agent,
    'AntigravitySelfCodingAgent.reviewPatch(' in self_coding,
    'Mandatory SHA-256' in updater,
    'currentSigners == signers(incoming)' in updater,

    # 10: Cost protection & usage diagnostics
    'evaluateRequest(' in cost_policy,
    'CostProtectionPolicy.evaluateRequest(' in ai_repo,
    'recordTtsUsage(' in quota_tracker,
]

for i, ok in enumerate(checks, 1):
    assert ok, f"V106 check #{i} failed"

print(f"{len(checks)}/{len(checks)} V106 Master Upgrade source checks passed")
