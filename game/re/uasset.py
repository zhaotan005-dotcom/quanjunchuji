# Minimal UE4.18 cooked package reader (summary/name/import/export + tagged properties). Read-only.
import struct,sys
class R:
    def __init__(s,b,o=0): s.b=b; s.o=o
    def i32(s): v=struct.unpack_from('<i',s.b,s.o)[0]; s.o+=4; return v
    def u32(s): v=struct.unpack_from('<I',s.b,s.o)[0]; s.o+=4; return v
    def i64(s): v=struct.unpack_from('<q',s.b,s.o)[0]; s.o+=8; return v
    def f32(s): v=struct.unpack_from('<f',s.b,s.o)[0]; s.o+=4; return v
    def u8(s): v=s.b[s.o]; s.o+=1; return v
    def fstr(s):
        n=s.i32()
        if n==0: return ''
        if n<0:
            n=-n; v=s.b[s.o:s.o+2*n].decode('utf-16le','replace').rstrip('\0'); s.o+=2*n; return v
        v=s.b[s.o:s.o+n].decode('latin1').rstrip('\0'); s.o+=n; return v
    def guid(s): v=s.b[s.o:s.o+16]; s.o+=16; return v
class Pkg:
    def __init__(s,path):
        s.fpath=path
        s.h=open(path,'rb').read()
        try: s.x=open(path[:-len(path.split('.')[-1])]+'uexp','rb').read()
        except: s.x=b''
        r=R(s.h)
        tag=r.u32(); assert tag==0x9E2A83C1
        lv=r.i32(); s.lv=lv
        if lv!=-4: r.i32()
        s.ue4ver=r.i32() or 513; r.i32()
        if lv<=-2:
            n=r.i32()
            for _ in range(n): r.guid(); r.i32()
        s.total=r.i32(); r.fstr(); s.flags=r.u32()
        s.nc=r.i32(); s.no=r.i32()
        if s.ue4ver>=459: r.i32(); r.i32()  # gatherable text
        s.ec=r.i32(); s.eo=r.i32(); s.ic=r.i32(); s.io=r.i32()
        s.do=r.i32()
        # names
        r2=R(s.h,s.no); s.names=[]
        for _ in range(s.nc):
            s.names.append(r2.fstr())
            if s.ue4ver>=504: r2.u32()
        r2=R(s.h,s.io); s.imports=[]
        for _ in range(s.ic):
            cp=s.nm(r2); cn=s.nm(r2); outer=r2.i32(); on=s.nm(r2)
            s.imports.append(dict(cpkg=cp,cls=cn,outer=outer,name=on))
        r2=R(s.h,s.eo); s.exports=[]
        for _ in range(s.ec):
            e=dict(cls=r2.i32(),sup=r2.i32(),tmpl=r2.i32(),outer=r2.i32(),name=s.nm(r2),flags=r2.u32(),size=r2.i64(),off=r2.i64())
            r2.o+=104-4*4-8-4-16
            s.exports.append(e)
    def nm(s,r):
        i=r.i32(); n=r.i32()
        v=s.names[i] if 0<=i<len(s.names) else '?%d'%i
        return v if n==0 else '%s_%d'%(v,n-1)
    def ref(s,i):
        if i==0: return 'None'
        if i<0:
            im=s.imports[-i-1]; return im['name']
        return s.exports[i-1]['name']
    def path(s,i):
        out=[]
        while i!=0:
            if i<0: im=s.imports[-i-1]; out.append(im['name']); i=im['outer']
            else: e=s.exports[i-1]; out.append(e['name']); i=e['outer']
        return '.'.join(reversed(out))
    def data(s,e):
        o=e['off']-s.total
        return s.x[o:o+e['size']] if o>=0 else s.h[e['off']:e['off']+e['size']]
    def props(s,b,o=0,end=None,depth=0):
        r=R(b,o); out=[]; end=end or len(b)
        while r.o<end:
            name=s.nm(r)
            if name=='None': break
            typ=s.nm(r); size=r.i32(); idx=r.i32()
            extra=None
            if typ=='StructProperty': extra=s.nm(r); r.guid()
            elif typ=='BoolProperty': extra=r.u8()
            elif typ in('ByteProperty','EnumProperty'): extra=s.nm(r)
            elif typ=='ArrayProperty': extra=s.nm(r)
            elif typ=='MapProperty': extra=(s.nm(r),s.nm(r))
            elif typ=='SetProperty': extra=s.nm(r)
            hg=r.u8()
            if hg: r.guid()
            vs=r.o
            val=s.val(b,vs,size,typ,extra,depth)
            out.append((name,idx,typ,extra,val))
            r.o=vs+size
        return out,r.o
    def val(s,b,o,size,typ,extra,depth):
        r=R(b,o)
        try:
            if typ=='IntProperty': return r.i32()
            if typ=='FloatProperty': return r.f32()
            if typ=='BoolProperty': return bool(extra)
            if typ=='ObjectProperty' or typ=='ClassProperty' or typ=='WeakObjectProperty': i=r.i32(); return s.path(i) if i else None
            if typ=='SoftObjectProperty' or typ=='AssetObjectProperty': return (s.nm(r),r.fstr())
            if typ=='NameProperty': return s.nm(r)
            if typ=='StrProperty': return r.fstr()
            if typ in('ByteProperty','EnumProperty'):
                if size==1: return r.u8()
                return s.nm(r)
            if typ=='StructProperty':
                if extra=='Vector': return struct.unpack_from('<3f',b,o)
                if extra=='Rotator': return struct.unpack_from('<3f',b,o)
                if extra=='Vector2D': return struct.unpack_from('<2f',b,o)
                if extra=='Quat': return struct.unpack_from('<4f',b,o)
                if extra=='Guid': return b[o:o+16].hex()
                if extra=='LinearColor': return struct.unpack_from('<4f',b,o)
                if extra=='Color': return tuple(b[o:o+4])
                if extra=='IntPoint': return struct.unpack_from('<2i',b,o)
                if extra=='Box': return struct.unpack_from('<6fB',b,o)
                p,_=s.props(b,o,o+size,depth+1); return p
            if typ=='ArrayProperty':
                n=r.i32(); items=[]
                if extra=='StructProperty':
                    nm=s.nm(r); t=s.nm(r); sz=r.i32(); ix=r.i32(); sn=s.nm(r); r.guid(); r.u8()
                    st=r.o
                    for k in range(n):
                        if sn=='Vector': items.append(struct.unpack_from('<3f',b,r.o)); r.o+=12; continue
                        p,no=s.props(b,r.o,st+sz,depth+1); items.append(p); r.o=no
                    return ('struct:'+sn,items)
                for k in range(n):
                    if extra=='ObjectProperty': i=r.i32(); items.append(s.path(i) if i else None)
                    elif extra=='IntProperty': items.append(r.i32())
                    elif extra=='FloatProperty': items.append(r.f32())
                    elif extra=='NameProperty': items.append(s.nm(r))
                    elif extra=='ByteProperty': items.append(r.u8())
                    elif extra=='StrProperty': items.append(r.fstr())
                    elif extra=='SoftObjectProperty': items.append((s.nm(r),r.fstr()))
                    else: items.append('?'); break
                return items
        except Exception as ex:
            return 'ERR %s'%ex
        return 'raw:'+b[o:o+min(size,48)].hex()
def show(p,depth=0):
    for name,idx,typ,extra,val in p:
        pre='  '*depth+('%s[%d]'%(name,idx) if idx else name)
        if isinstance(val,list) and val and isinstance(val[0],tuple) and len(val[0])==5:
            print(pre,typ,extra); show(val,depth+1)
        elif isinstance(val,tuple) and len(val)==2 and isinstance(val[0],str) and val[0].startswith('struct:'):
            print(pre,'Array<%s> n=%d'%(val[0][7:],len(val[1])))
            for k,it in enumerate(val[1]):
                if isinstance(it,list): print('  '*(depth+1)+'[%d]'%k); show(it,depth+2)
                else: print('  '*(depth+1)+'[%d] %s'%(k,it))
        else: print(pre,'=',val, '(%s)'%typ if typ not in('IntProperty','FloatProperty','ObjectProperty') else '')
if __name__=='__main__':
    p=Pkg(sys.argv[1])
    flt=sys.argv[2] if len(sys.argv)>2 else None
    if flt=='-imports':
        for k,im in enumerate(p.imports): print(-k-1,im['cpkg'],im['cls'],p.path(-k-1))
        sys.exit()
    if flt=='-names':
        print('\n'.join(p.names)); sys.exit()
    for k,e in enumerate(p.exports):
        nm=e['name']
        if flt and flt.lower() not in nm.lower() and flt.lower() not in p.ref(e['cls']).lower(): continue
        print('== export %d %s cls=%s outer=%s size=%d'%(k+1,nm,p.ref(e['cls']),p.ref(e['outer']),e['size']))
        if '-noprops' in sys.argv: continue
        b=p.data(e)
        try:
            pr,_=p.props(b); show(pr,1)
        except Exception as ex: print('  ERR',ex)
