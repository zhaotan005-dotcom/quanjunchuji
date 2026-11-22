# Scan an UNENCRYPTED-data pak for zlib blocks and stitch per-entry; no index/decryption used.
import zlib,sys,os,struct
p=sys.argv[1]; b=open(p,'rb').read(); outd=sys.argv[2]; os.makedirs(outd,exist_ok=True)
i=0; blocks=[]
while i<len(b)-2:
    if b[i]==0x78 and b[i+1] in (0x01,0x5e,0x9c,0xda):
        d=zlib.decompressobj()
        try:
            out=d.decompress(b[i:i+70000])
            if d.eof and len(out)>0:
                used=70000-len(d.unused_data) if len(b)-i>=70000 else (len(b)-i)-len(d.unused_data)
                blocks.append((i,out)); i+=used; continue
        except Exception: pass
    i+=1
# group consecutive blocks into files: new file when block begins with uasset magic or gap > 200
files=[]; cur=None; last=-1
for off,out in blocks:
    if cur is None or off-last>8 or out[:4]==b'\xc1\x83\x2a\x9e':
        cur=[off,b'']; files.append(cur)
    cur[1]+=out; last=off+0
    # compute end
for n,(off,data) in enumerate(files):
    open(os.path.join(outd,'%08x.bin'%off),'wb').write(data)
print(len(blocks),'blocks',len(files),'chunks')
