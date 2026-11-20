# Pandu Babu — Jarvis Edition

Telugu-first Android AI assistant built with Kotlin, Jetpack Compose, Room, Android SpeechRecognizer, Android Text-to-Speech, and Firebase AI Logic.

## Gemini connection and API key safety

The app keeps Firebase AI Logic as its no-key fallback. Settings also provides four optional Gemini Developer API key slots (one primary and three backups). When any slot is filled, the app tries configured keys in slot order and streams from the first working key. The model is gemini-3.8-flash.

Keys entered in the app are encrypted at rest with an AES-GCM key held by Android Keystore. They are not written to source code, BuildConfig, resources, GitHub, or the APK. This is still a client-side app: a rooted or instrumented device may expose a key while it is in use. Use keys with appropriate project restrictions and quota limits, and never put server-only secrets in a mobile app.

To use Firebase AI Logic instead, clear all four key slots, enable Gemini Developer API in Firebase Console → AI Services → AI Logic, and register the debug App Check token under Firebase Console → App Check → Apps → Manage debug tokens. The Firebase configuration API key in google-services.json is not a Gemini Developer API key.

## Accessibility and phone controls

Pandu Babu's optional Accessibility Service is deliberately limited to Android global navigation:
- Back
- Home
- Recents
- Notifications

The user must open Android Accessibility Settings and explicitly enable the service. The app checks Android's enabled Accessibility Service list and refreshes its status when the service connects/disconnects and when the app resumes. Every global navigation action requested by voice or tapped in the dashboard requires an in-app confirmation. If Android shows the service enabled but it has not connected yet, toggle it off and on once in system settings. The service does not read screen content, messages, passwords, OTPs, or other window content, and does not click controls inside other apps.

Other phone controls use normal Android APIs:
- App launch, camera, gallery, dialer, and messaging app launch
- Flashlight, media volume, and battery information
- Screen brightness (requires the user to grant Modify System Settings)
- Wi-Fi, Bluetooth, hotspot, and mobile-network settings screens (Android limits direct changes to several protected settings)

Voice recognition depends on an installed Android speech-recognition provider and its language/network support. Telugu text-to-speech depends on Telugu voices installed on the device.

## Build

1. Open this repository in Android Studio.
2. Let Gradle sync and install the Android SDK version requested by the project.
3. Build the debug variant with gradle assembleDebug or Android Studio's Build APK(s) action.
4. Run unit tests with gradle testDebugUnitTest.

The GitHub Actions workflow runs unit tests before building the debug APK.

## Package

- Application ID: com.aistudio.pandubabu.kxmpzq
- Display name: Pandu Babu
- Minimum Android SDK: 24
