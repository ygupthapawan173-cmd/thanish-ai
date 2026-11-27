#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
P=app/src/main/java/com/example
kotlinc "$P/omega/DailySchedulePolicy.kt" "$P/autonomous/EngineeringPolicy.kt" ci/infinity-release-check.kt -include-runtime -d "$T/check.jar"
java -jar "$T/check.jar"
