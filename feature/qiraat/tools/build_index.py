import sys, pickle, struct, gzip, json
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
    buf=bytearray(b'QRX2'); buf+=struct.pack('>H',len(res))
    empty=0
    for (s,a),(page,rects,_,number,_) in res.items():
        rects=strip_headers(rects)
        if not rects: empty+=1
        buf+=struct.pack('>HHB', page, number, len(rects))
        for (p,x0,y0,x1,y1) in rects:
            q=lambda v:max(0,min(65535,int(round(v*65535))))
            buf+=struct.pack('>HHHHH',p,q(x0),q(y0),q(x1),q(y1))
    data=gzip.compress(bytes(buf),9)
    open(f'{outdir}/{name}.qrx','wb').write(data)
    print(name, 'textmatches',nm,'unmatched',un,'empty',empty,'raw',len(buf),'gz',len(data))

    # side data for building audio timings (not shipped): each hafs ayah's number in this
    # riwayah's count, and the text width of each riwayah ayah
    rm=[k for k,t in enumerate(r) if t[6]=='m']
    widths=[]; prev=-1
    for k in rm:
        lines={}
        for t in r[prev+1:k]:
            if t[6]=='w':
                l=lines.setdefault((t[0],t[1]),[1,0,0])
                l[0]=min(l[0],t[2]); l[1]=max(l[1],t[4]); l[2]+=t[4]-t[2]
        # leave out the sura title and basmalah lines (narrow and centred) before the ayah
        keys=sorted(lines)
        while len(keys)>1:
            x0,x1,_=lines[keys[0]]
            if (x1-x0)<0.55 and abs((x0+x1)/2-0.5)<0.04: keys.pop(0)
            else: break
        widths.append(sum(lines[key][2] for key in keys)); prev=k
    json.dump({'number':[v[3] for v in res.values()],'marker':[v[4] for v in res.values()],
               'widths':widths}, open(f'{outdir}/{name}.meta.json','w'))
