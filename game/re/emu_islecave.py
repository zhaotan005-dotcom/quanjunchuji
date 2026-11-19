# unicorn：islecave S/L 两个入口（被调函数换成桩）
import struct
from unicorn import *
from unicorn.arm_const import *
exec(open('islecave.py').read().split("if __name__")[0])
code,lab=build()
BUF=0x900000; GM=0xa00000; PC=0xb00000; SLOTS=0xc00000; ARR=0xd00000; MGR=0xe00000; STK=0xf00000
code=code[:-4]+struct.pack('<I',BUF)
def run(case):
    mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
    mu.mem_map(0,0x4000000)
    mu.mem_write(ORG,code)
    RET=0x3000000
    bxlr=bytes.fromhex('1eff2fe1')
    calls=[]
    for t in (0x1082868,0x10845b0,0xb74980,0x1083a98,0x1086960,0x1165e34,0x10850a8): mu.mem_write(t,bxlr)
    w=lambda a,v: mu.mem_write(a,struct.pack('<i',v))
    r=lambda a: struct.unpack('<i',mu.mem_read(a,4))[0]
    w(GM+0xa44,9); w(GM+0xa3c,MGR); w(PC+0xbdc,-1); w(PC+0x558,0x123456)
    w(SLOTS+0x10,ARR); w(SLOTS+0x14,0); w(SLOTS+0x18,4)
    mu.mem_write(MGR+0x400,b'\x01'); w(MGR+0x440,-1)
    w(BUF,PC if case!='nopc' else 0)
    def hook(mu,addr,size,ud):
        if addr in (0x1082868,0x10845b0,0xb74980,0x1083a98,0x1086960,0x1165e34): calls.append(hex(addr))
        if addr==0x10845b0: mu.reg_write(UC_ARM_REG_R0,SLOTS)
        if addr==0x1083a98: w(PC+0xbdc,0); w(PC+0xbe0,9)
        if addr==0x1086960: w(MGR+0x440,0)   # LoadBlock 设了当前块，+400 还是 1 → 走 FirstLoad
    mu.hook_add(UC_HOOK_CODE,hook)
    mu.reg_write(UC_ARM_REG_SP,STK); mu.reg_write(UC_ARM_REG_LR,RET); mu.reg_write(UC_ARM_REG_R0,GM)
    for i in (4,5,6,7,8): mu.reg_write(getattr(__import__('unicorn.arm_const',fromlist=['x']),'UC_ARM_REG_R%d'%i),0x1110+i)
    mu.emu_start(lab['S'],RET)
    ok=all(mu.reg_read(getattr(__import__('unicorn.arm_const',fromlist=['x']),'UC_ARM_REG_R%d'%i))==0x1110+i for i in (4,5,6,7,8)) and mu.reg_read(UC_ARM_REG_SP)==STK
    print(case,calls,'阶段',r(BUF+8),'buf0',hex(r(BUF)),'次数',r(BUF+4),'数组',hex(r(ARR)),'num',r(SLOTS+0x14),'寄存器恢复',ok)
    # L 入口
    mu.reg_write(UC_ARM_REG_R6,PC); w(BUF,0)
    mu.emu_start(lab['L'],0x10850a8+4, count=10) if False else None
run('full'); run('nopc')
# L
mu=Uc(UC_ARCH_ARM,UC_MODE_ARM); mu.mem_map(0,0x4000000); mu.mem_write(ORG,code)
mu.reg_write(UC_ARM_REG_R6,PC); mu.emu_start(lab['L'],0x10850a8)
print('L: buf0',hex(struct.unpack('<I',mu.mem_read(BUF,4))[0]),'次数',struct.unpack('<I',mu.mem_read(BUF+16,4))[0])
