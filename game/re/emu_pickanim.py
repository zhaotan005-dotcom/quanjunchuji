# v2.70 unicorn test for pickanim_cave
from unicorn import *; from unicorn.arm_const import *
import struct, pickanim_cave as P
CODEB=0x1700000; DATA=0x5000000; WORLD=0x5100000; STK=0x5200000
def mk():
    mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
    mu.mem_map(0xd00000,0x100000); mu.mem_map(CODEB,0x100000); mu.mem_map(0x2700000,0x100000)
    mu.mem_map(DATA,0x1000); mu.mem_map(WORLD,0x1000); mu.mem_map(STK,0x10000)
    # enable VFP
    mu.reg_write(UC_ARM_REG_C1_C0_2, mu.reg_read(UC_ARM_REG_C1_C0_2) | (0xf << 20))
    mu.reg_write(UC_ARM_REG_FPEXC, 0x40000000)
    mu.mem_write(P.ORG, P.CODE + struct.pack('<I', DATA))
    mu.mem_write(0x273d080, struct.pack('<II', 0xe59f0000, 0xe12fff1e) + struct.pack('<I', WORLD))  # ldr r0,[pc]; bx lr; .word WORLD  (pc+8 -> word at +8)
    mu.mem_write(DATA, struct.pack('<IffII', 0, 0.0, 1.5, 0, 0))
    return mu
def call(mu, ch, t):
    mu.mem_write(WORLD+0x740, struct.pack('<f', t))
    ret=0xd00100
    mu.reg_write(UC_ARM_REG_SP, STK+0x8000); mu.reg_write(UC_ARM_REG_R0, ch); mu.reg_write(UC_ARM_REG_LR, ret)
    hit=[]
    def hk(uc,addr,size,u):
        if addr in (0xd21c04, ret): hit.append(addr); uc.emu_stop()
    h=mu.hook_add(UC_HOOK_CODE, hk)
    mu.emu_start(P.ORG, 0xffffffff, count=200)
    mu.hook_del(h)
    return 'orig' if hit and hit[0]==0xd21c04 else 'skip' if hit else 'none'
mu=mk()
r=[call(mu,0x1000,10.0), call(mu,0x1000,10.4), call(mu,0x1000,12.0), call(mu,0x2000,12.1), call(mu,0x2000,11.0)]
d=struct.unpack('<IffII', mu.mem_read(DATA,20)); print(r, d)
assert r==['orig','skip','orig','orig','orig'] and d[3]==1 and d[4]==5
print('ok')
