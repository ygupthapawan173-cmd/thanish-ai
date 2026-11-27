# Android 16 device acceptance — not executed
Record pass/fail, device model, OS build, app version, logs, and timestamp. Use a test account and test API key; do not share tokens, conversations, contacts, or private audio.
- Install over V47 only if signer matches. Verify data, settings, and permissions preserved.
- Telugu 30 phrases + English 30 + mixed 20 (quiet/noisy); count correct recognition, compare same V47 corpus. Test installed Android on-device Telugu pack and airplane mode fallback separately.
- Wake 30 attempts unlocked and 30 locked; background for 10 and 30 minutes; test explicit stop/mic mute, app minimized, Bluetooth, phone call, another app mic conflict; ensure no unauthorized network ASR while locked.
- Enable Accessibility from system settings, test 10 minutes with approved navigation, disable and reenable; verify actual state and logs. Never programmatically reenable after user disables it.
- With a trusted configured gateway test discovery, chat/code/reasoning selection, unavailable model, 429 (controlled mock), auth failure (no repeated retry), and strict free mode; no live paid quota abuse.
- Gemini Live controlled 400/404/429 simulation, verify distinct model fallback and cancellation. No model availability claims without provider tests.
- Owner profile: consensual samples only; verify short wake + command, non-owner rejection, profile reset. Do not treat speaker recognition as authentication for sensitive actions.
- Test memory irrelevant query, relevant query, backup/restore and offline mode. Check no old feature regression.
- Compare idle/active power, memory, APK size, and smoothness against V47 using same device/settings; check battery saver.
- Capture relevant sanitized logs: `adb logcat -d -s OmegaVoice OmegaAccessibility AndroidRuntime` (may include private data; review before sharing). No screenshots or other private records required.
- Production: verify signed release certificate fingerprint, non-debuggable state, version, alignment, and upgrade compatibility. Keep release signing secrets outside source.
