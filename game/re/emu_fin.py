import struct, capstone
from unicorn import *
from unicorn.arm_const import *
exec(open('fin_cave.py').read().split("if __name__")[0])
B=0x10000000; CAVE=B+0x4a2d540+0x3d0
md=capstone.Cs(capstone.CS_ARCH_ARM,capstone.CS_MODE_ARM)
c=bytes.fromhex(code())+struct.pack('<I',B+RET)
for i in md.disasm(c[:-4],CAVE): print(hex(i.address-B),i.mnemonic,i.op_str)
def run(stub_kind):
    mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
    mu.mem_map(B+0x4a2d000,0x2000); mu.mem_write(CAVE,c)
    mu.mem_map(B+0xcde000,0x1000)   # ret target page
    mu.mem_map(0x20000000,0x10000)  # heap
    mu.mem_map(0x30000000,0x10000)  # stack
    INV=0x20000000; VT=0x20001000; STUB=0x20002000; ITEM=0x20003000
    mu.mem_write(INV,struct.pack('<I',VT)); mu.mem_write(INV+0x2c0,struct.pack('<I',ITEM))
    mu.mem_write(VT+0x4a8,struct.pack('<I',STUB))
    if stub_kind=='local': s=[0xe3a03000,0xe58032c0,0xe3a01055,0xe3a02066,0xe12fff1e]   # mov r3,#0; str r3,[r0,#0x2c0]; clobber r1,r2; bx lr
    elif stub_kind=='remote': s=[0xe3a01055,0xe3a02066,0xe12fff1e]
    else: s=[0xe59f3008,0xe58032c0,0xe3a01055,0xe12fff1e,0x20004000]  # other item
    mu.mem_write(STUB,b''.join(struct.pack('<I',w) for w in s))
    mu.reg_write(UC_ARM_REG_R4,INV); mu.reg_write(UC_ARM_REG_R5,0x1234); mu.reg_write(UC_ARM_REG_R1,ITEM)
    mu.reg_write(UC_ARM_REG_SP,0x30008000)
    mu.emu_start(CAVE,B+RET,count=100)
    pc=mu.reg_read(UC_ARM_REG_PC); v=struct.unpack('<I',mu.mem_read(INV+0x2c0,4))[0]
    return hex(pc-B),hex(v),hex(mu.reg_read(UC_ARM_REG_SP)),hex(mu.reg_read(UC_ARM_REG_R5)),hex(mu.reg_read(UC_ARM_REG_R4))
for k in ('local','remote','other'): print(k,run(k))
