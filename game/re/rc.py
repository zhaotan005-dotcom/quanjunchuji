exec(open('ue.py').read())
import numpy as np
from collections import defaultdict
A=np.frombuffer(D[:0x4a2d528//4*4],dtype='<u4')
FA=np.array(FUNCS)
vm=((A & 0x0f300f00)==0x0d100a00)&((A>>23)&1==1)
vi=np.nonzero(vm)[0]
fidx=np.searchsorted(FA,vi*4,side='right')-1
vf=defaultdict(set)
for i,fi in zip(vi,fidx):
    vf[int(FA[fi])].add((int(A[i])&0xff)*4)
# recoil sub-struct base 0x118: fields 0x3c..0x60 -> 0x154..0x178 ; camera 0x17c:+8..0x10 -> 0x184..0x18c
# also row-relative (+8): 0x15c..0x180, 0x18c..0x194
for base,name in ((0,'tbl'),(8,'row')):
    R=set(range(0x154+base,0x17c+base,4)); C=set(range(0x184+base,0x190+base,4))
    for f,s in vf.items():
        a=len(s&R); c=len(s&C)
        if a>=4 and c>=1 and len(s)<60: print(name,hex(f),a,c,len(s),sorted(hex(x) for x in s&(R|C)))
