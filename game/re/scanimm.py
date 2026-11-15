import sys,struct
exec(open('ue.py').read())
lo,hi=int(sys.argv[1],16),int(sys.argv[2],16)
import numpy as np
A=np.frombuffer(D[:0x4a2d528//4*4],dtype='<u4')
# movw: cond 0011 0000 imm4 Rd imm12
m=(A & 0x0ff00000)==0x03000000
idx=np.nonzero(m)[0]
imm=((A[idx]>>4)&0xf000)|(A[idx]&0xfff)
sel=idx[(imm>=lo)&(imm<=hi)]
from collections import Counter
fc=Counter()
for i in sel:
    a=int(i)*4; s,e=func_of(a); fc[s]+=1
    print(hex(a),hex(int((A[i]>>4)&0xf000|A[i]&0xfff)),hex(s))
