exec(open('ue.py').read())
import numpy as np
from collections import defaultdict
A=np.frombuffer(D[:0x4a2d528//4*4],dtype='<u4')
FA=np.array(FUNCS)
def fn(a): return int(FA[np.searchsorted(FA,a,side='right')-1])
ld=np.nonzero((A&0x0ff00fff)==0x05d001ac)[0]
vm=((A & 0x0f300f00)==0x0d100a00)&((A>>23)&1==1)
vi=np.nonzero(vm)[0]
vf=defaultdict(set)
for i in vi:
    a=int(i)*4; vf[fn(a)].add((int(A[i])&0xff)*4)
for i in ld:
    a=int(i)*4; f=fn(a); s=vf.get(f,set())
    hit=s & {0x1d8,0x1dc,0x1e0,0x1e4,0x1e8,0x1ec,0x1c0,0x1bc,0x118+0x44+0,0x15c,0x164,0x16c}
    if hit: print(hex(a),hex(f),sorted(hex(x) for x in hit))
