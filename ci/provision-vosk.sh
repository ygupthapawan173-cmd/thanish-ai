#!/usr/bin/env bash
set -euo pipefail
archive="${RUNNER_TEMP:-/tmp}/pastel-vosk-model.zip"
curl --fail --location --proto '=https' --tlsv1.2 --retry 3 --max-time 180 https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -o "$archive"
echo "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498  $archive" | sha256sum -c -
python3 - "$archive" <<'EOF'
import pathlib,zipfile,sys,shutil,hashlib
archive=pathlib.Path(sys.argv[1]); target=pathlib.Path('app/src/main/assets/model-en-us'); target.mkdir(parents=True,exist_ok=True)
prefix='vosk-model-small-en-us-0.15/'
with zipfile.ZipFile(archive) as z:
    for info in z.infolist():
        if not info.filename.startswith(prefix) or info.is_dir():continue
        rel=pathlib.PurePosixPath(info.filename[len(prefix):]); assert not rel.is_absolute() and '..' not in rel.parts
        dest=target.joinpath(*rel.parts);dest.parent.mkdir(parents=True,exist_ok=True)
        with z.open(info) as inp,dest.open('wb') as out:shutil.copyfileobj(inp,out)
for name in ('am/final.mdl','conf/model.conf','graph/phones/word_boundary.int'):assert (target/name).stat().st_size>0
(target/'uuid').write_text(hashlib.sha256(archive.read_bytes()).hexdigest())
EOF

python3 - <<'PYHASH'
import pathlib,hashlib
base=pathlib.Path('app/src/main/assets')
for name in ('model-en-us','model-spk'):
    root=base/name
    files=sorted(p for p in root.rglob('*') if p.is_file() and p.name!='pack.sha256')
    assert files
    (root/'pack.sha256').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(root).as_posix()}\n' for p in files))
PYHASH
