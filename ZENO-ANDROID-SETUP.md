# Standalone ZENO AI v2

This branch keeps the standalone ZENO Android app separate from the existing thanish-ai app. The existing `main` and `feature/telugu-voice-profiles` branches are not changed.

## Build the APK

1. Upload the prepared archive `zeno_ai_v2_standalone_ready.zip` to the root of this branch (`standalone-zeno-v2`) using GitHub's **Add file → Upload files**.
2. Commit the upload to this branch.
3. Open **Actions → Build standalone ZENO AI APK**.
4. When the workflow succeeds, open the run and download the `zeno-ai-v2-debug-apk` artifact.

The workflow extracts the ZIP, installs JDK 17 and Android SDK 35, and builds the debug APK with Gradle 8.11.1.

No API key should be committed. Add your Gemini API key inside the app's secure settings after installing the APK.
