import sys,os; sys.path.insert(0,'/workspace/work/yellow')
exec(open('load.py').read().split("M=np.array")[0].replace("print(","(lambda *a:None)("))
import numpy as np
from skm import parse
from tripo import qmat
from build_mesh2 import lod_payload, build
from tex2 import write_tex
import tex2
from PIL import Image
D='/workspace/work/pilot/orig/PUBGM/Content/Avatar/Common/Overall/30323000002/'
O='/workspace/work/naiwa/mod/PUBGM/Content/Avatar/Common/Overall/30323000002/'
tex2.O=O; os.makedirs(O,exist_ok=True)
def gw(f):
    m,_,_=parse(D+f+'.uasset',D+f+'.uexp',False); out=[]
    for i,(n,p) in enumerate(m['bones']):
        q=m['poses'][i][0:4]; t=m['poses'][i][4:7]; s=m['poses'][i][7:10]
        T=np.eye(4); T[:3,:3]=qmat(q)*np.array(s); T[:3,3]=t
        out.append(out[p]@T if p>=0 else T)
    return np.array(out)[:,:3,3]
M3=np.array([[1,0,0],[0,0,1],[0,1,0]],float)
Pg=P.astype(np.float64)@M3; Ng=N.astype(np.float64)@M3; Ng/=np.linalg.norm(Ng,axis=1,keepdims=True)+1e-9
gi=np.array([GN.index(n) for n in jn])
W=np.zeros((len(Pg),len(GN)))
for k in range(4): np.add.at(W,(np.arange(len(Pg)),gi[J[:,k].astype(int)]),Wt[:,k])
W/=W.sum(1,keepdims=True)
male=gw('Male_Mesh')
print('male vs gskel', np.abs(male-GW[:,:3,3]).max())
def variant(ref):
    off=(ref-male)            # per-bone translation retarget
    Q=Pg+W@off
    return lod_payload(dict(pos=Q,idx=F.astype(np.int64),uv=UV.astype(np.float64),nrm=Ng,W=W))
for sex in ('Male','Female'):
    pay=variant(gw(sex+'_Mesh'))
    print(sex,'n',pay['n'],'tri',pay['ntri'],'bones',len(pay['bonemap']),'maxinf',pay['maxinf'],'z',pay['pos'][:,2].min().round(1),pay['pos'][:,2].max().round(1))
    for suf in ('_Mesh','_Mesh_Cut','_Mesh_UI','_Mesh_UI_Cut'):
        nm=sex+suf; build(D+nm+'.uasset',D+nm+'.uexp',[pay],O+nm+'.uasset',O+nm+'.uexp')
im=g.images[0]; bv=g.bufferViews[im.source if False else 0] if False else None
import io
imgi=g.textures[g.materials[0].pbrMetallicRoughness.baseColorTexture.index].source
bv=g.bufferViews[g.images[imgi].bufferView]
col=Image.open(io.BytesIO(blob[bv.byteOffset or 0:(bv.byteOffset or 0)+bv.byteLength])).convert('RGB'); print('tex',col.size)
col.save('col.png')
for t in ('M_Clothes_D','M_Trousers_D'): write_tex(t,col)
for t in ('cloth_N','Trousers_N'): write_tex(t,Image.new('RGB',(64,64),(128,128,255)))
for t in ('cloth_MRO','Trousers_MRO'): write_tex(t,Image.new('RGB',(64,64),(0,140,255)))
