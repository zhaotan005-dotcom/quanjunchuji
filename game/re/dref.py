import sys,numpy as np
exec(open('ue.py').read())
s=0x4a2d528
A=np.frombuffer(D[s:s+(0x6fcbf8//4)*4],dtype='<u4')
def drefs(v): return [0x4a2e528+int(i)*4 for i in np.nonzero(A==v)[0]]
if __name__=='__main__':
    for x in sys.argv[1:]: print(x,[hex(v) for v in drefs(int(x,16))][:20])
