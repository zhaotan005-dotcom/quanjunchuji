import struct
from unicorn import *
from unicorn.arm_const import *
from vcave import build
ORG=0x4a2d940
code,lab=build(ORG)
lit=lab['lit']-ORG
SPAWN=0x10000; code=code[:lit]+struct.pack('<I',SPAWN)+code[lit+4:]
lit2=lab['lit2']-ORG; BOT=0x10400; code=code[:lit2]+struct.pack('<I',BOT)+code[lit2+4:]
mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
mu.mem_map(0x4a2d000,0x2000); mu.mem_write(ORG,code)
mu.mem_map(0x10000,0x1000)
# spawn stub: str r1,[0x10800]; str r2..; ldr r0 from r2 -> store floats; bx lr
def W(a,v): mu.mem_write(a,struct.pack('<I',v))
stub=bytes.fromhex('04c09fe5' '00108ce5' '1eff2fe1' )+struct.pack('<I',0x10800)  # ldr ip,=0x10800; str r1,[ip]; bx lr
mu.mem_map(0x20000,0x10000)
mu.mem_write(0x10000,stub)
# bot stub: ip=0x10900; stm ip,{r0,r1,r2,r3}; ldr r0,[sp]; str r0,[ip,#16]; ldr r0,[sp,#8]; str r0,[ip,#20]; bx lr
mu.mem_write(BOT,bytes.fromhex('14c09fe5' '0f008ce8' '00009de5' '10008ce5' '08009de5' '14008ce5' '1eff2fe1')+struct.pack('<I',0x10900))
GW=0x10100; mu.mem_write(GW,bytes.fromhex('00009fe5'+'1eff2fe1')+struct.pack('<I',0x21000))  # ldr r0,[pc,#0]?? 
# ldr r0,[pc,#0] at GW loads GW+8 = world ptr; but next word is bx lr... fix: ldr r0,[pc,#0] -> reads GW+8
PCO=0x20000; VT=0x20800; W(PCO,VT); W(VT+0xa0,GW)
CH=0x22000; W(PCO+0x2c0,CH); RC=0x23000; W(CH+0x128,RC)
for i,o in enumerate([0x130,0x134,0x138,0x13c,0x140,0x144,0x148]): W(RC+o,0x100+i)
WORLD=0x21000; GM=0x24000; VM=0x25000; DT=0x26000
W(WORLD+0x94,GM); W(GM+0x914,VM); W(VM+0x2c0,DT)
BUF=0x28000
s='V1901001x%d'%BUF
STR=0x29000; mu.mem_write(STR,b''.join(struct.pack('<I',ord(c)) for c in s)+b'\0'*4)
mu.mem_map(0x7f000,0x1000)
SP=0x7ff00-0x44-36
# emulate stack as pushed by give cave: 9 regs then sub 0x44; ret address -> END
END=0x4a2e000-4
mu.mem_write(SP+0x44+32,struct.pack('<I',END))
mu.reg_write(UC_ARM_REG_SP,SP); mu.reg_write(UC_ARM_REG_R4,PCO); mu.reg_write(UC_ARM_REG_R5,STR); mu.reg_write(UC_ARM_REG_R6,len(s)+1); mu.reg_write(UC_ARM_REG_R0,ord('V'))
mu.emu_start(ORG,END)
r=lambda a: struct.unpack('<I',mu.mem_read(a,4))[0]
print('magic %x pc %x ch %x loc %x %x %x world %x gm %x vm %x dt %x id %d rc %x q %x' % (r(BUF),r(BUF+4),r(BUF+8),r(BUF+12),r(BUF+16),r(BUF+20),r(BUF+24),r(BUF+28),r(BUF+32),r(BUF+36),r(BUF+64),r(BUF+68),r(BUF+72)))
print('spawn got id', r(0x10800))
assert r(BUF)==0x5b03 and r(0x10800)==1901001 and r(BUF+36)==DT
# info only
mu.mem_write(BUF,b'\0'*128); s='V0x%d'%BUF; mu.mem_write(STR,b''.join(struct.pack('<I',ord(c)) for c in s)+b'\0'*4)
W(0x10800,0)
mu.reg_write(UC_ARM_REG_SP,SP); mu.reg_write(UC_ARM_REG_R4,PCO); mu.reg_write(UC_ARM_REG_R5,STR); mu.reg_write(UC_ARM_REG_R6,len(s)+1); mu.reg_write(UC_ARM_REG_R0,ord('V'))
mu.emu_start(ORG,END)
print('info magic %x dt %x spawncalled %d'%(r(BUF),r(BUF+36),r(0x10800))); assert r(BUF)==0x5b02 and r(0x10800)==0
# 'X' -> nothing
mu.mem_write(BUF,b'\0'*128); mu.reg_write(UC_ARM_REG_SP,SP); mu.reg_write(UC_ARM_REG_R0,ord('X')); mu.emu_start(ORG,END); assert r(BUF)==0
# bot
mu.mem_write(BUF,b'\0'*128); s='B1x%d'%BUF; mu.mem_write(STR,b''.join(struct.pack('<I',ord(c)) for c in s)+b'\0'*4)
W(BUF+40,0x41); W(BUF+44,0x42); W(BUF+48,0x43); W(BUF+52,0x51); W(BUF+56,0x52); W(BUF+60,0x53)
W(DT,0); W(VM+0x2c0,0)
mu.reg_write(UC_ARM_REG_SP,SP); mu.reg_write(UC_ARM_REG_R4,PCO); mu.reg_write(UC_ARM_REG_R5,STR); mu.reg_write(UC_ARM_REG_R6,len(s)+1); mu.reg_write(UC_ARM_REG_R0,ord('B'))
mu.emu_start(ORG,END)
print('bot magic %x args %x %d %x %x z %x yaw %x'%(r(BUF),r(0x10900),r(0x10904),r(0x10908),r(0x1090c),r(0x10910),r(0x10914)))
assert r(BUF)==0x5b04 and r(0x10900)==PCO and r(0x10904)==1 and r(0x10908)==0x41 and r(0x1090c)==0x42 and r(0x10910)==0x43 and r(0x10914)==0x52

# ---- v1.85 'A'：按难度加人机 ----
def stub(at, rec, ret):
    mu.mem_write(at, bytes.fromhex('10c09fe5' '0f008ce8' '00109de5' '10108ce5' '04009fe5' '1eff2fe1') + struct.pack('<II', rec, ret))
mu.mem_map(0x30000, 0x10000)
L = {n: lab[n] - ORG for n in ('lit3','lit4','lit5','lit6','lit7','lit8')}
ADDBOTAT, CLSF, ADDONE, ONPOSS, EQUIP, SWITCH = 0x10a00, 0x10b00, 0x10c00, 0x10d00, 0x10e00, 0x10f00
for n, v in zip(('lit3','lit4','lit5','lit6','lit7','lit8'), (ADDBOTAT, CLSF, ADDONE, ONPOSS, EQUIP, SWITCH)):
    mu.mem_write(ORG + L[n], struct.pack('<I', v))
CLS = 0x30000; BOTP = 0x31000; BOTVT = 0x31800; PAWN = 0x32000; PVT = 0x32800; GMVT = 0x24800
stub(CLSF, 0x33000, CLS); stub(ADDONE, 0x33100, BOTP); stub(EQUIP, 0x33200, 0)
mu.mem_write(SWITCH, bytes.fromhex('04109fe5' '241380e5' '1eff2fe1') + struct.pack('<I', 0x999))
F8 = 0x33400; C39 = 0x33500; TP = 0x33600; ROT = 0x33700
stub(F8, 0x33800, 0); stub(C39, 0x33900, 0); stub(TP, 0x33a00, 0); stub(ROT, 0x33b00, 0)
W(GM, GMVT); W(GMVT + 0x5c4, ADDBOTAT); W(GMVT + 0x5f8, F8); W(GMVT + 0x39c, C39)
W(BOTP, BOTVT); W(BOTP + 0x2b4, PAWN); W(PAWN, PVT); W(PVT + 0x250, TP); W(BOTVT + 0x308, ROT); W(BOTVT + 0x344, ONPOSS)
W(BOTP + 0x354, 0xb0b0); W(BOTP + 0x358, 0xbabe); W(BOTP + 0x324, 0); W(BOTP + 0x3ec, 2)
def runA(diff, flags):
    mu.mem_write(BUF, b'\0' * 128); s = 'A%dx%d' % (diff, BUF)
    mu.mem_write(STR, b''.join(struct.pack('<I', ord(c)) for c in s) + b'\0' * 4)
    W(BUF + 40, 0x41); W(BUF + 44, 0x42); W(BUF + 48, 0x43); W(BUF + 52, 0x51); W(BUF + 56, 0x52); W(BUF + 60, 0x53); W(BUF + 116, flags)
    for a in (0x33000, 0x33100, 0x33200, 0x33800, 0x33900, 0x33a00, 0x33b00): mu.mem_write(a, b'\0' * 32)
    mu.reg_write(UC_ARM_REG_SP, SP); mu.reg_write(UC_ARM_REG_R4, PCO); mu.reg_write(UC_ARM_REG_R5, STR); mu.reg_write(UC_ARM_REG_R6, len(s) + 1); mu.reg_write(UC_ARM_REG_R0, ord('A'))
    for rr, v in ((UC_ARM_REG_R7, 0xdead7), (UC_ARM_REG_R8, 0xdead8), (UC_ARM_REG_R9, 0xdead9), (UC_ARM_REG_R10, 0xdeada), (UC_ARM_REG_R11, 0xdeadb)):
        mu.reg_write(rr, v)
    mu.mem_write(SP + 0x44, struct.pack('<9I', 0x104, 0x105, 0x106, 0x107, 0x108, 0x109, 0x10a, 0x10b, END))
    try: mu.emu_start(ORG, END)
    except Exception as e:
        print('fault pc %x lr %x r0 %x r1 %x r2 %x r5 %x r6 %x' % tuple(mu.reg_read(x) for x in (UC_ARM_REG_PC, UC_ARM_REG_LR, UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R5, UC_ARM_REG_R6))); raise
    assert mu.reg_read(UC_ARM_REG_SP) == SP + 0x44 + 36
    assert [mu.reg_read(x) for x in (UC_ARM_REG_R4, UC_ARM_REG_R5, UC_ARM_REG_R11)] == [0x104, 0x105, 0x10b]
W(BOTP + 0x324, 0); runA(3, 1 | 2)
print('A magic %x cls(gm %x diff %d) addone(gm %x cls %x diff %d) bot %x pawn %x f8 %x 39c %x' % (r(BUF), r(0x33000), r(0x33004), r(0x33100), r(0x33104), r(0x33108), r(BUF + 92), r(BUF + 96), r(0x33804), r(0x33904)))
print('  tp(pawn %x loc %x rot %x test %d sp %d) rot(bot %x %x) equip %x bt %x %x brain %x diff %d after %x' % (r(0x33a00), r(0x33a04), r(0x33a08), r(0x33a0c), r(0x33a10), r(0x33b00), r(0x33b04), r(0x33200), r(BUF + 100), r(BUF + 104), r(BUF + 108), r(BUF + 112), r(BUF + 120)))
assert r(BUF) == 0x5b08 and r(0x33000) == GM and r(0x33004) == 3 and r(0x33100) == GM and r(0x33104) == CLS and r(0x33108) == 3
assert r(0x33804) == BOTP and r(0x33904) == BOTP and r(0x33a00) == PAWN and r(0x33a04) == BUF + 40 and r(0x33a08) == BUF + 52 and r(0x33a0c) == 0 and r(0x33a10) == 0
assert r(0x33b00) == BOTP and r(0x33b04) == BUF + 52 and r(0x33200) == BOTP and r(BUF + 104) == 0xbabe and r(BUF + 120) == 0x999 and r(BUF + 64) == 3
# 已经有行为树（brain 非 0）且没要求强制 → 不切、不给装备
W(BOTP + 0x324, 0x777); runA(1, 2); assert r(BUF) == 0x5b08 and r(BUF + 120) == 0x777 and r(0x33200) == 0
# 强制切
W(BOTP + 0x324, 0x777); runA(2, 4); assert r(BUF + 120) == 0x999
# GameMode 不对 → 5b05，什么都不调
W(GMVT + 0x5c4, 0x1234); runA(2, 7); assert r(BUF) == 0x5b05 and r(0x33100) == 0; W(GMVT + 0x5c4, ADDBOTAT)
# 人机类找不到 → 5b06
stub(CLSF, 0x33000, 0); runA(2, 7); assert r(BUF) == 0x5b06 and r(0x33100) == 0; stub(CLSF, 0x33000, CLS)
# 不是 PVP 人机控制器 → 5b07，不调装备/切树
W(BOTVT + 0x344, 0x4444); W(BOTP + 0x324, 0); runA(2, 7); assert r(BUF) == 0x5b07 and r(0x33200) == 0 and r(0x33a00) == PAWN; W(BOTVT + 0x344, ONPOSS)
# 没有人物 → 不传送，照样检查类
W(BOTP + 0x2b4, 0); runA(2, 1); assert r(BUF) == 0x5b08 and r(0x33a00) == 0 and r(0x33200) == BOTP; W(BOTP + 0x2b4, PAWN)
print('A OK')
def runE(ctrl, flags):
    mu.mem_write(BUF, b'\0' * 128); s = 'E%dx%d' % (ctrl, BUF)
    mu.mem_write(STR, b''.join(struct.pack('<I', ord(c)) for c in s) + b'\0' * 4)
    W(BUF + 116, flags); mu.mem_write(0x33200, b'\0' * 32); mu.mem_write(0x33100, b'\0' * 32)
    mu.reg_write(UC_ARM_REG_SP, SP); mu.reg_write(UC_ARM_REG_R4, PCO); mu.reg_write(UC_ARM_REG_R5, STR); mu.reg_write(UC_ARM_REG_R6, len(s) + 1); mu.reg_write(UC_ARM_REG_R0, ord('E'))
    mu.mem_write(SP + 0x44, struct.pack('<9I', 0x104, 0x105, 0x106, 0x107, 0x108, 0x109, 0x10a, 0x10b, END))
    mu.emu_start(ORG, END)
    assert mu.reg_read(UC_ARM_REG_SP) == SP + 0x44 + 36 and mu.reg_read(UC_ARM_REG_R4) == 0x104
W(BOTP + 0x324, 0x777); runE(BOTP, 1 | 4)
print('E', hex(r(BUF)), hex(r(0x33200)), hex(r(BUF+120)), hex(r(BUF+104)), hex(r(BUF+92)))
assert r(BUF) == 0x5b08 and r(0x33200) == BOTP and r(BUF + 120) == 0x999 and r(0x33100) == 0 and r(BUF + 104) == 0xbabe
W(BOTVT + 0x344, 0x4444); runE(BOTP, 1); assert r(BUF) == 0x5b07 and r(0x33200) == 0; W(BOTVT + 0x344, ONPOSS)
runE(0, 1); assert r(BUF) == 0x5b06
# 大地址（十进制 10 位）
mu.mem_map(0xb0000000, 0x1000); W(0xb0000000, BOTVT); W(0xb0000000 + 0x358, 0); runE(0xb0000000, 1); assert r(BUF) == 0x5b08 and r(0x33200) == 0xb0000000
print('E OK')
# ---- v1.88 'M'：FMemory::Malloc(size, 16) ----
MAL = 0x10f80
stub(MAL, 0x33c00, 0xdead0)
mu.mem_write(ORG + lab['lit9'] - ORG, struct.pack('<I', MAL))
def runM(size):
    mu.mem_write(BUF, b'\0' * 128); s = 'M%dx%d' % (size, BUF)
    mu.mem_write(STR, b''.join(struct.pack('<I', ord(c)) for c in s) + b'\0' * 4)
    mu.mem_write(0x33c00, b'\0' * 32)
    mu.reg_write(UC_ARM_REG_SP, SP); mu.reg_write(UC_ARM_REG_R4, PCO); mu.reg_write(UC_ARM_REG_R5, STR); mu.reg_write(UC_ARM_REG_R6, len(s) + 1); mu.reg_write(UC_ARM_REG_R0, ord('M'))
    mu.mem_write(SP + 0x44, struct.pack('<9I', 0x104, 0x105, 0x106, 0x107, 0x108, 0x109, 0x10a, 0x10b, END))
    mu.emu_start(ORG, END)
    assert mu.reg_read(UC_ARM_REG_SP) == SP + 0x44 + 36 and mu.reg_read(UC_ARM_REG_R4) == 0x104
runM(756); print('M', hex(r(BUF)), hex(r(BUF + 4)), r(0x33c00), r(0x33c04))
assert r(BUF) == 0x5b09 and r(BUF + 4) == 0xdead0 and r(0x33c00) == 756 and r(0x33c04) == 16
# 别的模式不受影响
mu.mem_write(0x33c00, b'\0' * 8); W(BOTP + 0x324, 0x777); runE(BOTP, 1); assert r(BUF) == 0x5b08 and r(0x33c00) == 0
print('M OK')
print('OK')
