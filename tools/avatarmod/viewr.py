import numpy as np,sys
from PIL import Image, ImageDraw
from render import render
from tripo import image
from gskel import GW,GN,GP
r=np.load(sys.argv[1] if len(sys.argv)>1 else 'reposed.npz')
tex=image(1)
ims=[]
for v in ['back','side']:
    P=r['pos']
    allp=np.vstack([P,GW[:,:3,3]])
    if v=='back': Q=np.c_[-allp[:,0],allp[:,2]]
    else: Q=np.c_[allp[:,1],allp[:,2]]
    lo,hi=Q.min(0),Q.max(0)
    im=render(P,r['idx'],uv=r['uv'],tex=tex,view=v,bounds=(lo,hi),W=450,H=650)
    s=min((450-20)/(hi[0]-lo[0]),(650-20)/(hi[1]-lo[1]))
    dr=ImageDraw.Draw(im)
    J=GW[:,:3,3]; JQ=np.c_[-J[:,0],J[:,2]] if v=='back' else np.c_[J[:,1],J[:,2]]
    X=(JQ[:,0]-lo[0])*s+10; Y=650-((JQ[:,1]-lo[1])*s+10)
    for i,p in enumerate(GP):
        if p>=0 and not GN[i].startswith('b_IK'): dr.line([X[p],Y[p],X[i],Y[i]],fill=(0,255,0),width=2)
    ims.append(im)
c=Image.new('RGB',(900,650)); [c.paste(x,(i*450,0)) for i,x in enumerate(ims)]; c.save(sys.argv[2] if len(sys.argv)>2 else 'reposed.png')
