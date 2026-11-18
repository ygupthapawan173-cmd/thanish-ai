# ZENO AI

ZENO AI is an Android-first Telugu voice assistant with chat, speech input/output, phone shortcuts, and an opt-in local memory.

## Local memory commands

ZENO only saves long-term memories when the user explicitly asks:

- `Remember: I prefer Telugu replies`
- `గుర్తుపెట్టుకో: నాకు చిన్న సమాధానాలు ఇష్టం`
- `Forget: I prefer Telugu replies`
- `Forget all memory`

Memories are stored locally on the device and added to the AI prompt when relevant. Ordinary conversation is not automatically saved as a personal memory. Passwords, OTPs, PINs, and API keys are rejected by the memory command.

## Build

1. Open the project in Android Studio.
2. Use JDK 21 and the Gradle wrapper configured by the project.
3. Configure Firebase / AI provider credentials using local secrets. Never commit API keys or embed them in the APK.
4. Run unit tests and build the debug APK.

## Self-improvement safety

Memory is opt-in and user-editable through explicit remember / forget commands. Future skill packs, knowledge refresh, and app updates must be verified and require user approval before code or permissions change. This app does not silently rewrite or install its own code.

## Wake phrase note

The bundled VoxRT wake-word model currently detects **“Hey Assistant”**, not “Hey Zeno”. ZENO branding is updated, but a custom Hey Zeno acoustic model is still required before that exact wake phrase can work.
