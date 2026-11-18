import sys
exec(open('ue.py').read())
def pcrefs(tlo,thi,lo,hi):
    out=[]
    for a in range(lo,hi,4):
        w=struct.unpack_from('<I',D,a)[0]
        # add Rd, pc, Rm  : cond 0000 1000 1111 Rd 0000 0000 Rm  -> 0x008f0000
        if (w&0x0fff0ff0)==0x008f0000:
            rm=w&0xf
            # find previous ldr rm,[pc,#imm] within 12 instrs
            for k in range(1,16):
                p=a-4*k; x=struct.unpack_from('<I',D,p)[0]
                if (x&0x0fff0000)==0x059f0000 and ((x>>12)&0xf)==rm:
                    lit=struct.unpack_from('<I',D,p+8+(x&0xfff))[0]
                    v=(lit+a+8)&0xffffffff
                    if tlo<=v<thi: out.append((a,v))
                    break
    return out
if __name__=='__main__':
    t=int(sys.argv[1],16); n=int(sys.argv[2],16); lo=int(sys.argv[3],16); hi=int(sys.argv[4],16)
    for a,v in pcrefs(t,t+n,lo,hi): print(hex(a),hex(v),[hex(x) for x in (func_of(a) or (0,0))])
