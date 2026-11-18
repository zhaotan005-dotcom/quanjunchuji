# v2.67 服务端不能上车：0xdb00f0 b → cave 0x171e7fc。unicorn 跑真 libUE4 代码，检查 4 种网络模式 × ClosedVehicle 有/无 的去向
import sys, struct; sys.path.insert(0, '/workspace/work/quanjunchuji/game/re')
from ue import D, v2f
from unicorn import *; from unicorn.arm_const import *
CAVE, HOOK = 0x171e7fc, 0xdb00f0
CAVE_O = '0400a0e10610a0e1eef7eaeb47ffffea906aa003'
CAVE_N = '000057e3fe46da0a020050e30847da0a3846daea'
HOOK_O, HOOK_N = 'ce00000a', 'c1b925ea'
assert D[v2f(CAVE):v2f(CAVE)+20].hex() == CAVE_O
assert D[v2f(HOOK):v2f(HOOK)+4].hex() == HOOK_O
def run(netmode, closed):
    mu = Uc(UC_ARCH_ARM, UC_MODE_ARM)
    for a in (0xdb0000, 0x171e000):
        mu.mem_map(a, 0x1000); mu.mem_write(a, D[v2f(a):v2f(a)+0x1000])
    mu.mem_write(CAVE, bytes.fromhex(CAVE_N)); mu.mem_write(HOOK, bytes.fromhex(HOOK_N))
    mu.mem_map(0x100000, 0x2000); mu.mem_write(0x100000 + 0xb84, struct.pack('<I', closed))
    mu.reg_write(UC_ARM_REG_R5, 0x100000); mu.reg_write(UC_ARM_REG_R0, netmode); mu.reg_write(UC_ARM_REG_R6, 0)
    end = {0xdb0400: '只查车/座位（加入的人）', 0xdb0430: '返回 0（不能上）', 0xdb00f4: '原版检查（距离/视线）'}
    hit = []
    def hk(uc, addr, size, ud):
        if addr in end: hit.append(addr); uc.emu_stop()
    mu.hook_add(UC_HOOK_CODE, hk)
    mu.emu_start(0xdb00e8, 0xdb0444, count=20)
    return hit[0], mu.reg_read(UC_ARM_REG_R0), mu.reg_read(UC_ARM_REG_R6)
want = {(0,0):0xdb0400,(0,1):0xdb00f4,(1,0):0xdb0400,(1,1):0xdb00f4,(2,0):0xdb0400,(2,1):0xdb0430,(3,0):0xdb0400,(3,1):0xdb00f4}
ok = True
for (nm, c), w in want.items():
    h, r0, r6 = run(nm, 0x200000 if c else 0)
    good = h == w and r0 == nm and r6 == 0
    ok &= good
    print('NetMode', nm, 'ClosedVehicle', '有' if c else '空', '->', hex(h), 'ok' if good else 'FAIL')
print('全部通过' if ok else '有失败'); sys.exit(0 if ok else 1)
