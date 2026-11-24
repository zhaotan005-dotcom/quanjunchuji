# unicorn：真 libUE4 的 CalcAirLineByRestrictedAreaInfo(0x118dc6c) + air_cave，库按 0 映射。
# 桩：rand 0x8acef8、sqrtf 0x8ad1bc、cosf 0x8ad1ec、sinf 0x8ad234（PLT）。检查起终点/跳伞段/高度。
import sys, struct, math, random
sys.path.insert(0, '/workspace/work/qj2/game/re')
import air_cave as A
from unicorn import *
from unicorn.arm_const import *
D = A.D
STOP = 0x30000000
def f2b(x): return struct.unpack('<I', struct.pack('<f', x))[0]
def b2f(x): return struct.unpack('<f', struct.pack('<I', x & 0xffffffff))[0]
def mk(cave=True):
    mu = Uc(UC_ARCH_ARM, UC_MODE_ARM)
    mu.mem_map(0, 0x4a2f000); mu.mem_write(0, D[:0x4a2d528])
    mu.mem_map(0x4a2f000, 0x1000000)
    mu.mem_write(0x4a2e528, D[0x4a2d528:0x4a2d528 + 0x6fcbf8])
    for base in (0x10000000, 0x20000000, STOP): mu.mem_map(base, 0x100000)
    mu.mem_write(STOP, b"\xfe\xff\xff\xea")
    # VFP 打开
    mu.reg_write(UC_ARM_REG_C1_C0_2, mu.reg_read(UC_ARM_REG_C1_C0_2) | (0xf << 20))
    mu.reg_write(UC_ARM_REG_FPEXC, 0x40000000)
    if cave:
        code, do = A.build(); mu.mem_write(A.CAVE, code); mu.mem_write(A.HOOK, A.hook_new())
    # GUObjectArray：*0x51250ac 指向的对象 +0x10 = 空表
    ga = struct.unpack('<I', mu.mem_read(0x51250ac, 4))[0]
    try: mu.mem_write(ga + 0x10, struct.pack('<I', 0x20080000))
    except Exception as e: print('GUObjectArray map?', hex(ga), e)
    return mu, (do if cave else 0)
def w32(mu, a, v): mu.mem_write(a, struct.pack('<I', v & 0xffffffff))
def r32(mu, a): return struct.unpack('<I', mu.mem_read(a, 4))[0]
def rf(mu, a): return b2f(r32(mu, a))
def run(side, height, center, radius, seed, cave=True, param_over=None):
    mu, do = mk(cave)
    if cave: mu.mem_write(A.CAVE + do, A.data(side, height))
    rnd = random.Random(seed)
    def h(uc, addr, size, ud):
        lr = uc.reg_read(UC_ARM_REG_LR)
        if addr == 0x8acef8: uc.reg_write(UC_ARM_REG_R0, rnd.randint(0, 0x7fffffff))
        elif addr == 0x8ad1bc: uc.reg_write(UC_ARM_REG_R0, f2b(math.sqrt(max(0, b2f(uc.reg_read(UC_ARM_REG_R0))))))
        elif addr == 0x8ad1ec: uc.reg_write(UC_ARM_REG_R0, f2b(math.cos(b2f(uc.reg_read(UC_ARM_REG_R0)))))
        elif addr == 0x8ad234: uc.reg_write(UC_ARM_REG_R0, f2b(math.sin(b2f(uc.reg_read(UC_ARM_REG_R0)))))
        else: return
        uc.reg_write(UC_ARM_REG_PC, lr)
    for a in (0x8acef8, 0x8ad1bc, 0x8ad1ec, 0x8ad234): mu.hook_add(UC_HOOK_CODE, h, begin=a, end=a + 3)
    bad = []
    def hp(uc, addr, size, ud):
        if addr < 0x8b0000 and addr >= 0x800000 and addr not in (0x8acef8, 0x8ad1bc, 0x8ad1ec, 0x8ad234): bad.append(hex(addr)); uc.emu_stop()
    mu.hook_add(UC_HOOK_CODE, hp, begin=0x800000, end=0x8b0000)
    SYS = 0x10000000; PLANE = 0x10010000; PARAM = 0x200f8000; CEN = 0x200f9000; RAD = 0x200f9100
    w32(mu, PLANE + 8, 1)
    # 游戏原数据（这个包）：Speed 10000 Height 20000 RegionFly/Jump 0（无效）CenterRadius 100000 MaxAirlineRadius 0 JumpRadiusScale 0.8 MaxDistToCenter -1
    p = {0: f2b(10000), 4: f2b(20000), 8: f2b(0.23), 0x34: f2b(100000), 0x38: 0, 0x3c: f2b(0.8), 0x40: f2b(-1)}
    if param_over: p.update(param_over)
    for k, v in p.items(): w32(mu, PARAM + k, v)
    mu.mem_write(PLANE + 0x2b8, bytes(mu.mem_read(PARAM, 0x45)))
    for i, v in enumerate(center): w32(mu, CEN + 4 * i, f2b(v))
    w32(mu, RAD, f2b(radius))
    sp = 0x200f0000; w32(mu, sp, PARAM)
    for r, v in ((UC_ARM_REG_R0, SYS), (UC_ARM_REG_R1, PLANE), (UC_ARM_REG_R2, CEN), (UC_ARM_REG_R3, RAD), (UC_ARM_REG_SP, sp), (UC_ARM_REG_LR, STOP)):
        mu.reg_write(r, v)
    mu.emu_start(0x118dc6c, STOP, count=200000)
    if bad: return {'bad': bad}
    g = lambda o: rf(mu, PLANE + o)
    return dict(ret=mu.reg_read(UC_ARM_REG_R0) & 0xff, sp=mu.reg_read(UC_ARM_REG_SP) == sp,
                start=(g(0x300), g(0x304), g(0x308)), end=(g(0x30c), g(0x310), g(0x314)), dir=(g(0x318), g(0x31c), g(0x320)),
                tot=g(0x324), js=(g(0x328), g(0x32c), g(0x330)), je=(g(0x334), g(0x338), g(0x33c)),
                planeH=rf(mu, PLANE + 0x2bc), planeFly=[rf(mu, PLANE + 0x2b8 + o) for o in (0xc, 0x10, 0x14, 0x18)])
ok = True
def check(n, c):
    global ok; print(('OK  ' if c else 'FAIL') + ' ' + n); ok &= bool(c)
if __name__ == '__main__':
    r = run(408000, 150000, (190448, 19298, 0), 150000, 1, cave=False)
    print('原版（无 cave）:', r)
    for side, nm in ((816000, '8x8'), (408000, '4x4')):
        for seed in range(30):
            cen = (random.Random(seed).uniform(-side/3, side/3), random.Random(seed+99).uniform(-side/3, side/3), 1200)
            r = run(side, 150000, cen, 150000, seed)
            if 'bad' in r: check('%s seed %d 调到未桩的外部函数 %s' % (nm, seed, r['bad']), False); continue
            sx, sy, sz = r['start']; ex, ey, ez = r['end']
            L = math.hypot(ex - sx, ey - sy)
            mx, my = (sx + ex) / 2, (sy + ey) / 2
            # 中线离地图正中的距离（点到直线）
            dx, dy = (ex - sx) / L, (ey - sy) / L
            dist = abs(-dy * (0 - sx) + dx * (0 - sy))
            jl = math.hypot(r['je'][0] - r['js'][0], r['je'][1] - r['js'][1])
            inside = all(abs(v) <= side / 2 * 0.9 + 1 for v in (r['js'][0], r['js'][1], r['je'][0], r['je'][1]))
            c = (r['ret'] == 1 and r['sp'] and sz == 150000 and ez == 150000 and r['js'][2] == 150000 and r['je'][2] == 150000
                 and L > side * 0.9 and L <= side * 1.1 + 10 and dist <= side * 0.1 + 10 and abs(r['tot'] - L) < 5 and jl > side * 0.5 and inside
                 and r['planeH'] == 150000)
            if seed < 3 or not c:
                print('  %s seed %d: 长 %.0f 米, 离中心 %.0f 米, 高 %.0f/%.0f, 跳伞段 %.0f 米 (%.0f,%.0f)~(%.0f,%.0f), tot %.0f, ret %d' % (nm, seed, L / 100, dist / 100, sz / 100, ez / 100, jl / 100, r['js'][0], r['js'][1], r['je'][0], r['je'][1], r['tot'], r['ret']))
            check('%s seed %d' % (nm, seed), c)
    # R==0 → 原样
    mu_r = run(408000, 0, (1000, 2000, 0), 150000, 5, cave=False)
    import copy
    def run0():
        return run.__wrapped__ if hasattr(run, '__wrapped__') else None
    print('ALL OK' if ok else 'SOME FAIL')
