# v1.55：InitAvatarClotheTry 入口 0x1503230 → cave 0x171e610，角色编号 0 时换成默认角色
import struct,re,unicorn as U
from unicorn.arm_const import *
s=open('zframe/src/com/zframe/inject/Mods.java').read()
g=lambda k: bytes.fromhex(re.search(k+r' = "([0-9a-f]+)"',s).group(1))
lib=open('game/x/lib/armeabi-v7a/libUE4.so','rb').read()
assert lib[0x1503230:0x1503234]==g('HALL_HOOK_O') and lib[0x171e610:0x171e630]==g('HALL_RCAVE_O')
assert g('HALL_RCAVE_N')[16:24]==g('HALL_GEN_F') and g('HALL_RCAVE_M')[16:24]==g('HALL_GEN_M')
bad=0
for cave,dflt in ((g('HALL_RCAVE_N'),10200000000),(g('HALL_RCAVE_M'),10100000000)):
  for arg in (0,10200000000,10100000000,30318000000):
    mu=U.Uc(U.UC_ARCH_ARM,U.UC_MODE_ARM)
    mu.mem_map(0x1503000,0x1000); mu.mem_map(0x171e000,0x1000); mu.mem_map(0x300000,0x10000)
    mu.mem_write(0x1503000,lib[0x1503000:0x1504000]); mu.mem_write(0x1503230,g('HALL_HOOK_N')); mu.mem_write(0x171e610,cave)
    fp=0x304000; mu.mem_write(fp+8,struct.pack('<Q',arg)); mu.reg_write(UC_ARM_REG_FP,fp); mu.reg_write(UC_ARM_REG_R0,1)
    mu.emu_start(0x1503230,0x1503238,count=50)
    got=mu.reg_read(UC_ARM_REG_SB)<<32|mu.reg_read(UC_ARM_REG_SL)
    exp=arg or dflt; ok=got==exp and mu.reg_read(UC_ARM_REG_R0)==1 and mu.reg_read(UC_ARM_REG_PC)==0x1503238
    bad+=not ok; print('OK ' if ok else 'BAD', arg,'->',got)
print('全部通过' if not bad else f'{bad} 个失败')
