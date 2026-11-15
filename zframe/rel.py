# release: take previous APK, swap dexes, bump versionCode/versionName in binary manifest
import zipfile, sys, struct
prev, out, oldv, newv, oldc, newc = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], int(sys.argv[5]), int(sys.argv[6])
zin = zipfile.ZipFile(prev)
m = bytearray(zin.read('AndroidManifest.xml'))
# versionName in string pool (utf-16 or utf-8)
for enc in ('utf-16le', 'utf-8'):
    a = oldv.encode(enc); b = newv.encode(enc)
    assert len(a) == len(b)
    i = m.find(a)
    if i >= 0: m[i:i+len(a)] = b; print('name', enc, i); break
else: raise SystemExit('versionName not found')
# LABEL: app name ZFrame -> 艾挽补丁更新 (same utf-16 length 6)
a='ZFrame'.encode('utf-16le'); b='艾挽补丁更新'.encode('utf-16le'); assert len(a)==len(b)
i=m.find(b'\x06\x00'+a)
if i>=0: m[i+2:i+2+len(a)]=b; print('label', i)
# versionCode attr: res id 0x0101021b; attribute = ns,name,rawValue,(size,0,type,data)
# find attr entries whose typed data is int-dec 0x10 with value oldc
pat = struct.pack('<HBBI', 8, 0, 0x10, oldc)
n = 0; i = m.find(pat)
while i >= 0:
    m[i+4:i+8] = struct.pack("<I", newc); n += 1; break
print('code hits', n); assert n >= 1
dexes = {'classes.dex': open('build/dex/classes.dex','rb').read(), 'assets/inject.dex': open('build/inject/classes.dex','rb').read(), 'AndroidManifest.xml': bytes(m)}
with open(out, 'wb') as f:
    zout = zipfile.ZipFile(f, 'w')
    for i in zin.infolist():
        if i.filename.startswith('META-INF/'): continue
        data = dexes.get(i.filename, zin.read(i.filename))
        zi = zipfile.ZipInfo(i.filename, (2008,1,1,0,0,0))
        if i.filename == 'resources.arsc' or i.filename.endswith('.so') or i.compress_type == zipfile.ZIP_STORED:
            zi.compress_type = zipfile.ZIP_STORED
            hdr = 30 + len(i.filename.encode()); pad = (4 - (f.tell() + hdr) % 4) % 4
            zi.extra = b'\x00' * pad
        else: zi.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zi, data)
    zout.close()
