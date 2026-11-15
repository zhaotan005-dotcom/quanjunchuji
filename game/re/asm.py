# tiny ARM (A32) assembler for the GiveItem cave; verified with capstone
import re, struct
CC={'eq':0,'ne':1,'hs':2,'lo':3,'mi':4,'pl':5,'vs':6,'vc':7,'hi':8,'ls':9,'ge':10,'lt':11,'gt':12,'le':13,'al':14,'':14}
R={('r%d'%i):i for i in range(16)}; R.update(sb=9,sl=10,fp=11,ip=12,sp=13,lr=14,pc=15)
DP={'and':0,'eor':1,'sub':2,'rsb':3,'add':4,'adc':5,'sbc':6,'rsc':7,'tst':8,'teq':9,'cmp':10,'cmn':11,'orr':12,'mov':13,'bic':14,'mvn':15}
def imm12(v):
    v&=0xffffffff
    for r in range(16):
        x=((v<<(2*r))|(v>>(32-2*r)))&0xffffffff if r else v
        if x<256: return (r<<8)|x
    raise ValueError('imm %x'%v)
def op2(s):
    s=s.strip()
    if s.startswith('#'): return (1<<25)|imm12(int(s[1:],0))
    m=re.match(r'(\w+)(?:\s*,\s*(lsl|lsr|asr)\s*#(\d+))?$',s)
    sh={'lsl':0,'lsr':1,'asr':2}[m.group(2)] if m.group(2) else 0
    return R[m.group(1)]|(int(m.group(3) or 0)<<7)|(sh<<5)
def split(m):
    for base in sorted(list(DP)+['ldr','str','ldrb','strb','b','bl','push','pop','umull','mla','movw','mul'],key=len,reverse=True):
        if m.startswith(base):
            rest=m[len(base):]; s=0
            if base in DP and rest.startswith('s') and rest[1:] in CC: s=1; rest=rest[1:]
            if rest in CC: return base,CC[rest],s
    raise ValueError(m)
def asm(src,org,syms={}):
    lines=[l.split(';')[0].strip() for l in src.split('\n')]
    lab={}; pc=org; ins=[]
    for l in lines:
        if not l: continue
        if l.endswith(':'): lab[l[:-1]]=pc; continue
        ins.append((pc,l)); pc+=4
    out=b''
    for pc,l in ins:
        mn,args=(l.split(None,1)+[''])[:2]
        base,c,s=split(mn); cond=c<<28
        a=[x.strip() for x in re.split(r',(?![^\[]*\])(?![^{]*})',args)] if args else []
        if base in('b','bl'):
            t=lab.get(a[0]) if a[0] in lab else (syms[a[0]] if a[0] in syms else int(a[0],0))
            off=((t-pc-8)>>2)&0xffffff
            w=cond|(0xa<<24)|((base=='bl')<<24)|off
        elif base in('push','pop'):
            regs=a[0].strip('{} ').split(','); m=0
            for r in regs:
                r=r.strip()
                if '-' in r: x,y=r.split('-'); m|=sum(1<<i for i in range(R[x],R[y]+1))
                else: m|=1<<R[r]
            w=cond|(0x92d<<16)|m if base=='push' else cond|(0x8bd<<16)|m
        elif base=='movw':
            v=int(a[1][1:],0); w=cond|0x03000000|((v>>12)<<16)|(R[a[0]]<<12)|(v&0xfff)
        elif base=='umull':
            w=cond|0x00800090|(R[a[1]]<<16)|(R[a[0]]<<12)|(R[a[3]]<<8)|R[a[2]]
        elif base=='mla':
            w=cond|0x00200090|(R[a[0]]<<16)|(R[a[3]]<<12)|(R[a[2]]<<8)|R[a[1]]
        elif base in('ldr','str','ldrb','strb'):
            L=base.startswith('ldr'); B=base.endswith('b')
            m=re.match(r'\[(\w+)(?:\s*,\s*(.+))?\]$',a[1]); rn=R[m.group(1)]; o=m.group(2)
            w=cond|(1<<26)|(1<<24)|(L<<20)|(B<<22)|(rn<<16)|(R[a[0]]<<12)
            if o is None: w|=1<<23
            elif o.startswith('#'):
                v=int(o[1:],0); w|=((v>=0)<<23)|abs(v)
            else: w|=(1<<25)|(1<<23)|op2(o)
        else:
            opc=DP[base]
            if base in('mov','mvn'): rd,rn,o=R[a[0]],0,a[1]
            elif base in('cmp','cmn','tst','teq'): rd,rn,o=0,R[a[0]],a[1]; s=1
            else: rd,rn,o=R[a[0]],R[a[1]],','.join(a[2:])
            w=cond|(opc<<21)|(s<<20)|(rn<<16)|(rd<<12)|op2(o)
        out+=struct.pack('<I',w)
    return out,lab
