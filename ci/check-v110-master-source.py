from pathlib import Path
import re

root = Path('app/src/main')
gradle = Path('app/build.gradle.kts').read_text()
manifest = (root / 'AndroidManifest.xml').read_text()

# 1. Verify Package, Version & APK Naming
assert 'applicationId = "com.aistudio.pandubabu.omega.v19ultra"' in gradle
assert 'versionCode = 119' in gradle
assert 'versionName = "V111 ULTRA VOICE LIQUID SAPPHIRE"' in gradle

# 2. Verify Removed Section 16 Components & Legacy Voice Stacks Are Absent
removed_files = [
    'java/com/example/voice/BluetoothAudioRouter.kt',
    'java/com/example/voice/CloudVoiceClient.kt',
    'java/com/example/voice/GeminiSpeechClient.kt',
    'java/com/example/voice/OwnerSpeaker.kt',
    'java/com/example/omega/OmegaWakeService.kt',
    'java/com/example/data/SemanticMemoryEngine.kt',
    'java/com/example/data/PortableDataBackup.kt',
    'java/com/example/ui/VoicePackPanel.kt',
    'java/com/example/ui/components/OmegaHorizonOrb.kt',
    'java/com/example/ui/components/ParticleAiOrb.kt',
    'java/com/example/autonomous/SelfCodingEngine.kt',
    'java/com/example/autonomous/AntigravitySelfCodingAgent.kt',
    'java/com/example/autonomous/SystemDiagnosticsEngine.kt',
    'java/com/example/autonomous/TermuxControlHelper.kt',
]
for rel in removed_files:
    assert not (root / rel).exists(), f"Removed file still present: {rel}"

forbidden_main_patterns = [
    r'android\.speech\.tts\.TextToSpeech',
    r'UtteranceProgressListener',
    r'ACTION_VOICE_SEARCH',
    r'RecognizerIntent\.ACTION_WEB_SEARCH',
    r'com\.termux',
    r'BLUETOOTH_CONNECT',
    r'anthropic/claude',
    r'deepseek/deepseek',
    r'qwen/',
    r'x-ai/grok',
    r'moonshotai/',
    r'mistralai/',
    r'meta-llama/',
    r'gpt-oss',
]

# Secret scan across app/src/main (exclude test files intentionally)
secret_patterns = [
    r'AIza[0-9A-Za-z\-_]{25,}',
    r'ghp_[0-9A-Za-z]{20,}',
    r'github_pat_[0-9A-Za-z_]{20,}',
    r'sk-(?:proj-|ant-)[0-9A-Za-z\-_]{20,}',
]

for p in root.rglob('*'):
    if p.suffix not in ('.kt', '.xml', '.kts'):
        continue
    txt = p.read_text(errors='ignore')
    if 'ModelCatalogPolicy.kt' not in str(p):
        for pat in forbidden_main_patterns:
            assert not re.search(pat, txt), f"Forbidden removed pattern '{pat}' found in {p}"
    for sp in secret_patterns:
        assert not re.search(sp, txt), f"Potential hardcoded credential pattern '{sp}' in {p}"

# 3. Verify Core V110 Feature Integrations & UI Architecture
vm = (root / 'java/com/example/ui/PanduViewModel.kt').read_text()
for tab in ('HOME("Home")', 'DASHBOARD("Dashboard")', 'AI_BRAIN("AI Brain")', 'VOICE("Voice")', 'CONTROL("Control")', 'SETTINGS("Settings")'):
    assert tab in vm, f"Missing navigation tab: {tab}"
for i in range(1, 21):
    assert f'DashboardIndicator("{i}"' in vm, f"Missing dashboard indicator #{i}"

assert 'class AiFaceVisualizer' in (root / 'java/com/example/ui/components/AiFaceVisualizer.kt').read_text() or \
       'fun AiFaceVisualizer' in (root / 'java/com/example/ui/components/AiFaceVisualizer.kt').read_text()
assert 'fun ParticleRibbonWaveform' in (root / 'java/com/example/ui/components/ParticleRibbonWaveform.kt').read_text()
assert 'saaras:v4' in (root / 'java/com/example/voice/SarvamRealtimeSttClient.kt').read_text()
assert 'https://api.sarvam.ai/text-to-speech' in (root / 'java/com/example/voice/SarvamVoiceClient.kt').read_text()
assert 'PreloadedAckPlayer' in (root / 'java/com/example/omega/OnlineBackgroundVoiceService.kt').read_text()
assert 'google_search' in (root / 'java/com/example/ai/WebResearchClient.kt').read_text()
assert 'deep-research-preview' in (root / 'java/com/example/ai/WebResearchClient.kt').read_text()
assert 'web_search' in (root / 'java/com/example/ai/WebResearchClient.kt').read_text()
assert 'Guptha' in (root / 'java/com/example/ai/CreatorIdentity.kt').read_text()
assert '.omega.OnlineBackgroundVoiceService' in manifest
assert 'JARVIS-INFINITY-V111-ULTRA-VOICE-LIQUID-SAPPHIRE.apk' in Path('ci/sign-release.sh').read_text()

assert 'ai.picovoice:porcupine-android:4.0.2' in gradle
assert 'PorcupineManager.Builder()' in (root / 'java/com/example/omega/OnlineBackgroundVoiceService.kt').read_text()
assert 'PorcupineWakeConfig' in (root / 'java/com/example/ui/SettingsScreen.kt').read_text()
assert 'vosk-android' not in gradle
assert 'org.vosk' not in (root / 'java/com/example/omega/OnlineBackgroundVoiceService.kt').read_text()
assert not Path('ci/provision-vosk.sh').exists()
print('PASS: JARVIS INFINITY V111 ULTRA VOICE LIQUID SAPPHIRE source audit & dependency verification passed')
