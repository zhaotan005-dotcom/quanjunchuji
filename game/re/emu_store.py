# v1.57 假仓库：InitAvatar 0x135b104 → cave 0x171e630 → CacheStoreProp(r5=ds, &TArray)
import struct,re,unicorn as U
from unicorn.arm_const import *
s=open('zframe/src/com/zframe/inject/Mods.java').read()
g=lambda k: re.search(k+r' = "([0-9a-f%s]+)"',s).group(1)
lib=open('game/x/lib/armeabi-v7a/libUE4.so','rb').read()
assert lib[0x135b104:0x135b108].hex()==g('STORE_HOOK_O') and lib[0x171e630:0x171e65c].hex()==g('STORE_CAVE_O') and lib[0x1503494:0x1503498].hex()==g('HALL_FILL_O')
def mov(top,rd,imm):
    w=(0xe3400000 if top else 0xe3000000)|((imm>>12)&0xf)<<16|rd<<12|(imm&0xfff); return struct.pack('<I',w).hex()
bad=0
for addr,num in ((0x7a3b1010,11),(0x00402000,11),(0x7a3b1010,0)):
    cave=bytes.fromhex(g('STORE_CAVE_T')%(mov(0,1,addr&0xffff),mov(1,1,addr>>16)))
    assert len(cave)==44
    mu=U.Uc(U.UC_ARCH_ARM,U.UC_MODE_ARM)
    for a,sz in ((0x135b000,0x1000),(0x171e000,0x1000),(0x1872000,0x1000),(0x300000,0x10000),(addr&~0xfff,0x2000)): 
        try: mu.mem_map(a,sz)
        except U.UcError: pass
    mu.mem_write(0x135b000,lib[0x135b000:0x135c000]); mu.mem_write(0x135b104,bytes.fromhex(g('STORE_HOOK_N'))); mu.mem_write(0x171e630,cave)
    mu.mem_write(0x1872b04,struct.pack('<II',0xe1a0f00e,0))  # stub: mov pc, lr
    mu.mem_write(addr,struct.pack('<III',addr+16,num,num))
    hit=[]
    mu.hook_add(U.UC_HOOK_CODE,lambda uc,a,sz,u: hit.append((uc.reg_read(UC_ARM_REG_R0),uc.reg_read(UC_ARM_REG_R1),uc.reg_read(UC_ARM_REG_SP)%8)),begin=0x1872b04,end=0x1872b04)
    mu.reg_write(UC_ARM_REG_SP,0x308000); mu.reg_write(UC_ARM_REG_R5,0x5555); mu.reg_write(UC_ARM_REG_R4,0x4444)
    for r,v in ((UC_ARM_REG_R2,0x22),(UC_ARM_REG_R3,0x33),(UC_ARM_REG_R12,0xcc)): mu.reg_write(r,v)
    mu.emu_start(0x135b104,0x135b108,count=100)
    ok=mu.reg_read(UC_ARM_REG_R0)==1 and mu.reg_read(UC_ARM_REG_SP)==0x308000 and mu.reg_read(UC_ARM_REG_R2)==0x22 and mu.reg_read(UC_ARM_REG_R12)==0xcc
    ok=ok and (hit==[(0x5555,addr,0)] if num else hit==[])
    bad+=not ok; print('OK ' if ok else 'BAD',hex(addr),num,hit)
print('全部通过' if not bad else f'{bad} 个失败')
