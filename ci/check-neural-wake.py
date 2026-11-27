"""Validate optional upstream ONNX pipeline shape and real CPU inference (not detection accuracy)."""
from pathlib import Path
import hashlib
import numpy as np
import onnxruntime as ort
p=Path('app/src/main/assets/neural-wake')
def session(name):
    f=p/name
    assert hashlib.sha256(f.read_bytes()).hexdigest()==(p/(name+'.sha256')).read_text().strip()
    return ort.InferenceSession(str(f), providers=['CPUExecutionProvider'])
def infer(s,x):
    y=s.run(None,{s.get_inputs()[0].name:x})[0]
    assert np.isfinite(y).all()
    return y
m=session('melspectrogram.onnx'); e=session('embedding_model.onnx'); h=session('hey_jarvis_v0.1.onnx')
x=infer(m,np.zeros((1,1760),dtype=np.float32)).reshape(-1,32)
assert len(x)>=8
x=np.tile(x[-8:],(10,1))[-76:]/10+2
y=infer(e,x.reshape(1,76,32,1).astype(np.float32)).reshape(-1)
assert y.size==96
z=infer(h,np.tile(y,(16,1)).reshape(1,16,96).astype(np.float32))
assert z.size==1 and 0<=z.item()<=1
print('Optional openWakeWord real CPU pipeline passed shape and finite checks; accuracy untested')
