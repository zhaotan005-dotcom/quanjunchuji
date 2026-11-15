import zipfile, sys
src, dex, out = sys.argv[1:4]
zin = zipfile.ZipFile(src)
with open(out, 'wb') as f:
    zout = zipfile.ZipFile(f, 'w')
    items = [(i.filename, zin.read(i.filename)) for i in zin.infolist()] + [('classes.dex', open(dex,'rb').read())]
    for name, data in items:
        zi = zipfile.ZipInfo(name, (2008,1,1,0,0,0))
        if name == 'resources.arsc' or name.endswith('.so'):
            zi.compress_type = zipfile.ZIP_STORED
            # align data start to 4 bytes using extra field padding
            hdr = 30 + len(name.encode())
            pad = (4 - (f.tell() + hdr) % 4) % 4
            zi.extra = b'\x00' * pad
        else:
            zi.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zi, data)
    zout.close()
