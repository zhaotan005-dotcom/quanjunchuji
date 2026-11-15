# v1.54 大厅人物：InitAvatarClotheTry 默认分支 0x1503680 的 bl c62c54 → cave2(0x171e600) → 复制默认衣服后跳进风衣 cave(0x171e4dc)
import struct, re, unicorn as U
from unicorn.arm_const import *
s=open('zframe/src/com/zframe/inject/Mods.java').read()
g=lambda k: bytes.fromhex(re.search(k+r' = "([0-9a-f]+)"',s).group(1))
coat=g('COAT_CAVE_N'); cave2=g('HALL_CAVE_N'); hook=g('HALL_COAT_N')
lib=open('game/x/lib/armeabi-v7a/libUE4.so','rb').read()
assert lib[0x1503680:0x1503684]==g('HALL_COAT_O') and lib[0x171e600:0x171e610]==g('HALL_CAVE_O')
def run(lst):
    mu=U.Uc(U.UC_ARCH_ARM,U.UC_MODE_ARM)
    for a in (0xc62000,0x171e000,0x1503000,0x200000,0x300000): mu.mem_map(a,0x1000 if a<0x200000 else 0x10000)
    # c62c54 桩：out(r0) = {0x200100, n, n}
    stub=struct.pack('<IIIIIIIII',0xe59f3010,0xe5803000,0xe59f300c,0xe5803004,0xe5803008,0xe12fff1e,0,0x200100,len(lst))
    stub=struct.pack('<IIIIII',0xe59f3010,0xe5803000,0xe59f300c,0xe5803004,0xe5803008,0xe12fff1e)+struct.pack('<II',0x200100,len(lst))
    mu.mem_write(0xc62c54,stub); mu.mem_write(0x171e4d0,coat); mu.mem_write(0x171e600,cave2); mu.mem_write(0x1503680,hook)
    for i,v in enumerate(lst): mu.mem_write(0x200100+8*i,struct.pack('<Q',v))
    mu.reg_write(UC_ARM_REG_SP,0x308000); mu.reg_write(UC_ARM_REG_R0,0x200000)
    for r in (UC_ARM_REG_R4,UC_ARM_REG_R5,UC_ARM_REG_R6,UC_ARM_REG_R7,UC_ARM_REG_R8): mu.reg_write(r,0x1111)
    mu.emu_start(0x1503680,0x1503684,count=5000)
    data,num=struct.unpack('<II',mu.mem_read(0x200000,8))
    out=[struct.unpack('<Q',mu.mem_read(data+8*i,8))[0] for i in range(num)]
    ok=all(mu.reg_read(r)==0x1111 for r in (UC_ARM_REG_R4,UC_ARM_REG_R5,UC_ARM_REG_R6,UC_ARM_REG_R7,UC_ARM_REG_R8)) and mu.reg_read(UC_ARM_REG_SP)==0x308000
    return out,ok
cases=[([30302000000,30301000001,30306000000,30213000003,30303000006],[30303000006,30318000000,30306000000,30213000003]),
       ([30301000001,30302000000],[30318000000]),([],[])]
bad=0
for lst,exp in cases:
    out,ok=run(lst); good=ok and out==exp; bad+=not good; print('OK ' if good else 'BAD',lst,'->',out)
print('全部通过' if not bad else f'{bad} 个失败')
