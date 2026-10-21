import sys, pickle, struct, gzip
sys.path.insert(0, sys.argv[0].rsplit('/',1)[0])
import align
D=sys.argv[1]; outdir=sys.argv[2]; ids=[int(x) for x in sys.argv[3].split(',')]; names=sys.argv[4].split(',')

def strip_headers(rects):
    # drop leading sura-title / basmalah lines: narrow and centred
    if not rects or rects[0][0] <= 2: return rects
    out=list(rects)
    while len(out)>1:
        p,x0,y0,x1,y1=out[0]
        if (x1-x0)<0.55 and abs((x0+x1)/2-0.5)<0.04: out.pop(0)
        else: break
    return out

h=align.body_filter(pickle.load(open(f'{D}/st/r10.pkl','rb')))
K=align.body_filter(pickle.load(open(f'{D}/st/r12.pkl','rb')))
for i,name in zip(ids,names):
    r=align.body_filter(pickle.load(open(f'{D}/st/r{i}.pkl','rb')))
    sib=align.body_filter(pickle.load(open(f'{D}/st/r17.pkl','rb'))) if i==6 else None
    g,nm=align.hafs_coords_for(h,K,r,i,sib)
    res,un=align.regions(h,r,g)
    buf=bytearray(b'QRX1'); buf+=struct.pack('>H',len(res))
    empty=0
    for (s,a),(page,rects,_) in res.items():
        rects=strip_headers(rects)
        if not rects: empty+=1
        buf+=struct.pack('>HB', page, len(rects))
        for (p,x0,y0,x1,y1) in rects:
            q=lambda v:max(0,min(65535,int(round(v*65535))))
            buf+=struct.pack('>HHHHH',p,q(x0),q(y0),q(x1),q(y1))
    data=gzip.compress(bytes(buf),9)
    open(f'{outdir}/{name}.bin.gz','wb').write(data)
    print(name, 'textmatches',nm,'unmatched',un,'empty',empty,'raw',len(buf),'gz',len(data))
