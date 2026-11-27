#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
P=app/src/main/java/com/example
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
kotlinc "$P/ai/ProviderBudgetPolicy.kt" "$P/autonomous/SelectedDependencyAnalysis.kt" "$P/ai/OfflineUnitConverter.kt" ci/check-budget-dependency.kt -include-runtime -d "$T/check.jar"
java -jar "$T/check.jar"
