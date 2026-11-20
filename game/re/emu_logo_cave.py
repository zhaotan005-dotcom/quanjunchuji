# unicorn test for logo_cave.py: 3 paths (widget exists -> base tick; waiting; >0.5s -> Begin with elapsed reset) + timeout value
import struct, sys
sys.path.insert(0, '.')
from unicorn import *
from unicorn.arm_const import *
import logo_cave as L
def run(widget, elapsed, dt, entry):
    mu = Uc(UC_ARCH_ARM, UC_MODE_ARM)
    # enable VFP: CPACR full access + FPEXC.EN
    mu.mem_map(0x1000000, 0x800000)
    mu.mem_map(0x10000, 0x1000); mu.mem_map(0x7f000, 0x1000)
    mu.mem_write(L.ORG, L.CODE)
    for a in (L.BEGIN, L.BASE_TICK): mu.mem_write(a, struct.pack('<I', 0xe320f000))  # nop markers
    mu.mem_write(0x10000, b'\0' * 0x100)
    mu.mem_write(0x10024, struct.pack('<I', widget)); mu.mem_write(0x10020, struct.pack('<f', elapsed))
    # VFP enable sequence
    init = struct.pack('<4I', 0xee111f50, 0xe381160f, 0xee011f50, 0xe3a00101)  # mrc p15 CPACR; orr #0xf00000; mcr; mov r0,#0x40000000
    init += struct.pack('<2I', 0xeee80a10, 0xe320f000)  # vmsr fpexc, r0 ; nop
    mu.mem_map(0x20000, 0x1000); mu.mem_write(0x20000, init); mu.emu_start(0x20000, 0x20000 + len(init) - 4)
    mu.reg_write(UC_ARM_REG_R0, 0x10000); mu.reg_write(UC_ARM_REG_R1, struct.unpack('<I', struct.pack('<f', dt))[0])
    mu.reg_write(UC_ARM_REG_LR, 0x7f000); mu.reg_write(UC_ARM_REG_SP, 0x7f800)
    end = {}
    def hook(uc, addr, size, ud):
        if addr in (L.BEGIN, L.BASE_TICK, 0x7f000): end['at'] = addr; uc.emu_stop()
    mu.hook_add(UC_HOOK_CODE, hook)
    mu.emu_start(entry, 0x7f004, count=100)
    el = struct.unpack('<f', mu.mem_read(0x10020, 4))[0]
    return end.get('at'), el, mu.reg_read(UC_ARM_REG_R0)
r = run(0x1234, 1.0, 0.1, L.LTICK); assert r[0] == L.BASE_TICK and r[2] == 0x10000, r; print('widget exists -> base tick ok', r)
r = run(0, 0.2, 0.1, L.LTICK); assert r[0] == 0x7f000 and abs(r[1] - 0.3) < 1e-6, r; print('waiting -> return, elapsed 0.3 ok', r)
r = run(0, 0.45, 0.1, L.LTICK); assert r[0] == L.BEGIN and r[1] == 0.0 and r[2] == 0x10000, r; print('>0.5 -> Begin(this), elapsed reset ok', r)
r = run(0, 0.0, 0.0, L.LBEGIN); assert r[0] == 0x7f000, r; print('Begin slot -> return ok')
r = run(0, 0.0, 0.0, L.LTIME); assert r[0] == 0x7f000 and struct.unpack('<f', struct.pack('<I', r[2]))[0] == 4.5, r; print('timeout 4.5 ok')
print('ALL OK')
