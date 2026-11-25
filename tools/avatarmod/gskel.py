import numpy as np
from skm import parse
from tripo import qmat
D='orig/PUBGM/Content/Avatar/Common/WholeBody/30325000000/'
m,_b,_d=parse(D+'Male_Mesh.uasset',D+'Male_Mesh.uexp',False)
bones=m['bones']; poses=m['poses']
GW=[]
for i,(n,p) in enumerate(bones):
    q=poses[i][0:4]; t=poses[i][4:7]; s=poses[i][7:10]
    M=np.eye(4); M[:3,:3]=qmat(q)*np.array(s); M[:3,3]=t
    GW.append(GW[p]@M if p>=0 else M)
GW=np.array(GW); GN=[n for n,_ in bones]; GP=[p for _,p in bones]
