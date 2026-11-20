# Pandu Babu — Jarvis Edition

Telugu-first Android AI assistant built with Kotlin, Jetpack Compose, Room, Android SpeechRecognizer, Android Text-to-Speech, and Firebase AI Logic.

## AI intelligence and provider slots

Open the app's **Settings** tab to configure four optional AI API providers:

1. Google Gemini — default model: `gemini-3.8-flash`
2. Groq — default model: `openai/gpt-oss-20b`
3. OpenRouter — default model: `openrouter/free`
4. OpenAI — default model: `gpt-4.1-mini`

For each provider you can enter an API key, change the model ID, choose **Save & Test**, and select **Use This** to make it the active provider. You can remove a saved key at any time.

API keys are encrypted at rest with AES-GCM using a key held by Android Keystore. The encrypted provider preferences are excluded from Android cloud backup and device transfer. Keys are not hardcoded in the source code, BuildConfig, or APK. This is still a client-side application: a rooted or instrumented device may expose a key while it is in use. Use project restrictions and quota limits on provider accounts; never put server-only secrets in a mobile app.

Responses stream into the chat as text arrives. If the active provider fails before producing response text, Pandu Babu tries the other configured providers. When no provider key is configured, the app falls back to its existing Firebase AI Logic Gemini connection.

Provider usage and charges depend on the provider account and model. OpenRouter's `openrouter/free` model router uses currently available free models and is subject to provider rate limits. Model IDs can be changed from Settings as provider catalogs evolve.

## Accessibility and phone controls

Pandu Babu's optional Accessibility Service is deliberately limited to Android global navigation:
- Back
- Home
- Recents
- Notifications

The user must open Android Accessibility Settings and explicitly enable the service. The Phone Controls dashboard distinguishes between **enabled in Android Settings** and **connected to the running app**, and refreshes that state when the service connects/disconnects and when the app resumes. Use **Enable** or **Open Settings** to return to Android's Accessibility page. If Android shows the service enabled but it has not connected, toggle it off and on once.

Every global navigation action requested by voice or tapped in the dashboard requires an in-app confirmation. The service does not read screen content, messages, passwords, OTPs, or other window content, and does not click controls inside other apps.

Other phone controls use normal Android APIs:
- App launch, camera, gallery, dialer, and messaging app launch
- Flashlight, media volume, and battery information
- Screen brightness (requires the user to grant Modify System Settings)
- Wi-Fi, Bluetooth, hotspot, and mobile-network settings screens (Android limits direct changes to several protected settings)

Voice recognition depends on an installed Android speech-recognition provider and its language/network support. Telugu text-to-speech depends on Telugu voices installed on the device.

## Build

1. Open this repository in Android Studio.
2. Let Gradle sync and install the Android SDK version requested by the project.
3. Build the debug variant with `gradle assembleDebug` or Android Studio's Build APK(s) action.
4. Run unit tests with `gradle testDebugUnitTest`.

The GitHub Actions workflow runs unit tests before building the debug APK on pushes and pull requests targeting `main`.

## Package

- Application ID: `com.aistudio.pandubabu.kxmpzq`
- Display name: Pandu Babu
- Version: 1.2
- Minimum Android SDK: 24
