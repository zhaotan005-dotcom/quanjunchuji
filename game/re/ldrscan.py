import sys
exec(open('ue.py').read())
def scan(imm, lo=0x8a0000, hi=0x4a2d528):
    out=[]
    for a in range(lo,hi,4):
        w=struct.unpack_from('<I',D,a)[0]
        if (w & 0x0ff00fff)==(0x05900000|imm) and (w>>28)!=0xf:
            out.append(a)
    return out
if __name__=='__main__':
    imm=int(sys.argv[1],16)
    for a in scan(imm):
        # check following blx within 6 instrs
        ok=False
        for k in range(1,7):
            w=struct.unpack_from('<I',D,a+4*k)[0]
            if (w&0x0ffffff0)==0x012fff30 or (w&0x0ffffff0)==0x012fff10: ok=True;break
        if ok: print(hex(a),[hex(x) for x in (func_of(a) or (0,0))])
