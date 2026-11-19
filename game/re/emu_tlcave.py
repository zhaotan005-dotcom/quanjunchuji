# unicorn: tlcave 在 TileMode 管理器上把服务器层里缺的层追加进 CurrentLayers，跳过 N0 / N18(+2c9=0) / 已有同名
import struct, unicorn as U, unicorn.arm_const as A
exec(open('tlcave.py').read().split("if __name__")[0])
code,lab=build()
lib=open('/workspace/work/qjcj/x/lib/armeabi-v7a/libUE4.so','rb').read()
exec(open('ue.py').read())
def run(case):
    mu=U.Uc(U.UC_ARCH_ARM,U.UC_MODE_ARM)
    for a in (0x171e000,0x115c000,0xbc5000,0x100000,0x200000,0x300000): mu.mem_map(a,0x1000 if a>0x300000 or a in (0x171e000,0x115c000,0xbc5000) else 0x10000)
    A0=0x171e4b0-ORG
    mu.mem_write(ORG,code[:A0]); mu.mem_write(B_ORG,code[B_ORG-ORG:])
    mu.mem_write(0x115c744,bytes.fromhex('2a0717ea')); mu.mem_write(0x115c74c,b'\xfe\xff\xff\xea')  # b . (stop)
    # GROW stub: realloc into 0x300800, copy 8 entries, set cap
    grow=asm('''
  ldr r2, [r0]
  mov r3, #0x300000
  add r3, r3, #0x800
  str r3, [r0]
  mov r12, #64
  str r12, [r0, #8]
cp:
  cmp r1, #0
  beq out
  ldr r12, [r2]
  add r2, r2, #4
  str r12, [r3]
  add r3, r3, #4
  ldr r12, [r2]
  add r2, r2, #4
  str r12, [r3]
  add r3, r3, #4
  sub r1, r1, #1
  b cp
out:
  .word 0xe12fff1e''',0xbc5580)[0]
    mu.mem_write(0xbc5580,grow)
    VT=0x200100; N=0x200200; BUF=0x200300; MGR=0x100000
    struct.pack_into
    lits=struct.pack('<4I',VT,N,BUF,0x51734e0)
    mu.mem_write(lab['litvt'],lits)
    # names: N0=(0x10,0) N18=(0x20,0)
    mu.mem_write(N,struct.pack('<8I',0x10,0,0,0,0,0,0x20,0))
    layers={}
    def mk(i,name):
        a=0x101000+i*0x40; mu.mem_write(a,struct.pack('<4I',0,0,name,0)); return a
    cur=[mk(0,0x30),mk(1,0x31)]                 # Landscape, Ocean (display)
    srv=[mk(10,0x30),mk(11,0x10),mk(12,0x20),mk(13,0x40),mk(14,0x41),mk(15,0x42),cur[1]]  # Landscape(dup name), N0, N18, SO, SOM, SOG, same ptr
    ctrl=0x102000
    mu.mem_write(ctrl,b'\0'*0x100)
    def arr(addr,ptrs):
        b=b''.join(struct.pack('<2I',p,ctrl+8*i if p else 0) for i,p in enumerate(ptrs)); mu.mem_write(addr,b)
    arr(0x103000,cur); arr(0x104000,srv)
    mvt=VT if case!='other' else VT+4
    mu.mem_write(MGR,struct.pack('<I',mvt))
    mu.mem_write(MGR+0x2e8,struct.pack('<2I',0x104000,len(srv)))
    mu.mem_write(MGR+0x2f4,struct.pack('<3I',0x103000,len(cur),2 if case=='grow' else 16))
    so=mk(20,0x10); mu.mem_write(MGR+0x2d4,struct.pack('<2I',so,ctrl+0xf0) if case!='noso' else b'\0'*8)
    mu.mem_write(MGR+0x2c9,bytes([1 if case=='coll' else 0]))
    sp=0x30f000; mu.mem_write(sp+0x24,struct.pack('<I',MGR))
    for r,v in ((A.UC_ARM_REG_SP,sp),(A.UC_ARM_REG_R4,0x4444),(A.UC_ARM_REG_R5,0x5555),(A.UC_ARM_REG_R11,0xbbbb),(A.UC_ARM_REG_LR,0x1e1e)): mu.reg_write(r,v)
    mu.emu_start(0x115c744,0x115c74c,count=5000)
    assert mu.reg_read(A.UC_ARM_REG_PC)==0x115c74c
    assert mu.reg_read(A.UC_ARM_REG_SP)==sp and mu.reg_read(A.UC_ARM_REG_R4)==0x4444 and mu.reg_read(A.UC_ARM_REG_R5)==0x5555 and mu.reg_read(A.UC_ARM_REG_R11)==0xbbbb and mu.reg_read(A.UC_ARM_REG_LR)==0x1e1e
    assert mu.reg_read(A.UC_ARM_REG_R0)==0x51734e0
    data,cnt=struct.unpack('<2I',mu.mem_read(MGR+0x2f4,8))
    names=[struct.unpack('<I',mu.mem_read(struct.unpack('<I',mu.mem_read(data+8*i,4))[0]+8,4))[0] for i in range(cnt)]
    b=struct.unpack('<3I',mu.mem_read(BUF,12))
    rc=[struct.unpack('<I',mu.mem_read(ctrl+8*i+4,4))[0] for i in range(len(srv))]+[struct.unpack('<I',mu.mem_read(ctrl+0xf4,4))[0]]
    print(case,[hex(n) for n in names],b,rc)
    return names,b
n,b=run('normal'); assert n==[0x30,0x31,0x10,0x40,0x41,0x42] and b==(0,0,7)
n,b=run('coll'); assert n==[0x30,0x31,0x10,0x20,0x40,0x41,0x42]
n,b=run('grow'); assert n==[0x30,0x31,0x10,0x40,0x41,0x42]
n,b=run('noso'); assert n==[0x30,0x31,0x40,0x41,0x42]
n,b=run('other'); assert n==[0x30,0x31] and b==(0,0,0)
print('ALL OK')
