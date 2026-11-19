# v3.46 训练岛同帧摆人 cave（Island.java）。两个入口：
#  L: Login 没找到位置时（PR 补丁的 beq 0x10850a8 改成 beq L）：把 PC(r6) 记进缓冲区 +0，再回 0x10850a8
#  S: Tick 0x1082e84 bl SetupPlayerStartActor 改成 bl S：原函数跑完同一帧里按 Login 原步骤
#     FindSlot → 追加进位置玩家数组 → AddPlayerToSlot → Client Load Block(PC) →（+400 仍 1 且 +440>=0）FirstLoad
#  缓冲区：+0 PC（L 写；S 看过就清 0，不留旧指针）+4 S 调用次数 +8 阶段 +12 位置指针 +16 Login 记录次数
import sys, struct
exec(open('asm.py').read())
ORG=0x171e2c0
SRC='''
L:
  ldr r0, [pc, #LITL]
  str r6, [r0]
  ldr r1, [r0, #16]
  add r1, r1, #1
  str r1, [r0, #16]
  b 0x10850a8
S:
  push {r4, r5, r6, r7, r8, lr}
  mov r4, r0
  bl 0x1082868
  ldr r7, [pc, #LITS]
  ldr r0, [r7, #4]
  add r0, r0, #1
  str r0, [r7, #4]
  ldr r5, [r7]
  cmp r5, #0
  beq out
  ldr r0, [r4, #0xa44]
  cmp r0, #0
  beq clr
  ldr r0, [r5, #0xbdc]
  cmp r0, #0
  bge clr
  ldr r0, [r5, #0x558]
  cmp r0, #0
  beq clr
  mov r0, #1
  str r0, [r7, #8]
  mov r0, r4
  mov r1, r5
  bl 0x10845b0
  movs r6, r0
  beq clr
  str r6, [r7, #12]
  ldr r8, [r6, #0x14]
  add r0, r8, #1
  str r0, [r6, #0x14]
  ldr r1, [r6, #0x18]
  cmp r8, r1
  blt st
  add r0, r6, #0x10
  mov r1, r8
  bl 0xb74980
st:
  ldr r0, [r6, #0x10]
  str r5, [r0, r8, lsl #2]
  mov r0, #2
  str r0, [r7, #8]
  mov r0, r4
  mov r1, r5
  bl 0x1083a98
  ldr r0, [r5, #0xbdc]
  cmp r0, #0
  blt clr
  ldr r0, [r5, #0xbe0]
  cmp r0, #1
  blt clr
  mov r0, #3
  str r0, [r7, #8]
  mov r0, r5
  bl 0x1086960
  mov r0, #4
  str r0, [r7, #8]
  ldr r0, [r4, #0xa3c]
  cmp r0, #0
  beq clr
  ldrb r1, [r0, #0x400]
  cmp r1, #0
  beq clr
  ldr r1, [r0, #0x440]
  cmp r1, #0
  blt clr
  bl 0x1165e34
  mov r0, #5
  str r0, [r7, #8]
clr:
  mov r0, #0
  str r0, [r7]
out:
  pop {r4, r5, r6, r7, r8, pc}
lit:
  .word 0
'''
def build():
    # 两遍：先拿标签地址，再填 pc 相对偏移
    s=SRC.replace('#LITL','#0').replace('#LITS','#0')
    _,lab=asm(s,ORG)
    lit=lab['lit']
    # L 第一条在 lab['L']；S 里 ldr r7 是 S+12
    s=SRC.replace('#LITL','#%d'%(lit-(lab['L']+8))).replace('#LITS','#%d'%(lit-(lab['S']+12+8)))
    code,lab=asm(s,ORG)
    return code,lab
if __name__=='__main__':
    code,lab=build()
    import capstone
    md=capstone.Cs(capstone.CS_ARCH_ARM,capstone.CS_MODE_ARM)
    for i in md.disasm(code,ORG): print('%x %s %s'%(i.address,i.mnemonic,i.op_str))
    print({k:hex(v) for k,v in lab.items()})
    print('len',hex(len(code)))
    print('CODE',code[:-4].hex())
    # 入口改法
    def bcc(cond,at,t): return struct.pack('<I',(cond<<28)|(0xa<<24)|(((t-at-8)>>2)&0xffffff)).hex()
    def bl(at,t): return struct.pack('<I',(14<<28)|(0xb<<24)|(((t-at-8)>>2)&0xffffff)).hex()
    print('LOGIN 0x1084f38 beq L', bcc(0,0x1084f38,lab['L']))
    print('TICK 0x1082e84 bl S', bl(0x1082e84,lab['S']))
