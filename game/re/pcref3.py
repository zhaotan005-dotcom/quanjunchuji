import sys,numpy as np
exec(open('ue.py').read())
W=np.frombuffer(D[:0x4a2d528],dtype='<u4')
# add rX, pc, rm  : cond 0000 1000 1111 xxxx 0000 0000 rm  -> mask 0x0fff0ff0 == 0x008f0000
# ldr rX, [pc, rm]: cond 0111 1001 1111 xxxx 0000 0000 rm -> mask 0x0fff0ff0 == 0x079f0000
idx=np.nonzero(((W&0x0fff0ff0)==0x008f0000)|((W&0x0fff0ff0)==0x079f0000))[0]
def refs(targets):
    out=[]
    for i in idx:
        a=int(i)*4; w=int(W[i]); rm=w&0xf
        for k in range(1,20):
            x=int(W[i-k])
            if (x&0x0fff0000)==0x059f0000 and ((x>>12)&0xf)==rm:
                o=a-4*k+8+(x&0xfff)
                if o+4>len(D): break
                lit=struct.unpack_from('<I',D,o)[0]; v=(lit+a+8)&0xffffffff
                if v in targets: out.append((a,v))
                break
    return out
if __name__=='__main__':
    T=set(int(x,16) for x in sys.argv[1:])
    for a,v in refs(T): print(hex(a),hex(v),hex(func_of(a)[0]))
