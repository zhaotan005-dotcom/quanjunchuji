# v2.20 联机房主：地图物资/门（ServerOnly 流式层）
# 背景：ANZStreamingLevelsManagerActor::BeginPlay（0x115beb4）按 GetNetMode 选层：
#   ==1（专用服务器）→ 服务器路径，CurrentLayers(+0x2f4) 里放 ServerOnly 层（+0x2d4，层名 WorldMeshes/VehicleSpawn/Door/Glass/Fence/Navigation/Pickups）；
#   否则 → 客户端路径（0x115c3fc），只拷贝 +0x2dc 的显示层，ServerOnly 层从不进 CurrentLayers，
#   FillLayersWithCacheData（只遍历 CurrentLayers）就不会给它建流式关卡 → 物资点/门/车点都不存在。
#   房主是 listen server（NetMode 2），走的是客户端路径。
# 补丁 SL：客户端路径末尾 0x115c498 `b 0x115c744` → b 本 cave（0x171e720，废掉的 GM 面板函数 0x171e4b0 里 NQ cave 后面的空地）。
#   cave：world=[sp+0xc]，GetNetMode(world)==2 时，把 ServerOnly 层（共享指针 +0x2d4/+0x2d8，引用计数 +1）追加到 CurrentLayers
#   （已在里面就不加；满了用游戏自己的 TArray 扩容 0xbc5580(arr, 旧个数)，和 0x115c6f8 同样的调法），然后 b 0x115c744。
#   NetMode 0（单机）/3（客户端）原样走，什么都不加。
import struct, sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from asm import asm
ORG = 0x171e720
HOOK = 0x115c498
BACK = 0x115c744
NETMODE = 0x2e65f00
GROW = 0xbc5580
SRC = '''
    ldr r0, [sp, #0xc]
    cmp r0, #0
    beq out
    bl NETMODE
    cmp r0, #2
    bne out
    ldr r6, [r4, #0x2d4]
    cmp r6, #0
    beq out
    ldr r5, [r4, #0x2f8]
    ldr r1, [r4, #0x2f4]
    mov r2, #0
chk:
    cmp r2, r5
    bge add
    ldr r3, [r1, r2, lsl #3]
    cmp r3, r6
    beq out
    add r2, r2, #1
    b chk
add:
    add r0, r5, #1
    str r0, [r4, #0x2f8]
    ldr r0, [r4, #0x2fc]
    cmp r5, r0
    blt nogrow
    add r0, r4, #0x2f4
    mov r1, r5
    bl GROW
nogrow:
    ldr r1, [r4, #0x2f4]
    add r1, r1, r5, lsl #3
    str r6, [r1]
    ldr r0, [r4, #0x2d8]
    str r0, [r1, #4]
    cmp r0, #0
    beq out
    add r0, r0, #4
    .word 0xf57ff05b
inc:
    .word 0xe1901f9f
    add r1, r1, #1
    .word 0xe1802f91
    cmp r2, #0
    bne inc
    .word 0xf57ff05b
out:
    b BACK
'''
SYMS = dict(NETMODE=NETMODE, GROW=GROW, BACK=BACK)
def build(org=ORG):
    return asm(SRC, org, SYMS)
def hook():
    off = ((ORG - HOOK - 8) >> 2) & 0xffffff
    return struct.pack('<I', 0xea000000 | off)
# 补丁 PK：UNZBRPickupConfigComponent::BeginPlay（0x10c0b5c）只在 NetMode∈{0 单机,1 专用服} 时把物资点交给 PickupManager（AddSpawnComp 0xff1e88）。
#   0x10c0ba4 orreq r0,r0,#1 / cmpeq r0,#1 / bne exit → bne exit（世界类型不对）/ cmp r0,#3 / beq exit：只有客户端(3)不交，房主(2)也交。
PK = 0x10c0ba4
PK_O = '01008003' '01005003' '1200001a'
PK_N = '1400001a' '030050e3' '1200000a'
if __name__ == '__main__':
    import capstone
    b, lab = build()
    md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
    for x in md.disasm(b, ORG): print('%x %s %s %s' % (x.address, x.bytes.hex(), x.mnemonic, x.op_str))
    print(len(b), hex(ORG + len(b)), b.hex())
    print('hook', hook().hex())
    for x in md.disasm(bytes.fromhex(PK_N), PK): print('%x %s %s' % (x.address, x.mnemonic, x.op_str))
