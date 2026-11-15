import sys,struct,re
exec(open('ue.py').read())
def isparam(p):
    f=v2f(p)
    if f is None or f+12>len(D): return False
    w=struct.unpack_from('<4I',D,f)
    return w[2]==0x45 and cstr(w[1]) is not None
def owner(S):
    rs=ptrrefs(S)
    res=[]
    for a in rs:
        st=a
        while isparam(u32v(st-4)): st-=4
        en=a
        while isparam(u32v(en+4)): en+=4
        names=[cstr(u32v(u32v(x)+4)) for x in range(st,en+4,4)]
        # who refs array
        refs=ptrrefs(st)
        ctx=[]
        for r in refs:
            ws=struct.unpack_from('<16I',D,v2f(r)-24)
            strs=[cstr(x,80) for x in ws if v2f(x) is not None and x<0x4a2d528]
            ctx.append((hex(r),[s for s in strs if s]))
        res.append((hex(st),len(names),names,ctx))
    return res
for a in sys.argv[1:]:
    for r in owner(int(a,16)-4): print(r)
