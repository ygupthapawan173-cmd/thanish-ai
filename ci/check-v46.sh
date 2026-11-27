#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
P=app/src/main/java/com/example
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
kotlinc "$P/voice/VoicePackPolicy.kt" "$P/voice/VoiceSessionPolicy.kt" "$P/autonomous/SelectedDependencyAnalysis.kt" ci/check-v46.kt -include-runtime -d "$T/check.jar"
java -jar "$T/check.jar"
