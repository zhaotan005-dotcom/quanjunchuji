import sys,struct
exec(open('/workspace/work/quanjunchuji/game/re/ue.py').read())
def fstart(a):
    v=a&~3
    while v>a-0x4000:
        x=struct.unpack_from('<I',D,v)[0]
        # push {..lr} = stmdb sp!, {...,lr}: 0xE92D4xxx
        if (x&0xFFFF4000)==0xE92D4000:
            y=struct.unpack_from('<I',D,v+4)[0]
            if (y&0xFFFFF000)==0xE28DB000: return v
        v-=4
    return None
if __name__=='__main__':
    for s in sys.argv[1:]: print(s,hex(fstart(int(s,16))))
