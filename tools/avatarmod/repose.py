import numpy as np
from tripo import *
from gskel import GW,GN,GP
G={n:GW[i][:3,3] for i,n in enumerate(GN)}
MAP=np.array([[0,0,-1],[1,0,0],[0,1,0]],float)
TJ={n:JW[i][:3,3] for i,n in enumerate(jname)}
S=G['b_Hips'][2]/TJ['Hip'][1]
def tg(p): return (MAP@np.asarray(p).T).T*S
T={n:tg(v) for n,v in TJ.items()}
J2G={'Root':'b_Hips','Hip':'b_Hips','Pelvis':'b_Hips','Waist':'b_Hips','Spine01':'b_Spine','Spine02':'b_Spine1',
 'NeckTwist01':'b_Neck','NeckTwist02':'b_Neck','Head':'b_Head'}
for side,gs in (('L','Left'),('R','Right')):
    J2G.update({f'{side}_Clavicle':f'b_{gs}Clav',f'{side}_Upperarm':f'b_{gs}Arm',f'{side}_UpperarmTwist01':f'b_{gs}Arm',f'{side}_UpperarmTwist02':f'b_{gs}Arm',
     f'{side}_Forearm':f'b_{gs}ForeArm',f'{side}_ForearmTwist01':f'b_{gs}ForeArm',f'{side}_ForearmTwist02':f'b_{gs}ForeArmRoll',f'{side}_Hand':f'b_{gs}Hand',
     f'{side}_Thigh':f'b_{gs}LegUpper',f'{side}_ThighTwist01':f'b_{gs}LegUpper',f'{side}_ThighTwist02':f'b_{gs}LegUpper',
     f'{side}_Calf':f'b_{gs}Leg',f'{side}_CalfTwist01':f'b_{gs}Leg',f'{side}_CalfTwist02':f'b_{gs}Leg',f'{side}_Foot':f'b_{gs}Ankle',f'{side}_ToeBase':f'b_{gs}Toe'})
def rot_align(a,b):
    a=a/np.linalg.norm(a); b=b/np.linalg.norm(b); v=np.cross(a,b); c=a@b
    if np.linalg.norm(v)<1e-9: return np.eye(3)
    K=np.array([[0,-v[2],v[1]],[v[2],0,-v[0]],[-v[1],v[0],0]])
    return np.eye(3)+K+K@K*(1/(1+c))
def seg(ts,te,gs,ge,stretch=True):
    a=te-ts; b=ge-gs; u=a/np.linalg.norm(a)
    k=np.linalg.norm(b)/np.linalg.norm(a) if stretch else 1.0
    Sc=np.eye(3)+(k-1)*np.outer(u,u)
    R=rot_align(a,b); M=R@Sc
    return M, gs-M@ts
A={}
def tr(ts,gs,M=np.eye(3)): return M, gs-M@ts
A['b_Hips']=tr(T['Hip'],G['b_Hips'])
A['b_Spine']=seg(T['Spine01'],T['Spine02'],G['b_Spine'],G['b_Spine1'])
A['b_Spine1']=seg(T['Spine02'],T['NeckTwist01'],G['b_Spine1'],G['b_Neck'])
A['b_Neck']=seg(T['NeckTwist01'],T['Head'],G['b_Neck'],G['b_Head'])
A['b_Head']=tr(T['Head'],G['b_Head'],np.eye(3)*0.85)
for side,gs in (('L','Left'),('R','Right')):
    A[f'b_{gs}Clav']=seg(T[f'{side}_Clavicle'],T[f'{side}_Upperarm'],G[f'b_{gs}Clav'],G[f'b_{gs}Arm'])
    A[f'b_{gs}Arm']=seg(T[f'{side}_Upperarm'],T[f'{side}_Forearm'],G[f'b_{gs}Arm'],G[f'b_{gs}ForeArm'])
    A[f'b_{gs}ForeArm']=seg(T[f'{side}_Forearm'],T[f'{side}_Hand'],G[f'b_{gs}ForeArm'],G[f'b_{gs}Hand'])
    A[f'b_{gs}ForeArmRoll']=A[f'b_{gs}ForeArm']
    fdir=T[f'{side}_Hand']-T[f'{side}_Forearm']
    M,_=seg(T[f'{side}_Hand'],T[f'{side}_Hand']+fdir,G[f'b_{gs}Hand'],G[f'b_{gs}Middle1'],stretch=False)
    A[f'b_{gs}Hand']=tr(T[f'{side}_Hand'],G[f'b_{gs}Hand'],M)
    A[f'b_{gs}LegUpper']=seg(T[f'{side}_Thigh'],T[f'{side}_Calf'],G[f'b_{gs}LegUpper'],G[f'b_{gs}Leg'])
    gl,ga=G[f'b_{gs}Leg'],G[f'b_{gs}Ankle']; tz=T[f'{side}_Foot'][2]
    t=(gl[2]-tz)/(gl[2]-ga[2]); Pf=gl+(ga-gl)*t
    A[f'b_{gs}Leg']=seg(T[f'{side}_Calf'],T[f'{side}_Foot'],gl,Pf)
    A[f'b_{gs}Ankle']=tr(T[f'{side}_Foot'],Pf)
    A[f'b_{gs}Toe']=A[f'b_{gs}Ankle']
def build():
    d=mesh(0)
    P=tg(d['wpos']); N=(MAP@d['wnrm'].T).T
    gidx=np.array([GN.index(J2G[jname[j]]) for j in range(len(jname))])
    nb=len(GN); W=np.zeros((len(P),nb))
    for k in range(4): np.add.at(W,(np.arange(len(P)),gidx[d['j'][:,k]]),d['w'][:,k])
    W/=W.sum(1,keepdims=True)
    out=np.zeros_like(P); outn=np.zeros_like(P)
    for bi in np.nonzero(W.sum(0)>0)[0]:
        M,t=A[GN[bi]]; w=W[:,bi:bi+1]
        out+=w*(P@M.T+t)
        Minv=np.linalg.inv(M).T; outn+=w*(N@Minv.T)
    outn/=np.linalg.norm(outn,axis=1,keepdims=True)+1e-9
    return dict(pos=out,nrm=outn,uv=d['uv'],idx=d['idx'],W=W,tpos=P)
if __name__=='__main__':
    print('scale',S)
    for n in ['Hip','Spine01','Spine02','NeckTwist01','Head','L_Clavicle','L_Upperarm','L_Forearm','L_Hand','L_Thigh','L_Calf','L_Foot','L_ToeBase']: print(n,T[n].round(1))
    r=build(); print('min',r['pos'].min(0),'max',r['pos'].max(0))
    np.savez('reposed.npz',**r)
