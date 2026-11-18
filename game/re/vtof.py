# vtable of a class by its utf32 name 'ANZBRCharacter': find GetPrivateStaticClass -> ctor -> vtable
import sys
exec(open('ue.py').read())
def ctor_of(name):
    b=name.encode('utf-32le')+b'\0\0\0\0'
    i=D.find(b)
    while i>=0:
        if D[i-4:i]==b'\0\0\0\0' or True:
            v=f2v(i)
            for x,k in XR.get(v,[]):
                # walk back to find str rX,[sp,#0x14] with add rX,pc
                f=func_of(x)[0]
                for ins in mda.disasm(D[v2f(f):v2f(x)+8],f):
                    pass
                return f,v
        i=D.find(b,i+1)
def vt_from_ctor(c,depth=0):
    # first 'add rX, pc, rY' followed by 'add rX, rX, #8' and 'str rX, [r4]'
    lits={}
    for ins in mda.disasm(D[v2f(c):v2f(c)+0x400],c):
        o=ins.op_str
        if ins.mnemonic=='ldr' and '[pc, #' in o:
            r=o.split(',')[0]; imm=int(o.split('#')[1].rstrip(']'),16); lits[r]=u32v(ins.address+8+imm)
        if ins.mnemonic=='add' and ', pc, ' in o:
            r=o.split(', ')[2]
            if r in lits: last=(lits[r]+ins.address+8)&0xffffffff
        if ins.mnemonic=='add' and o.endswith('#8') and 'last' in dir(): cand=last+8
        if ins.mnemonic=='str' and o.endswith('[r4]') and 'cand' in dir(): return cand
        if ins.mnemonic in('pop',) : break
    return None
