# v1.58 假仓库：取当前角色 0x1877bfc 入口 → cave 0x171e630 → (表空且开关开) CacheStoreProp(ds, &TArray)
import struct,re,unicorn as U
from unicorn.arm_const import *
s=open('zframe/src/com/zframe/inject/Mods.java').read()
g=lambda k: re.search(k+r' = "([0-9a-f%s]+)"',s).group(1)
lib=open('game/x/lib/armeabi-v7a/libUE4.so','rb').read()
assert lib[0x1877bfc:0x1877c00].hex()==g('STORE_HOOK_O') and lib[0x171e630:0x171e690].hex()==g('STORE_CAVE_O') and lib[0x1503494:0x1503498].hex()==g('HALL_FILL_O')
def mov(top,rd,imm):
    w=(0xe3400000 if top else 0xe3000000)|((imm>>12)&0xf)<<16|rd<<12|(imm&0xfff); return struct.pack('<I',w).hex()
bad=0; DS=0x500000
for addr,num,dsnum in ((0x7a3b1010,11,0),(0x00602000,11,0),(0x7a3b1010,11,5),(0x7a3b1010,0,0)):
    mw,mt=mov(0,2,addr&0xffff),mov(1,2,addr>>16)
    cave=bytes.fromhex(g('STORE_CAVE_T')%(mw,mt,mw,mt)); assert len(cave)==0x60
    mu=U.Uc(U.UC_ARCH_ARM,U.UC_MODE_ARM)
    for a,sz in ((0x1877000,0x1000),(0x171e000,0x1000),(0x1872000,0x1000),(0x300000,0x10000),(addr&~0xfff,0x2000),(DS,0x1000)):
        mu.mem_map(a,sz)
    mu.mem_write(0x1877000,lib[0x1877000:0x1878000]); mu.mem_write(0x1877bfc,bytes.fromhex(g('STORE_HOOK_N'))); mu.mem_write(0x171e630,cave)
    # 假 CacheStoreProp: ds+0x60 = arr.num ; 乱改 r2,r3,ip
    stub='''ldr r2, [r1, #4]
str r2, [r0, #0x60]
mov r3, #0x99
mov ip, #0x98
mov pc, lr'''
    import sys; sys.path.insert(0,'game/re'); from asm import asm
    mu.mem_write(0x1872b04,asm(stub,0x1872b04)[0])
    mu.mem_write(addr,struct.pack('<III',addr+0x40,num,num)); mu.mem_write(DS+0x60,struct.pack('<I',dsnum))
    calls=[]
    mu.hook_add(U.UC_HOOK_CODE,lambda uc,a,sz,u: calls.append((uc.reg_read(UC_ARM_REG_R0),uc.reg_read(UC_ARM_REG_R1),uc.reg_read(UC_ARM_REG_SP)%8)),begin=0x1872b04,end=0x1872b04)
    SP=0x308000; mu.reg_write(UC_ARM_REG_SP,SP)
    regs={UC_ARM_REG_R0:0x400100,UC_ARM_REG_R1:DS,UC_ARM_REG_R4:0x44,UC_ARM_REG_R5:0x55,UC_ARM_REG_R11:0xbb,UC_ARM_REG_LR:0x1234560}
    for r,v in regs.items(): mu.reg_write(r,v)
    mu.emu_start(0x1877bfc,0x1877c00,count=200)
    ok=all(mu.reg_read(r)==v for r,v in regs.items()) and mu.reg_read(UC_ARM_REG_SP)==SP-16
    ok=ok and struct.unpack('<4I',mu.mem_read(SP-16,16))==(0x44,0x55,0xbb,0x1234560)
    cnt,dsr,after=struct.unpack('<III',mu.mem_read(addr+0xc,12))
    want=[(DS,addr,0)] if (num and dsnum==0) else []
    ok=ok and calls==want and cnt==1 and dsr==DS and after==(num if want else 0)
    bad+=not ok; print('OK ' if ok else 'BAD',hex(addr),num,dsnum,calls,cnt,hex(dsr),after)
print('全部通过' if not bad else f'{bad} 个失败')
