# v3.61 TiMi(LogoUI) cave: defer UNZStartupFlowLogo::Begin until the flow has ticked 0.5 s (game time),
# and make the Logo flow timeout 4.5 s (LogoAnim PlaybackRange 0..4.0015 s, pak LogoUI.uasset).
# Placed in the dead GM-panel function gap 0x171e690..0x171e6ff (after store cave 0x171e630..0x68f, before Lan NQ 0x171e700).
import struct, sys, capstone
sys.path.insert(0, '.')
ORG = 0x171e690
BEGIN, BASE_TICK = 0x121166c, 0x120f460
def b(at, to, cond=0xe): return (cond << 28) | (0xa << 24) | (((to - (at + 8)) >> 2) & 0xffffff)
W = [
    0xe12fff1e,            # 690 LBEGIN: bx lr   (Begin deferred to tick)
    0xe5902024,            # 694 LTICK: ldr r2,[r0,#0x24]   widget
    0xe3520000,            # 698 cmp r2,#0
    None,                  # 69c bne ORIG
    0xee001a10,            # 6a0 vmov s0,r1     dt
    0xed901a08,            # 6a4 vldr s2,[r0,#0x20]
    0xee311a00,            # 6a8 vadd.f32 s2,s2,s0
    0xed801a08,            # 6ac vstr s2,[r0,#0x20]
    0xeeb60a00,            # 6b0 vmov.f32 s0,#0.5
    0xeeb41ac0,            # 6b4 vcmpe.f32 s2,s0
    0xeef1fa10,            # 6b8 vmrs APSR_nzcv,fpscr
    0xd12fff1e,            # 6bc bxle lr        still waiting
    0xe3a02000,            # 6c0 mov r2,#0
    0xe5802020,            # 6c4 str r2,[r0,#0x20]   elapsed = 0 (Begin only clears it when +0x1c set)
    None,                  # 6c8 b Begin (tail call, r0=this)
    0xe320f000,            # 6cc nop
    None,                  # 6d0 ORIG: b base Tick
    0xe3000000,            # 6d4 LTIME: movw r0,#0
    0xe3440090,            # 6d8 movt r0,#0x4090   4.5f
    0xe12fff1e,            # 6dc bx lr
]
W[3] = b(ORG + 0xc, ORG + 0x40, 0x1)
W[14] = b(ORG + 0x38, BEGIN)
W[16] = b(ORG + 0x40, BASE_TICK)
CODE = b''.join(struct.pack('<I', x) for x in W)
LBEGIN, LTICK, LTIME = ORG, ORG + 4, ORG + 0x44
if __name__ == '__main__':
    md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
    for i in md.disasm(CODE, ORG): print('%08x: %s %s' % (i.address, i.mnemonic, i.op_str))
    print('HEX', CODE.hex(), len(CODE))
