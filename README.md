# Pandu Babu — Jarvis Edition

Telugu-first Android AI assistant built with Kotlin, Jetpack Compose, Room, Android SpeechRecognizer, Android Text-to-Speech, and Firebase AI Logic.

## Gemini connection and API key safety

This app already uses the Firebase AI Logic Android SDK through Firebase.ai.generativeModel(...).

Do not add a Gemini Developer API key to the APK, source code, GitHub, or a client-side .env file. Firebase AI Logic's current mobile authorization flow uses a Google-managed service agent and App Check; a standard Gemini API key is not required in the Android app. Never use the Firebase configuration API key as a Gemini API key.

Setup:
1. In the Firebase Console, open AI Services → AI Logic and enable Gemini Developer API for this Firebase project.
2. Keep the project's app/google-services.json in the app module. It contains Firebase app configuration, not a Gemini Developer API key.
3. Keep Firebase App Check enabled. For a debug APK, launch the app once and copy the App Check debug token from Logcat into Firebase Console → App Check → Apps → Manage debug tokens.
4. For release builds, configure an approved App Check attestation provider for the Android app.

The Settings screen explains this connection. If Gemini requests fail, check that AI Logic is enabled for the Firebase project and that the installed debug app's App Check token is registered.

## Accessibility and phone controls

Pandu Babu's optional Accessibility Service is deliberately limited to Android global navigation:
- Back
- Home
- Recents
- Notifications

The user must open Android Accessibility Settings and explicitly enable the service. Every global navigation action requested by voice or tapped in the dashboard requires an in-app confirmation. The service does not read screen content, messages, passwords, OTPs, or other window content, and does not click controls inside other apps.

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
