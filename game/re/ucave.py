# v2.51 'U' cave（SLACK+0x224..0x2ff 空位）：用道具（草丛/盾/雷等功能道具）
# vcave 的字母判断走完（不是 V/B/A/E/M/I/T/G）跳到这里；入口同 vcave：栈已 push {r4-r11,lr}; sub sp,#0x44；r0 = 第一个字符（UTF-32）。
# 'U'（"AddAvaItem U"）：缓冲区地址在 cave 末尾字面量 ubuf（Java 装的时候填）
#   buf+28 道具对象（非 0 时先把 buf+40..+63 六个字拷到 道具+0x338..+0x34c（放置位置 FVector + 朝向 FRotator），再把 道具+0x336（放置有效）置 1）
#   buf+12 对象 obj、buf+16 参数 arg、buf+20 虚表偏移 off → 调 obj->vt[off](obj, arg)，返回写 buf+24；完成标记 buf+0 = 0x5b0e，obj 空 = 0x5b0f
#   v3.05：buf+36 第 3 个参数 r2、buf+64 第 4 个参数 r3、buf+68 第 5 个参数（栈上）；v3.07：buf+72 第 6 个参数（[sp+4]）；Java 每次清零 0..75
#   v2.60：buf+32 非 0 = 直接调这个绝对地址 fn(obj, arg)（不走虚表；Java 填 libUE4 基址+偏移，只用于收回道具/取消伪装）
#   Java 只用两种：背包组件 vt+0x4d0 DoSelectedFunctionalProp(道具)（0xfe9570）、vt+0x484 用当前选中的道具（0xfe98b0），调之前核对虚表项
# 其他字母：原样收尾（和 vcave 的 done 一样）
import struct
from asm import asm
SRC = r'''
    cmp r0, #85
    bne du
    ldr r8, [pc, #L:ubuf]
    ldr r0, [r8, #28]
    cmp r0, #0
    beq nopos
    ldr r1, [r8, #40]
    str r1, [r0, #0x338]
    ldr r1, [r8, #44]
    str r1, [r0, #0x33c]
    ldr r1, [r8, #48]
    str r1, [r0, #0x340]
    ldr r1, [r8, #52]
    str r1, [r0, #0x344]
    ldr r1, [r8, #56]
    str r1, [r0, #0x348]
    ldr r1, [r8, #60]
    str r1, [r0, #0x34c]
    mov r1, #1
    strb r1, [r0, #0x336]
nopos:
    ldr r0, [r8, #12]
    cmp r0, #0
    beq fail
    ldr r1, [r8, #68]
    str r1, [sp]
    ldr r1, [r8, #72]
    str r1, [sp, #4]
    ldr r2, [r8, #36]
    ldr r3, [r8, #64]
    ldr r1, [r8, #16]
    ldr ip, [r8, #32]
    cmp ip, #0
    bne go
    ldr lr, [r0]
    ldr ip, [r8, #20]
    ldr ip, [lr, ip]
go:
    .word 0xe12fff3c
    str r0, [r8, #24]
    movw r0, #0x5b0e
    str r0, [r8]
    b du
fail:
    movw r0, #0x5b0f
    str r0, [r8]
du:
    add sp, sp, #0x44
    pop {r4-r11, pc}
ubuf:
    .word 0x0
'''
ORG = 0x4a2d540 + 0x240   # v2.54：0x224 和服务端门重叠，挪到 0x240
def build(org=ORG):
    lines = SRC.split('\n'); refs = []; out = []; k = 0
    for l in lines:
        t = l.split(';')[0].strip()
        if '#L:' in t:
            refs.append((k, t.split('#L:')[1].rstrip(']').strip()))
            l = l.split('#L:')[0] + '#0]'
        if t and not t.endswith(':'): k += 1
        out.append(l)
    b, lab = asm('\n'.join(out), org)
    for i, name in refs:
        off = lab[name] - (org + 4 * i + 8)
        assert 0 <= off < 4096
        w = struct.unpack_from('<I', b, 4 * i)[0] | off
        b = b[:4 * i] + struct.pack('<I', w) + b[4 * i + 4:]
    assert lab['ubuf'] == org + len(b) - 4
    assert org + len(b) <= 0x4a2d540 + 0x300, 'ucave too big'
    return b, lab
def code(org=ORG):
    """不含末尾 ubuf 字面量（Java 拼上缓冲区地址）"""
    b, lab = build(org)
    return b[:-4]
if __name__ == '__main__':
    import capstone
    b, lab = build()
    md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
    for x in md.disasm(b, ORG): print('%x %s %s %s' % (x.address, x.bytes.hex(), x.mnemonic, x.op_str))
    print(len(b), hex(ORG + len(b)), {k: hex(v) for k, v in lab.items()})
    print(code().hex())
