# v2.50 pickup mesh fallback cave (ANZPickupInventory async mesh callback 0xce5fc0)
import sys; sys.path.insert(0,'.')
from asm import asm
import capstone
ORG=0x4a2d540+0x300
src='''
c1:
 push {r0-r2}
 ldr r0, [pc, #LSLOT]
 ldr r1, [r0]
 cmp r1, #0
 bne c1x
 str r6, [r0]
 ldr r1, [pc, #LGUOA]
 ldr r1, [r1, #0x10]
 ldr r2, [r6, #8]
 add r1, r1, r2, lsl #4
 ldr r2, [r1, #4]
 orr r2, r2, #0x40000000
 str r2, [r1, #4]
c1x:
 pop {r0-r2}
 ldr r7, [r5, #0x2c4]
 cmp r7, #0
 ldr pc, [pc, #LBACK1]
c2:
 push {r0}
 ldr r0, [pc, #LSLOT]
 ldr r0, [r0]
 cmp r0, #0
 movne r6, r0
 pop {r0}
 ldrne pc, [pc, #LSUCC]
 ldr r0, [pc, #LR0]
 ldr pc, [pc, #LBACK2]
'''
# two-pass: first assemble with dummy to get label addrs
lines=[l for l in src.strip().split('\n')]
n=sum(1 for l in lines if l.strip() and not l.strip().endswith(':'))
LIT0=ORG+4*n
lits=['LSLOT','LGUOA','LBACK1','LSUCC','LR0','LBACK2']
la={k:LIT0+4*i for i,k in enumerate(lits)}
pc=ORG; out=[]
for l in lines:
    s=l.strip()
    if not s or s.endswith(':'): out.append(l); continue
    for k,v in la.items():
        if '#'+k in s: s=s.replace('#'+k,'#%d'%(v-pc-8))
    out.append(' '+s); pc+=4
code,lab=asm('\n'.join(out),ORG)
code+=b'\0'*(4*len(lits))
md=capstone.Cs(capstone.CS_ARCH_ARM,capstone.CS_MODE_ARM)
for i in md.disasm(code[:4*n],ORG): print('%08x %s %s'%(i.address,i.mnemonic,i.op_str))
print('labels',{k:hex(v) for k,v in lab.items()}, {k:hex(v) for k,v in la.items()})
print('C1off',hex(lab['c1']-0x4a2d540),'C2off',hex(lab['c2']-0x4a2d540),'len',hex(len(code)))
print('HEX',code[:4*n].hex())
