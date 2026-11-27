#!/usr/bin/env bash
set -euo pipefail
# Non-commercial test only. No assets in normal production build.
p=app/src/main/assets/neural-wake
mkdir -p "$p"
base=https://github.com/dscripka/openWakeWord/releases/download/v0.5.1
for name in melspectrogram.onnx embedding_model.onnx hey_jarvis_v0.1.onnx; do
  curl --fail --location --proto '=https' --retry 2 --max-time 120 "$base/$name" -o "$p/$name"
  (cd "$p"; sha256sum "$name" | cut -d' ' -f1 > "$name.sha256")
done
cat > "$p/MODEL-NOTICE.txt" <<'NOTICE'
openWakeWord upstream v0.5.1 model files, unmodified.
https://github.com/dscripka/openWakeWord
Pretrained classifier license: CC BY-NC-SA 4.0 (non-commercial).
https://creativecommons.org/licenses/by-nc-sa/4.0/
No production or commercial rights are asserted. Verify all model licenses before redistribution.
NOTICE
