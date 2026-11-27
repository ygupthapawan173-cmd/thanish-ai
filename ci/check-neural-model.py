"""Actual CPU ONNX model smoke test, run in CI after pinned model provisioning."""
from pathlib import Path
import hashlib
import numpy as np
import onnxruntime as ort
p=Path('app/src/main/assets/neural-vad')
model=p/'silero_vad.onnx'
assert hashlib.sha256(model.read_bytes()).hexdigest()==(p/'model.sha256').read_text().strip()
s=ort.InferenceSession(str(model),providers=['CPUExecutionProvider'])
assert {x.name for x in s.get_inputs()}=={'input','state','sr'}
state=np.zeros((2,1,128),dtype=np.float32)
for _ in range(3):
    prob,state=s.run(None,{'input':np.zeros((1,576),dtype=np.float32),'state':state,'sr':np.array(16000,dtype=np.int64)})
    assert prob.size==1 and np.isfinite(prob).all() and 0<=float(prob.item())<=1
    assert state.shape==(2,1,128) and np.isfinite(state).all()
print('PASS: genuine model inference and recurrent state shape on CPU (not Android microphone test)')
