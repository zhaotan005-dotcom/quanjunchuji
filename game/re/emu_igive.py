# v1.98 'I<pawn>x<buf>' 测试：给指定人物发物品
import struct
from unicorn import *
from unicorn.arm_const import *
from vcave import build, LITS
ORG=0x4a2d940
code,lab=build(ORG)
mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
mu.mem_map(0x4a2d000,0x2000)
def W(a,v): mu.mem_write(a,struct.pack('<I',v&0xffffffff))
r=lambda a: struct.unpack('<I',mu.mem_read(a,4))[0]
mu.mem_map(0x10000,0x1000); mu.mem_map(0x20000,0x10000); mu.mem_map(0x7f000,0x1000)
mu.mem_write(ORG,code)
ADD=0x10000; GIVE=0x10400
# ADD stub: ip=0x10800; stm ip,{r0-r3}; ldr r0,[sp,#4]; str r0,[ip,#16]; mov r0,#3; bx lr
mu.mem_write(ADD,bytes.fromhex('10c09fe5'+'0f008ce8'+'04009de5'+'10008ce5'+'0300a0e3'+'1eff2fe1')+struct.pack('<I',0x10800))
# GIVE stub: ip=0x10900; stm ip,{r0-r3}; ldr r0,[sp]; str r0,[ip,#16]; ldr r0,[sp,#0x10]; str r0,[ip,#20]; mov r0,#1; bx lr
mu.mem_write(GIVE,bytes.fromhex('18c09fe5'+'0f008ce8'+'00009de5'+'10008ce5'+'10009de5'+'14008ce5'+'0100a0e3'+'1eff2fe1')+struct.pack('<I',0x10900))
W(lab['lit11'],ADD); W(lab['lit12'],GIVE)
PCO=0x20000; PAWN=0x22000; INV=0x23000; W(PAWN+0x838,INV)
BUF=0x28000; W(BUF+128,2010100); W(BUF+132,4); W(BUF+136,1)
def run(s):
    STR=0x29000; mu.mem_write(STR,b''.join(struct.pack('<I',ord(c)) for c in s)+b'\0'*4)
    SP=0x7ff00-0x44-36; END=0x4a2e000-4
    mu.mem_write(SP+0x44+32,struct.pack('<I',END))
    mu.reg_write(UC_ARM_REG_SP,SP); mu.reg_write(UC_ARM_REG_R4,PCO); mu.reg_write(UC_ARM_REG_R5,STR); mu.reg_write(UC_ARM_REG_R6,len(s)+1); mu.reg_write(UC_ARM_REG_R0,ord(s[0]))
    mu.emu_start(ORG,END)
    assert mu.reg_read(UC_ARM_REG_SP)==SP+0x44+36
run('I%dx%d'%(PAWN,BUF))
print('magic %x inv %x add(%x,%d,%d,%d,%x) n %d give(%x,%d,%d,%d) cnt %d %d ret %d'%(r(BUF),r(BUF+4),r(0x10800),r(0x10804),r(0x10808),r(0x1080c),r(0x10810),r(BUF+140),r(0x10900),r(0x10904),r(0x10908),r(0x1090c),r(0x10910),r(0x10914),r(BUF+144)))
assert r(BUF)==0x5b0b and r(0x10800)==INV and r(0x10804)==2010100 and r(0x10808)==4 and r(0x1080c)==1 and r(0x10900)==PAWN and r(0x10910)==3 and r(0x10914)==3 and r(BUF+144)==1
W(PAWN+0x838,0); run('I%dx%d'%(PAWN,BUF)); assert r(BUF)==0x5b0c
run('I0x%d'%BUF); assert r(BUF)==0x5b06
print('I path OK')
