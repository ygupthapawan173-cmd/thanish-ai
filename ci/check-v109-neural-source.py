from pathlib import Path
p=Path('app/src/main/java/com/example')
def r(f): return (p/f).read_text()
t=r('voice/TeluguTtsController.kt'); c=r('voice/VoicePackCatalog.kt'); u=r('ui/VoicePackPanel.kt'); pref=r('voice/CloudVoicePreferences.kt'); a=r('ui/SettingsScreen.kt')
checks=[
 'getBoolean("v104_paid_opt_in", false)' in pref, 'InstalledSpeechPolicy.paidAllowed' in pref,
 '.putBoolean("v104_paid_opt_in", value)' in pref, 'InstalledSpeechPolicy.networkVoiceAllowed' in t,
 'InstalledSpeechPolicy.voiceEligible' in t, 'CloudVoicePreferences.enabled(appContext)' in t,
 'if (!startCloud(rawText)) speakInternal(rawText)' in t, 'TextToSpeech(appContext, listener, requested)' in t,
 'token != engineGeneration' in t, 'requested != engineRequest || initFailed' in t, 'tts?.shutdown()' in t,
 'VoicePackCatalog.engine(appContext, language)' in t, 'VoicePackCatalog.choices(it, engineRequest)' in t,
 'queryIntentServices' in c, 'engine_$language' in c, '.remove(language)' in c,
 'VoicePackCatalog.selectEngine' in u, 'it.engineRequest==engineChoice' in u, '!it.needsData && !it.network' in u,
 'https://github.com/HayaiApp/HayaiTTS/releases' in u, 'https://hear2read.org/solution-android' in u,
 'surfaceContainerHigh=Color(0xFF182435)' in u, 'LocalContentColor provides theme.primaryText()' in u,
 'disabledContentColor=Color(0xFF9CAFC2)' in u, 'Hands-free wake controls' in a,
 any(v in Path('app/build.gradle.kts').read_text() for v in ('versionCode = 110','versionCode = 111','versionCode = 112','versionCode = 113')), any(v in Path('app/build.gradle.kts').read_text() for v in ('105.0-dual-api-candidate','v106','v107','v107.1'))]
for i,ok in enumerate(checks,1): assert ok,i
print(f'{len(checks)}/{len(checks)} V104 installed voice integration static checks passed')
