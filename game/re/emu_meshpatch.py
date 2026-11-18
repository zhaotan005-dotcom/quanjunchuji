# v3.19：客户端 SetStaticMesh Static 检查补丁 0x29575fc 的 unicorn 测试
from unicorn import *
from unicorn.arm_const import *
D=bytearray(open('/workspace/work/qjcj/x/lib/armeabi-v7a/libUE4.so','rb').read())
BASE=0x2950000; SIZE=0x10000; DATA=0x100000
O=bytes.fromhex('6e01d4e5000050e3f700001a90049fe538608de2'); N=bytes.fromhex('6e01d4e5000050e30200a0036e01c405f50000ea')
assert D[0x29575fc:0x2957610]==O, 'orig bytes mismatch'
def run(code, mob):
    mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
    mu.mem_map(BASE,SIZE); mu.mem_map(DATA,0x1000)
    img=bytearray(D[BASE:BASE+SIZE]); img[0x29575fc-BASE:0x2957610-BASE]=code
    mu.mem_write(BASE,bytes(img))
    mu.mem_write(DATA+0x16e,bytes([mob]))
    mu.reg_write(UC_ARM_REG_R4,DATA); mu.reg_write(UC_ARM_REG_R0,0x1234)
    mu.emu_start(0x29575fc,0,count=(3 if code==O else 5))
    return mu.reg_read(UC_ARM_REG_PC), mu.mem_read(DATA+0x16e,1)[0], mu.reg_read(UC_ARM_REG_R4)
for mob in (0,1,2):
    pc,m,r4=run(O,mob); print('orig  mob=%d -> pc=%x mob=%d'%(mob,pc,m))
    pc,m,r4=run(N,mob); print('patch mob=%d -> pc=%x mob=%d r4=%x'%(mob,pc,m,r4))
    assert pc==0x29579e8 and m==(2 if mob==0 else mob) and r4==DATA  # 只把 Static 改成 Movable，Stationary/Movable 不动
# orig with static must go to the warning path (falls through to 0x2957608)
assert run(O,0)[0]==0x2957608
print('OK')
