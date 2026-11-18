import sys,pickle
exec(open('ue.py').read())
C=pickle.load(open('/workspace/work/quanjunchuji/game/re/calls.pkl','rb'))
for a in sys.argv[1:]:
    t=int(a,16)
    print(a,[ (hex(s),hex(func_of(s)[0])) for s in C.get(t,[]) if not (func_of(s)[0]<=t<func_of(s)[1])])
