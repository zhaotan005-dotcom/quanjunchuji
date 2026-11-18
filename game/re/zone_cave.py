# v2.70: cave B also sets blue circle radius +0x3b0 = first entry radius +0x3c0 (no visible circle at start)
# v2.45 native poison-zone table: InitArea (0xd9777c) reads our DefaultRestrictedAreaInfos, restores after
import asm, capstone, struct
S_OFF = 0x990            # inside Server.SLACK
A_OFF = S_OFF + 0x20
def caveA(org):
    src = '''
  str r0, [sp, #0x60]
  sub ip, pc, #0x2c
  ldr r1, [ip]
  cmp r1, #0
  beq done
  ldr r2, [ip, #8]
  cmp r2, #0
  bne done
  ldr r2, [r4, #0x33c]
  ldr r0, [r4, #0x340]
  cmp r2, #0
  beq done
  cmp r0, #1
  blt done
  str r4, [ip, #8]
  str r2, [ip, #0xc]
  str r0, [ip, #0x10]
  ldr r3, [r4, #0x344]
  str r3, [ip, #0x14]
  sub r0, r0, #1
  mov r3, #0x54
  mla r0, r0, r3, r2
  ldr r3, [ip, #4]
  push {r6, r7}
  mov r5, #0
loop:
  cmp r5, r3
  bge endloop
  sub r6, r3, #1
  cmp r5, r6
  moveq lr, r0
  movne lr, r2
  mov r6, #0x54
  mla ip, r5, r6, r1
  mov r7, #0x20
inner:
  ldr r6, [lr, r7]
  str r6, [ip, r7]
  add r7, r7, #4
  cmp r7, #0x54
  blt inner
  add r5, r5, #1
  b loop
endloop:
  pop {r6, r7}
  str r1, [r4, #0x33c]
  str r3, [r4, #0x340]
  str r3, [r4, #0x344]
done:
  ldr r5, [r4, #0x34c]
  ldr pc, [pc, #-4]
'''
    return asm.asm(src, org)[0]
def caveB(org):
    k = org + 8 - S_OFF
    src = """
  sub ip, pc, #%d
  ldr r1, [ip, #8]
  cmp r1, #0
  beq out
  ldr r2, [ip, #0xc]
  str r2, [r1, #0x33c]
  ldr r2, [ip, #0x10]
  str r2, [r1, #0x340]
  ldr r2, [ip, #0x14]
  str r2, [r1, #0x344]
  mov r2, #0
  str r2, [ip, #8]
  ldr r2, [r1, #0x358]
  cmp r2, #0
  ble out
  ldr r2, [r1, #0x3c0]
  str r2, [r1, #0x3b0]
out:
  sub sp, fp, #0x1c
  pop {r4-r11, pc}
""" % k
    return asm.asm(src, org)[0]
A = caveA(A_OFF)
B_OFF = A_OFF + len(A) + 4
B = caveB(B_OFF)
END = B_OFF + len(B)
if __name__ == '__main__':
    md=capstone.Cs(capstone.CS_ARCH_ARM,capstone.CS_MODE_ARM)
    for i in md.disasm(A,A_OFF): print(hex(i.address),i.mnemonic,i.op_str)
    for i in md.disasm(B,B_OFF): print(hex(i.address),i.mnemonic,i.op_str)
    print('A',A.hex()); print('B',B.hex()); print(hex(B_OFF), hex(END), 'limit', hex(0x4a2e000-0x4a2d540))
