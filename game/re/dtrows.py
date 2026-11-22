import sys,struct
sys.path.insert(0,'/workspace/work/qjcj-gh/game/re')
from uasset import Pkg,R,show
p=Pkg(sys.argv[1]); e=p.exports[0]; b=p.data(e)
pr,o=p.props(b); show(pr,0)
r=R(b,o); r.o+=4
n=r.i32(); print('rows',n)
for k in range(n):
    nm=p.nm(r); rows,no=p.props(b,r.o); r.o=no
    print('ROW',nm); show(rows,1)
