#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
P=app/src/main/java/com/example
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
kotlinc "$P/autonomous/EngineeringPolicy.kt" "$P/autonomous/FinalIntegrationPolicy.kt" "$P/control/VisionEncodingPolicy.kt" "$P/ui/SettingsSearchPolicy.kt" ci/check-v100-final.kt -include-runtime -d "$T/check.jar"
java -jar "$T/check.jar"
