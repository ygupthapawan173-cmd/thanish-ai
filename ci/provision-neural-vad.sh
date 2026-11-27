#!/usr/bin/env bash
set -euo pipefail
# Pinned upstream revision, MIT. No fabricated upstream SHA-256; the build creates the digest.
ref=60b7ffa243625ebdc1070275a29f18c87843786a
base="https://raw.githubusercontent.com/snakers4/silero-vad/$ref"
target=app/src/main/assets/neural-vad
mkdir -p "$target"
curl --fail --location --proto '=https' --tlsv1.2 --retry 2 --max-time 90 "$base/src/silero_vad/data/silero_vad.onnx" -o "$target/silero_vad.onnx"
curl --fail --location --proto '=https' --tlsv1.2 --retry 2 --max-time 90 "$base/LICENSE" -o "$target/LICENSE"
python3 - "$target" <<'PY'
from pathlib import Path
import hashlib,sys
p=Path(sys.argv[1]); model=(p/'silero_vad.onnx').read_bytes(); assert 100000 < len(model) < 8000000
(p/'model.sha256').write_text(hashlib.sha256(model).hexdigest()+'\n')
assert 'MIT' in (p/'LICENSE').read_text()
PY
