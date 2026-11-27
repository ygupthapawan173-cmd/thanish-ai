# V108.1 lightweight source export

Original tested revision: 50c3ff98a202bf42231697bad49f556dc465dcb2 (V108.1 WAKE RECOVERY, versionCode 116).
This is a source-review copy for V109 development. Kotlin sources, UI resources, Gradle configuration, unit tests and CI provisioning scripts are preserved unchanged. Historical ZIPs, large speaker-model weights, Firebase project configuration, obsolete workflow files, and the old source checksum manifest are omitted. The application uses on-device API configuration; never embed actual API keys or GitHub tokens.
For a production-equivalent build, restore any required omitted assets from the original revision, provision models using ci/provision-vosk.sh and ci/provision-neural-vad.sh, and use the original build workflow. The lightweight export itself has not been built or device-tested. The original working branch remains unchanged.
