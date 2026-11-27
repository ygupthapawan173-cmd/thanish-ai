#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
P=app/src/main/java/com/example
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
python3 - "$T/MemoryFact.kt" <<'PY'
from pathlib import Path
import sys
s=Path('app/src/main/java/com/example/data/AppDatabase.kt').read_text()
a=s.index('data class MemoryFact('); b=s.index('\ndata class VoiceRoutine(',a)
Path(sys.argv[1]).write_text('package com.example.data\n'+s[a:b])
PY
kotlinc "$P/ai/AgentExecutionPolicy.kt" "$P/omega/TriggerDebouncePolicy.kt" "$P/data/SemanticMemoryEngine.kt" "$T/MemoryFact.kt" ci/check-v100-agents.kt -include-runtime -d "$T/check.jar"
java -jar "$T/check.jar"
