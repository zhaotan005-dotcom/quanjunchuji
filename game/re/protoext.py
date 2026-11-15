# Extract embedded FileDescriptorProto blobs from libUE4 and print .proto text (schemas only, no game assets)
import re, sys
from google.protobuf import descriptor_pb2
exec(open('ue.py').read())
def varint(b,i):
    r=s=0
    while True:
        c=b[i]; i+=1; r|=(c&0x7f)<<s; s+=7
        if c<0x80: return r,i
def walk(b,i):
    st=i
    while i<len(b):
        try: tag,j=varint(b,i)
        except: break
        f,w=tag>>3,tag&7
        if not(1<=f<=12) or w not in (0,2): break
        if w==0: _,j=varint(b,j)
        else:
            l,j=varint(b,j); j+=l
        if j>len(b): break
        i=j
    return i
out={}
for m in re.finditer(rb'\x0a[\x01-\x7f][A-Za-z0-9_/]+\.proto\x12',D):
    s=m.start(); e=walk(D,s)
    fd=descriptor_pb2.FileDescriptorProto()
    try: fd.ParseFromString(D[s:e])
    except Exception as ex: print('fail',m.group(),ex); continue
    out[fd.name]=fd
print(len(out))
import pickle; pickle.dump({k:v.SerializeToString() for k,v in out.items()},open('../proto/fds.pkl','wb'))
