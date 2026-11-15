import pickle,sys,os
from google.protobuf import descriptor_pb2 as dp
F=dp.FieldDescriptorProto
TN={v:k[5:].lower() for k,v in F.Type.items()}
LB={1:'optional',2:'required',3:'repeated'}
fds={k:dp.FileDescriptorProto.FromString(v) for k,v in pickle.load(open('../proto/fds.pkl','rb')).items()}
def msg(m,ind=''):
    o=[f'{ind}message {m.name} {{']
    for e in m.enum_type: o+=enum(e,ind+'  ')
    for n in m.nested_type: o+=msg(n,ind+'  ')
    for f in m.field:
        t=f.type_name.lstrip('.') if f.type_name else TN[f.type]
        d=f' [default={f.default_value}]' if f.default_value else ''
        o.append(f'{ind}  {LB[f.label]} {t} {f.name} = {f.number}{d};')
    o.append(ind+'}'); return o
def enum(e,ind=''):
    return [f'{ind}enum {e.name} {{']+[f'{ind}  {v.name} = {v.number};' for v in e.value]+[ind+'}']
for name,fd in fds.items():
    o=[f'// {name}  package {fd.package}']+[f'import "{d}";' for d in fd.dependency]
    for e in fd.enum_type: o+=enum(e)
    for m in fd.message_type: o+=msg(m)
    os.makedirs(os.path.dirname('../proto/'+name),exist_ok=True); open('../proto/'+name,'w').write('\n'.join(o)+'\n')
