# v3.21 核对单挑自动选场地用到的原生偏移（capstone + 反射参数表），跑：python3 trace_re.py
exec(open('ue.py').read())
def ins(a,n): return [(hex(i.address),i.mnemonic,i.op_str) for i in dis(a,n,False)]
# 1. ProcessEvent = UObject 虚表 +0x100（RPC 包装 0xe66908：ldr r5,[vt,#0x100]; bl FindFunctionChecked 0x1bd3414; blx r5(this, func, &parms)）
w=ins(0xe66908,16); print(w[7],w[8],w[15]); assert w[10][2]=='r5, [r1, #0x100]' and w[11][2]=='#0x1bd3414' and w[15][2]=='r5', w
# 2. LineTraceSingle FFunctionParams 0x4fd05c4：名字、参数块大小 0xe8、属性 14 个
fp=0x4fd05c4; assert cstr(u32v(fp))=='LineTraceSingle'; assert u32v(fp+0x10)==0xe8 and u32v(fp+0x14)==0x4fd0588 and u32v(fp+0x18)==14
want={'WorldContextObject':0,'Start':4,'End':0x10,'TraceChannel':0x1c,'ActorsToIgnore':0x20,'DrawDebugType':0x2c,'OutHit':0x30,'TraceColor':0xbc,'TraceHitColor':0xcc,'DrawTime':0xdc}
got={}
for i in range(14):
    p=u32v(0x4fd0588+4*i); nm=cstr(u32v(p+4))
    if u32v(p)==0xc:   # bool：偏移看 setter
        st=u32v(p+0x2c); d=list(dis(st,2,False)); got[nm]=d[1].op_str
    else: got.setdefault(nm,u32v(p+0x20))
print(got)
for k,v in want.items(): assert got[k]==v,(k,got[k])
assert got['bTraceComplex'].endswith('#0x1d]') and got['bIgnoreSelf'].endswith('#0xb8]') and got['ReturnValue'].endswith('#0xe0]')
# 3. FHitResult：ImpactPoint +0x18、Actor(弱指针) +0x64、TraceStart +0x3c（属性表 0x4f8d0xx~0x4f8d3xx 同一组）
for r,o in [(0x4f8d324,0x18),(0x4f8d1e4,0x64),(0x4f8d2ac,0x3c),(0x4f8d16c,0x80)]:
    assert u32v(r-4+0x20)==o, hex(r)
print('FHitResult ok')
# 4. SetGamePhase 0xffc1d8（NZBRGameState.cpp，r0=GameState、r1=阶段）；入口 +0xc 是不上飞机钩子
d=ins(0xffc1d8,6); print(d); assert d[0][2]=='{r4, r5, r6, r7, fp, lr}' and d[5][2]=='r5, r1'
lit=u32v(0xffc294+8+0x1d0); assert 'NZBRGameState.cpp' in cstr((0xffc2a8+8+lit)&0xffffffff)
print('all ok')
