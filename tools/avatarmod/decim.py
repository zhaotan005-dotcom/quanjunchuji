import numpy as np, os, sys
os.environ['LD_LIBRARY_PATH']='/workspace/work/gl/root/usr/lib/aarch64-linux-gnu'
import pymeshlab as pml
from scipy.spatial import cKDTree
r=dict(np.load('reposed.npz'))
def decimate(target):
    ms=pml.MeshSet()
    P=r['pos']; F=r['idx']
    if not os.path.exists('rep.obj'):
        with open('rep.mtl','w') as f: f.write('newmtl m0\nmap_Kd tex.png\n')
        from PIL import Image; Image.new('RGB',(8,8)).save('tex.png')
        with open('rep.obj','w') as f:
            f.write('mtllib rep.mtl\n')
            f.write(''.join('v %.6f %.6f %.6f\n'%tuple(p) for p in P))
            f.write(''.join('vt %.7f %.7f\n'%tuple(t) for t in r['uv']))
            f.write('usemtl m0\n')
            f.write(''.join('f %d/%d %d/%d %d/%d\n'%(a+1,a+1,b+1,b+1,c+1,c+1) for a,b,c in F))
    ms.load_new_mesh('rep.obj')
    ms.meshing_merge_close_vertices(threshold=pml.PercentageValue(0.01))
    ms.meshing_decimation_quadric_edge_collapse_with_texture(targetfacenum=target,qualitythr=0.3,preserveboundary=True,boundaryweight=2,optimalplacement=True,preservenormal=True,planarquadric=True)
    cm=ms.current_mesh()
    V=cm.vertex_matrix(); F=cm.face_matrix(); WT=cm.wedge_tex_coord_matrix().reshape(-1,3,2)
    # split by (v,uv)
    key={}; nv=[];nuv=[];NF=np.zeros_like(F)
    for f in range(len(F)):
        for c in range(3):
            k=(F[f,c],round(WT[f,c,0],5),round(WT[f,c,1],5))
            if k not in key: key[k]=len(nv); nv.append(F[f,c]); nuv.append(WT[f,c])
            NF[f,c]=key[k]
    nv=np.array(nv); pos=V[nv]; uv=np.array(nuv)
    tree=cKDTree(r['pos']); _,near=tree.query(pos)
    W=r['W'][near]; nrm=r['nrm'][near]
    return dict(pos=pos,uv=uv,idx=NF,W=W,nrm=nrm)
if __name__=='__main__':
    for t,name in ((12000,'lod0'),(7000,'lod1')):
        d=decimate(t); print(name,len(d['pos']),'verts',len(d['idx']),'tris')
        np.savez(name+'.npz',**d)
