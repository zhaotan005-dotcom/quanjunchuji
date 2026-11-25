import struct, numpy as np, etcpak, os, shutil
from PIL import Image
D='orig/PUBGM/Content/Avatar/Common/WholeBody/30325000000/'
O='mod/PUBGM/Content/Avatar/Common/WholeBody/30325000000/'
def mips_layout(uexp):
    b=open(uexp,'rb').read()
    i=b.index(b'PF_ETC1\x00')+8
    first=struct.unpack_from('<i',b,i)[0]; i+=4
    n=struct.unpack_from('<i',b,i)[0]; i+=4
    out=[]
    for k in range(n):
        ck=struct.unpack_from('<i',b,i)[0]; i+=4
        fl,cnt,sz,off=struct.unpack_from('<IiiQ',b,i); i+=20
        dpos=None
        if not (fl & 0x100):
            dpos=i; i+=sz
        sx,sy=struct.unpack_from('<ii',b,i); i+=8
        out.append((fl,sz,dpos,sx,sy,off))
    return b,out
def etc1(img,w,h):
    a=np.asarray(img.convert('RGBA').resize((w,h),Image.LANCZOS)).copy()
    pass
    bw,bh=max(w,4),max(h,4)
    if (bw,bh)!=(w,h):
        a=np.asarray(Image.fromarray(a).resize((bw,bh)))
    return etcpak.compress_etc1_rgb(a.tobytes(),bw,bh)
def write_tex(name,img):
    b,ml=mips_layout(D+name+'.uexp'); b=bytearray(b)
    bulk=bytearray()
    for fl,sz,dpos,sx,sy,off in ml:
        data=etc1(img,sx,sy)
        assert len(data)==sz,(name,sx,len(data),sz)
        if dpos is None: bulk+=data
        else: b[dpos:dpos+sz]=data
    ob=open(D+name+'.ubulk','rb').read(); assert len(bulk)==len(ob),(len(bulk),len(ob))
    os.makedirs(O,exist_ok=True)
    open(O+name+'.uexp','wb').write(b); open(O+name+'.ubulk','wb').write(bulk)
    shutil.copy(D+name+'.uasset',O+name+'.uasset')
    print(name,'ok',[(m[3],m[0]) for m in ml])
if __name__=='__main__':
    from tripo import image
    col=image(1).convert('RGB')
    nrm=image(0).convert('RGB'); a=np.asarray(nrm).copy(); a[:,:,1]=255-a[:,:,1]; nrm=Image.fromarray(a)
    for n in ['M_Comb_D_01','M_Comb_D_01_Low','M_Comb_D_0303']: write_tex(n,col)
    for n in ['M_Comb_N_01','M_Comb_N_01_0303']: write_tex(n,nrm)
