import struct, sys
sys.path.insert(0,'.')
from unicorn import *
from unicorn.arm_const import *
import boxcave
code=bytearray(boxcave.build())
mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
mu.mem_map(0x0,0x4000000)
BUF=0x3000000; BOX=0x3100000; DATA=0x3200000; NEW=0x3300000; STK=0x3f00000
struct.pack_into('<I',code,len(code)-4,BUF)
mu.mem_write(boxcave.ORG,bytes(code))
# GROW stub: copy old data to NEW, set arr.data=NEW, max=64 ; ORIG stub: write 0xdead to BUF+0x100
grow=bytes.fromhex('00' * 0)
# implement GROW: ldr r2,[r0]; (skip copy) ldr r3,=NEW; str r3,[r0]; mov r3,#64; str r3,[r0,#8]; bx lr
from asm import asm
g,_=asm('''
    movw r3, #0
    movt r3, #0x330
    str r3, [r0]
    mov r3, #64
    str r3, [r0, #8]
    .word 0xe12fff1e
''',boxcave.GROW,{})
mu.mem_write(boxcave.GROW,g)
o,_=asm('''
    movw r3, #0xdead
    movw r2, #0x100
    movt r2, #0x300
    str r3, [r2]
    .word 0xe12fff1e
''',boxcave.ORIG,{})
mu.mem_write(boxcave.ORIG,o)
RET=0x100000; mu.mem_write(RET,bytes.fromhex('feffffea'))
def run(n,num,mx):
    mu.mem_write(BUF,struct.pack('<II',n,0)+b''.join(struct.pack('<II',1000+i,i) for i in range(n)))
    mu.mem_write(BOX+0x3f8,struct.pack('<III',DATA,num,mx))
    mu.mem_write(BUF+0x100,b'\0'*4)
    mu.reg_write(UC_ARM_REG_R0,BOX); mu.reg_write(UC_ARM_REG_R1,0x77); mu.reg_write(UC_ARM_REG_SP,STK); mu.reg_write(UC_ARM_REG_LR,RET)
    for r in (UC_ARM_REG_R4,UC_ARM_REG_R5,UC_ARM_REG_R6,UC_ARM_REG_R7,UC_ARM_REG_R8): mu.reg_write(r,0x1111)
    mu.emu_start(boxcave.ORG,RET,count=5000)
    d,nm,mx2=struct.unpack('<III',mu.mem_read(BOX+0x3f8,12))
    ents=[struct.unpack('<II',mu.mem_read(d+8*i,8)) for i in range(nm)]
    print('n',n,'num',nm,'max',mx2,'data',hex(d),ents[:4],'..',ents[-1:] if ents else '', 'buf0',struct.unpack('<II',mu.mem_read(BUF,8)),'orig',hex(struct.unpack('<I',mu.mem_read(BUF+0x100,4))[0]),'sp',hex(mu.reg_read(UC_ARM_REG_SP)),'r4',hex(mu.reg_read(UC_ARM_REG_R4)))
run(5,0,10)
run(5,2,3)
run(0,0,0)
