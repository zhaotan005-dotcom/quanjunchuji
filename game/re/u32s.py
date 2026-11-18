import sys,re
exec(open('ue.py').read())
def u32find(pat):
    b=pat.encode('utf-32le'); out=[]
    i=D.find(b)
    while i>=0:
        if i%4==0:
            s=i
            while s>=4 and D[s-4:s]!=b'\0\0\0\0' : s-=4
            e=D.find(b'\0\0\0\0',i)
            while (e-s)%4: e=D.find(b'\0\0\0\0',e+1)
            try: out.append((f2v(s),D[s:e].decode('utf-32le')))
            except: pass
        i=D.find(b,i+1)
    return out
if __name__=='__main__':
    seen=set()
    for p in sys.argv[1:]:
        for a,s in u32find(p):
            if a in seen: continue
            seen.add(a); print(hex(a),s[:200])
