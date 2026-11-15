import sys,struct
exec(open('ue.py').read())
def props(name):
    out=[]
    for s in findstr(name):
        for r in ptrrefs(s):
            f=v2f(r)
            w=struct.unpack_from('<12I',D,f-4)
            out.append((r,w))
    return out
for n in sys.argv[1:]:
    for r,w in props(n):
        print(n, hex(r), ' '.join('%x'%x for x in w))
