import struct, sys, unicorn as U
from unicorn.arm_const import *
sys.path.insert(0,'re'); import cap_cave as C
BUF=0x40001000
code=bytearray(C.code); i=16*4
def mw(v): return 0xe3004000|((v>>12)<<16)|(v&0xfff)
def mt(v): return 0xe3404000|((v>>12)<<16)|(v&0xfff)
struct.pack_into('<II',code,i,mw(BUF&0xffff),mt(BUF>>16))
def run(entry,ret,regs,cap,pre=b''):
    mu=U.Uc(U.UC_ARCH_ARM,U.UC_MODE_ARM)
    mu.mem_map(0x171e000,0x1000); mu.mem_write(0x171e600,bytes(code))
    mu.mem_map(0xf94000,0x2000); mu.mem_map(0x40000000,0x10000); mu.mem_map(0x300000,0x10000)
    mu.mem_write(BUF,struct.pack('<II',16,cap))
    mu.mem_write(0x300100,bytes(range(1,41)))
    mu.mem_write(0x300080,struct.pack('<II',0x300100,regs.get('n',40)))
    mu.reg_write(UC_ARM_REG_SP,0x308000)
    for k,v in regs.items():
        if k!='n': mu.reg_write(k,v)
    mu.emu_start(entry,ret,count=10000)
    sp=mu.reg_read(UC_ARM_REG_SP)
    used,_=struct.unpack('<II',mu.mem_read(BUF,8))
    rec=bytes(mu.mem_read(BUF+16,used-16))
    regs_ok=all(mu.reg_read(r)==v for r,v in regs.items() if r!='n')
    return sp,used,rec,regs_ok
ok=True
# capA：SendMsg(this, id=15001, &TArray)
sp,used,rec,rk=run(0x171e600,0xf94a04,{UC_ARM_REG_R0:0x7777,UC_ARM_REG_R1:15001,UC_ARM_REG_R2:0x300080,UC_ARM_REG_R4:0x44,UC_ARM_REG_LR:0x1234567},4096)
t,e,n=struct.unpack('<III',rec[:12]); good= rk and sp==0x308000-36 and (t,e,n)==(1,15001,40) and rec[12:52]==bytes(range(1,41)) and used==16+12+40
print('capA',good,sp==0x308000-36,t,e,n,used); ok&=good
# capB：SendRawBuffer(this, buf, 7)
sp,used,rec,rk=run(0x171e624,0xf950e4,{UC_ARM_REG_R0:0x7777,UC_ARM_REG_R1:0x300100,UC_ARM_REG_R2:7,UC_ARM_REG_R3:5,UC_ARM_REG_LR:0xabcdef0},4096)
t,e,n=struct.unpack('<III',rec[:12]); good= rk and sp==0x308000-24 and (t,e,n)==(2,0xabcdef0,7) and used==16+12+8
print('capB',good,t,hex(e),n,used); ok&=good
# 满了不写
sp,used,rec,rk=run(0x171e624,0xf950e4,{UC_ARM_REG_R1:0x300100,UC_ARM_REG_R2:40},50)
print('full',rk and used==16); ok&=rk and used==16
# 空指针不写
sp,used,rec,rk=run(0x171e624,0xf950e4,{UC_ARM_REG_R1:0,UC_ARM_REG_R2:4},4096)
print('null',rk and used==16); ok&=rk and used==16
print('全部通过' if ok else '有失败')
