import struct, unicorn as U
from unicorn.arm_const import *
c=open('/tmp/cave2.txt').read().split()
cave=bytes.fromhex(c[1]); hook=bytes.fromhex(c[3])
lib=open('x/lib/armeabi-v7a/libUE4.so','rb').read()
assert lib[0x171e4d0:0x171e4d0+len(cave)].hex()==c[0] and lib[0x10d882c:0x10d8830].hex()==c[2]
def run(lst):
    mu=U.Uc(U.UC_ARCH_ARM,U.UC_MODE_ARM)
    mu.mem_map(0xc62000,0x1000); mu.mem_map(0x171e000,0x1000); mu.mem_map(0x10d8000,0x1000)
    mu.mem_map(0x200000,0x10000); mu.mem_map(0x300000,0x10000)
    stub=struct.pack('<IIIIIII',0xe59f300c,0xe5813000,0xe59f3008,0xe5813004,0xe12fff1e,0x200100,len(lst))
    mu.mem_write(0xc62d48,stub); mu.mem_write(0x171e4d0,cave); mu.mem_write(0x10d882c,hook)
    for i,v in enumerate(lst): mu.mem_write(0x200100+8*i,struct.pack('<Q',v))
    mu.reg_write(UC_ARM_REG_SP,0x308000); mu.reg_write(UC_ARM_REG_R1,0x200000); mu.reg_write(UC_ARM_REG_R0,0x5555)
    for r in (UC_ARM_REG_R4,UC_ARM_REG_R5,UC_ARM_REG_R6,UC_ARM_REG_R7,UC_ARM_REG_R8): mu.reg_write(r,0x1111)
    mu.emu_start(0x10d882c,0x10d8830,count=5000)
    data,num=struct.unpack('<II',mu.mem_read(0x200000,8))
    out=[struct.unpack('<Q',mu.mem_read(data+8*i,8))[0] for i in range(num)]
    ok=all(mu.reg_read(r)==0x1111 for r in (UC_ARM_REG_R4,UC_ARM_REG_R5,UC_ARM_REG_R6,UC_ARM_REG_R7,UC_ARM_REG_R8)) and mu.reg_read(UC_ARM_REG_SP)==0x308000 and mu.reg_read(UC_ARM_REG_PC)==0x10d8830
    return out,ok
cases=[([30302000000,30301000001,30306000000,30113000001,30303000006],[30303000006,30318000000,30306000000,30113000001]),
       ([30301000001,30302000000,30303000006,30306000000,30113000001],[30318000000,30113000001,30303000006,30306000000]),
       ([30302000000],[]),([],[]),([30303000006,30302000000],[30303000006]),
       ([10200000000,30302000000,30301000001,30306000000,30213000003,30303000006],[10200000000,30303000006,30318000000,30306000000,30213000003])]
bad=0
for lst,exp in cases:
    out,ok=run(lst); good= ok and out==exp; bad+= not good
    print('OK ' if good else 'BAD',lst,'->',out)
print('全部通过' if bad==0 else f'{bad} 个失败')
