import sys,struct
exec(open('ue.py').read())
def vcalls(off,lo=0x8aeb18,hi=0x8aeb18+0x378b814):
    res=[]
    w=D[lo:hi]
    for o in range(0,len(w)-20,4):
        x=struct.unpack_from('<I',w,o)[0]
        if (x&0x0F700FFF)==(0x05100000|off) and (x>>23)&1:
            rt=(x>>12)&15
            for k in range(1,6):
                y=struct.unpack_from('<I',w,o+4*k)[0]
                if (y&0x0FFFFFF0)==0x012FFF30 and (y&15)==rt: res.append(lo+o);break
                if (y&0x0FFFFFF0)==0x012FFF10 and (y&15)==rt: res.append(lo+o);break
    return res
if __name__=='__main__':
    off=int(sys.argv[1],16)
    lo=int(sys.argv[2],16) if len(sys.argv)>2 else 0x8aeb18
    hi=int(sys.argv[3],16) if len(sys.argv)>3 else 0x8aeb18+0x378b814
    r=vcalls(off,lo,hi); print(len(r)); print(' '.join(hex(x) for x in r))
