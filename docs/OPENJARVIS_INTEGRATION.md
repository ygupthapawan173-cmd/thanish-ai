# OpenJarvis integration for ZENO AI

ZENO AI remains the Android/Kotlin client. OpenJarvis runs as a separate Python API service and is called through its OpenAI-compatible endpoint.

## Build the Android app against a private OpenJarvis HTTPS endpoint

The app's existing OpenRouter path remains the default. To route chat through OpenJarvis, build with:

```bash
./gradlew assembleDebug \
  -POPENJARVIS_BASE_URL=https://YOUR_PRIVATE_OPENJARVIS_HOST \
  -POPENJARVIS_MODEL=qwen3:8b
```

The base URL is the server origin (or may end in `/v1`). The client calls `/v1/chat/completions`. Set the model to an identifier returned by the OpenJarvis `GET /v1/models` endpoint.

If `OPENJARVIS_BASE_URL` is omitted, ZENO AI continues using its existing OpenRouter Free integration unchanged.

## Start the OpenJarvis API server

On a supported Linux/WSL2/macOS host, install OpenJarvis with the server extra and start it:

```bash
git clone https://github.com/open-jarvis/OpenJarvis.git
cd OpenJarvis
uv sync --extra server
uv run jarvis doctor
uv run jarvis serve --host 127.0.0.1 --port 8000
```

Configure a working inference engine and model in OpenJarvis first. OpenJarvis documentation: https://open-jarvis.github.io/OpenJarvis/deployment/api-server/

## Security and Android notes

- Do not expose an unauthenticated OpenJarvis server to the public internet. Put it behind HTTPS and an authentication/access-control layer before using a remote endpoint.
- The app intentionally accepts HTTPS endpoints only. Do not use a public plain-HTTP URL.
- OpenJarvis does not officially list Android Termux as a supported install target. The Android app is only the client; the Python/Rust OpenJarvis service must run on a compatible host.
- No OpenJarvis API secret is embedded in the APK.
- This integration is opt-in at build time. The existing OpenRouter API key storage and ZENO voice/memory/device-control features are retained.
