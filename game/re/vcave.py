# v1.86 加 'E<控制器地址>x<buf>'：对已有的 PVP 人机再给装备/切战斗树（标志同 A）
# v1.85 加 'A<难度>x<buf>'：按难度加人机（游戏自己的 AddOneBot，带装备/切战斗行为树）
# v1.80 刷车 cave: AddAvaItem "V<resId>x<buf>"（buf 十进制地址）。挂在 GiveItem cave 的 'P' 判断后面。
# 入口时栈已经 push {r4-r11,lr}; sub sp,#0x44；r4=PlayerController，r5=字符(UTF-32)，r6=长度，r0=第一个字符
import struct, capstone
from asm import asm
SRC = r'''
    mov r11, #0
    cmp r0, #86
    beq go
    mov r11, #1
    cmp r0, #66
    beq go
    mov r11, #2
    cmp r0, #65
    beq go
    mov r11, #3
    cmp r0, #69
    bne done
go:
    mov r9, #1
    mov r7, #0
p1:
    cmp r9, r6
    bge e1
    ldr r0, [r5, r9, lsl #2]
    sub r0, r0, #48
    cmp r0, #9
    bhi e1
    add r7, r7, r7, lsl #2
    add r7, r0, r7, lsl #1
    add r9, r9, #1
    b p1
e1:
    add r9, r9, #1
    mov r8, #0
p2:
    cmp r9, r6
    bge e2
    ldr r0, [r5, r9, lsl #2]
    sub r0, r0, #48
    cmp r0, #9
    bhi e2
    add r8, r8, r8, lsl #2
    add r8, r0, r8, lsl #1
    add r9, r9, #1
    b p2
e2:
    cmp r8, #0
    beq done
    movw r0, #0x5b01
    str r0, [r8]
    str r4, [r8, #4]
    ldr r10, [r4, #0x2c0]
    str r10, [r8, #8]
    cmp r10, #0
    beq fin
    ldr r0, [r10, #0x128]
    str r0, [r8, #68]
    cmp r0, #0
    beq fin
    ldr r1, [r0, #0x140]
    str r1, [r8, #12]
    ldr r1, [r0, #0x144]
    str r1, [r8, #16]
    ldr r1, [r0, #0x148]
    str r1, [r8, #20]
    ldr r1, [r0, #0x130]
    str r1, [r8, #72]
    ldr r1, [r0, #0x134]
    str r1, [r8, #76]
    ldr r1, [r0, #0x138]
    str r1, [r8, #80]
    ldr r1, [r0, #0x13c]
    str r1, [r8, #84]
    ldr r0, [r4]
    ldr r1, [r0, #0xa0]
    mov r0, r4
    .word 0xe12fff31
    str r0, [r8, #24]
    cmp r0, #0
    beq fin
    ldr r0, [r0, #0x94]
    str r0, [r8, #28]
    cmp r0, #0
    beq fin
    cmp r11, #0
    bne bot
    ldr r0, [r0, #0x914]
    str r0, [r8, #32]
    cmp r0, #0
    beq fin
    ldr r1, [r0, #0x2c0]
    str r1, [r8, #36]
    cmp r7, #0
    beq fin
    cmp r1, #0
    beq fin
    str r7, [r8, #64]
    mov r0, r4
    mov r1, r7
    add r2, r8, #40
    add r3, r8, #52
    ldr ip, [pc, #L:lit]
    .word 0xe12fff3c
    movw r0, #0x5b03
    str r0, [r8]
    b done
bot:
    cmp r11, #1
    bne abot
    cmp r7, #0
    beq fin
    str r7, [r8, #64]
    ldr r0, [r8, #48]
    str r0, [sp]
    ldr r0, [r8, #52]
    str r0, [sp, #4]
    ldr r0, [r8, #56]
    str r0, [sp, #8]
    ldr r0, [r8, #60]
    str r0, [sp, #12]
    mov r0, r4
    mov r1, r7
    ldr r2, [r8, #40]
    ldr r3, [r8, #44]
    ldr ip, [pc, #L:lit2]
    .word 0xe12fff3c
    movw r0, #0x5b04
    str r0, [r8]
    b done
abot:
    cmp r11, #3
    beq ebot
    mov r6, r0
    ldr r1, [r0]
    ldr r1, [r1, #0x5c4]
    ldr r2, [pc, #L:lit3]
    cmp r1, r2
    movw r0, #0x5b05
    bne afin
    str r7, [r8, #64]
    mov r0, r6
    mov r1, r7
    ldr ip, [pc, #L:lit4]
    .word 0xe12fff3c
    str r0, [r8, #88]
    cmp r0, #0
    movw r0, #0x5b06
    beq afin
    ldr r1, [r8, #88]
    mov r0, r6
    mov r2, r7
    ldr ip, [pc, #L:lit5]
    .word 0xe12fff3c
    mov r5, r0
    str r5, [r8, #92]
    cmp r5, #0
    movw r0, #0x5b06
    beq afin
    ldr r0, [r6]
    ldr r2, [r0, #0x5f8]
    mov r0, r6
    mov r1, r5
    .word 0xe12fff32
    ldr r0, [r6]
    ldr r2, [r0, #0x39c]
    mov r0, r6
    mov r1, r5
    .word 0xe12fff32
    ldr r0, [r5, #0x2b4]
    str r0, [r8, #96]
    cmp r0, #0
    beq notp
    ldr r1, [r0]
    ldr ip, [r1, #0x250]
    mov r1, #0
    str r1, [sp]
    add r1, r8, #40
    add r2, r8, #52
    mov r3, #0
    .word 0xe12fff3c
    ldr r0, [r5]
    ldr r2, [r0, #0x308]
    mov r0, r5
    add r1, r8, #52
    .word 0xe12fff32
notp:
    ldr r0, [r5]
    ldr r0, [r0, #0x344]
    ldr r1, [pc, #L:lit6]
    cmp r0, r1
    movw r0, #0x5b07
    bne afin
pvp:
    ldr r0, [r5, #0x354]
    str r0, [r8, #100]
    ldr r0, [r5, #0x358]
    str r0, [r8, #104]
    ldr r0, [r5, #0x324]
    str r0, [r8, #108]
    ldr r0, [r5, #0x3ec]
    str r0, [r8, #112]
    ldr r9, [r8, #116]
    tst r9, #1
    beq noeq
    mov r0, r5
    ldr ip, [pc, #L:lit7]
    .word 0xe12fff3c
noeq:
    tst r9, #4
    bne dosw
    tst r9, #2
    beq aok
    ldr r0, [r5, #0x324]
    cmp r0, #0
    bne aok
dosw:
    ldr r0, [r5, #0x358]
    cmp r0, #0
    beq aok
    mov r0, r5
    ldr ip, [pc, #L:lit8]
    .word 0xe12fff3c
aok:
    ldr r0, [r5, #0x324]
    str r0, [r8, #120]
    movw r0, #0x5b08
afin:
    str r0, [r8]
    b done
ebot:
    mov r5, r7
    cmp r5, #0
    movw r0, #0x5b06
    beq afin
    ldr r0, [r5]
    ldr r0, [r0, #0x344]
    ldr r1, [pc, #L:lit6]
    cmp r0, r1
    movw r0, #0x5b07
    bne afin
    b pvp
fin:
    movw r0, #0x5b02
    str r0, [r8]
done:
    add sp, sp, #0x44
    pop {r4-r11, pc}
lit:
    .word 0x11111111
lit2:
    .word 0x22222222
lit3:
    .word 0x33333333
lit4:
    .word 0x44444444
lit5:
    .word 0x55555555
lit6:
    .word 0x66666666
lit7:
    .word 0x77777777
lit8:
    .word 0x88888888
'''
LITS = ('lit', 'lit2', 'lit3', 'lit4', 'lit5', 'lit6', 'lit7', 'lit8')
# 字面量顺序（Java 在 cave 后面按这个顺序拼地址，都要 + 库基址）：
# lit 刷车 0x1102ca4，lit2 ServerAddBotAt 0x1115620，lit3 GM AddBotAt 实现 0x1012318（校验 GameMode），
# lit4 难度→人机类 0x1014898，lit5 AddOneBot 0x10112d0，lit6 PVPAIController::OnPossess 0xc1bbd8（校验类），
# lit7 ForceGiveAllEquipToAI 0xc1a534，lit8 SwitchToBattleBehaviorTree 0xc17fe0
def build(org=0):
    lines = SRC.split('\n'); refs = []; out = []; k = 0
    for l in lines:
        t = l.split(';')[0].strip()
        if '#L:' in t:
            refs.append((k, t.split('#L:')[1].rstrip(']').strip()))
            l = l.split('#L:')[0] + '#0]'
        if t and not t.endswith(':'): k += 1
        out.append(l)
    b, lab = asm('\n'.join(out), org)
    # 字面量必须在代码末尾，顺序和 LITS 一致
    assert [lab[n] for n in LITS] == [org + len(b) - 4 * (len(LITS) - i) for i in range(len(LITS))], 'lit order'
    for i, name in refs:
        off = lab[name] - (org + 4 * i + 8)
        assert 0 <= off < 4096
        w = struct.unpack_from('<I', b, 4 * i)[0] | off
        b = b[:4 * i] + struct.pack('<I', w) + b[4 * i + 4:]
    return b, lab
def code(org=0):
    """不含字面量的部分（Java 里的 CAVE 字符串）"""
    b, lab = build(org)
    return b[:len(b) - 4 * len(LITS)]
if __name__ == '__main__':
    b, lab = build(0x4a2d940)
    md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
    for x in md.disasm(b, 0x4a2d940): print('%x %s %s %s' % (x.address, x.bytes.hex(), x.mnemonic, x.op_str))
    print(len(b), {k: hex(v) for k, v in lab.items()})
    print(b.hex())
