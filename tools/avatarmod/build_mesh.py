import numpy as np, struct, sys
from skm import parse
from gskel import GN,GP
def pk(v): return np.clip(np.round(v*127.5+127.5),0,255).astype(np.uint8)
def lod_payload(d):
    P=d['pos'].astype(np.float64); F=d['idx'].astype(np.int64); UV=d['uv']; N=d['nrm']
    n=len(P)
    # tangents
    e1=P[F[:,1]]-P[F[:,0]]; e2=P[F[:,2]]-P[F[:,0]]
    d1=UV[F[:,1]]-UV[F[:,0]]; d2=UV[F[:,2]]-UV[F[:,0]]
    r=d1[:,0]*d2[:,1]-d2[:,0]*d1[:,1]; r=np.where(np.abs(r)<1e-12,1e-12,r)
    Tf=(e1*d2[:,1:2]-e2*d1[:,1:2])/r[:,None]; Bf=(e2*d1[:,0:1]-e1*d2[:,0:1])/r[:,None]
    T=np.zeros((n,3)); B=np.zeros((n,3))
    for c in range(3): np.add.at(T,F[:,c],Tf); np.add.at(B,F[:,c],Bf)
    T=T-N*np.sum(T*N,1,keepdims=True)
    bad=np.linalg.norm(T,axis=1)<1e-9
    T[bad]=np.cross(N[bad],[0,0,1.0])+1e-6
    T/=np.linalg.norm(T,axis=1,keepdims=True)
    w=np.where(np.sum(np.cross(N,T)*B,1)>=0,1.0,-1.0)
    # weights
    W=d['W']; top=np.argsort(-W,axis=1)[:,:4]; tw=np.take_along_axis(W,top,1)
    tw[tw<0.01]=0; tw/=tw.sum(1,keepdims=True)
    q=np.round(tw*255).astype(int); q[:,0]+=255-q.sum(1)
    used=sorted(set(top[q>0].ravel().tolist()))
    bmap={b:i for i,b in enumerate(used)}
    bi=np.vectorize(lambda b:bmap.get(b,0))(top); bi[q==0]=0
    maxinf=int((q>0).sum(1).max())
    vb=np.zeros((n,24),np.uint8)
    vb[:,0:3]=pk(T); vb[:,3]=128
    vb[:,4:7]=pk(N); vb[:,7]=np.where(w>0,255,0)
    vb[:,8:20]=P.astype('<f4').view(np.uint8).reshape(n,12)
    vb[:,20:24]=UV.astype('<f2').view(np.uint8).reshape(n,4)
    sw=np.zeros((n,8),np.uint8); sw[:,0:4]=bi; sw[:,4:8]=q
    act=set(used)
    for b in used:
        p=GP[b]
        while p>=0: act.add(p); p=GP[p]
    return dict(n=n,ntri=len(F),idx=F.astype('<u2').tobytes(),vb=vb.tobytes(),sw=sw.tobytes(),bonemap=used,maxinf=maxinf,active=sorted(act),pos=P)
def i32(v): return struct.pack('<i',v)
def build(uasset,uexp,lodsrc,out_uasset,out_uexp):
    m,b,d=parse(uasset,uexp,False)
    L0=m['lods']
    out=bytearray(b[:m['bounds_off']])
    P0=lodsrc[0]['pos']; lo,hi=P0.min(0),P0.max(0); org=(lo+hi)/2; ext=(hi-lo)/2; rad=np.linalg.norm(P0-org,axis=1).max()
    out+=struct.pack('<7f',*org,*ext,rad)
    out+=b[m['bounds_off']+28:m['lods_off']]
    out+=i32(len(L0))
    for li,L in enumerate(L0):
        p=lodsrc[min(li,len(lodsrc)-1)]
        s0=L['secs'][0]
        out+=L['strip']; out+=i32(1)
        sec=bytearray(); sec+=s0['strip']; sec+=struct.pack('<h',0); sec+=i32(0)+i32(p['ntri'])
        sec+=b[s0['start']+12:s0['start']+12+20-4]  # trisort..castshadow (16 bytes)
        sec+=i32(0)  # basevert
        sec+=i32(len(p['bonemap']))+struct.pack('<%dH'%len(p['bonemap']),*p['bonemap'])
        sec+=i32(p['n'])+i32(p['maxinf'])
        sec+=i32(0)+i32(0)+i32(0)
        sec+=b[s0['end']-22:s0['end']]  # clothasset + clothdata
        out+=sec
        out+=bytes([2])+i32(2)+i32(p['ntri']*3)+p['idx']
        out+=i32(len(p['active']))+struct.pack('<%dh'%len(p['active']),*p['active'])
        out+=i32(0)+i32(p['n'])
        out+=i32(len(L['required']))+struct.pack('<%dh'%len(L['required']),*L['required'])
        out+=i32(p['n'])+struct.pack('<%di'%p['n'],*range(p['n']))+i32(p['n']-1)
        out+=i32(1)
        out+=L['vb_strip']+i32(1)+i32(0)+struct.pack('<6f',*L['vb_ext'],*L['vb_org'])
        out+=i32(24)+i32(p['n'])+p['vb']
        out+=L['sw_strip']+i32(L['sw_extra'])+i32(p['n'])+i32(8)+i32(p['n'])+p['sw']
        assert L['end']==L['after_sw']
    out+=b[L0[-1]['end']:]
    ua=bytearray(open(uasset,'rb').read())
    from ue import summary
    sd=summary(bytes(ua))
    newsize=len(out)-4
    eo=sd['expo']
    # export entry: cls,super,tmpl,outer,name(2),flags => 7*4=28 then size i64
    struct.pack_into('<q',ua,eo+28,newsize)
    # bulk data start offset: find int64 bulko
    old=sd['bulko']; pos=bytes(ua).find(struct.pack('<q',old))
    struct.pack_into('<q',ua,pos,sd['totalhdr']+newsize)
    open(out_uasset,'wb').write(ua); open(out_uexp,'wb').write(out)
    # verify
    m2,_,_=parse(out_uasset,out_uexp,False)
    print(out_uexp.split('/')[-1],'ok lods',[(len(x['secs']),x['nverts']) for x in m2['lods']],'size',len(out))
if __name__=='__main__':
    import os
    D='orig/PUBGM/Content/Avatar/Common/WholeBody/30325000000/'
    O='mod/PUBGM/Content/Avatar/Common/WholeBody/30325000000/'; os.makedirs(O,exist_ok=True)
    srcs=[lod_payload(dict(np.load(f))) for f in ('lod0.npz','lod1.npz')]
    for p in srcs: print('lod',p['n'],p['ntri'],'bones',len(p['bonemap']),'maxinf',p['maxinf'])
    for nm in ('Male_Mesh','Male_Mesh_Cut'):
        build(D+nm+'.uasset',D+nm+'.uexp',srcs,O+nm+'.uasset',O+nm+'.uexp')
