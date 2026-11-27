from pathlib import Path

root = Path(".")
gradle = (root / "app/build.gradle.kts").read_text()
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
p = root / "app/src/main/java/com/example"

def r(rel: str) -> str:
    return (p / rel).read_text()

termux = r("autonomous/TermuxControlHelper.kt")
rollback = r("autonomous/RollbackPreparationManager.kt")
github = r("autonomous/GitHubEngineeringClient.kt")
lab = r("ui/AutonomousLabScreen.kt")
vm = r("ui/PanduViewModel.kt")

checks = [
    'applicationId = "com.aistudio.pandubabu.omega.v19ultra"' in gradle,
    'minSdk = 24' in gradle,
    'targetSdk = 36' in gradle,
    'versionCode = 114' in gradle or 'versionCode = 115' in gradle,
    'versionName = "v107.2"' in gradle or 'versionName = "V108 ULTIMATE MASTER AI"' in gradle,
    'com.termux' in manifest and 'com.termux.api' in manifest,
    'inspectEnvironment(' in termux and 'launchTermux(' in termux and 'openBatterySettings(' in termux,
    'prepareSnapshot(' in rollback and 'restoreSnapshot(' in rollback and 'sha256Bytes(' in rollback,
    'listRecentWorkflowRuns(' in github,
    'NavyBgTop' in lab and 'NeonCyan' in lab and 'ANTIGRAVITY CONTROL CENTER' in lab,
    'Launch Termux' in lab and 'Prepare Verified Rollback Snapshot' in lab,
    'refreshControlCenter(' in vm and 'prepareRollbackSnapshot(' in vm and 'restoreRollbackSnapshot(' in vm,
]

for i, ok in enumerate(checks, 1):
    assert ok, f"V107.2 check #{i} failed"

print(f"{len(checks)}/{len(checks)} V107.2 Control Center source checks passed")
