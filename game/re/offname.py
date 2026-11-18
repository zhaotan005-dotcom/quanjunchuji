import sys,numpy as np
exec(open('ue.py').read())
s,e=0x4a2d528,0x4a2d528+0x6fcbf8
A=np.frombuffer(D[s:s+((e-s)//4)*4],dtype='<u4')
def offname(off):
    idx=np.nonzero(A==off)[0]
    out=[]
    for i in idx:
        j=i-8
        if j<1: continue
        if A[j+2]==0x45:
            nm=cstr(int(A[j+1]))
            if nm: out.append((nm,hex(int(A[j])),hex(s+4*int(j))))
    return out
for o in sys.argv[1:]:
    print(o,offname(int(o,16)))
