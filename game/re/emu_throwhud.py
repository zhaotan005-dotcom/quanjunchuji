# unicorn 跑 throwhud_cave.py 的三个 cave（库基址按 0 映射）
import sys, struct
sys.path.insert(0, '/workspace/work/qj2/game/re')
import throwhud_cave as T
from unicorn import *
from unicorn.arm_const import *

D = T.D
A, B, IA, CA, LB = T.all_caves()
STOP = 0x30000000

def mk():
    mu = Uc(UC_ARCH_ARM, UC_MODE_ARM)
    mu.mem_map(0, 0x4a2f000); mu.mem_write(0, D[:0x4a2d528])
    mu.mem_map(0x4a2f000, 0x1000000)
    mu.mem_write(0x4a2e528, D[0x4a2d528:0x4a2d528 + 0x6fcbf8])
    mu.mem_write(IA, A); mu.mem_write(LB, B)
    for base in (0x10000000, 0x20000000, STOP): mu.mem_map(base, 0x100000)
    mu.mem_write(STOP, b"\xfe\xff\xff\xea")
    return mu

def w32(mu, a, v): mu.mem_write(a, struct.pack('<I', v & 0xffffffff))
def r32(mu, a): return struct.unpack('<I', mu.mem_read(a, 4))[0]

def run(mu, pc, stops, regs):
    hit = {}
    def h(uc, addr, size, ud):
        if addr in stops:
            hit['at'] = addr; hit['regs'] = {r: uc.reg_read(r) for r in (UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3, UC_ARM_REG_R4, UC_ARM_REG_R5, UC_ARM_REG_R6, UC_ARM_REG_R7, UC_ARM_REG_SP, UC_ARM_REG_LR, UC_ARM_REG_R11)}
            uc.emu_stop()
    mu.hook_add(UC_HOOK_CODE, h)
    for r, v in regs.items(): mu.reg_write(r, v)
    mu.emu_start(pc, STOP + 0x800, count=2000)
    return hit

def isthrow_hook(mu):
    def h(uc, addr, size, ud):
        if addr == 0x8ad168:
            n = uc.reg_read(UC_ARM_REG_R0) | (uc.reg_read(UC_ARM_REG_R1) << 32)
            d = uc.reg_read(UC_ARM_REG_R2) | (uc.reg_read(UC_ARM_REG_R3) << 32)
            q, r = divmod(n, d)
            for reg, v in ((UC_ARM_REG_R0, q & 0xffffffff), (UC_ARM_REG_R1, q >> 32), (UC_ARM_REG_R2, r & 0xffffffff), (UC_ARM_REG_R3, r >> 32)): uc.reg_write(reg, v)
            uc.reg_write(UC_ARM_REG_PC, uc.reg_read(UC_ARM_REG_LR))
    mu.hook_add(UC_HOOK_CODE, h, begin=0x8ad168, end=0x8ad16c)

ok = True
def check(name, cond):
    global ok
    print(('OK  ' if cond else 'FAIL') + ' ' + name)
    ok &= bool(cond)

# ---- 图标 cave
for idv, want in ((20401000003, 'printf'), (20401000001, 'printf'), (20402000001, 'printf'), (10101001001, 'tostr'), (20501000001, 'tostr'), (None, 'tostr'), (0, 'tostr')):
    mu = mk(); W = 0x10000000; WEP = 0x10010000
    w32(mu, W + 0x1ac, 0 if idv is None else WEP)
    if idv is not None: w32(mu, WEP + 0x2cc, idv // 10000); w32(mu, WEP + 0x2d0, idv % 10000)
    sp = 0x200f0000
    hit = run(mu, IA, {T.PRINTF, T.TOSTR, STOP}, {UC_ARM_REG_R0: 0x200f1000 - 0x100, UC_ARM_REG_R1: 0x200f1000 - 0x80, UC_ARM_REG_R5: W,
              UC_ARM_REG_R4: 0x44, UC_ARM_REG_R6: 0x66, UC_ARM_REG_R7: 0x77, UC_ARM_REG_SP: sp, UC_ARM_REG_LR: STOP})
    g = hit['regs']
    if want == 'printf':
        val = g[UC_ARM_REG_R2] | (g[UC_ARM_REG_R3] << 32)
        check('icon %s → printf("%%llu", %d) fmt=%#x out=%#x' % (idv, val, g[UC_ARM_REG_R1], g[UC_ARM_REG_R0]),
              hit['at'] == T.PRINTF and val == idv and g[UC_ARM_REG_R1] == T.FMT_LLU and g[UC_ARM_REG_R0] == 0x200f1000 - 0x100)
        # 模拟 printf 返回：跳回 LR，看是否回到调用者且寄存器恢复
        mu2 = mk(); mu2.mem_write(0x10000000, bytes(mu.mem_read(0x10000000, 0x100000))); mu2.mem_write(0x20000000, bytes(mu.mem_read(0x20000000, 0x100000)))
        regs = dict(g); regs[UC_ARM_REG_R4] = 0xdead; regs[UC_ARM_REG_R6] = 0xdead; regs[UC_ARM_REG_R7] = 0xdead
        lr = g[UC_ARM_REG_LR]
        h2 = run(mu2, lr, {STOP}, {**regs})
        r = h2['regs']
        check('   printf 返回后回到调用者，r4/r6/r7/sp 复原', h2['at'] == STOP and r[UC_ARM_REG_R4] == 0x44 and r[UC_ARM_REG_R6] == 0x66 and r[UC_ARM_REG_R7] == 0x77 and r[UC_ARM_REG_SP] == sp)
    else:
        check('icon %s → 原样 ToString(r0,r1 不变，lr=调用者，寄存器复原)' % idv,
              hit['at'] == T.TOSTR and g[UC_ARM_REG_R0] == 0x200f1000 - 0x100 and g[UC_ARM_REG_R1] == 0x200f1000 - 0x80 and g[UC_ARM_REG_LR] == STOP
              and g[UC_ARM_REG_R4] == 0x44 and g[UC_ARM_REG_R6] == 0x66 and g[UC_ARM_REG_R7] == 0x77 and g[UC_ARM_REG_SP] == sp)

# ---- 倒计时 cave
for typ, wm, held_same, want_write in ((1, True, True, True), (2, True, True, True), (3, True, True, True), (1, True, False, False),
                                       (3, True, False, False), (4, True, True, False), (0, True, True, False), (1, False, None, True), (2, False, None, True)):
    mu = mk(); COMP = 0x10000000; PAWN = 0x10010000; WMGR = 0x10020000; WEP = 0x10030000
    mu.mem_write(COMP + 0x22, bytes([typ])); w32(mu, COMP + 0x1c, WEP)
    w32(mu, PAWN + 0x888, WMGR if wm else 0); w32(mu, WMGR + 0x2d8, WEP if held_same else 0x10040000)
    w32(mu, PAWN + 0xdd0, 0x12345678)
    hit = run(mu, CA, {T.CD_RET}, {UC_ARM_REG_R4: COMP, UC_ARM_REG_R5: PAWN, UC_ARM_REG_R6: 0x41000000, UC_ARM_REG_SP: 0x200f0000})
    v = r32(mu, PAWN + 0xdd0)
    check('countdown type=%d wm=%s 手上=%s → %s' % (typ, wm, held_same, '写 8.0' if want_write else '不写'),
          hit.get('at') == T.CD_RET and (v == 0x41000000) == want_write and hit['regs'][UC_ARM_REG_R4] == COMP and hit['regs'][UC_ARM_REG_R5] == PAWN and hit['regs'][UC_ARM_REG_SP] == 0x200f0000)

# ---- GetLastThrowWeapon cave
GOTV = struct.unpack('<I', D[0x4a2d528 + (T.GOT_UOBJ - 0x4a2e528):][:4])[0]
for case in ('held_throw', 'held_gun', 'pending', 'no_pawn', 'wrong_back', 'no_wm', 'no_held'):
    mu = mk(); isthrow_hook(mu)
    MGR = 0x10000000; PAWN = 0x10010000; WMGR = 0x10020000; WEP = 0x10030000; OBJ = 0x10040000
    mu.mem_map(GOTV & ~0xfff, 0x2000) if not (0x4a2f000 <= GOTV < 0x4a2f000 + 0x1000000) else None
    w32(mu, GOTV + 0x10, OBJ)
    w32(mu, MGR + 0x390, 0 if case == 'no_pawn' else PAWN)
    w32(mu, PAWN + 0x838, 0x10050000 if case == 'wrong_back' else MGR)
    w32(mu, PAWN + 0x888, 0 if case == 'no_wm' else WMGR)
    w32(mu, WMGR + 0x2d8, 0 if case == 'no_held' else WEP)
    w32(mu, WEP + 8, 5)
    mu.mem_write(OBJ + 16 * 5 + 7, bytes([0x20 if case == 'pending' else 0]))
    idv = 10101001001 if case == 'held_gun' else 20401000003
    w32(mu, WEP + 0x2cc, idv // 10000); w32(mu, WEP + 0x2d0, idv % 10000)
    w32(mu, MGR + 0x558, 0x777)
    sp = 0x200f0000
    hit = run(mu, LB, {STOP, T.LAST_BODY}, {UC_ARM_REG_R0: MGR, UC_ARM_REG_R4: 0x44, UC_ARM_REG_R5: 0x55, UC_ARM_REG_R11: 0xbb, UC_ARM_REG_SP: sp, UC_ARM_REG_LR: STOP})
    g = hit['regs']
    if case == 'held_throw':
        check('last %s → 返回手上投掷物并缓存 +0x558，寄存器复原' % case, hit['at'] == STOP and g[UC_ARM_REG_R0] == WEP and r32(mu, MGR + 0x558) == WEP
              and g[UC_ARM_REG_R4] == 0x44 and g[UC_ARM_REG_R5] == 0x55 and g[UC_ARM_REG_R11] == 0xbb and g[UC_ARM_REG_SP] == sp)
    else:
        st = [r32(mu, g[UC_ARM_REG_SP] + 4 * k) for k in range(4)]
        check('last %s → 走原函数 0xfe90ec（r0=管理器，栈 = 原 push {r4,r5,fp,lr}，+0x558 没动）' % case,
              hit['at'] == T.LAST_BODY and g[UC_ARM_REG_R0] == MGR and g[UC_ARM_REG_SP] == sp - 16 and st == [0x44, 0x55, 0xbb, STOP] and r32(mu, MGR + 0x558) == 0x777)

# 原函数接着跑：从 0xfe90ec 继续，模拟管理器 vt+0x388 返回 +0x55c
mu = mk(); isthrow_hook(mu)
MGR = 0x10000000; VT = 0x10060000; WEP = 0x10030000; OBJ = 0x10040000
w32(mu, GOTV + 0x10, OBJ); w32(mu, MGR, VT); w32(mu, VT + 0x388, 0xf0e34c); w32(mu, MGR + 0x55c, WEP); w32(mu, WEP + 8, 3)
w32(mu, WEP + 0x2cc, 20401000002 // 10000); w32(mu, WEP + 0x2d0, 20401000002 % 10000)
hit = run(mu, LB, {STOP}, {UC_ARM_REG_R0: MGR, UC_ARM_REG_R4: 0x44, UC_ARM_REG_R5: 0x55, UC_ARM_REG_R11: 0xbb, UC_ARM_REG_SP: 0x200f0000, UC_ARM_REG_LR: STOP})
g = hit['regs']
check('last 没人物 → 原函数照旧返回 +0x55c 的投掷物并缓存', hit['at'] == STOP and g[UC_ARM_REG_R0] == WEP and r32(mu, MGR + 0x558) == WEP and g[UC_ARM_REG_R4] == 0x44 and g[UC_ARM_REG_SP] == 0x200f0000)

print('ALL OK' if ok else 'SOME FAILED')
sys.exit(0 if ok else 1)
