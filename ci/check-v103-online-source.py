from pathlib import Path
import xml.etree.ElementTree as ET
p=Path('app/src/main/java/com/example')
def read(x): return (p/x).read_text()
a=read('data/OmegaPreferences.kt');u=read('ui/SettingsScreen.kt');v=read('ui/PanduViewModel.kt');r=read('voice/VoiceInputController.kt')
ns='{http://schemas.android.com/apk/res/android}'
service=next(x for x in ET.parse('app/src/main/AndroidManifest.xml').getroot().iter('service') if x.attrib.get(ns+'name')=='.omega.OmegaWakeService')
checks=[service.attrib[ns+'enabled']=='false',any(v in Path('app/build.gradle.kts').read_text() for v in ('versionCode = 103','versionCode = 104','versionCode = 105','versionCode = 106','versionCode = 107','versionCode = 108','versionCode = 110','versionCode = 111','versionCode = 112','versionCode = 113','versionCode = 114','versionCode = 115','versionCode = 116','versionCode = 117')),'val proposed = transform(old).copy(preferOfflineSpeech = false, wakeWordEnabled = false, offlineFastCommands = false)' in a,all(x in a.split('private fun load(): OmegaSettings',1)[1].split('private fun protectedApiKey',1)[0] for x in ['preferOfflineSpeech = false','wakeWordEnabled = false','offlineFastCommands = false']),'title = "Local Brain Mode"' not in u,'"Prefer Android on-device speech"' not in u,'"Hey Jarvis Background Wake"' not in u,'OmegaWakeService.start(appContext)' not in v,'OmegaWakeService.stop(appContext)' in v,'onlineOnly = true' in r,'NET_CAPABILITY_VALIDATED' in r,'EXTRA_PREFER_OFFLINE, false' in r,'if (privacyRestricted()) { finishError' in r,'com.example.voice.OwnerSpeaker(context).clear()' in u,'onVoicePreview("te-IN")' in u,'onVoicePreview("en-IN")' in u]
for i,x in enumerate(checks): assert x, i+1
print(f'{len(checks)}/{len(checks)} V100.3 source guards passed')
