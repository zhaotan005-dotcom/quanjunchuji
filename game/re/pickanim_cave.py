# v2.70 拾取两次动作：BeginPlayingPickupAnim 0xd21bfc（C++ 0xf041e8 和 Lua thunk 0xe3ff20 都调）
# 入口 0xd21bfc 改成 b cave；cave：同一个人物 1.5 秒内（世界 TimeSeconds = GetWorld(0x273d080)+0x740）第二次调用就直接返回并计数，
# 否则记下人物和时间，照抄 push/add fp 回到 0xd21c04。数据在 Java 的直接缓冲区：+0 人物 +4 时间(float) +8 1.5f +12 跳过次数 +16 调用次数
import struct, capstone
from asm import asm
ORG = 0x171e200
HOOK = 0xd21bfc
src = '''
 push {r0-r4, lr}
 mov r4, r0
 ldr ip, [pc, #LDATA]
 ldr r1, [ip, #16]
 add r1, r1, #1
 str r1, [ip, #16]
 bl 0x273d080
 cmp r0, #0
 beq go
 add r0, r0, #0x740
 .word 0xED900A00
 ldr ip, [pc, #LDATA]
 ldr r1, [ip]
 cmp r1, r4
 bne store
 .word 0xEDDC0A01
 .word 0xEE301A60
 .word 0xEEB51AC0
 .word 0xEEF1FA10
 blt store
 .word 0xEDDC1A02
 .word 0xEEB41AE1
 .word 0xEEF1FA10
 bge store
 ldr r1, [ip, #12]
 add r1, r1, #1
 str r1, [ip, #12]
 pop {r0-r4, pc}
store:
 str r4, [ip]
 .word 0xED8C0A01
go:
 pop {r0-r4, lr}
 push {r4, r5, r6, r7, r8, sb, fp, lr}
 add fp, sp, #0x18
 b 0xd21c04
'''
def build():
    lines = src.strip().split('\n')
    n = sum(1 for l in lines if l.strip() and not l.strip().endswith(':'))
    lit = ORG + 4 * n
    pc = ORG; out = []
    for l in lines:
        s = l.strip()
        if not s or s.endswith(':'): out.append(s); continue
        s = s.replace('#LDATA', '#%d' % (lit - pc - 8))
        out.append(s); pc += 4
    code, lab = asm('\n'.join(out), ORG)
    return code, n
CODE, N = build()
HOOK_N = struct.pack('<I', 0xea000000 | (((ORG - HOOK - 8) >> 2) & 0xffffff)).hex()
if __name__ == '__main__':
    md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
    for i in md.disasm(CODE, ORG): print('%08x %s %s' % (i.address, i.mnemonic, i.op_str))
    for i in md.disasm(bytes.fromhex(HOOK_N), HOOK): print('hook', i.mnemonic, i.op_str)
    print('CODE', CODE.hex(), 'len', hex(len(CODE) + 4), 'HOOK', HOOK_N)
