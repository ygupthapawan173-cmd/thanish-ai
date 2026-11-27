from pathlib import Path
import xml.etree.ElementTree as ET
p=Path('app/src/main/java/com/example')
def r(f): return (p/f).read_text()
s=r('omega/OnlineBackgroundVoiceService.kt'); vm=r('ui/PanduViewModel.kt'); u=r('ui/VoicePackPanel.kt'); a=r('voice/VoiceInputController.kt'); w=r('voice/HandsFreeWakePolicy.kt')
ns='{http://schemas.android.com/apk/res/android}'
services={x.attrib[ns+'name']:x.attrib for x in ET.parse('app/src/main/AndroidManifest.xml').getroot().iter('service')}
checks=[services['.omega.OmegaWakeService'][ns+'enabled']=='false',services['.omega.OnlineBackgroundVoiceService'][ns+'exported']=='false',services['.omega.OnlineBackgroundVoiceService'][ns+'foregroundServiceType']=='microphone','START_NOT_STICKY' in s,'StorageService.unpack(this, "model-en-us"' in s,'JSONArray(HandsFreeWakePolicy.phrases + "[unk]")' in s,'override fun onPartialResult(hypothesis: String?) = Unit' in s,'HandsFreeWakePolicy.accept' in s,'VoiceMic.lease.acquire(wakeLease)' in s,'VoiceMic.lease.release(wakeLease)' in s,'token == wakeGeneration' in s,'old?.shutdown()' in s,'wakeModel?.let' in s,'AgentHudBus.emitWakeTrigger("")' in s,'tts.speak("Yes, Boss")' in s,'returnToWake' in s,'HandsFreeWakePolicy.retryDelay' in s,'isDeviceLocked' in s and 'isInteractive' in s,'micPrivacyMuted' in s,'areNotificationsEnabled()' in s,'answerBackgroundQuestion' in s and 'executeTool(' not in s,'preferOffline = false' in s,'onlineOnly = true' in a,'yieldToForeground(appContext)' in vm,'Start Hey JARVIS' in u and 'Stop Hey JARVIS' in u,'HandsFreeWakeControls(uiState)' in r('ui/SettingsScreen.kt'),'LocalBrainEngine.kt' not in [x.name for x in p.rglob('*.kt')],any(v in Path('app/build.gradle.kts').read_text() for v in ('versionCode = 108','versionCode = 110','versionCode = 111','versionCode = 112','versionCode = 113','versionCode = 114','versionCode = 115'))]
for i,c in enumerate(checks,1): assert c,i
print(f'{len(checks)}/{len(checks)} V103 hands-free static source checks passed')
