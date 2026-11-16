import struct
from unicorn import *
from unicorn.arm_const import *
from vcave import build
ORG=0x4a2d940
code,lab=build(ORG)
lit=lab['lit']-ORG
SPAWN=0x10000; code=code[:lit]+struct.pack('<I',SPAWN)+code[lit+4:]
mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
mu.mem_map(0x4a2d000,0x2000); mu.mem_write(ORG,code)
mu.mem_map(0x10000,0x1000)
# spawn stub: str r1,[0x10800]; str r2..; ldr r0 from r2 -> store floats; bx lr
def W(a,v): mu.mem_write(a,struct.pack('<I',v))
stub=bytes.fromhex('04c09fe5' '00108ce5' '1eff2fe1' )+struct.pack('<I',0x10800)  # ldr ip,=0x10800; str r1,[ip]; bx lr
mu.mem_map(0x20000,0x10000)
mu.mem_write(0x10000,stub)
GW=0x10100; mu.mem_write(GW,bytes.fromhex('00009fe5'+'1eff2fe1')+struct.pack('<I',0x21000))  # ldr r0,[pc,#0]?? 
# ldr r0,[pc,#0] at GW loads GW+8 = world ptr; but next word is bx lr... fix: ldr r0,[pc,#0] -> reads GW+8
PCO=0x20000; VT=0x20800; W(PCO,VT); W(VT+0xa0,GW)
CH=0x22000; W(PCO+0x2c0,CH); RC=0x23000; W(CH+0x128,RC)
for i,o in enumerate([0x130,0x134,0x138,0x13c,0x140,0x144,0x148]): W(RC+o,0x100+i)
WORLD=0x21000; GM=0x24000; VM=0x25000; DT=0x26000
W(WORLD+0x94,GM); W(GM+0x914,VM); W(VM+0x2c0,DT)
BUF=0x28000
s='V1901001x%d'%BUF
STR=0x29000; mu.mem_write(STR,b''.join(struct.pack('<I',ord(c)) for c in s)+b'\0'*4)
mu.mem_map(0x7f000,0x1000)
SP=0x7ff00-0x44-36
# emulate stack as pushed by give cave: 9 regs then sub 0x44; ret address -> END
END=0x4a2e000-4
mu.mem_write(SP+0x44+32,struct.pack('<I',END))
mu.reg_write(UC_ARM_REG_SP,SP); mu.reg_write(UC_ARM_REG_R4,PCO); mu.reg_write(UC_ARM_REG_R5,STR); mu.reg_write(UC_ARM_REG_R6,len(s)+1); mu.reg_write(UC_ARM_REG_R0,ord('V'))
mu.emu_start(ORG,END)
r=lambda a: struct.unpack('<I',mu.mem_read(a,4))[0]
print('magic %x pc %x ch %x loc %x %x %x world %x gm %x vm %x dt %x id %d rc %x q %x' % (r(BUF),r(BUF+4),r(BUF+8),r(BUF+12),r(BUF+16),r(BUF+20),r(BUF+24),r(BUF+28),r(BUF+32),r(BUF+36),r(BUF+64),r(BUF+68),r(BUF+72)))
print('spawn got id', r(0x10800))
assert r(BUF)==0x5b03 and r(0x10800)==1901001 and r(BUF+36)==DT
# info only
mu.mem_write(BUF,b'\0'*128); s='V0x%d'%BUF; mu.mem_write(STR,b''.join(struct.pack('<I',ord(c)) for c in s)+b'\0'*4)
W(0x10800,0)
mu.reg_write(UC_ARM_REG_SP,SP); mu.reg_write(UC_ARM_REG_R4,PCO); mu.reg_write(UC_ARM_REG_R5,STR); mu.reg_write(UC_ARM_REG_R6,len(s)+1); mu.reg_write(UC_ARM_REG_R0,ord('V'))
mu.emu_start(ORG,END)
print('info magic %x dt %x spawncalled %d'%(r(BUF),r(BUF+36),r(0x10800))); assert r(BUF)==0x5b02 and r(0x10800)==0
# 'X' -> nothing
mu.mem_write(BUF,b'\0'*128); mu.reg_write(UC_ARM_REG_SP,SP); mu.reg_write(UC_ARM_REG_R0,ord('X')); mu.emu_start(ORG,END); assert r(BUF)==0
print('OK')
