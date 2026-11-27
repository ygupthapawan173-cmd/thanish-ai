#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
kotlinc app/src/main/java/com/example/voice/TtsChunkPolicy.kt ci/check-v47.kt -include-runtime -d "$T/check.jar"
java -jar "$T/check.jar"
python3 ci/check-v47-source.py
