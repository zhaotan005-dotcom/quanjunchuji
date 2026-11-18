# v3.07 空投箱内容：0xfd3274（空投箱初始化，DropAirSupplyBox 最后调）里 0xfd3424 `bl 0xfd3ac0`（按空投物品池填 PickupItemIDList 箱子+0x3f8）
#   → bl 本 cave（放在被剥掉的作弊 RPC thunk ServerTestSpawnAirSupplyBox 0x11de78c 和后面一个 thunk 0x11de7e8 上，共 0xb8 字节，没人调）。
# cave：缓冲区（Java 直接内存，地址在 cave 末尾字面量）[0]=要写的个数 n，[4]=用过次数，[8..]=n 个 FName（8 字节：名字序号, Number）。
#   n==0 → 照原样尾调 0xfd3ac0（游戏自己的随机物资）。
#   n>0  → 不调原函数，把 n 个 FName 逐个追加进 箱子+0x3f8 TArray（满了用游戏扩容 0xbc5580(arr, 旧个数)，和 0xfd7790 同样的调法），然后 [0]=0、[4]++。
#   这一步在 0x1112cb0 登记箱子物资之前，所以游戏后面按我们的列表出物资。
import struct, sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from asm import asm
ORG = 0x11de78c
END = 0x11de844
HOOK = 0xfd3424
HOOK_O = bytes.fromhex('a50100eb')   # bl 0xfd3ac0（libUE4 原字节）
ORIG = 0xfd3ac0
GROW = 0xbc5580
SRC = '''
    push {r4, r5, r6, r7, r8, lr}
    mov r4, r0
    mov r8, r1
    ldr r5, [pc, #LIT]
    ldr r6, [r5]
    cmp r6, #0
    beq orig
    cmp r6, #200
    movgt r6, #200
    mov r7, #0
loop:
    cmp r7, r6
    bge done
    ldr r1, [r4, #0x3fc]
    add r0, r1, #1
    str r0, [r4, #0x3fc]
    ldr r0, [r4, #0x400]
    cmp r1, r0
    blt nog
    add r0, r4, #0x3f8
    push {r1, r2}
    bl GROW
    pop {r1, r2}
nog:
    ldr r0, [r4, #0x3f8]
    add r0, r0, r1, lsl #3
    add r2, r5, #8
    add r2, r2, r7, lsl #3
    ldr r3, [r2]
    str r3, [r0]
    ldr r3, [r2, #4]
    str r3, [r0, #4]
    add r7, r7, #1
    b loop
done:
    mov r0, #0
    str r0, [r5]
    ldr r0, [r5, #4]
    add r0, r0, #1
    str r0, [r5, #4]
    pop {r4, r5, r6, r7, r8, pc}
orig:
    mov r0, r4
    mov r1, r8
    pop {r4, r5, r6, r7, r8, lr}
    b ORIG
buf:
    .word 0x0
'''
SYMS = dict(GROW=GROW, ORIG=ORIG)
def build(org=ORG):
    # 先用 #0 汇编拿标签位置，再填 ldr 偏移（第 4 条指令）
    b, lab = asm(SRC.replace('#LIT', '#0'), org, SYMS)
    off = lab['buf'] - (org + 4 * 3 + 8)
    b, lab = asm(SRC.replace('#LIT', '#%d' % off), org, SYMS)
    assert lab['buf'] == org + len(b) - 4 and org + len(b) <= END, hex(len(b))
    return b
def hook():
    off = ((ORG - HOOK - 8) >> 2) & 0xffffff
    return struct.pack('<I', 0xeb000000 | off)
if __name__ == '__main__':
    b = build(); print(len(b), b.hex()); print('hook', hook().hex())
