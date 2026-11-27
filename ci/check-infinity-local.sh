#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
P=app/src/main/java/com/example
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
kotlinc "$P/ai/CreatorIdentity.kt" "$P/data/OrbColorPreset.kt" "$P/data/RenderQuality.kt" ci/infinity-local-check.kt -include-runtime -d "$T/previous.jar"
java -jar "$T/previous.jar"
kotlinc "$P/ai/DirectDeviceCommand.kt" "$P/voice/BackgroundVoicePolicy.kt" "$P/voice/LiveSilencePolicy.kt" ci/infinity-bugfix-check.kt -include-runtime -d "$T/bugfix.jar"
java -jar "$T/bugfix.jar"
# Test doubles are separate from app source and never packaged in APK.
KOTLIN_BIN=$(readlink -f "$(command -v kotlinc)")
CORO="$(dirname "$KOTLIN_BIN")/../lib/kotlinx-coroutines-core-jvm.jar"
kotlinc "$P/ai/CancellableHttp.kt" ci/test-doubles/OkHttpTestDouble.kt ci/test-doubles/CancelCheck.kt -classpath "$CORO" -include-runtime -d "$T/cancel.jar"
java -cp "$T/cancel.jar:$CORO" com.example.ai.CancelCheckKt
kotlinc "$P/ai/SmartModelPolicy.kt" ci/infinity-free-check.kt -include-runtime -d "$T/free.jar"
java -jar "$T/free.jar"
kotlinc "$P/ai/SmartModelPolicy.kt" "$P/voice/LiveSilencePolicy.kt" ci/infinity-stability-check.kt -include-runtime -d "$T/stability.jar"
java -jar "$T/stability.jar"
