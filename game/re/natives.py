from ue import *
import json
DS,DE=0x4a2e528,0x4a2e528+0x6fcbf8
TS,TE=0x8aeb18,0x8aeb18+0x378b814
RS,RE=0x4299530,0x4a2d528
fo=v2f(DS); n=(0x6fcbf8)//4
W=struct.unpack_from('<%dI'%n,D,fo)
res=[]
for i in range(n-2):
    a,b,c=W[i],W[i+1],W[i+2]
    if RS<=a<RE and TS<=b<TE and c==0 and b%4==0:
        nm=cstr(a,80)
        if nm and nm.isidentifier():
            res.append((DS+4*i,nm,b))
# resolve impl: thunk body; find last 'b' or 'bl' to text
def impl(t):
    ins=list(mda.disasm(D[v2f(t):v2f(t)+0x400],t))
    calls=[]
    for x in ins[:200]:
        if x.mnemonic in ('b','bl','blx') and x.op_str.startswith('#'):
            calls.append(int(x.op_str[1:],16))
        if x.mnemonic=='b' and x.op_str.startswith('#'): break
        if x.mnemonic=='pop' and 'pc' in x.op_str: break
        if x.mnemonic=='bx' and x.op_str=='lr': break
    return calls
out=[]
for loc,nm,t in res:
    out.append(dict(loc=hex(loc),name=nm,thunk=hex(t),calls=[hex(c) for c in impl(t)]))
json.dump(out,open('natives.json','w'),indent=0)
print(len(out))
