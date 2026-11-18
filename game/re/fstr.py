import sys,pickle
exec(open('ue.py').read())
XRr=pickle.load(open('/tmp/xrr.pkl','rb'))
for a in sys.argv[1:]:
    f=func_of(int(a,16))[0]
    print(a,hex(f),[strat(t) for t in XRr.get(f,[]) if strat(t)][:12])
