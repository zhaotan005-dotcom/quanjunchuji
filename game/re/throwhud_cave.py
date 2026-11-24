# v4.19 投掷物 HUD 三处防御补丁（core321）：生成 cave 字节 + capstone 反汇编核对 + unicorn 模拟。
# 全部 cave 放在死掉的 GM 面板函数里（同一个库内，b/bl 相对跳转，不需要运行时字面量）：
#   CAVE_A = 0x171eaf4（GM SwitchToAimSet 实现，0x11c 字节）：图标 cave + 倒计时 cave
#   CAVE_B = 0x171e88c（GM CloseGMPanel 实现，0x94 字节）：GetLastThrowWeapon cave
# 钩子（4 字节，原字节核对）：
#   H_ICON 0x17ea2a4  bl FName::ToString(0x1a8e880)          → bl CAVE_ICON
#   H_LAST 0xfe90e8   push {r4,r5,fp,lr}（GetLastThrowWeapon 入口）→ b CAVE_LAST
#   H_CD   0xef9ab8   ldrb r0,[r4,#0x22]（AutoFireDelay 写人物+0xdd0 之前）→ b CAVE_CD
import sys, struct
sys.path.insert(0, '/workspace/work/qj2/game/re')
from asm import asm
import capstone

LIB = '/workspace/work/zf/game/x/lib/armeabi-v7a/libUE4.so'
D = open(LIB, 'rb').read()   # 0..0x4a2d528 文件偏移 = 虚拟地址
CAVE_A, CAVE_A_LEN = 0x171eaf4, 0x11c
CAVE_B, CAVE_B_LEN = 0x171e88c, 0x94
H_ICON, H_LAST, H_CD = 0x17ea2a4, 0xfe90e8, 0xef9ab8
TOSTR, PRINTF, FMT_LLU = 0x1a8e880, 0x1936cfc, 0x48e7e98
ISTHROW, GOT_UOBJ = 0xcc93d0, 0x51250ac
ICON_RET_UNUSED = 0
CD_RET = 0xef9acc
LAST_BODY = 0xfe90ec
MIN, MAX = 20400000000, 20500000000   # 204 投掷物一类

def lo(v): return v & 0xffffffff
def hi(v): return v >> 32

# --- 图标：投掷物（InventoryId 在 204xxxxxxxx）就用自己的 InventoryId 拼 "%llu"，否则照原样 FName::ToString
# 入口：r0 = 输出 FString*，r1 = &FName 副本，r5 = 武器栏小控件（+0x1ac 刚存了这把武器）
ICON = f'''
icon:
    push {{r4, r6, r7, lr}}
    ldr r4, [r5, #0x1ac]
    cmp r4, #0
    beq itostr
    ldr r2, [r4, #0x2cc]
    ldr r3, [r4, #0x2d0]
    movw ip, #10000
    umull r6, r7, r2, ip
    adds r6, r6, r3
    adc r7, r7, #0
    ldr r2, [pc, #imin_lo]
    ldr r3, [pc, #imin_hi]
    subs r2, r6, r2
    sbcs r3, r7, r3
    blo itostr
    ldr r2, [pc, #imax_lo]
    ldr r3, [pc, #imax_hi]
    subs r2, r6, r2
    sbcs r3, r7, r3
    bhs itostr
    mov r2, r6
    mov r3, r7
    ldr r1, [pc, #ifmt]
ifmtpc:
    add r1, pc, r1
    bl {PRINTF:#x}
    pop {{r4, r6, r7, pc}}
itostr:
    pop {{r4, r6, r7, lr}}
    b {TOSTR:#x}
'''
# --- 倒计时：原生只给 ThrowableType 1..3（手雷/震爆/烟雾）写 人物+0xdd0 = 自己的 AutoFireDelay；
# 加一条：这个开火模式的武器（+0x1c）必须是人物手上那把（WeaponManager+0x888 → +0x2d8，和原生每帧抛物线检查同一判断）
CD = f'''
cd:
    ldrb r0, [r4, #0x22]
    sub r0, r0, #1
    and r0, r0, #0xff
    cmp r0, #2
    bhi cdout
    ldr r0, [r5, #0x888]
    cmp r0, #0
    beq cdwr
    ldr r0, [r0, #0x2d8]
    ldr r1, [r4, #0x1c]
    cmp r0, r1
    bne cdout
cdwr:
    str r6, [r5, #0xdd0]
cdout:
    b {CD_RET:#x}
'''
# --- GetLastThrowWeapon：先看人物手上那把（管理器+0x390 = 人物，且 人物+0x838 == 这个管理器 才信），
# 是有效的投掷物就缓存到 +0x558 并返回；否则走原函数（原函数用 管理器 vt+0x388 = +0x55c）
LAST = f'''
last:
    push {{r4, r5, fp, lr}}
    add fp, sp, #8
    mov r4, r0
    ldr r0, [r4, #0x390]
    cmp r0, #0
    beq lnat
    ldr r1, [r0, #0x838]
    cmp r1, r4
    bne lnat
    ldr r0, [r0, #0x888]
    cmp r0, #0
    beq lnat
    ldr r5, [r0, #0x2d8]
    cmp r5, #0
    beq lnat
    ldr r0, [pc, #lgot]
lgotpc:
    ldr r0, [pc, r0]
    ldr r0, [r0, #0x10]
    ldr r1, [r5, #8]
    add r0, r0, r1, lsl #4
    ldrb r0, [r0, #7]
    tst r0, #0x20
    bne lnat
    mov r0, r5
    bl {ISTHROW:#x}
    cmp r0, #1
    bne lnat
    str r5, [r4, #0x558]
    mov r0, r5
    pop {{r4, r5, fp, pc}}
lnat:
    mov r0, r4
    b {LAST_BODY:#x}
'''

def build(src, org, tail_words):
    """两遍：先量长度定字面量位置，再把 [pc, #label] 换成真实偏移"""
    import re
    names = [n for n, _ in tail_words]
    body = src
    # 第一遍：占位 0
    tmp = re.sub(r'#(\w+)\]', lambda m: '#0]' if m.group(1) in names else m.group(0), body)
    code, lab = asm(tmp, org)
    lit_at = org + len(code)
    addr = {n: lit_at + 4 * i for i, (n, _) in enumerate(tail_words)}
    # 每条 ldr [pc,#name]：偏移 = 字面量地址 - (指令地址 + 8)
    lines = [l for l in body.split('\n')]
    out = []; pc = org
    for l in lines:
        s = l.split(';')[0].strip()
        if not s: out.append(l); continue
        if s.endswith(':'): out.append(l); continue
        m = re.search(r'\[pc, #(\w+)\]', s)
        if m and m.group(1) in addr:
            l = l.replace('#' + m.group(1) + ']', '#%d]' % (addr[m.group(1)] - (pc + 8)))
        out.append(l); pc += 4
    code, lab = asm('\n'.join(out), org)
    assert org + len(code) == lit_at
    vals = []
    for n, v in tail_words:
        vals.append(v(lab) if callable(v) else v)
    code += b''.join(struct.pack('<I', x & 0xffffffff) for x in vals)
    return code, lab

def all_caves():
    icon, li = build(ICON, CAVE_A, [('imin_lo', lo(MIN)), ('imin_hi', hi(MIN)), ('imax_lo', lo(MAX)), ('imax_hi', hi(MAX)),
                                    ('ifmt', lambda lab: FMT_LLU - (lab['ifmtpc'] + 8))])
    cd_org = CAVE_A + len(icon)
    cd, lc = asm(CD, cd_org)
    a = icon + cd
    assert len(a) <= CAVE_A_LEN, hex(len(a))
    last, ll = build(LAST, CAVE_B, [('lgot', lambda lab: GOT_UOBJ - (lab['lgotpc'] + 8))])
    assert len(last) <= CAVE_B_LEN, hex(len(last))
    return a, last, CAVE_A, cd_org, CAVE_B

def bl(at, to, link):
    return struct.pack('<I', (0xeb if link else 0xea) << 24 | (((to - at - 8) >> 2) & 0xffffff))

if __name__ == '__main__':
    a, b, ia, ca, lb = all_caves()
    md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
    for name, code, org in (('CAVE_A', a, CAVE_A), ('CAVE_B', b, CAVE_B)):
        print('====', name, hex(org), hex(len(code)))
        for i in md.disasm(code, org): print('%08x: %s %s' % (i.address, i.mnemonic, i.op_str))
    hooks = [(H_ICON, D[H_ICON:H_ICON + 4].hex(), bl(H_ICON, ia, True).hex()),
             (H_LAST, D[H_LAST:H_LAST + 4].hex(), bl(H_LAST, lb, False).hex()),
             (H_CD, D[H_CD:H_CD + 4].hex(), bl(H_CD, ca, False).hex())]
    for h in hooks: print('HOOK %#x orig %s new %s' % h)
    print('CAVE_A_ORIG', D[CAVE_A:CAVE_A + len(a)].hex())
    print('CAVE_A_NEW ', a.hex())
    print('CAVE_B_ORIG', D[CAVE_B:CAVE_B + len(b)].hex())
    print('CAVE_B_NEW ', b.hex())
