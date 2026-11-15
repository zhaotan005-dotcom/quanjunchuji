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
# --- TICK（v1.62：挂在 UpdateService::Tick 开头，头 32 字节，+12 = 连接器对象）---
mu.mem_write(0x18000,bytes.fromhex(L[1]))
recs=[b'abc',b'0123456789']; buf=b''
for rr in recs:
    buf+=len(rr).to_bytes(4,'little')+rr; buf+=b'\0'*((-len(buf))%4)
mu.mem_write(0x7b000020,buf); mu.mem_write(0x7b000000,len(buf).to_bytes(4,'little'))
CONN=0x30000; VT=0x30100; STUB=0x30200; UPD=0x30800
mu.mem_write(CONN,VT.to_bytes(4,'little')); mu.mem_write(VT+0x164,STUB.to_bytes(4,'little')); mu.mem_write(STUB,bytes.fromhex('1eff2fe1'))
got=[]
def h(uc,addr,size,ud):
    got.append((uc.reg_read(UC_ARM_REG_R0),bytes(uc.mem_read(uc.reg_read(UC_ARM_REG_R1),uc.reg_read(UC_ARM_REG_R2))),uc.reg_read(UC_ARM_REG_R3)))
mu.hook_add(UC_HOOK_CODE,h,begin=STUB,end=STUB)
def tick():
    for r,v in ((UC_ARM_REG_R0,UPD),(UC_ARM_REG_R1,0x3c888889),(UC_ARM_REG_SP,SP),(UC_ARM_REG_R4,0x1111),(UC_ARM_REG_R8,0x8888),(UC_ARM_REG_R10,0xaaaa),(UC_ARM_REG_FP,0xbbbb),(UC_ARM_REG_LR,0xcccc)): mu.reg_write(r,v)
    mu.emu_start(0x18000,0x40000008,count=800)
    g=lambda r: mu.reg_read(r)
    st=bytes(mu.mem_read(SP-16,16))
    return (g(UC_ARM_REG_R0)==UPD and g(UC_ARM_REG_R1)==0x3c888889 and g(UC_ARM_REG_SP)==SP-16 and g(UC_ARM_REG_FP)==SP-8
            and st==b''.join(x.to_bytes(4,'little') for x in (0x1111,0xaaaa,0xbbbb,0xcccc)) and g(UC_ARM_REG_R8)==0x8888)
hd=lambda o: int.from_bytes(mu.mem_read(0x7b000000+o,4),'little')
# 1) 连接器还没找到：不投递，只记帧数和 this
ok&= tick() and got==[] and hd(16)==UPD and hd(20)==1 and hd(8)==0
# 2) 填上连接器：两条都投递
mu.mem_write(0x7b00000c,CONN.to_bytes(4,'little'))
ok&= tick(); print('TICK calls',got,'已收',hd(8),'帧',hd(20))
ok&= [g[1] for g in got]==recs and all(g[0]==CONN and g[2]==0 for g in got) and hd(8)==2 and hd(20)==2
# 3) 没有新包：不调用
got.clear(); ok&= tick() and got==[] and hd(20)==3
# --- v1.61 邮件 ---
k=M('msg.CSPkg').FromString(bytes.fromhex(L[5])); n=M('msg.CSMailPushNewMailNtf').FromString(k.body)
m=n.mail_list.mails[0]; print('25007:',k.head.cmd,'type',n.mail_list.type,'index',m.index,m.title,'附件',len(m.attachment.props),'已读',m.read_flag)
ok&= k.head.cmd==25007 and n.mail_list.type==1 and m.index==1 and m.title=='补给' and len(m.attachment.props)>=20 and not m.read_flag and m.recv_time>0
ok&= m.attachment.props[0].prop_role_attr.sex==1 and m.attachment.props[1].prop_role_attr.sex==2
g=M('msg.CSGetMailListRes').FromString(bytes.fromhex(L[6])); print('25002:',g.result,len(g.mail_lists),len(g.mail_lists[0].mails))
ok&= g.result==0 and g.mail_lists[0].type==1 and len(g.mail_lists[0].mails)==1
r=M('msg.CSReadMailsRes').FromString(bytes.fromhex(L[7])); ids=[p.id for p in r.change.prop_changes]
print('25004:',r.result,'type',r.type,'read',list(r.read_indexs),'新物品',len(ids))
ok&= r.result==0 and list(r.read_indexs)==[1] and 30325000003 in ids and 30318000000 not in ids and 10200000000 not in ids and len(set(ids))==len(ids)
d=M('msg.CSDepositGetPropsRes').FromString(bytes.fromhex(L[8])); print('领取后 13002:',len(d.prop_list),'个')
ok&= len(d.prop_list)==8+len(ids) and all(p.owner_id==10200000000 for p in d.prop_list if p.id//100000000!=101 and p.id//100000000!=102)
r2=M('msg.CSReadMailsRes').FromString(bytes.fromhex(L[9])); print('再领:',len(r2.change.prop_changes)); ok&= len(r2.change.prop_changes)==0
print('全部通过' if ok else '失败')
