# robust ARM disasm: python3 dd.py start end  (skips undecodable words)
import sys,struct,capstone
exec(open('/workspace/work/qjcj-gh/game/re/ue.py').read())
exec(open('/workspace/work/qjcj-gh/game/re/ds.py').read().split('def show')[0].split("exec(open('ue.py').read())")[1])
md=capstone.Cs(capstone.CS_ARCH_ARM,capstone.CS_MODE_ARM)
def show2(a,b):
    lit={}
    x=a
    while x<b:
        ins=list(md.disasm(D[x:x+4],x))
        if not ins: print(' %x  .word %08x'%(x,struct.unpack_from('<I',D,x)[0])); x+=4; continue
        i=ins[0]; ops=i.op_str; c=''
        if i.mnemonic=='ldr' and '[pc, #' in ops:
            imm=int(ops.split('#')[1].rstrip(']'),16); lit[ops.split(',')[0]]=struct.unpack_from('<I',D,i.address+8+imm)[0]; c='  ; lit %x'%lit[ops.split(',')[0]]
        elif i.mnemonic=='add' and ', pc, ' in ops:
            r=ops.split(', ')[2]
            if r in lit:
                v=(lit[r]+i.address+8)&0xffffffff; s=cstr(v) if cstr(v) and len(cstr(v))>2 else wstr(v); c='  ; '+repr(s) if s else '  ; =%x'%v
        elif i.mnemonic=='ldr' and '[pc, r' in ops:
            r=ops.split('[pc, ')[1].rstrip(']')
            if r in lit: v=(lit[r]+i.address+8)&0xffffffff; c='  ; *%x'%v
        print(' %x  %s %s%s'%(i.address,i.mnemonic,ops,c)); x+=4
if __name__=='__main__': show2(int(sys.argv[1],16),int(sys.argv[2],16))
