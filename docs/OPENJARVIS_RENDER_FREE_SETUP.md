# ZENO AI — Free OpenJarvis + OpenRouter deployment on Render

This is an evaluation setup. The Android app stores the Render URL and the Render-generated OpenJarvis API key using Android Keystore encryption.

## 1. Create a free OpenRouter key

1. Sign in at https://openrouter.ai/
2. Create a key at https://openrouter.ai/settings/keys
3. Keep it private. Add it only to the Render environment variables, never to GitHub or chat.

Use the free router model `openrouter/free`. OpenRouter selects an available free model for each request. Free endpoints are rate-limited and availability can vary.

## 2. Create the Render service

1. Open https://dashboard.render.com/
2. Choose **New → Web Service**.
3. Choose **Public Git Repository** and enter `https://github.com/open-jarvis/OpenJarvis`.
4. Select **Docker** runtime if prompted.
5. Set:
   - Dockerfile Path: `./deploy/docker/Dockerfile`
   - Docker Context: `.`
   - Instance Type: **Free**
   - Health Check Path: `/health`
   - Docker Command: `serve --host 0.0.0.0 --port 10000 --engine cloud --model openrouter/free`

If the Render dashboard does not expose a Docker Command field, use a fork of OpenJarvis and customize its `render.yaml` Blueprint instead.

## 3. Add environment variables in Render

Add these under the service's Environment tab:

- `PORT` = `10000`
- `OPENJARVIS_API_KEY` = generate a long random secret (at least 32 random bytes)
- `OPENROUTER_API_KEY` = your `sk-or-v1-...` key from OpenRouter

Do not add `OPENAI_API_KEY`; the selected model uses OpenRouter. Save changes and deploy.

Wait until the service is Live. Test these endpoints in a browser:

- `https://YOUR-SERVICE.onrender.com/health` should return `{"status":"ok"}`.
- `https://YOUR-SERVICE.onrender.com/v1/models` may require authorization; the app uses the configured model ID `openrouter/free`.

## 4. Connect the Android app

1. Open ZENO AI → Settings.
2. Find **AI Provider — OpenJarvis Cloud**.
3. Enter the Render URL, for example `https://your-service.onrender.com`.
4. Enter the Render `OPENJARVIS_API_KEY` (not the OpenRouter key).
5. Model ID: `openrouter/free`.
6. Tap **Connect OpenJarvis Securely**.

The app sends the Render API key as an Authorization Bearer header. The OpenRouter key remains only on the Render server.

## Important free-tier limitations

- Render Free has 512 MB RAM and sleeps after inactivity. The first request after sleeping can take a while.
- Render Free filesystems are ephemeral. Do not store irreplaceable memory, credentials, or database state on the service.
- OpenRouter free models have request limits and availability can vary. They are not a guaranteed always-on production service.
- This deploy runs the OpenJarvis API layer in the cloud; model inference is provided by OpenRouter, not by the Render CPU instance.
- Never publish either API key or put them in source code.

Official references:
- OpenJarvis Render deployment: https://open-jarvis.github.io/OpenJarvis/deployment/render/
- OpenJarvis API server: https://open-jarvis.github.io/OpenJarvis/deployment/api-server/
- OpenRouter free models: https://openrouter.ai/collections/free-models/
- Render free services: https://render.com/docs/free
