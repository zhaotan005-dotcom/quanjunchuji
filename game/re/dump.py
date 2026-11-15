import sys,struct
exec(open('ue.py').read())
def dumparr(st,n):
    for x in range(st,st+4*n,4):
        p=u32v(x); w=struct.unpack_from('<12I',D,v2f(p))
        print('  %-40s typ=%2x off=0x%x flags=%x  extra=%s'%(cstr(w[1]),w[0],w[8],w[4],' '.join('%x'%y for y in w[9:12])))
for a in sys.argv[1:]:
    st,n=a.split(':'); print(st); dumparr(int(st,16),int(n))
