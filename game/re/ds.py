# disasm with pc-relative string resolution: python3 ds.py start end
import sys
exec(open('ue.py').read())
md=capstone.Cs(capstone.CS_ARCH_ARM,capstone.CS_MODE_ARM)
def wstr(v):
    f=v2f(v)
    if f is None: return None
    e=f
    while e<f+800 and D[e:e+4]!=b'\0\0\0\0': e+=4
    try:
        s=D[f:e].decode('utf-32le')
        return s if len(s)>2 and s.isprintable() else None
    except: return None
def show(a,b):
    lit={}
    for i in md.disasm(D[a:b],a):
        c=''
        ops=i.op_str
        if i.mnemonic=='ldr' and '[pc, #' in ops:
            imm=int(ops.split('#')[1].rstrip(']'),16); lit[ops.split(',')[0]]=struct.unpack_from('<I',D,i.address+8+imm)[0]
        elif i.mnemonic=='add' and ', pc, ' in ops:
            r=ops.split(', ')[2]
            if r in lit:
                v=(lit[r]+i.address+8)&0xffffffff; s=wstr(v) if (cstr(v) or '')[:1] and len(cstr(v) or '')<=1 else (cstr(v) or wstr(v)); c='  ; '+repr(s) if s else '  ; =%x'%v
        elif i.mnemonic=='ldr' and '[pc, r' in ops:
            r=ops.split('[pc, ')[1].rstrip(']')
            if r in lit: v=(lit[r]+i.address+8)&0xffffffff; c='  ; *%x'%v
        elif i.mnemonic in('bl','blx') and ops.startswith('#'): c=''
        print(' %x  %s %s%s'%(i.address,i.mnemonic,ops,c))
if __name__=='__main__': show(int(sys.argv[1],16),int(sys.argv[2],16))
