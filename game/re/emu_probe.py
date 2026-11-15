# v1.59 人物探针：用 Java 生成的跳板(/tmp/pt/out.txt)，模拟「函数开头被换成 ldr pc 跳板 → 计数 → 原两条指令 → 回到函数+8」
import struct,sys,unicorn as U
from unicorn.arm_const import *
lib=open('game/x/lib/armeabi-v7a/libUE4.so','rb').read()
B=0xabd00000; CNT=0x7a3b1010+8; SL=0x4a2d540
bad=0
for i,l in enumerate(open(sys.argv[1] if len(sys.argv)>1 else '/tmp/pt/out.txt')):
    off,orig,tr,hk=l.split(); off=int(off,16)
    assert lib[off:off+8].hex()==orig, hex(off)
    tr=bytes.fromhex(tr); assert len(tr)<=0x40
    mu=U.Uc(U.UC_ARCH_ARM,U.UC_MODE_ARM)
    fpg=(B+off)&~0xfff; mu.mem_map(fpg,0x3000); mu.mem_write(fpg,lib[fpg-B:fpg-B+0x3000])
    mu.mem_map((B+SL)&~0xfff,0x2000); mu.mem_map(0x7a3b1000,0x1000); mu.mem_map(0x300000,0x10000); mu.mem_map(0,0x1000)
    t=B+SL  # 模拟里所有探针都放在第一个槽，hook 也指向它
    mu.mem_write(t,tr); mu.mem_write(B+off,bytes.fromhex(hk))
    mu.mem_write(CNT,struct.pack('<II',5,0))
    SP=0x308000; regs={UC_ARM_REG_R0:0x11,UC_ARM_REG_R1:0x22,UC_ARM_REG_R2:0x33,UC_ARM_REG_R3:0x44,UC_ARM_REG_R4:0x55,UC_ARM_REG_LR:0x1234560,UC_ARM_REG_R11:0x7700}
    for r,v in regs.items(): mu.reg_write(r,v)
    mu.reg_write(UC_ARM_REG_SP,SP)
    mu.emu_start(B+off,B+off+8,count=50)
    # 对照：原始函数前两条
    m2=U.Uc(U.UC_ARCH_ARM,U.UC_MODE_ARM); m2.mem_map(fpg,0x3000); m2.mem_write(fpg,lib[fpg-B:fpg-B+0x3000]); m2.mem_map(0x300000,0x10000); m2.mem_map(0,0x1000)
    for r,v in regs.items(): m2.reg_write(r,v)
    m2.reg_write(UC_ARM_REG_SP,SP); m2.emu_start(B+off,B+off+8,count=2)
    same=all(mu.reg_read(r)==m2.reg_read(r) for r in [UC_ARM_REG_R0,UC_ARM_REG_R1,UC_ARM_REG_R2,UC_ARM_REG_R3,UC_ARM_REG_R4,UC_ARM_REG_SP,UC_ARM_REG_R11,UC_ARM_REG_LR])
    sp2=m2.reg_read(UC_ARM_REG_SP); same=same and mu.mem_read(sp2,SP-sp2)==m2.mem_read(sp2,SP-sp2)
    n,lr=struct.unpack('<II',mu.mem_read(CNT,8))
    ok=same and n==6 and lr==0x1234560
    bad+=not ok; print('OK ' if ok else 'BAD',hex(off),n,hex(lr))
print('全部通过' if not bad else f'{bad} 个失败')
