# v4.21 航线/飞机高度从源头改：CalcAirLineByRestrictedAreaInfo(0x118dc6c) 入口 → cave（GM SwitchToAimSet 死函数 0x171eaf4，0x11c 字节）。
# 原生算法（RE，见 history v4.21）：圈心 C、半径 R（第一个安全区）→ 在 min(R, MaxDistToCenter) 圆内随机点 P →
#   航线方向 ⟂ (P-C)，取过 P 的弦（半长 sqrt(R²-|P-C|²)）→ 用 RegionFly 框裁 → 两头各加 MaxAirlineRadius；Z = Param.Height。
#   跳伞段 = 同一条弦 × JumpRadiusScale，再用 RegionJump 框裁。
# 这个包的数据：Height 20000（200 米）、第一圈 1500 米 → 航线 ~2 公里、200 米高（跳伞组件按 1500 米设计 → 来不及开伞）。
# cave 只改「输入」：圈心 → 地图正中 (0,0)、半径 → 半边长×1.1、MaxDistToCenter → 边长×0.1、MaxAirlineRadius → 0、
#   RegionFly → ±半边长×1.1、RegionJump → ±半边长×0.9、Height → 数据区；同样写进飞机自己的 Param 副本（飞机+0x2b8，0x118cb9c 先 memcpy 过）。
# 数据区（cave 末尾 6 字）：R、H、D、F、J、JS（float 位）；R==0 → 原样走游戏（Java 每次进图按地图写）。
import sys, struct
sys.path.insert(0, '/workspace/work/qj2/game/re')
from asm import asm
LIB = '/workspace/work/zf/game/x/lib/armeabi-v7a/libUE4.so'
D = open(LIB, 'rb').read()
CAVE, CAVE_LEN = 0x171eaf4, 0x11c
HOOK, HOOK_RET = 0x118dc6c, 0x118dc70
HOOK_O = bytes.fromhex('f04f2de9')   # push {r4-fp,lr}

def src(data_off):
    # data_off: 数据区相对 cave 起点；add r4, pc, #(data_off - (pc_of_add+8 - CAVE))
    return '''
    push {r4,r5,r6,lr}
    add r4, pc, #%d
    ldr r5, [r4]
    cmp r5, #0
    beq out
    mov ip, #0
    str ip, [r2]
    str ip, [r2, #4]
    str r5, [r3]
    ldr r6, [sp, #16]
    bl fill
    add r6, r1, #0x2b8
    bl fill
out:
    pop {r4,r5,r6,lr}
    push {r4,r5,r6,r7,r8,r9,r10,r11,lr}
    b 0x%x
fill:
    ldr r5, [r4, #4]
    str r5, [r6, #4]
    ldr r5, [r4, #8]
    str r5, [r6, #0x40]
    mov r5, #0
    str r5, [r6, #0x38]
    ldr r5, [r4, #12]
    str r5, [r6, #0x14]
    str r5, [r6, #0x18]
    eor r5, r5, #0x80000000
    str r5, [r6, #0xc]
    str r5, [r6, #0x10]
    ldr r5, [r4, #16]
    str r5, [r6, #0x28]
    str r5, [r6, #0x2c]
    eor r5, r5, #0x80000000
    str r5, [r6, #0x20]
    str r5, [r6, #0x24]
    mov r5, #1
    strb r5, [r6, #0x1c]
    strb r5, [r6, #0x30]
    ldr r5, [r4, #20]
    str r5, [r6, #0x3c]
    mov pc, lr
''' % (data_off - 12, HOOK_RET)

def build():
    code, lab = asm(src(0x100), CAVE)
    data_off = (len(code) + 3) & ~3
    code, lab = asm(src(data_off), CAVE)
    assert len(code) == data_off
    return code, data_off

def data(side, height, js=0.8):
    h = side / 2
    return struct.pack('<6f', h * 1.1, height, side * 0.1, h * 1.1, h * 0.9, js)

def hook_new():
    off = ((CAVE - HOOK - 8) >> 2) & 0xffffff
    return struct.pack('<I', 0xea000000 | off)

if __name__ == '__main__':
    import capstone
    code, do = build()
    print('code len', len(code), 'data_off', hex(do), 'room', CAVE_LEN)
    assert do + 24 <= CAVE_LEN
    md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
    for i in md.disasm(code, CAVE): print(' %x %s %s' % (i.address, i.mnemonic, i.op_str))
    print('CODE', code.hex()); print('ORIG', D[CAVE:CAVE + do + 24].hex())
    print('HOOK_N', hook_new().hex(), 'HOOK_O', D[HOOK:HOOK + 4].hex())
