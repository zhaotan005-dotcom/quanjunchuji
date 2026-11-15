# v1.61 自建服务端：1) 用游戏自带的协议定义解 Java 拼的包；2) unicorn 跑 SEND / TICK 两段机器码
import pickle, sys
from google.protobuf import descriptor_pb2 as dp, descriptor_pool, message_factory
from unicorn import *; from unicorn.arm_const import *
L=open('/tmp/pt/o.txt').read().split('\n')
pool=descriptor_pool.DescriptorPool()
fds={k:dp.FileDescriptorProto.FromString(v) for k,v in pickle.load(open('../proto/fds.pkl','rb')).items()}
done=set()
def add(n):
    if n in done: return
    for d in fds[n].dependency: add(d)
    pool.Add(fds[n]); done.add(n)
for n in fds: add(n)
M=lambda n: message_factory.GetMessageClass(pool.FindMessageTypeByName(n))
ok=True
r=M('msg.CSDepositGetPropsRes').FromString(bytes.fromhex(L[2]))
print('13002:', len(r.prop_list),'props role_id',r.role_id)
for p in r.prop_list: print('  ',p.id,p.gid,p.owner_id,p.equip_position, p.prop_role_attr.sex if p.HasField('prop_role_attr') else '')
ok&= len(r.prop_list)==8 and r.role_id==10200000000
e=M('msg.CSDepositEquipPropRes').FromString(bytes.fromhex(L[3])); print('13004:',e)
ok&= e.prop_list[0].equip_position==8
k=M('msg.CSPkg').FromString(bytes.fromhex(L[4])); print('pkg:',k.head.cmd,k.head.client_sequence_id,k.head.key_pkg)
ok&= k.head.cmd==36001 and k.head.key_pkg
# --- SEND ---
mu=Uc(UC_ARCH_ARM,UC_MODE_ARM)
for a in (0x10000,0x7a000000,0x20000,0x30000,0x7b000000,0x40000000): mu.mem_map(a,0x100000 if a in(0x7a000000,0x7b000000) else 0x10000)
mu.mem_write(0x10000,bytes.fromhex(L[0])); mu.mem_write(0x7a000008,(1<<20).to_bytes(4,'little'))
SP=0x30000+0xff00
for i,pl in enumerate([b'hello',b'\x01\x02\x03\x04\x05\x06\x07\x08\x09']):
    mu.mem_write(0x20000,pl)
    mu.reg_write(UC_ARM_REG_R1,0x20000); mu.reg_write(UC_ARM_REG_R2,len(pl)); mu.reg_write(UC_ARM_REG_SP,SP); mu.reg_write(UC_ARM_REG_LR,0x40000000)
    mu.emu_start(0x10000,0x40000000,count=500)
    assert mu.reg_read(UC_ARM_REG_R0)==len(pl) and mu.reg_read(UC_ARM_REG_SP)==SP
hd=mu.mem_read(0x7a000000,16); wp=int.from_bytes(hd[:4],'little'); cnt=int.from_bytes(hd[4:8],'little')
data=bytes(mu.mem_read(0x7a000010,wp)); print('SEND wp',wp,'cnt',cnt,data.hex())
ok&= wp==28 and cnt==2 and data[:9]==b'\x05\0\0\0hello' and data[12:16]==b'\x09\0\0\0'
# --- TICK ---
mu.mem_write(0x18000,bytes.fromhex(L[1]))
recs=[b'abc',b'0123456789']; off=0; buf=b''
for rr in recs:
    buf+=len(rr).to_bytes(4,'little')+rr; buf+=b'\0'*((-len(buf))%4)
mu.mem_write(0x7b000010,buf); mu.mem_write(0x7b000000,len(buf).to_bytes(4,'little'))
THIS=0x30000; VT=0x30100; STUB=0x30200
mu.mem_write(THIS,VT.to_bytes(4,'little')); mu.mem_write(VT+0x164,STUB.to_bytes(4,'little')); mu.mem_write(STUB,bytes.fromhex('1eff2fe1'))
got=[]
def h(uc,addr,size,ud):
    got.append((uc.reg_read(UC_ARM_REG_R0),bytes(uc.mem_read(uc.reg_read(UC_ARM_REG_R1),uc.reg_read(UC_ARM_REG_R2))),uc.reg_read(UC_ARM_REG_R3)))
mu.hook_add(UC_HOOK_CODE,h,begin=STUB,end=STUB)
mu.reg_write(UC_ARM_REG_R8,THIS); mu.reg_write(UC_ARM_REG_SP,SP); mu.reg_write(UC_ARM_REG_R4,0x1111); mu.reg_write(UC_ARM_REG_R9,0x9999)
mu.emu_start(0x18000,0x40000008,count=500)
print('TICK calls',got,'r0',hex(mu.reg_read(UC_ARM_REG_R0)),'sp ok',mu.reg_read(UC_ARM_REG_SP)==SP,'r4',hex(mu.reg_read(UC_ARM_REG_R4)))
ok&= [g[1] for g in got]==recs and all(g[0]==THIS and g[2]==0 for g in got) and mu.reg_read(UC_ARM_REG_R0)==0xfa054 and mu.reg_read(UC_ARM_REG_SP)==SP and mu.reg_read(UC_ARM_REG_R4)==0x1111 and mu.reg_read(UC_ARM_REG_R9)==0x9999
ok&= int.from_bytes(mu.mem_read(0x7b000008,4),'little')==2
# 第二次 Tick：没有新包，不调用
got.clear(); mu.reg_write(UC_ARM_REG_SP,SP); mu.emu_start(0x18000,0x40000008,count=500); ok&= got==[]
print('全部通过' if ok else '失败')
