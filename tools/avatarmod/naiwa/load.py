import sys; sys.path.insert(0,'/workspace/work/yellow')
import numpy as np, pygltflib, io
from PIL import Image
from gskel import GW,GN
g=pygltflib.GLTF2().load('/workspace/work/yellow/x/YellowChar.glb'); blob=g.binary_blob()
def acc(i):
    a=g.accessors[i]; bv=g.bufferViews[a.bufferView]
    dt={5126:np.float32,5123:np.uint16,5125:np.uint32,5121:np.uint8}[a.componentType]
    nc={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a.type]
    o=(bv.byteOffset or 0)+(a.byteOffset or 0); st=bv.byteStride
    if st and st!=nc*np.dtype(dt).itemsize:
        raw=np.frombuffer(blob,np.uint8,a.count*st,o).reshape(a.count,st)[:,:nc*np.dtype(dt).itemsize]
        return raw.copy().view(dt).reshape(a.count,nc)
    return np.frombuffer(blob,dt,a.count*nc,o).reshape(a.count,nc).copy()
p=g.meshes[0].primitives[0]; at=p.attributes
P=acc(at.POSITION); N=acc(at.NORMAL); UV=acc(at.TEXCOORD_0); J=acc(at.JOINTS_0); Wt=acc(at.WEIGHTS_0); F=acc(p.indices).reshape(-1,3)
sk=g.skins[0]; jn=[g.nodes[j].name for j in sk.joints]
IB=acc(sk.inverseBindMatrices).reshape(-1,4,4).transpose(0,2,1)
JW=np.linalg.inv(IB)[:,:3,3]
# mesh node transform
mn=[i for i,n in enumerate(g.nodes) if n.mesh==0][0]; print('mesh node',g.nodes[mn].matrix,g.nodes[mn].translation,g.nodes[mn].rotation,g.nodes[mn].scale)
G=np.array([GW[GN.index(n)][:3,3] for n in jn])
X=np.c_[JW,np.ones(len(JW))]; A,res,_,_=np.linalg.lstsq(X,G,rcond=None)
err=np.abs(X@A-G).max(); print('fit err cm',err); print(A.round(4))
print('verts',len(P),'tris',len(F),'uv range',UV.min(0),UV.max(0))
M=np.array([[1,0,0],[0,0,1],[0,1,0]],float)
Pg=P@M; Ng=N@M
hb=GW[GN.index('b_Head')][:3,3]; nk=GW[GN.index('b_Neck')][:3,3]
print('head bone',hb.round(1),'neck',nk.round(1),'zrange',Pg[:,2].min(),Pg[:,2].max())
for z in range(int(nk[2])-5,int(Pg[:,2].max()),5):
    s=Pg[np.abs(Pg[:,2]-z)<2.5]
    if len(s): 
        c=s[:,:2]-hb[:2]; print(z,'minR',np.linalg.norm(c,axis=1).min().round(1),'x',s[:,0].min().round(1),s[:,0].max().round(1),'y',s[:,1].min().round(1),s[:,1].max().round(1))
print('mat',g.materials[p.material])
