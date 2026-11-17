# release: take previous APK, swap dexes, bump versionCode/versionName in binary manifest
import zipfile, sys, struct
prev, out, oldv, newv, oldc, newc = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], int(sys.argv[5]), int(sys.argv[6])
zin = zipfile.ZipFile(prev)
m = bytearray(zin.read('AndroidManifest.xml'))
# v2.36 用户：安装后的名字和版本号都带上 服务端/客户端/单机。重建字符串池（按下标改内容，引用不变）
import re
ed = sys.argv[7] if len(sys.argv) > 7 else ''
def rebuild_pool(m, fn):
    t, hs, sz = struct.unpack_from('<HHI', m, 8); assert t == 1
    sc, stc, fl, ss, sts = struct.unpack_from('<IIIII', m, 16)
    assert stc == 0 and not (fl & 0x100), 'pool must be utf16 without styles'
    offs = struct.unpack_from('<%dI' % sc, m, 36); base = 8 + ss
    strs = []
    for o in offs:
        n = struct.unpack_from('<H', m, base + o)[0]; strs.append(m[base+o+2:base+o+2+2*n].decode('utf-16le'))
    strs = [fn(x) for x in strs]
    data = bytearray(); no = []
    for x in strs:
        no.append(len(data)); e = x.encode('utf-16le'); data += struct.pack('<H', len(e)//2) + e + b'\0\0'
    while len(data) % 4: data += b'\0'
    nss = 28 + 4 * sc; nsz = nss + len(data)
    chunk = struct.pack('<HHIIIIII', 1, 28, nsz, sc, 0, fl, nss, 0) + struct.pack('<%dI' % sc, *no) + bytes(data)
    out = bytearray(m[:8]) + chunk + m[8+sz:]
    struct.pack_into('<I', out, 4, len(out))
    return out, strs
label = ('全军出击' + ed + ' v' + newv) if ed else '艾挽补丁更新'
def fix(x):
    if re.fullmatch(r'\d+\.\d+(-.*)?', x): return newv + ('-' + ed if ed else '')
    if x in ('ZFrame', '艾挽补丁更新') or x.startswith('全军出击'): return label
    return x
m, strs = rebuild_pool(m, fix)
print('pool', [x for x in strs if x.startswith('全军') or x.startswith(newv)])
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
