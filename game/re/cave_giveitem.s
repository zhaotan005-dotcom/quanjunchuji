; GiveItem cave at 0x17169c0 (old OnBtnGiveGrenade body). Shared 1-FString exec thunk (GiveItem/AddAvaItem/ServerCmd).
; v1.28: only acts when the string starts with 'P' and r0 is the PlayerController (AddAvaItem); pawn = [PC+0x2c0] (AController::Character)
    push {r4-r11, lr}
    sub sp, sp, #0x44
    mov r4, r0
    ldr r5, [r1]
    ldr r6, [r1, #4]
    cmp r5, #0
    beq out
    cmp r6, #2
    blt out
    ldr r0, [r5]
    cmp r0, #80
    bne out
    ldr r4, [r4, #0x2c0]
    cmp r4, #0
    beq out
    mov r7, #0
    mov r8, #0
    mov r9, #0
skip1:
    cmp r9, r6
    bge out
    ldr r0, [r5, r9, lsl #2]
    sub r0, r0, #48
    cmp r0, #9
    addhi r9, r9, #1
    bhi skip1
loop1:
    cmp r9, r6
    bge end1
    ldr r0, [r5, r9, lsl #2]
    sub r0, r0, #48
    cmp r0, #9
    bhi end1
    mov r1, #10
    umull r2, r3, r7, r1
    mla r3, r8, r1, r3
    adds r7, r2, r0
    adc r8, r3, #0
    add r9, r9, #1
    b loop1
end1:
    mov r10, #0
skip2:
    cmp r9, r6
    bge end2
    ldr r0, [r5, r9, lsl #2]
    sub r0, r0, #48
    cmp r0, #9
    addhi r9, r9, #1
    bhi skip2
loop2:
    cmp r9, r6
    bge end2
    ldr r0, [r5, r9, lsl #2]
    sub r0, r0, #48
    cmp r0, #9
    bhi end2
    add r10, r10, r10, lsl #2
    add r10, r0, r10, lsl #1
    add r9, r9, #1
    b loop2
end2:
    cmp r10, #0
    moveq r10, #1
    movw r0, #999
    cmp r10, r0
    movgt r10, r0
    mov r12, #0
skip3:
    cmp r9, r6
    bge end3
    ldr r0, [r5, r9, lsl #2]
    sub r0, r0, #48
    cmp r0, #9
    addhi r9, r9, #1
    bhi skip3
loop3:
    cmp r9, r6
    bge end3
    ldr r0, [r5, r9, lsl #2]
    sub r0, r0, #48
    cmp r0, #9
    bhi end3
    add r12, r12, r12, lsl #2
    add r12, r0, r12, lsl #1
    add r9, r9, #1
    b loop3
end3:
    str r12, [sp, #0x30]
    cmp r12, #0
    beq nodbg
    movw r0, #0x5a01
    str r0, [r12]
    str r4, [r12, #4]
    str r10, [r12, #20]
nodbg:
    orrs r0, r7, r8
    beq out
    mov r0, r7
    mov r1, r8
    movw r2, #10000
    mov r3, #0
    bl LDIVMOD
    mov r7, r0
    mov r8, r2
    ldr r11, [r4, #0x838]
    ldr r12, [sp, #0x30]
    cmp r12, #0
    strne r11, [r12, #8]
    strne r7, [r12, #12]
    strne r8, [r12, #16]
    cmp r11, #0
    beq out
    mov r0, #0
    str r0, [sp]
    str r0, [sp, #12]
    mvn r0, #0
    str r0, [sp, #4]
    str r0, [sp, #8]
    mov r0, r11
    mov r1, r7
    mov r2, r8
    mov r3, r10
    bl ADDCHECK
    ldr r12, [sp, #0x30]
    cmp r12, #0
    strne r0, [r12, #24]
    cmp r0, #1
    movlt r0, r10
    mov r6, r0
    mov r0, #0
    str r0, [sp, #0x40]
    str r0, [sp, #8]
    str r0, [sp, #12]
    str r6, [sp]
    mvn r1, #0
    str r1, [sp, #4]
    str r6, [sp, #0x10]
    add r1, sp, #0x40
    str r1, [sp, #0x14]
    str r0, [sp, #0x18]
    str r0, [sp, #0x1c]
    str r0, [sp, #0x20]
    str r0, [sp, #0x24]
    str r0, [sp, #0x28]
    mov r0, r4
    mov r1, r7
    mov r2, r8
    mov r3, #0
    bl GIVE
    ldr r12, [sp, #0x30]
    cmp r12, #0
    strne r0, [r12, #28]
    movwne r0, #0x5a02
    strne r0, [r12]
out:
    add sp, sp, #0x44
    pop {r4-r11, pc}
