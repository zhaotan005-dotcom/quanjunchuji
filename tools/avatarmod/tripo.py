import pygltflib, numpy as np, io
from PIL import Image
GLB='/workspace/projects/be3cea53-7f2e-4f76-9c91-1a5bd87cea0a/files/uploads/eazo-viewer-resource-1791709150991-zm2twrrk4sp-4f28b89fa219.glb'
g=pygltflib.GLTF2().load(GLB); blob=g.binary_blob()
CT={5126:np.float32,5123:np.uint16,5121:np.uint8,5125:np.uint32,5122:np.int16,5120:np.int8}
NC={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}
def acc(i):
    a=g.accessors[i]; bv=g.bufferViews[a.bufferView]
    dt=np.dtype(CT[a.componentType]); n=NC[a.type]
    off=(bv.byteOffset or 0)+(a.byteOffset or 0)
    st=bv.byteStride or dt.itemsize*n
    raw=np.frombuffer(blob,dtype=np.uint8,count=st*(a.count-1)+dt.itemsize*n,offset=off)
    out=np.lib.stride_tricks.as_strided(raw,(a.count,dt.itemsize*n),(st,1)).copy().view(dt).reshape(a.count,n)
    if a.normalized:
        out=out.astype(np.float32)/np.iinfo(dt).max
    return out
def qmat(q):
    x,y,z,w=q
    return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def local(nd):
    if nd.matrix: return np.array(nd.matrix).reshape(4,4).T
    M=np.eye(4)
    R=qmat(nd.rotation) if nd.rotation else np.eye(3)
    S=np.diag(nd.scale) if nd.scale else np.eye(3)
    M[:3,:3]=R@S
    if nd.translation: M[:3,3]=nd.translation
    return M
par={}
for i,nd in enumerate(g.nodes):
    for c in (nd.children or []): par[c]=i
def world(i):
    M=local(g.nodes[i])
    while i in par: i=par[i]; M=local(g.nodes[i])@M
    return M
sk=g.skins[0]; joints=sk.joints
jname=[g.nodes[j].name for j in joints]
IBM=acc(sk.inverseBindMatrices).reshape(-1,4,4).transpose(0,2,1)
JW=np.array([world(j) for j in joints])
skinM=JW@IBM
def mesh(mi):
    p=g.meshes[mi].primitives[0]; A=p.attributes
    d={'pos':acc(A.POSITION),'nrm':acc(A.NORMAL),'idx':acc(p.indices).reshape(-1,3).astype(np.int64),
       'j':acc(A.JOINTS_0).astype(int),'w':acc(A.WEIGHTS_0).astype(np.float32)}
    if A.TEXCOORD_0 is not None: d['uv']=acc(A.TEXCOORD_0)
    if A.COLOR_0 is not None: d['col']=acc(A.COLOR_0)
    # bind-pose world positions
    M=np.einsum('vk,vkab->vab',d['w'],skinM[d['j']])
    P=np.c_[d['pos'],np.ones(len(d['pos']))]
    d['wpos']=np.einsum('vab,vb->va',M,P)[:,:3]
    d['wnrm']=np.einsum('vab,vb->va',M[:,:3,:3],d['nrm'])
    d['wnrm']/=np.linalg.norm(d['wnrm'],axis=1,keepdims=True)+1e-9
    return d
def image(i):
    im=g.images[i]; bv=g.bufferViews[im.bufferView]
    return Image.open(io.BytesIO(blob[bv.byteOffset or 0:(bv.byteOffset or 0)+bv.byteLength]))
