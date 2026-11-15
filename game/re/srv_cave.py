# v1.61 自建服务端的两段机器码。地址用占位，Java 里填。
import sys
sys.path.insert(0,'.')
from asm import asm
DMB='.word 0xf57ff05b'
BLXIP='.word 0xe12fff3c'
# SendRawBuffer(this, buf=r1, len=r2, x=r3) 整个换掉：把 len+数据 追加到请求区，返回 len
SEND='''
 push {r4,r5,r6,r7,r8,lr}
 movw r4, #0
 movt r4, #0
 ldr r5, [r4]
 add r6, r5, r2
 add r6, r6, #8
 ldr r7, [r4, #8]
 cmp r6, r7
 bhi full
 add r7, r4, #16
 add r7, r7, r5
 str r2, [r7]
 add r7, r7, #4
 mov r6, #0
cp:
 cmp r6, r2
 bge done
 ldrb ip, [r1, r6]
 strb ip, [r7, r6]
 add r6, r6, #1
 b cp
done:
 add r5, r5, #4
 add r5, r5, r2
 add r5, r5, #3
 bic r5, r5, #3
 %s
 str r5, [r4]
 ldr r6, [r4, #4]
 add r6, r6, #1
 str r6, [r4, #4]
 mov r0, r2
 pop {r4,r5,r6,r7,r8,pc}
full:
 ldr r6, [r4, #12]
 add r6, r6, #1
 str r6, [r4, #12]
 mov r0, r2
 pop {r4,r5,r6,r7,r8,pc}
'''%DMB
# Tick 里 0xf92fb0（movw r0,#0xa054; movt r0,#0xf）：先把回包区里 Java 放好的包逐个交给 vt+0x164(this,buf,len,0)
TICK='''
 push {r4,r5,r6,r7,r9,lr}
 movw r4, #0
 movt r4, #0
lp:
 ldr r5, [r4, #4]
 ldr r6, [r4]
 %s
 cmp r5, r6
 bge out
 add r7, r4, #16
 add r7, r7, r5
 ldr r2, [r7]
 add r1, r7, #4
 add r5, r5, #4
 add r5, r5, r2
 add r5, r5, #3
 bic r5, r5, #3
 str r5, [r4, #4]
 ldr r6, [r4, #8]
 add r6, r6, #1
 str r6, [r4, #8]
 mov r0, r8
 mov r3, #0
 ldr ip, [r8]
 ldr ip, [ip, #0x164]
 %s
 b lp
out:
 pop {r4,r5,r6,r7,r9,lr}
 movw r0, #0xa054
 movt r0, #0xf
 ldr pc, [pc, #-4]
 .word 0
'''%(DMB,BLXIP)
if __name__=='__main__':
    for n,src in (('SEND',SEND),('TICK',TICK)):
        b,_=asm(src,0); print(n,len(b),b.hex())
