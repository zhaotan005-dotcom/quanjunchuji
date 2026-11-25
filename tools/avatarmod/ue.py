import struct,sys
class R:
    def __init__(s,b,o=0): s.b=b; s.o=o
    def i32(s): v=struct.unpack_from('<i',s.b,s.o)[0]; s.o+=4; return v
    def u32(s): v=struct.unpack_from('<I',s.b,s.o)[0]; s.o+=4; return v
    def i64(s): v=struct.unpack_from('<q',s.b,s.o)[0]; s.o+=8; return v
    def f(s): v=struct.unpack_from('<f',s.b,s.o)[0]; s.o+=4; return v
    def u8(s): v=s.b[s.o]; s.o+=1; return v
    def raw(s,n): v=s.b[s.o:s.o+n]; s.o+=n; return v
    def fstr(s):
        n=s.i32()
        if n==0: return ''
        if n>0: v=s.b[s.o:s.o+n-1].decode('latin1'); s.o+=n; return v
        n=-n; v=s.b[s.o:s.o+2*n-2].decode('utf-16le'); s.o+=2*n; return v
def summary(b):
    r=R(b); d={}
    d['tag']=hex(r.u32()); d['legacy']=r.i32()
    if d['legacy']!=-4: d['ue3']=r.i32()
    d['ue4']=r.i32() or 516; d['lic']=r.i32()
    nc=r.i32(); d['cv']=[(r.raw(16).hex(),r.i32()) for _ in range(nc)]
    d['totalhdr']=r.i32(); d['folder']=r.fstr(); d['flags']=hex(r.u32())
    d['namec']=r.i32(); d['nameo']=r.i32()
    d['gatherc']=r.i32(); d['gathero']=r.i32()
    d['expc']=r.i32(); d['expo']=r.i32(); d['impc']=r.i32(); d['impo']=r.i32()
    d['dependo']=r.i32(); d['softrefc']=r.i32(); d['softrefo']=r.i32()
    d['searchableo']=r.i32(); d['thumbo']=r.i32(); d['guid']=r.raw(16).hex()
    gc=r.i32(); d['gens']=[(r.i32(),r.i32()) for _ in range(gc)]
    d['savedver']=(r.raw(2*3+4), r.fstr()); d['compatver']=(r.raw(10),r.fstr())
    d['compflags']=r.u32(); cc=r.i32(); d['chunks']=cc
    d['pkgsrc']=hex(r.u32()); ac=r.i32(); d['addl']=[r.fstr() for _ in range(ac)]
    d['assetregd']=r.i32(); d['bulko']=r.i64(); d['worldtileo']=r.i32()
    n=r.i32(); d['chunkids']=[r.i32() for _ in range(n)]
    d['preloadc']=r.i32(); d['preloado']=r.i32(); d['end']=r.o
    r.o=d['nameo']; names=[]
    for _ in range(d['namec']):
        names.append(r.fstr())
        if d['ue4']>=504: r.raw(4)
    d['names']=names
    r.o=d['impo']; imps=[]
    for _ in range(d['impc']):
        cp=names[r.i32()];r.i32(); cn=names[r.i32()];r.i32(); outer=r.i32(); on=names[r.i32()];onn=r.i32()
        imps.append((cp,cn,outer,on))
    d['imps']=imps
    r.o=d['expo']; exps=[]
    for _ in range(d['expc']):
        e={}
        e['cls']=r.i32(); e['super']=r.i32(); e['tmpl']=r.i32() if d['ue4']>=508 else None
        e['outer']=r.i32(); e['name']=names[r.i32()]; r.i32(); e['oflags']=hex(r.u32())
        e['size']=r.i64(); e['off']=r.i64()
        e['rest']=r.raw(4*5+16+4+4*5).hex()
        exps.append(e)
    d['exps']=exps
    return d
if __name__=='__main__':
    d=summary(open(sys.argv[1],'rb').read())
    for k,v in d.items():
        if k not in('names',): print(k,v)
    print(len(d['names']),'names')
