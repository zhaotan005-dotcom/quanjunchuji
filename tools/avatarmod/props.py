import sys; from ue import *
def readtags(r,names,depth=0,out=None):
    while True:
        st=r.o
        n=names[r.i32()]; r.i32()
        if n=='None': return
        t=names[r.i32()]; r.i32(); size=r.i32(); idx=r.i32()
        extra=''
        if t=='StructProperty': extra=names[r.i32()]; r.i32(); r.raw(16)
        elif t in('BoolProperty',): extra=r.u8()
        elif t in('ByteProperty','EnumProperty'): extra=names[r.i32()]; r.i32()
        elif t=='ArrayProperty': extra=names[r.i32()]; r.i32()
        elif t=='MapProperty': extra=(names[r.i32()],r.i32(),names[r.i32()],r.i32())
        elif t=='SetProperty': extra=names[r.i32()]; r.i32()
        hasg=r.u8()
        if hasg: r.raw(16)
        vo=r.o
        print('  '*depth+f'{st:#x} {n} {t} {extra} size={size} idx={idx} @{vo:#x}')
        r.o=vo+size
if __name__=="__main__":
  d=summary(open(sys.argv[1],'rb').read())
  b=open(sys.argv[2],'rb').read()
  r=R(b); readtags(r,d['names'])
  print('props end', hex(r.o), 'total', hex(len(b)))
