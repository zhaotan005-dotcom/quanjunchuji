# v3.47 训练岛地上物资（枪）：TileMode 流式管理器在单机/客户端路径不把服务器层（ServerOnly / ServerOnlyMajority /
#  ServerOnlyGroundObjects，Main.umap ServerLayersSettings）放进 CurrentLayers(+0x2f4) → FillLayersWithCacheData(0x11649bc，
#  只遍历 CurrentLayers、按层名 +8 匹配缓存项) 永远不给这些层建关卡流对象 → 刷枪点/物资不存在（v3.45 诊断：地上物资 0、
#  客户端层缓存 +370 里 ServerOnly×5 ServerOnlyMajority×5 ServerOnlyGroundObjects×5，当前层 11 个都是显示层）。
# 钩子：BeginPlay 0x115beb4 三条路径的汇合点 0x115c744（ldr r0,[pc,#0x8d4]）→ b 本 cave。
#  cave：管理器 = [sp,#0x24]；只在 vptr == TileMode 虚表（Java 填绝对地址）时：照专用服路径 0x115c0a8..0x115c31c 的规则
#  遍历服务器层列表 +0x2e8/+0x2ec：跳过名字 == 静态名 N0(0x5173280) 的、名字 == N18(+0x18) 且 +0x2c9==0 的，
#  再跳过 CurrentLayers 里已有同指针或同名（层 +8 FName）的 → 追加（满了 0xbc5580 扩容，共享引用 +1，同 0x115c1b0）。
#  然后补回原指令（r0 = 0x51734e0 绝对地址由 Java 填）→ b 0x115c74c。
#  缓冲区（Java 填地址）：+0 跑过次数（TileMode）+8 服务器层总数（追加了几层看诊断里当前层）
#  空位：A 0x171e3f4..0x171e4af（GiveItem 废函数，islecave 后面），B 0x171e568..0x171e5fb（风衣 cave 0x171e4d0+0x98 之后、大厅 cave 0x171e600 之前）
import sys, struct
exec(open('asm.py').read())
ORG=0x171e3f4
HOOK=0x115c744
SRC='''
  push {r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, lr}
  ldr r4, [sp, #0x58]
  cmp r4, #0
  beq done
  ldr r0, [r4]
  ldr r1, [pc, #LVT]
  cmp r0, r1
  bne done
  ldr r10, [pc, #LBUF]
  ldr r0, [r10]
  add r0, r0, #1
  str r0, [r10]
  ldr r9, [pc, #LN]
  ldr r7, [r4, #0x2e8]
  ldr r8, [r4, #0x2ec]
  str r8, [r10, #8]
  mov r6, #0
loop:
  cmp r6, r8
  bge done
  add r0, r7, r6, lsl #3
  ldr r5, [r0]
  ldr r11, [r0, #4]
  cmp r5, #0
  beq next
  ldr r2, [r5, #8]
  ldr r3, [r5, #12]
  ldr r0, [r9]
  ldr r1, [r9, #4]
  cmp r0, r2
  cmpeq r1, r3
  beq next
  ldr r0, [r9, #0x18]
  ldr r1, [r9, #0x1c]
  cmp r0, r2
  cmpeq r1, r3
  bne chk
  ldrb r0, [r4, #0x2c9]
  cmp r0, #0
  beq next
  b chk
done:
  pop {r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, lr}
  ldr r0, [pc, #LR0]
  b 0x115c74c
litvt:
  .word 0
litn:
  .word 0
litbuf:
  .word 0
litr0:
  .word 0
SPLIT
chk:
  ldr lr, [r4, #0x2f4]
  ldr r12, [r4, #0x2f8]
  mov r1, #0
c1:
  cmp r1, r12
  bge add
  ldr r0, [lr, r1, lsl #3]
  add r1, r1, #1
  cmp r0, #0
  beq c1
  cmp r0, r5
  beq next
  ldr r10, [r0, #12]
  ldr r0, [r0, #8]
  cmp r0, r2
  cmpeq r10, r3
  beq next
  b c1
add:
  add r0, r12, #1
  str r0, [r4, #0x2f8]
  ldr r0, [r4, #0x2fc]
  cmp r12, r0
  mov r10, r12
  blt ng
  add r0, r4, #0x2f4
  mov r1, r10
  bl 0xbc5580
ng:
  ldr r1, [r4, #0x2f4]
  add r1, r1, r10, lsl #3
  str r5, [r1]
  str r11, [r1, #4]
  cmp r11, #0
  beq next
  ldr r0, [r11, #4]
  add r0, r0, #1
  str r0, [r11, #4]
next:
  add r6, r6, #1
  b loop
'''
A_END=0x171e4b0; B_ORG=0x171e568
def pad(src):
    a,b=src.split('SPLIT')
    n=sum(1 for l in a.split('\n') if l.split(';')[0].strip() and not l.strip().endswith(':'))
    gap=(B_ORG-ORG)//4-n
    assert ORG+4*n<=A_END, hex(ORG+4*n)
    return a+'\n'.join(['  .word 0']*gap)+'\n'+b
def build():
    global SRC
    SRC=pad(SRC)
    keys={'#LVT':'litvt','#LBUF':'litbuf','#LN':'litn','#LR0':'litr0'}
    s=SRC
    for k in sorted(keys,key=len,reverse=True): s=s.replace(k,'#0')
    _,lab=asm(s,ORG)
    # 找每个占位 ldr 的地址：按顺序重新组装，逐行替换
    lines=SRC.split('\n'); pc=ORG; out=[]
    for l in lines:
        t=l.split(';')[0].strip()
        if t and not t.endswith(':'):
            for k in sorted(keys,key=len,reverse=True):
                if k in t: t=t.replace(k,'#%d'%(lab[keys[k]]-(pc+8))); break
            out.append('  '+t); pc+=4
        else: out.append(l)
    code,lab=asm('\n'.join(out),ORG)
    return code,lab
if __name__=='__main__':
    code,lab=build()
    import capstone
    md=capstone.Cs(capstone.CS_ARCH_ARM,capstone.CS_MODE_ARM)
    for i in md.disasm(code,ORG): print('%x %s %s'%(i.address,i.mnemonic,i.op_str))
    print({k:hex(v) for k,v in lab.items()})
    print('end',hex(ORG+len(code)),'len',hex(len(code)))
    A=code[:A_END-ORG]; A=A[:len(A)-(A_END-ORG)+((A_END-ORG))]
    print('A',code[:0x171e4b0-ORG].rstrip(b'\0').hex() if False else code[:0x171e4b0-ORG].hex())
    print('B',code[B_ORG-ORG:].hex())
    print('HOOK', struct.pack('<I',(14<<28)|(0xa<<24)|(((ORG-HOOK-8)>>2)&0xffffff)).hex())
