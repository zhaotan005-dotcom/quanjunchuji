# v3.19：验证 Walls.place 写的 FQuat(0,0,sin(yaw/2),cos(yaw/2)) 和游戏 FRotator->FQuat(0x19fac90，SpawnActor 0x2aa8ad4 用的)一致
import struct, math
from unicorn import *
from unicorn.arm_const import *
D=open('/workspace/work/qjcj/x/lib/armeabi-v7a/libUE4.so','rb').read()
CODE=0x19f0000; STK=0x7000000; DATA=0x100000
def q(yaw,pitch=0.0,roll=0.0):
    mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
    mu.mem_map(CODE,0x20000); mu.mem_write(CODE,D[CODE:CODE+0x20000])
    mu.mem_map(STK-0x10000,0x20000); mu.mem_map(DATA,0x1000)
    # enable VFP/NEON
    mu.reg_write(UC_ARM_REG_C1_C0_2, mu.reg_read(UC_ARM_REG_C1_C0_2)|(0xf<<20))
    mu.reg_write(UC_ARM_REG_FPEXC,0x40000000)
    mu.mem_write(DATA+0x40,struct.pack('<3f',pitch,yaw,roll))
    mu.reg_write(UC_ARM_REG_R0,DATA); mu.reg_write(UC_ARM_REG_R1,DATA+0x40)
    mu.reg_write(UC_ARM_REG_SP,STK); mu.reg_write(UC_ARM_REG_LR,0xdead0)
    mu.mem_map(0xd0000,0x1000)
    mu.emu_start(0x19fac90,0xdead0,count=2000)
    return struct.unpack('<4f',mu.mem_read(DATA,16))
for yaw in (0,37.5,90,-135,200):
    g=q(yaw); r=math.radians(yaw); mine=(0,0,math.sin(r/2),math.cos(r/2))
    ok=all(abs(a-b)<1e-4 for a,b in zip(g,mine)) or all(abs(a+b)<1e-4 for a,b in zip(g,mine))
    print(yaw,['%.4f'%x for x in g],['%.4f'%x for x in mine],'OK' if ok else 'MISMATCH'); assert ok
print('ALL OK')
