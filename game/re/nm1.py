# all sites: bl GetNetMode(0x274dbb0) followed within 3 insns by cmp r0,#1 (or #2/#3)
import sys,numpy as np,pickle
exec(open('ue.py').read())
TS,TE=0x8aeb18,0x8aeb18+0x378b814
W=np.frombuffer(D[TS:TE],dtype='<u4')
tgt=0x274dbb0
idx=np.nonzero((W&0x0f000000)==0x0b000000)[0]
res=[]
XRr={}
for t,l in XR.items():
    for a,k in l: XRr.setdefault(func_of(a)[0],[]).append(t)
def srcfile(f):
    for t in XRr.get(f,[]):
        s=wstr(t) if 'wstr' in globals() else None
        if s is None:
            fo=v2f(t); 
            if fo is None: continue
            e=fo
            while e<fo+800 and D[e:e+4]!=b'\0\0\0\0': e+=4
            try: s=D[fo:e].decode('utf-32le')
            except: s=None
        if s and '.cpp' in s: return s.split('\\')[-1]
    return '?'
for i in idx:
    w=int(W[i]); a=TS+4*int(i)
    off=w&0xffffff
    if off&0x800000: off-=0x1000000
    if a+8+off*4!=tgt: continue
    # look at next 4 insns for cmp r0,#imm
    for k in range(1,5):
        x=int(W[i+k])
        if (x&0x0fff0f00)==0x03500000 and ((x>>16)&15)==0:
            res.append((a,x&0xff)); break
out=[]
for a,v in res:
    f=func_of(a)[0]
    out.append((a,v,f,srcfile(f)))
pickle.dump(out,open('/tmp/nm_sites.pkl','wb'))
for a,v,f,s in out: print(hex(a),v,hex(f),s)
