from pathlib import Path

root = Path(".")
gradle = (root / "app/build.gradle.kts").read_text()
p = root / "app/src/main/java/com/example"

def r(rel: str) -> str:
    return (p / rel).read_text()

checks = [
    'applicationId = "com.aistudio.pandubabu.omega.v19ultra"' in gradle,
    'versionCode = 118' in gradle or 'versionCode = 119' in gradle,
    'versionName = "V110 ULTRA INTELLIGENCE MASTER"' in gradle or 'versionName = "V110.1 PREMIUM SPEED MASTER"' in gradle,
    'enum class AiBrainFamily' in r("ai/MultiBrainPolicy.kt"),
    all(b in r("ai/MultiBrainPolicy.kt") for b in (
        "GEMINI_DIRECT", "OPENAI_GPT", "CLAUDE_CODING", "DEEPSEEK_REASONING",
        "QWEN_INTELLIGENCE", "GROK_AI", "KIMI_LONG_CONTEXT", "MISTRAL_FAST",
        "LLAMA_AI", "GPT_OSS_REASONING"
    )),
    'ResponseQualityVerifier' in r("ai/ResponseQualityVerifier.kt"),
    'ResponseQualityVerifier.verify(' in r("ai/PanduAiRepository.kt"),
    'GeminiModelSelectionPolicy.buildCandidates(' in r("ai/PanduAiRepository.kt"),
    'FastWakeDetector' in r("omega/OnlineBackgroundVoiceService.kt"),
    'TeluguVoiceReplies' in r("omega/OnlineBackgroundVoiceService.kt"),
    'TtsRoutingPolicy' in r("voice/TeluguTtsController.kt"),
    'AutoLanguageSession' in r("voice/VoiceInputController.kt"),
    'PersonalAssistantEngine' in r("autonomous/PersonalAssistantEngine.kt"),
    'DocumentIntelligenceHelper' in r("control/DocumentIntelligenceHelper.kt"),
    'navigateBack()' in r("ui/PanduViewModel.kt") and 'BackHandler(' in r("ui/OmegaDashboardApp.kt"),
    'rememberSaveableStateHolder()' in r("ui/OmegaDashboardApp.kt"),
    'ParticleRibbonWaveform(' in r("ui/InfinityScreens.kt"),
    not (p / "ai/LocalBrainEngine.kt").exists(),
]

for i, ok in enumerate(checks, 1):
    assert ok, f"V110 check #{i} failed"

print(f"{len(checks)}/{len(checks)} V110 ULTRA INTELLIGENCE MASTER source checks passed")
