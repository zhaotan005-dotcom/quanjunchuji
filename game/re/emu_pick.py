from unicorn import *; from unicorn.arm_const import *
import struct
code=bytes.fromhex('07002de95c009fe5001090e5000051e30700001a006080e54c109fe5101091e5082096e5021281e0042091e5012182e3042081e50700bde8c47295e5000057e328f09fe501002de918009fe5000090e5000050e30060a0110100bde810f09f1510009fe510f09fe5')
B=0x10000000; ORG=B+0x4a2d840
mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
mu.mem_map(B,0x6000000)
mu.mem_map(0x80000000,0x100000)  # heap: slot, objects, stack
SLOT=0x80000000; OBJS=0x80001000; GUOA=0x80002000; MESH=0x80003000; STK=0x800f0000
lits=[SLOT,GUOA,B+0xce6070,B+0xce6068,B+0x4a2eab0,B+0xce62a8]
mu.mem_write(ORG,code+b''.join(struct.pack('<I',x) for x in lits))
mu.mem_write(GUOA+0x10,struct.pack('<I',OBJS)); mu.mem_write(MESH+8,struct.pack('<I',3))
def run(start,regs):
    for k,v in regs.items(): mu.reg_write(k,v)
    mu.reg_write(UC_ARM_REG_SP,STK)
    stops=[B+0xce6070,B+0xce6068,B+0xce62a8]
    pc=start; n=0
    while pc not in stops and n<100:
        mu.emu_start(pc,0,count=1); pc=mu.reg_read(UC_ARM_REG_PC); n+=1
    return pc
R=UC_ARM_REG_R0
# 1) failure with empty slot -> back2, r0 = B+0x4a2eab0, r6 unchanged
p=run(ORG+0x44,{UC_ARM_REG_R6:0x1234,UC_ARM_REG_R0:7})
print('fail empty ->',hex(p-B),hex(mu.reg_read(R)-B),hex(mu.reg_read(UC_ARM_REG_R6)),hex(mu.reg_read(UC_ARM_REG_SP)))
# 2) success path stores mesh + root flag
pk=0x80004000; mu.mem_write(pk+0x2c4,struct.pack('<I',0x5555))
p=run(ORG,{UC_ARM_REG_R6:MESH,UC_ARM_REG_R5:pk,UC_ARM_REG_R0:1,UC_ARM_REG_R1:2,UC_ARM_REG_R2:3})
fl=struct.unpack('<I',mu.mem_read(OBJS+3*16+4,4))[0]
print('succ ->',hex(p-B),'r7',hex(mu.reg_read(UC_ARM_REG_R7)),'slot',hex(struct.unpack('<I',mu.mem_read(SLOT,4))[0]),'flag',hex(fl),'r0-2',mu.reg_read(R),mu.reg_read(UC_ARM_REG_R1),mu.reg_read(UC_ARM_REG_R2),'Z',(mu.reg_read(UC_ARM_REG_CPSR)>>30)&1)
# 3) failure with slot set -> succ, r6=MESH
p=run(ORG+0x44,{UC_ARM_REG_R6:0x1234,UC_ARM_REG_R0:7})
print('fail set ->',hex(p-B),'r6',hex(mu.reg_read(UC_ARM_REG_R6)),'r0',mu.reg_read(R),hex(mu.reg_read(UC_ARM_REG_SP)))
# 4) success again: no second store
p=run(ORG,{UC_ARM_REG_R6:0x9999,UC_ARM_REG_R5:pk})
print('succ2 slot',hex(struct.unpack('<I',mu.mem_read(SLOT,4))[0]))
