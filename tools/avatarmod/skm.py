import sys,struct; from ue import *
import numpy as np
def tarr(r,fn):
    n=r.i32(); return [fn() for _ in range(n)]
def bulk(r):
    es=r.i32(); n=r.i32(); st=r.o; r.o+=es*n; return es,n,st
def parse(uasset,uexp,verbose=True):
    d=summary(open(uasset,'rb').read()); names=d['names']
    b=open(uexp,'rb').read(); r=R(b)
    import props
    P=print if verbose else (lambda *a:None)
    m={}
    # props
    import io,contextlib
    with contextlib.redirect_stdout(io.StringIO()): props.readtags(r,names)
    m['props_end']=r.o
    r.i32() # guid flag
    m['strip']=r.raw(2)
    m['bounds_off']=r.o; m['bounds']=[r.f() for _ in range(7)]
    P('bounds',m['bounds'])
    nm=r.i32(); r.o+=nm*36
    m['skel_off']=r.o
    bones=[]
    for _ in range(r.i32()):
        n=names[r.i32()]; r.i32(); p=r.i32(); bones.append((n,p))
    m['bones']=bones
    poses=[]
    for _ in range(r.i32()): poses.append(struct.unpack_from('<10f',b,r.o)); r.o+=40
    m['poses']=poses
    nmap=r.i32(); r.o+=nmap*12
    m['lods_off']=r.o
    nl=r.i32(); P('LODs',nl,'at',hex(r.o))
    lods=[]
    for li in range(nl):
        L={'start':r.o}
        L['strip']=r.raw(2)
        secs=[]
        for _ in range(r.i32()):
            s={'start':r.o}
            s['strip']=r.raw(2)
            s['mat']=struct.unpack_from('<h',b,r.o)[0]; r.o+=2
            s['baseidx']=r.i32(); s['ntri']=r.i32()
            s['trisort']=r.u8(); s['disabled']=r.i32(); s['clothsec']=struct.unpack_from('<h',b,r.o)[0]; r.o+=2
            s['clothlod']=r.u8(); s['recomp']=r.i32(); s['castshadow']=r.i32()
            s['basevert']=r.u32()
            n=r.i32(); s['bonemap']=list(struct.unpack_from('<%dH'%n,b,r.o)); r.o+=2*n
            s['nverts']=r.i32(); s['maxinf']=r.i32()
            nc=r.i32(); assert nc==0,nc
            assert r.i32()==0 and r.i32()==0
            s['clothasset']=struct.unpack_from('<h',b,r.o)[0]; r.o+=2
            s['clothdata']=r.raw(20)
            s['end']=r.o
            secs.append(s)
        L['secs']=secs
        L['idx_off']=r.o
        L['idx_ds']=r.u8(); L['idx']=bulk(r)
        n=r.i32(); L['active']=list(struct.unpack_from('<%dh'%n,b,r.o)); r.o+=2*n
        L['size']=r.i32(); L['nverts']=r.i32()
        n=r.i32(); L['required']=list(struct.unpack_from('<%dh'%n,b,r.o)); r.o+=2*n
        L['m2i_off']=r.o
        n=r.i32(); r.o+=4*n; L['m2i']=n; L['maximport']=r.i32()
        L['ntc']=r.i32()
        L['vb_off']=r.o
        L['vb_strip']=r.raw(2); L['vb_ntc']=r.i32(); L['vb_fulluv']=r.i32()
        L['vb_ext']=[r.f() for _ in range(3)]; L['vb_org']=[r.f() for _ in range(3)]
        L['verts']=bulk(r)
        L['sw_off']=r.o
        L['sw_strip']=r.raw(2); L['sw_extra']=r.i32(); L['sw_n']=r.i32(); L['sw']=bulk(r)
        L['after_sw']=r.o
        L['tail']=b[r.o:r.o+64].hex(' ')
        lods.append(L)
        # try to continue: color buffer only if bHasVertexColors; adjacency if not stripped
        L['adj_off']=r.o
        if not (L['strip'][1]&1):
            L['adj_ds']=r.u8(); L['adj']=bulk(r)
        L['end']=r.o
    m['lods']=lods
    return m,b,d
if __name__=='__main__':
    m,b,d=parse(sys.argv[1],sys.argv[2])
    for k,v in m.items():
        if k not in('poses',): print(k,v)
