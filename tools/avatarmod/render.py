import numpy as np
from PIL import Image
def render(pos,idx,colors=None,uv=None,tex=None,W=400,H=600,view='front',light=None,nrm=None,bounds=None):
    # pos Nx3 in Z-up, front looks along +Y? we project: front: x->right, z->up ; side: y->right
    if view=='front': P=np.c_[pos[:,0],pos[:,2],-pos[:,1]]
    elif view=='back': P=np.c_[-pos[:,0],pos[:,2],pos[:,1]]
    else: P=np.c_[pos[:,1],pos[:,2],pos[:,0]]
    if bounds is None: lo,hi=P[:,:2].min(0),P[:,:2].max(0)
    else: lo,hi=bounds
    s=min((W-20)/(hi[0]-lo[0]),(H-20)/(hi[1]-lo[1]))
    X=(P[:,0]-lo[0])*s+10; Y=H-((P[:,1]-lo[1])*s+10); Z=P[:,2]
    img=np.full((H,W,3),40,np.uint8); zb=np.full((H,W),-1e9)
    texa=np.asarray(tex.convert('RGB')) if tex is not None else None
    fn=np.cross(pos[idx[:,1]]-pos[idx[:,0]],pos[idx[:,2]]-pos[idx[:,0]]); fn/=np.linalg.norm(fn,axis=1,keepdims=True)+1e-12
    L=np.array([0.3,-0.8,0.5]) if view!='back' else np.array([0.3,0.8,0.5]); L/=np.linalg.norm(L)
    shade=np.clip(np.abs(fn@L),0.25,1)
    for f,(a,b,c) in enumerate(idx):
        xs=np.array([X[a],X[b],X[c]]); ys=np.array([Y[a],Y[b],Y[c]])
        x0,x1=int(max(xs.min(),0)),int(min(xs.max()+1,W)); y0,y1=int(max(ys.min(),0)),int(min(ys.max()+1,H))
        if x0>=x1 or y0>=y1: continue
        gx,gy=np.meshgrid(np.arange(x0,x1)+.5,np.arange(y0,y1)+.5)
        d=(ys[1]-ys[2])*(xs[0]-xs[2])+(xs[2]-xs[1])*(ys[0]-ys[2])
        if abs(d)<1e-9: continue
        l0=((ys[1]-ys[2])*(gx-xs[2])+(xs[2]-xs[1])*(gy-ys[2]))/d
        l1=((ys[2]-ys[0])*(gx-xs[2])+(xs[0]-xs[2])*(gy-ys[2]))/d
        l2=1-l0-l1; m=(l0>=0)&(l1>=0)&(l2>=0)
        if not m.any(): continue
        z=l0*Z[a]+l1*Z[b]+l2*Z[c]
        sub=zb[y0:y1,x0:x1]; m&=z>sub
        if not m.any(): continue
        sub[m]=z[m]
        if texa is not None:
            u=l0*uv[a,0]+l1*uv[b,0]+l2*uv[c,0]; v=l0*uv[a,1]+l1*uv[b,1]+l2*uv[c,1]
            th,tw=texa.shape[:2]
            col=texa[np.clip((v*th).astype(int),0,th-1),np.clip((u*tw).astype(int),0,tw-1)]
        elif colors is not None:
            col=(l0[...,None]*colors[a]+l1[...,None]*colors[b]+l2[...,None]*colors[c])
        else: col=np.full(gx.shape+(3,),200.)
        img[y0:y1,x0:x1][m]=np.clip(col[m]*shade[f],0,255).astype(np.uint8)
    return Image.fromarray(img)
