import sys
exec(open('ue.py').read())
TS,TE=0x8aeb18,0x8aeb18+0x378b814
def vt(a):
    v=a
    while True:
        x=u32v(v-4)
        if not (TS<=x<TE): break
        v-=4
    return v
if __name__=='__main__':
    t=int(sys.argv[1],16)
    for r in ptrrefs(t): s=vt(r); print(hex(r),'vt',hex(s),'slot',hex(r-s))
