#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
version=2.0.3
aar="sherpa-onnx-static-link-onnxruntime-${version}.aar"
base="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${version}"
curl --fail --location --retry 3 "$base/$aar" -o "$tmp/$aar"
curl --fail --location --retry 3 "$base/checksum.txt" -o "$tmp/checksum.txt"
# Refuse to build if the upstream release's published checksum is unavailable or mismatches.
(cd "$tmp"; awk -v file="$aar" '$2 == file || $2 == "*"file {print}' checksum.txt > selected.sha256; test -s selected.sha256; sha256sum -c selected.sha256)
mkdir -p "$root/app/libs"
cp "$tmp/$aar" "$root/app/libs/$aar"
model=sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01
curl --fail --location --retry 3 "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/$model.tar.bz2" -o "$tmp/model.tar.bz2"
tar -xjf "$tmp/model.tar.bz2" -C "$tmp" --no-same-owner --no-same-permissions
dest="$root/app/src/main/assets/sherpa-kws"
mkdir -p "$dest"
for component in encoder decoder joiner; do
  cp "$tmp/$model/$component-epoch-12-avg-2-chunk-16-left-64.int8.onnx" "$dest/$component.int8.onnx"
done
cp "$tmp/$model/tokens.txt" "$dest/tokens.txt"
# Encode the actual wake phrase against the shipped vocabulary (not guessed tokens).
python3 -m pip install --disable-pip-version-check 'sentencepiece==0.2.1'
python3 - "$tmp/$model/bpe.model" "$dest" <<'PY'
import pathlib, sys, sentencepiece as sp
model, dest = sys.argv[1], pathlib.Path(sys.argv[2])
processor = sp.SentencePieceProcessor(model_file=model)
tokens = processor.encode("HEY JARVIS", out_type=str)
vocab = {line.rsplit(maxsplit=1)[0] for line in (dest / "tokens.txt").read_text().splitlines() if line.strip()}
assert tokens and all(t in vocab and t != "<unk>" for t in tokens), tokens
(dest / "keywords.txt").write_text(" ".join(tokens) + " @HEY_JARVIS\n")
PY
cp "$tmp/$model/README.md" "$dest/UPSTREAM_MODEL_README.md"
(cd "$dest"; sha256sum *.onnx tokens.txt keywords.txt > SHA256SUMS.txt)
