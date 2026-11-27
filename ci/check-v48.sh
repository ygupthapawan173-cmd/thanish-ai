#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
P=app/src/main/java/com/example
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
kotlinc "$P/voice/VoiceRecoveryPolicy.kt" "$P/omega/WakeRetryPolicy.kt" "$P/ai/LiveRetryPolicy.kt" "$P/ai/GatewayRetryPolicy.kt" "$P/ai/SmartModelPolicy.kt" ci/check-v48.kt -include-runtime -d "$T/check.jar"
java -jar "$T/check.jar"
