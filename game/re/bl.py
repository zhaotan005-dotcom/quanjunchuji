import numpy as np, pickle
from ue import *
TS,TE=0x8aeb18,0x8aeb18+0x378b814
w=np.frombuffer(D[TS:TE],dtype='<u4').astype(np.int64)
idx=np.nonzero(((w>>24)&0xF)==0xB)[0]   # B/BL with link, any cond (excluding 0xF? fine)
imm=w[idx]&0xFFFFFF
imm=np.where(imm&0x800000, imm-0x1000000, imm)
src=TS+4*idx; tgt=src+8+imm*4
b_idx=np.nonzero(((w>>24)&0xF)==0xA)[0]
bimm=w[b_idx]&0xFFFFFF; bimm=np.where(bimm&0x800000,bimm-0x1000000,bimm)
bsrc=TS+4*b_idx; btgt=bsrc+8+bimm*4
calls={}
for s,t in zip(src.tolist(),tgt.tolist()): calls.setdefault(t,[]).append(s)
for s,t in zip(bsrc.tolist(),btgt.tolist()): calls.setdefault(t,[]).append(s)  # includes local branches; filter later
pickle.dump(calls,open('calls.pkl','wb'))
