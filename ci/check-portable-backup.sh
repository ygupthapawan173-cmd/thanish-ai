#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
kotlinc app/src/main/java/com/example/security/PortableBackupCipher.kt ci/check-portable-backup.kt -include-runtime -d "$T/check.jar"
java -jar "$T/check.jar"
