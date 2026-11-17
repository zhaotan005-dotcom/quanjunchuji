# v2.20 unicorn 测试：房主 ServerOnly 层 cave（SL）+ 物资点网络模式补丁（PK），在真 libUE4 代码上跑
import struct, sys
from unicorn import *
from unicorn.arm_const import *
sys.path.insert(0, __file__.rsplit('/', 1)[0])
import lootcave as L
LIB = '/workspace/work/quanjunchuji/game/x/lib/armeabi-v7a/libUE4.so'
D = open(LIB, 'rb').read()
TEXT = 0x4a2e000
def mk(patch_pk=True, patch_sl=True):
    mu = Uc(UC_ARCH_ARM, UC_MODE_ARM)
    mu.mem_map(0, TEXT); mu.mem_write(0, D[:0x4a2d528])
    code, lab = L.build()
    assert D[L.ORG:L.ORG + len(code)] == bytes.fromhex(CAVE_O), 'cave orig'
    if patch_sl:
        mu.mem_write(L.ORG, code); mu.mem_write(L.HOOK, L.hook())
    if patch_pk: mu.mem_write(L.PK, bytes.fromhex(L.PK_N))
    mu.mem_map(0x10000000, 0x100000)   # heap
    mu.mem_map(0x20000000, 0x10000)    # stack
    return mu
code, _ = L.build()
CAVE_O = D[L.ORG:L.ORG + len(code)].hex()
assert D[L.HOOK:L.HOOK + 4].hex() == 'a90000ea'
assert D[L.PK:L.PK + 12].hex() == L.PK_O
R = lambda mu, a: struct.unpack('<I', mu.mem_read(a, 4))[0]
def W(mu, a, v): mu.mem_write(a, struct.pack('<I', v))

state = {}
def hook_code(mu, addr, size, ud):
    if addr == L.NETMODE:
        state['nm_called'] = R(mu, 0) if False else mu.reg_read(UC_ARM_REG_R0)
        mu.reg_write(UC_ARM_REG_R0, state['mode']); mu.reg_write(UC_ARM_REG_PC, mu.reg_read(UC_ARM_REG_LR))
    elif addr == L.GROW:
        arr = mu.reg_read(UC_ARM_REG_R0); old = mu.reg_read(UC_ARM_REG_R1)
        num = R(mu, arr + 4); assert num == old + 1, (num, old)
        newp = 0x10080000; data = R(mu, arr)
        mu.mem_write(newp, bytes(mu.mem_read(data, 8 * old)))
        W(mu, arr, newp); W(mu, arr + 8, old + 4); state['grow'] = state.get('grow', 0) + 1
        for r in (UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3, UC_ARM_REG_IP): mu.reg_write(r, 0xdead)
        mu.reg_write(UC_ARM_REG_PC, mu.reg_read(UC_ARM_REG_LR))

def run_sl(mode, num, mx, world=0x10050000, layer=0x10060000, ctrl=0x10070000, already=False):
    mu = mk(); state.clear(); state['mode'] = mode
    mu.hook_add(UC_HOOK_CODE, hook_code, begin=L.NETMODE, end=L.NETMODE)
    mu.hook_add(UC_HOOK_CODE, hook_code, begin=L.GROW, end=L.GROW)
    THIS = 0x10000000; ARR = 0x10010000
    W(mu, THIS + 0x2d4, layer); W(mu, THIS + 0x2d8, ctrl)
    W(mu, THIS + 0x2f4, ARR); W(mu, THIS + 0x2f8, num); W(mu, THIS + 0x2fc, mx)
    for i in range(num): W(mu, ARR + 8 * i, 0x10040000 + 0x10 * i); W(mu, ARR + 8 * i + 4, 0)
    if already and num: W(mu, ARR + 8 * (num - 1), layer)
    if ctrl: W(mu, ctrl + 4, 5)
    SP = 0x20008000; W(mu, SP + 0xc, world); W(mu, SP + 0x24, THIS)
    mu.reg_write(UC_ARM_REG_SP, SP); mu.reg_write(UC_ARM_REG_R4, THIS)
    for r, v in ((UC_ARM_REG_R7, 0x77), (UC_ARM_REG_R8, 0x88), (UC_ARM_REG_R9, 0x99), (UC_ARM_REG_R10, 0xaa), (UC_ARM_REG_R11, 0xbb)): mu.reg_write(r, v)
    mu.emu_start(L.HOOK, L.BACK, count=400)
    assert mu.reg_read(UC_ARM_REG_PC) == L.BACK
    assert mu.reg_read(UC_ARM_REG_SP) == SP and mu.reg_read(UC_ARM_REG_R4) == THIS
    assert [mu.reg_read(r) for r in (UC_ARM_REG_R7, UC_ARM_REG_R8, UC_ARM_REG_R9, UC_ARM_REG_R10, UC_ARM_REG_R11)] == [0x77, 0x88, 0x99, 0xaa, 0xbb]
    n2 = R(mu, THIS + 0x2f8); data = R(mu, THIS + 0x2f4)
    items = [(R(mu, data + 8 * i), R(mu, data + 8 * i + 4)) for i in range(n2)]
    rc = R(mu, ctrl + 4) if ctrl else None
    return n2, items, rc, state.get('grow', 0), state.get('nm_called')

# 房主：加进去，引用计数 +1，原来的层不动
n, it, rc, g, w = run_sl(2, 3, 8)
assert n == 4 and it[3] == (0x10060000, 0x10070000) and rc == 6 and g == 0 and w == 0x10050000 and it[0][0] == 0x10040000, (n, it, rc)
# 房主 + 数组满了：走游戏扩容
n, it, rc, g, w = run_sl(2, 4, 4)
assert n == 5 and g == 1 and it[4] == (0x10060000, 0x10070000) and it[1][0] == 0x10040010 and rc == 6
# 房主 + 已经在里面：不重复加
n, it, rc, g, w = run_sl(2, 3, 8, already=True)
assert n == 3 and rc == 5 and g == 0
# 房主 + 没有控制块：照样加，不碰引用计数
n, it, rc, g, w = run_sl(2, 0, 0, ctrl=0)
assert n == 1 and it[0] == (0x10060000, 0) and g == 1
# 单机 0 / 专用服 1 / 客户端 3：什么都不加
for m in (0, 1, 3):
    n, it, rc, g, w = run_sl(m, 3, 8); assert n == 3 and rc == 5 and g == 0, m
# 没有 world / 没有 ServerOnly 层
n, it, rc, g, w = run_sl(2, 3, 8, world=0); assert n == 3 and w is None
n, it, rc, g, w = run_sl(2, 3, 8, layer=0); assert n == 3 and rc == 5
print('SL cave: 7 种情况全过')

# ---- PK：0x10c0b94 起跑真代码，看是交给 PickupManager（到 0x10c0bb0）还是退出（0x10c0bfc） ----
def run_pk(patch, wtype, mode):
    mu = mk(patch_pk=patch, patch_sl=False); state.clear(); state['mode'] = mode
    mu.hook_add(UC_HOOK_CODE, hook_code, begin=L.NETMODE, end=L.NETMODE)
    WORLD = 0x10050000; W(mu, WORLD + 0x6d8, wtype)
    SP = 0x20008000; mu.reg_write(UC_ARM_REG_SP, SP); mu.reg_write(UC_ARM_REG_R0, WORLD); mu.reg_write(UC_ARM_REG_R4, 0x10000000)
    stop = []
    def h(mu, addr, size, ud):
        if addr in (0x10c0bb0, 0x10c0bfc): stop.append(addr); mu.emu_stop()
    mu.hook_add(UC_HOOK_CODE, h, begin=0x10c0bb0, end=0x10c0bfc)
    mu.emu_start(0x10c0b94, 0x10c0c60, count=50)
    return stop[0] == 0x10c0bb0
for wt in range(5):
    for m in range(4):
        old, new = run_pk(False, wt, m), run_pk(True, wt, m)
        want_new = wt in (1, 3) and m != 3
        assert new == want_new, (wt, m, new)
        if m in (0, 1, 3): assert old == new, (wt, m)   # 单机/专用服/客户端行为不变
        if m == 2 and wt in (1, 3): assert old is False and new is True   # 只放开房主
print('PK: 5 种世界类型 x 4 种网络模式全对（只多放开房主 NetMode 2）')
print('CAVE_O', CAVE_O)
print('CAVE_N', code.hex())
