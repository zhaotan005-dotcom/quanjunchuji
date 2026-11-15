# 服务端调试：大厅请求抓取 cave（放在 GM OnSpawnVehicleSelectChange 0x171e600 起，前面 0x171e4b0~0x171e558 已被别的 cave 用）
# 钩 1：KeyLobby SendMsg 0xf94a00(this, msgId r1, TArray* r2)   原 push {r4-sl,fp,lr}
# 钩 2：ConnectorService::SendRawBuffer 0xf950e0(this, buf r1, len r2)  原 push {r4-r7,fp,lr}
# 缓冲区（Java mmap 的文件）：[0]=已用字节(从16开始) [4]=总大小，记录 = tag,extra,len,bytes(4对齐)
import sys; sys.path.insert(0,'.')
from asm import asm
ORG=0x171e600
SRC='''
capA:
 push {r0-r7, lr}
 mov r6, r1
 ldr r1, [r2]
 ldr r2, [r2, #4]
 mov r0, #1
 bl rec
 pop {r0-r7, lr}
 push {r4-sl, fp, lr}
 b 0xf94a04
capB:
 push {r0-r7, lr}
 mov r6, lr
 mov r0, #2
 bl rec
 pop {r0-r7, lr}
 push {r4-r7, fp, lr}
 b 0xf950e4
rec:
 movw r4, #0
 movt r4, #0
 ldr r5, [r4]
 ldr r7, [r4, #4]
 cmp r1, #0
 moveq pc, lr
 cmp r2, r7
 movhi pc, lr
 add r3, r5, r2
 add r3, r3, #12
 cmp r3, r7
 movhi pc, lr
 add r3, r4, r5
 str r0, [r3]
 str r6, [r3, #4]
 str r2, [r3, #8]
 add r3, r3, #12
 mov r0, #0
loop:
 cmp r0, r2
 bhs fin
 ldrb r7, [r1, r0]
 strb r7, [r3, r0]
 add r0, r0, #1
 b loop
fin:
 add r5, r5, r2
 add r5, r5, #15
 bic r5, r5, #3
 str r5, [r4]
 mov pc, lr
'''
code,lab=asm(SRC,ORG)
if __name__=='__main__':
    lib=open('../x/lib/armeabi-v7a/libUE4.so','rb').read()
    orig=lib[ORG:ORG+len(code)].hex()
    def b(src,dst): return ((0xea<<24)|(((dst-src-8)>>2)&0xffffff)).to_bytes(4,'little').hex()
    print('CAVE_O',orig); print('CAVE_N',code.hex()); print('len',len(code))
    print('A',hex(lab['capA']),b(0xf94a00,lab['capA'])); print('B',hex(lab['capB']),b(0xf950e0,lab['capB']))
    print('movw idx', (lab['rec']-ORG)//4)
    import capstone
    md=capstone.Cs(capstone.CS_ARCH_ARM,capstone.CS_MODE_ARM)
    for i in md.disasm(code,ORG): print('%x: %s %s'%(i.address,i.mnemonic,i.op_str))
