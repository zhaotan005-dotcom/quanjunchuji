# v2.51 unicorn：vcave(0x4a2d940) + ucave(0x4a2d764) 一起跑 'U'：拷放置位置、置有效、调 obj->vt[off](obj,arg)、写返回/标记；obj 空 → 5b0f；'Z' 原样返回
import struct
from unicorn import *
from unicorn.arm_const import *
import vcave, ucave
VORG=0x4a2d940
vb,vl=vcave.build(VORG); ub,ul=ucave.build()
mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
mu.mem_map(0x4a2d000,0x2000); mu.mem_write(VORG,vb); mu.mem_write(ucave.ORG,ub)
BUF=0x28000
mu.mem_write(ul['ubuf'],struct.pack('<I',BUF))
mu.mem_map(0x10000,0x30000); mu.mem_map(0x7f000,0x1000)
W=lambda a,v: mu.mem_write(a,struct.pack('<I',v&0xffffffff)); r=lambda a: struct.unpack('<I',mu.mem_read(a,4))[0]
# 被调函数：记 r0/r1 到 0x10800，返回 0x77
F=0x10000; mu.mem_write(F,bytes.fromhex('08c09fe5'+'03008ce8'+'7700a0e3'+'1eff2fe1')+struct.pack('<I',0x10800))
OBJ=0x20000; VT=0x20800; W(OBJ,VT); W(VT+0x4d0,F); W(VT+0x484,F)
ITEM=0x22000
END=0x4a2e000-4; SP=0x7ff00-0x44-36
def run(ch, regs_keep=True):
    mu.mem_write(SP+0x44+32,struct.pack('<I',END))
    for i,rg in enumerate([UC_ARM_REG_R4,UC_ARM_REG_R5,UC_ARM_REG_R6,UC_ARM_REG_R7,UC_ARM_REG_R8,UC_ARM_REG_R9,UC_ARM_REG_R10,UC_ARM_REG_R11]):
        mu.mem_write(SP+0x44+4*i,struct.pack('<I',0x1000+i))   # 调用者的 r4..r11，出口要恢复
    mu.reg_write(UC_ARM_REG_SP,SP); mu.reg_write(UC_ARM_REG_R0,ord(ch)); mu.reg_write(UC_ARM_REG_R4,0x30000); mu.reg_write(UC_ARM_REG_R6,1)
    mu.emu_start(VORG,END)
    assert mu.reg_read(UC_ARM_REG_SP)==SP+0x44+36, hex(mu.reg_read(UC_ARM_REG_SP))
    assert [mu.reg_read(x) for x in (UC_ARM_REG_R4,UC_ARM_REG_R8,UC_ARM_REG_R11)]==[0x1000,0x1004,0x1007]
# 1) 选中：不拷位置
mu.mem_write(BUF,b'\0'*128); W(BUF+12,OBJ); W(BUF+16,ITEM); W(BUF+20,0x4d0); W(BUF+28,0)
run('U'); assert r(BUF)==0x5b0e and r(BUF+24)==0x77 and r(0x10800)==OBJ and r(0x10804)==ITEM and mu.mem_read(ITEM+0x336,1)[0]==0
print('select OK')
# 2) 放下：先拷位置 + 置有效
mu.mem_write(BUF,b'\0'*128); W(0x10800,0); W(0x10804,0)
for k in range(6): W(BUF+40+4*k,0x3f800000+k)
W(BUF+12,OBJ); W(BUF+16,0); W(BUF+20,0x484); W(BUF+28,ITEM)
run('U'); assert r(BUF)==0x5b0e and r(0x10800)==OBJ and mu.mem_read(ITEM+0x336,1)[0]==1
assert [r(ITEM+0x338+4*k) for k in range(6)]==[0x3f800000+k for k in range(6)]
print('place OK')
# 3) obj 空
mu.mem_write(BUF,b'\0'*128); run('U'); assert r(BUF)==0x5b0f; print('null OK')
# 4) 别的字母原样收尾
mu.mem_write(BUF,b'\0'*128); run('Z'); assert r(BUF)==0; print('other OK')
print('OK')
