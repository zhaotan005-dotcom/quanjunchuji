# v2.70 test cave B: restores arrays and sets +0x3b0 from +0x3c0
from unicorn import *; from unicorn.arm_const import *
import struct, zone_cave as z
BASE=0x100000; SYS=0x200000; STK=0x300000
def run(cnt, swapped=True):
    mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
    mu.mem_map(BASE,0x10000); mu.mem_map(SYS,0x1000); mu.mem_map(STK,0x10000)
    S=BASE  # ZS
    mu.mem_write(S+z.B_OFF-z.S_OFF, z.B)
    hdr=struct.pack('<6I',0x5000,11,SYS if swapped else 0,0xAAA,2,2)
    mu.mem_write(S,hdr)
    mu.mem_write(SYS+0x358,struct.pack('<I',cnt))
    mu.mem_write(SYS+0x3c0,struct.pack('<f',1224000.0))
    mu.mem_write(SYS+0x3b0,struct.pack('<f',432749.0))
    # fake frame: fp such that sp=fp-0x1c then pop r4-r11,pc
    fp=STK+0x8000; frame=fp-0x1c
    ret=BASE+0x8000; mu.mem_write(ret,b'\x00\x00\xa0\xe1')
    mu.mem_write(frame,struct.pack('<9I',SYS,0,0,0,0,0,0,0,ret))
    mu.reg_write(UC_ARM_REG_FP,fp); mu.reg_write(UC_ARM_REG_SP,STK+0x100)
    mu.emu_start(S+z.B_OFF-z.S_OFF, ret)
    r=struct.unpack('<f',mu.mem_read(SYS+0x3b0,4))[0]
    a=struct.unpack('<3I',mu.mem_read(SYS+0x33c,12)); h=struct.unpack('<6I',mu.mem_read(S,24))
    return r,a,h[2]
print(run(11)); print(run(0)); print(run(11,False))
assert run(11)[0]==1224000.0 and run(0)[0]==432749.0 and run(11,False)[0]==432749.0
assert run(11)[1]==(0xAAA,2,2) and run(11)[2]==0
print('ok')
