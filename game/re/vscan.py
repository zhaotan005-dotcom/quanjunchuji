import sys,struct
exec(open('ue.py').read())
import numpy as np
from collections import defaultdict
A=np.frombuffer(D[:0x4a2d528//4*4],dtype='<u4')
m=((A & 0x0f300f00)==0x0d100a00)&((A>>23)&1==1)
idx=np.nonzero(m)[0]
fs=defaultdict(set)
FA=np.array(FUNCS)
for i in idx:
    a=int(i)*4; w=int(A[i]); rn=(w>>16)&15; off=(w&0xff)*4
    j=np.searchsorted(FA,a,side='right')-1
    fs[int(FA[j])].add((rn,off))
def find(want,name):
    for f,s in fs.items():
        byr=defaultdict(set)
        for rn,o in s: byr[rn].add(o)
        for rn,os in byr.items():
            if want<=os: print(name,hex(f),'r%d'%rn,sorted(hex(x) for x in os if x<0x100)); break
find({0x44,0x48,0x4c,0x54,0x58,0x5c},'RECOIL')
find({0x28,0x2c,0x30,0x34,0x38,0x3c},'CFM')
find({0x3c,0x40,0x48,0x4c,0x50,0x54},'ACCRATIO')
find({0x3c,0x40,0x44,0x48,0x4c},'SPREAD')
