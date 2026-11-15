import numpy as np, pickle
from ue import *
TS,TE=0x8aeb18,0x8aeb18+0x378b814
w=np.frombuffer(D[TS:TE],dtype='<u4')
addr=lambda i: TS+4*i
cand_add=np.nonzero((w&0x0FEF0FF0)==0x008F0000)[0]
cand_ldr=np.nonzero((w&0x0FFF0FF0)==0x079F0000)[0]
xr={}
def back(i,rm):
    for j in range(i-1,max(i-24,-1),-1):
        x=int(w[j])
        if (x&0x0F7F0000)==0x051F0000 and ((x>>12)&15)==rm:
            imm=x&0xfff; 
            la=addr(j)+8+(imm if x&(1<<23) else -imm)
            return u32v(la)
        if ((x>>12)&15)==rm and (x&0x0C000000)==0 : return None # other write (rough)
    return None
for kind,arr in (('add',cand_add),('ldrpc',cand_ldr)):
    for i in arr:
        x=int(w[i]); rm=x&15
        lit=back(i,rm)
        if lit is None: continue
        t=(addr(i)+8+lit)&0xffffffff
        xr.setdefault(t,[]).append((addr(i),kind))
pickle.dump(xr,open('xr.pkl','wb'))
print(len(xr))
