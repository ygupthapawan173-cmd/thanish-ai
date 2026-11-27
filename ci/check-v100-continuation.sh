#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
P=app/src/main/java/com/example
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
KOTLIN_LIB="$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib"
CP="$KOTLIN_LIB/kotlinx-coroutines-core-jvm.jar"
kotlinc -classpath "$CP" "$P/ai/ModelCooldownLedger.kt" "$P/ai/SmartModelPolicy.kt" "$P/autonomous/UpdateTransferPolicy.kt" "$P/data/ApiQuotaTracker.kt" ci/check-v100-continuation.kt -include-runtime -d "$T/check.jar"
java -cp "$T/check.jar:$CP" Check_v100_continuationKt
