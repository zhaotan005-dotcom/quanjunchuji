import struct, re, capstone, bisect
P='/workspace/work/qj2/game/x/lib/armeabi-v7a/libUE4.so'
D=open(P,'rb').read()
SEGS=[(0x0,0x0,0x4a2d528),(0x4a2d528,0x4a2e528,0x6fcbf8),(0x512a324,0x53d4324,0x12c8),(0x512b5ec,0x53d65ec,0x3fbc),(0x512fe50,0x53dbe50,0x7bc)]
def v2f(v):
    for fo,va,sz in SEGS:
        if va<=v<va+sz: return v-va+fo
    return None
def f2v(f):
    for fo,va,sz in SEGS:
        if fo<=f<fo+sz: return f-fo+va
    return None
def u32v(v): return struct.unpack_from('<I',D,v2f(v))[0]
def cstr(v,n=200):
    f=v2f(v); 
    if f is None: return None
    e=D.find(b'\0',f,f+n); 
    if e<0: return None
    try: return D[f:e].decode()
    except: return None
def findstr(s):
    b=s.encode()+b'\0'; out=[]; i=D.find(b)
    while i>=0:
        if i==0 or D[i-1]==0: out.append(f2v(i))
        i=D.find(b,i+1)
    return out
def wfindstr(s):  # UTF-16 TCHAR (android TCHAR is char16)
    b=s.encode('utf-16le')+b'\0\0'; out=[]; i=D.find(b)
    while i>=0:
        out.append(f2v(i)); i=D.find(b,i+1)
    return out
def ptrrefs(v,start=0x4a2d528):
    b=struct.pack('<I',v); out=[]; i=D.find(b,start)
    while i>=0:
        if i%4==0: out.append(f2v(i))
        i=D.find(b,i+1)
    return out
md=capstone.Cs(capstone.CS_ARCH_ARM,capstone.CS_MODE_THUMB); md.detail=True
mda=capstone.Cs(capstone.CS_ARCH_ARM,capstone.CS_MODE_ARM); mda.detail=True
def dis(v,n=60,thumb=True,stopret=False):
    v&=~1; f=v2f(v); m=md if thumb else mda
    out=[]
    for ins in m.disasm(D[f:f+n*4],v):
        out.append(ins)
        if len(out)>=n: break
    return out
def pd(v,n=60,thumb=True):
    for i in dis(v,n,thumb): print('%08x: %-12s %s %s'%(i.address,i.bytes.hex(),i.mnemonic,i.op_str))
def _exidx():
    s=0x419b120; n=0xfe410//8; out=[]
    for i in range(n):
        a=s+8*i; x=struct.unpack_from('<I',D,a)[0]
        off=x&0x7fffffff
        if off&0x40000000: off-=0x80000000
        out.append((a+off)&0xffffffff)
    return sorted(set(out))
FUNCS=_exidx()
def func_of(a):
    i=bisect.bisect_right(FUNCS,a)-1; return FUNCS[i], (FUNCS[i+1] if i+1<len(FUNCS) else a+4)
def disf(start,limit=4000):
    s,e=func_of(start); e=min(e,s+limit*4)
    return list(mda.disasm(D[v2f(s):v2f(e)],s))
def pf(a,limit=4000):
    for i in disf(a,limit): print('%08x: %-9s %s %s'%(i.address,i.bytes.hex(),i.mnemonic,i.op_str))
import pickle as _p
XR=_p.load(open('/workspace/work/qj2/game/re/xr.pkl','rb'))
_REV={}
for t,l in XR.items():
    for a,k in l: _REV[a]=t
def strat(t):
    s=cstr(t,120)
    if s and len(s)>=3 and s.isprintable(): return s
    # utf32
    f=v2f(t)
    if f is None: return None
    out=''
    for k in range(120):
        c=struct.unpack_from('<I',D,f+4*k)[0]
        if c==0: break
        if not (32<=c<127): return None
        out+=chr(c)
    return 'L"'+out+'"' if len(out)>=3 else None
def fstrings(fa):
    s,e=func_of(fa); out=[]
    for a in range(s,e,4):
        if a in _REV:
            t=_REV[a]; out.append((a,t,strat(t)))
    return out
def wfind32(s):
    b=b''.join(struct.pack('<I',ord(c)) for c in s)+b'\0\0\0\0'; out=[]; i=D.find(b)
    while i>=0:
        if i%4==0: out.append(f2v(i))
        i=D.find(b,i+1)
    return out
CALLS=_p.load(open('/workspace/work/qj2/game/re/calls.pkl','rb'))
def callers(t):
    s0,e0=func_of(t)
    return sorted(set(func_of(a)[0] for a in CALLS.get(t,[]) if not (s0<=a<e0)))
def callsites(t):
    s0,e0=func_of(t); return [a for a in CALLS.get(t,[]) if not (s0<=a<e0)]
def bls(fa):
    out=[]
    for i in disf(fa):
        if i.mnemonic in('bl','b','blx') and i.op_str.startswith('#'):
            t=int(i.op_str[1:],16); s,e=func_of(fa)
            if not(s<=t<e): out.append((i.address,i.mnemonic,t))
        if i.mnemonic.startswith('blx') and not i.op_str.startswith('#'): out.append((i.address,'blx',i.op_str))
    return out
def fsize(a): s,e=func_of(a); return e-s
def veneer(a):
    w0=u32v(a); w1=u32v(a+4)
    if w0==0xe59fc000 and w1==0xe08fc00c or w1==0xe08ff00c:
        return (a+12+u32v(a+8))&0xffffffff
    return None
NMF={0xca2c08,0x1097658,0x2e65f00}
def info(fa):
    s,e=func_of(fa)
    ss=[x[2] for x in fstrings(s) if x[2] and not x[2].startswith('D:')]
    nm=any(x in NMF for a,m,x in bls(s) if isinstance(x,int))
    return '%x[%x] nm=%s %s'%(s,e-s,nm,[q[:70] for q in ss[:3]])
def strfuncs(v):
    return sorted(set(func_of(a)[0] for a,k in XR.get(v,[])))
def classname(sc):
    # find utf32 string ref in StaticClass function (GetPrivateStaticClass)
    s,e=func_of(sc)
    seen=[sc]
    for a,m,x in bls(sc):
        if isinstance(x,int): seen.append(x)
    for f in seen:
        for a,t,st in fstrings(f):
            if st and st.startswith('L"') and not st.startswith('L"/Script'): return st
    return None
