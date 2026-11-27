from pathlib import Path
p=Path('app/src/main/java/com/example')
a=(p/'data/OmegaPreferences.kt').read_text();repo=(p/'ai/PanduAiRepository.kt').read_text();vm=(p/'ui/PanduViewModel.kt').read_text();gateway=(p/'ai/SmartGatewayClient.kt').read_text()
all_code='\n'.join(f.read_text() for f in p.rglob('*.kt'))
checks=[not (p/'ai/LocalBrainEngine.kt').exists(), all(x not in all_code for x in ('localBrainMode','LocalBrainEngine','localLlmEndpoint','toggleLocalBrainMode')), '.remove("local_brain_mode")' in a, '.remove("local_llm_url")' in a, 'val proposed = transform(old).copy(preferOfflineSpeech = false, wakeWordEnabled = false, offlineFastCommands = false)' in a, 'SmartGatewayClient()' in repo,'gateway.answer(settings, userMessage' in repo,'effectiveOpenRouterKey()' in gateway, 'providerBudgetUsd' in a, 'KeystoreCryptoManager' in a, 'localRouter.executeExplicit(userMessage)' in repo, 'dispatchToAi(prompt, isVoiceInput = false, imageBase64 = b64)' in vm, any(v in Path('app/build.gradle.kts').read_text() for v in ('versionCode = 104','versionCode = 105','versionCode = 106','versionCode = 107','versionCode = 108','versionCode = 110','versionCode = 111','versionCode = 112','versionCode = 113','versionCode = 114','versionCode = 115'))]
for i,c in enumerate(checks,1): assert c,i
print(f'{len(checks)}/{len(checks)} V100.4 cloud-only source checks passed')
