import sys,numpy as np
exec(open('ue.py').read())
W=np.frombuffer(D[:0x4a2d528],dtype='<u4')
def pcrefs_all(tlo,thi):
    idx=np.nonzero((W&0x0fff0ff0)==0x008f0000)[0]
    out=[]
    for i in idx:
        a=int(i)*4; w=int(W[i]); rm=w&0xf
        for k in range(1,16):
            x=int(W[i-k])
            if (x&0x0fff0000)==0x059f0000 and ((x>>12)&0xf)==rm:
                p=a-4*k; o=p+8+(x&0xfff)
                if o+4>len(D): break
                lit=struct.unpack_from('<I',D,o)[0]; v=(lit+a+8)&0xffffffff
                if tlo<=v<thi: out.append((a,v))
                break
    return out
if __name__=='__main__':
    t=int(sys.argv[1],16); n=int(sys.argv[2],16)
    for a,v in pcrefs_all(t,t+n): print(hex(a),hex(v),[hex(x) for x in (func_of(a) or (0,0))])
