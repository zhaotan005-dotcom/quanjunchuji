# v1.80 刷车 cave: AddAvaItem "V<resId>x<buf>"（buf 十进制地址）。挂在 GiveItem cave 的 'P' 判断后面。
# 入口时栈已经 push {r4-r11,lr}; sub sp,#0x44；r4=PlayerController，r5=字符(UTF-32)，r6=长度，r0=第一个字符
import struct, capstone
from asm import asm
SRC = r'''
    mov r11, #0
    cmp r0, #66
    moveq r11, #1
    cmpne r0, #86
    bne done
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
    ldr ip, [pc, #LIT]
    .word 0xe12fff3c
    movw r0, #0x5b03
    str r0, [r8]
    b done
bot:
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
    ldr ip, [pc, #LIT2]
    .word 0xe12fff3c
    movw r0, #0x5b04
    str r0, [r8]
    b done
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
'''
def build(org=0):
    src = SRC.replace('#LIT2', '#0').replace('#LIT', '#0')
    b, lab = asm(src, org)
    # 填 ldr ip,[pc,#x] 的偏移（第 1 个 → lit 刷车，第 2 个 → lit2 刷人机）
    ii = [k for k in range(0, len(b), 4) if struct.unpack_from('<I', b, k)[0] & 0x0fff0000 == 0x059f0000 and (struct.unpack_from('<I', b, k)[0] >> 12) & 15 == 12]
    assert len(ii) == 2
    for i, name in zip(ii, ('lit', 'lit2')):
        off = lab[name] - (org + i + 8)
        w = struct.unpack_from('<I', b, i)[0] | off
        b = b[:i] + struct.pack('<I', w) + b[i + 4:]
    return b, lab
if __name__ == '__main__':
    b, lab = build(0x4a2d940)
    md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
    for x in md.disasm(b, 0x4a2d940): print('%x %s %s %s' % (x.address, x.bytes.hex(), x.mnemonic, x.op_str))
    print(len(b), {k: hex(v) for k, v in lab.items()})
    print(b.hex())
