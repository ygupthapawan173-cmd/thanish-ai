#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
P=app/src/main/java/com/example
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
KOTLIN_BIN=$(readlink -f "$(command -v kotlinc)")
CORO="$(dirname "$KOTLIN_BIN")/../lib/kotlinx-coroutines-core-jvm.jar"
kotlinc "$P/data/ApiQuotaTracker.kt" ci/infinity-level6-check.kt -classpath "$CORO" -include-runtime -d "$T/level6.jar"
java -cp "$T/level6.jar:$CORO" Infinity_level6_checkKt
python3 ci/check-level6-sql.py
