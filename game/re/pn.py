import sys,struct,re
exec(open('ue.py').read())
pat=re.compile(sys.argv[1].encode())
# scan all ascii strings in rodata matching pattern, then param structs
seen=set()
for m in re.finditer(rb'(?<=\x00)[A-Za-z_][A-Za-z0-9_]{2,60}(?=\x00)', D[:0x4a2d528]):
    s=m.group()
    if not pat.search(s) or s in seen: continue
    seen.add(s)
    v=f2v(m.start())
    for r in ptrrefs(v):
        f=v2f(r)
        if f<4: continue
        w=struct.unpack_from('<12I',D,f-4)
        if w[2]==0x45 and w[6]==1:
            print('%-40s typ=%2x off=0x%x flags=%x pr=%x'%(s.decode(),w[0],w[8],w[4],r))
