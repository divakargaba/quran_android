import pickle, sys
sys.path.insert(0, sys.argv[0].rsplit('/',1)[0])
from madani import page_ayahs

def coords(st):
    """global coordinate g for every token: page + cumulative text-width fraction"""
    from collections import defaultdict
    tot=defaultdict(float)
    for t in st:
        if t[6]=='w': tot[t[0]]+=t[4]-t[2]
    g=[]; cur=0; lastp=None
    for t in st:
        if t[0]!=lastp: cur=0; lastp=t[0]
        if t[6]=='w': cur+=t[4]-t[2]
        g.append(t[0]-1+ (cur/tot[t[0]] if tot[t[0]] else 0))
    return g

def align(gh, gr, gap=0.035, maxd=0.25, band=80, end_h=None, end_r=None):
    n,m=len(gh),len(gr)
    INF=1e18
    # banded DP: D[i][j] best cost aligning gh[:i], gr[:j]
    D=[dict() for _ in range(n+1)]; B=[dict() for _ in range(n+1)]
    D[0][0]=0
    for j in range(1,min(m,band)+1): D[0][j]=j*gap; B[0][j]=2
    off=m-n
    for i in range(1,n+1):
        c=int(i+off*i/n)
        for j in range(max(0,c-band), min(m,c+band)+1):
            best=INF; bk=None
            v=D[i-1].get(j)
            if v is not None and v+gap<best: best=v+gap; bk=1   # hafs marker unmatched
            v=D[i].get(j-1)
            if v is not None and v+gap<best: best=v+gap; bk=2   # riwayah marker unmatched
            v=D[i-1].get(j-1)
            if v is not None and abs(gh[i-1]-gr[j-1])<maxd:
                d=abs(gh[i-1]-gr[j-1])
                if end_h is not None:
                    # a sura's last ayah ends where a sura title follows, and no other ayah does
                    if end_r[j-1] and not end_h[i-1]: d+=1.0
                    elif end_h[i-1] and not end_r[j-1]: d+=0.05
                if v+d<best: best=v+d; bk=3
            if bk: D[i][j]=best; B[i][j]=bk
    i,j=n,m; match=[None]*n
    while i>0 or j>0:
        b=B[i][j]
        if b==3: match[i-1]=j-1; i-=1; j-=1
        elif b==1: i-=1
        else: j-=1
    return match

def regions(hst, rst, gr_all=None):
    gh_all=coords(hst)
    if gr_all is None: gr_all=coords(rst)
    hm=[k for k,t in enumerate(hst) if t[6]=='m']
    rm=[k for k,t in enumerate(rst) if t[6]=='m']
    gh=[gh_all[k] for k in hm]; gr=[gr_all[k] for k in rm]
    ayahs=[sa for p in range(1,605) for sa in page_ayahs(p)]
    if len(gh)==len(gr) and max(abs(a-b) for a,b in zip(gh,gr))<0.6:
        # same ayah count (Kufi): markers correspond one to one
        match=list(range(len(gr)))
    else:
        end_h=[k+1==len(ayahs) or ayahs[k+1][0]!=s for k,(s,_) in enumerate(ayahs)]
        match=align(gh,gr,end_h=end_h,end_r=heading_after(rst))
    assert len(ayahs)==len(hm)
    import bisect
    def end_idx(i):   # riwayah marker index (in rm) that closes hafs ayah i
        if match[i] is not None: return match[i]
        return min(bisect.bisect_left(gr, gh[i]), len(rm)-1)
    def start_idx(i): # riwayah marker index after which hafs ayah i starts (-1 = start)
        if i==0: return -1
        if match[i-1] is not None: return match[i-1]
        return bisect.bisect_right(gr, gh[i-1])-1
    out={}
    for i,(s,a) in enumerate(ayahs):
        a0=start_idx(i); a1=end_idx(i)
        if a1<=a0: a1=a0+1 if a0+1<len(rm) else a0
        k0=rm[a0]+1 if a0>=0 else 0; k1=rm[a1]
        toks=rst[k0:k1+1]
        lines={}
        for t in toks:
            key=(t[0],t[1]); r=lines.get(key)
            lines[key]=(min(r[0],t[2]),min(r[1],t[3]),max(r[2],t[4]),max(r[3],t[5])) if r else (t[2],t[3],t[4],t[5])
        out[(s,a)]=(rst[k1][0], [(k[0],)+v for k,v in sorted(lines.items())], a1-a0, a1)
    # number each hafs ayah by the riwayah ayah (in that sura) that ends it; suras always end on
    # an ayah end in every count
    ends={k:v[3] for k,v in out.items()}
    sura_base=-1; last=None
    for (s,a),v in out.items():
        if last is not None and s!=last[0]: sura_base=ends[last]
        out[(s,a)]=v[:3]+(v[3]-sura_base, v[3])
        last=(s,a)
    unmatched=sum(1 for x in match if x is None)
    return out, unmatched

def body_filter(st):
    ys=sorted(t[3] for t in st if t[6]=='m'); ye=sorted(t[5] for t in st if t[6]=='m')
    lo=ys[int(len(ys)*0.002)]; hi=ye[int(len(ye)*0.998)]
    lh=sorted(t[5]-t[3] for t in st if t[6]=='m')[len(ys)//2]
    return [t for t in st if t[6]=='m' or (t[3]+t[5])/2 > lo-0.6*lh and (t[3]+t[5])/2 < hi+0.6*lh]

def ref_coords(ref, r):
    """coordinates for stream r expressed in ref's coordinate system, via word-level text alignment"""
    import difflib, bisect
    from collections import defaultdict
    g_ref=coords(ref)
    rw=defaultdict(list); fw=defaultdict(list)
    for k,t in enumerate(r):
        if t[6]=='w': rw[t[0]].append(k)
    for k,t in enumerate(ref):
        if t[6]=='w': fw[t[0]].append(k)
    pairs=[]
    for p in range(1,605):
        win=fw[p-1]+fw[p]+fw[p+1] if p>1 else fw[p]+fw[p+1]
        a=[ref[k][7] for k in win]; b=[r[k][7] for k in rw[p]]
        sm=difflib.SequenceMatcher(None,a,b,autojunk=False)
        for blk in sm.get_matching_blocks():
            if blk.size>=2:
                for o in range(blk.size): pairs.append((rw[p][blk.b+o], win[blk.a+o]))
    pairs.sort()
    # longest increasing subsequence on ref index
    tails=[]; tidx=[]; prev=[-1]*len(pairs)
    for i,(_,f) in enumerate(pairs):
        j=bisect.bisect_left(tails,f)
        if j==len(tails): tails.append(f); tidx.append(i)
        else: tails[j]=f; tidx[j]=i
        prev[i]=tidx[j-1] if j>0 else -1
    seq=[]; i=tidx[-1] if tidx else -1
    while i>=0: seq.append(pairs[i]); i=prev[i]
    seq.reverse()
    xs=[a for a,_ in seq]; ys=[g_ref[b] for _,b in seq]
    g=[]
    for k in range(len(r)):
        j=bisect.bisect_left(xs,k)
        if j<len(xs) and xs[j]==k: g.append(ys[j])
        elif j==0: g.append(ys[0])
        elif j==len(xs): g.append(ys[-1])
        else:
            x0,x1,y0,y1=xs[j-1],xs[j],ys[j-1],ys[j]
            g.append(y0+(y1-y0)*(k-x0)/(x1-x0))
    return g, len(seq)

def piecewise(xs, ys):
    import bisect
    def F(v):
        j=bisect.bisect_left(xs,v)
        if j==0: return ys[0]+(v-xs[0])
        if j==len(xs): return ys[-1]+(v-xs[-1])
        x0,x1,y0,y1=xs[j-1],xs[j],ys[j-1],ys[j]
        return y0+(y1-y0)*(v-x0)/(x1-x0) if x1>x0 else y0
    return F

def hafs_coords_for(h, ref_k, r, idx, sibling=None):
    """coordinates of stream r (pdf index idx) expressed in hafs' coordinate system"""
    import bisect
    if idx==10: return coords(h), None
    if idx==6:
        # al-Bazzi's pdf uses a different font encoding from the others, so text alignment is
        # weak; instead anchor its ayah markers to Qunbul's (same Makki count, same qari)
        gq,_=hafs_coords_for(h, ref_k, sibling, 17)
        rq=coords(sibling); rb=coords(r)
        qm=[k for k,t in enumerate(sibling) if t[6]=='m']; bm=[k for k,t in enumerate(r) if t[6]=='m']
        m=align([rq[k] for k in qm],[rb[k] for k in bm])
        xs=[]; ys=[]
        for i,j in enumerate(m):
            if j is not None and (not xs or rb[bm[j]]>xs[-1]) and (not ys or gq[qm[i]]>ys[-1]):
                xs.append(rb[bm[j]]); ys.append(gq[qm[i]])
        F=piecewise(xs,ys)
        return [F(v) for v in rb], len(xs)
    gh=coords(h); gk=coords(ref_k)
    hm=[k for k,t in enumerate(h) if t[6]=='m']; km=[k for k,t in enumerate(ref_k) if t[6]=='m']
    m=list(range(len(km))) if len(km)==len(hm) else align([gh[k] for k in hm],[gk[k] for k in km])
    xs=[]; ys=[]
    for i,j in enumerate(m):
        if j is not None and (not xs or gk[km[j]]>xs[-1]): xs.append(gk[km[j]]); ys.append(gh[hm[i]])
    def F(v):
        j=bisect.bisect_left(xs,v)
        if j==0: return ys[0]+(v-xs[0])
        if j==len(xs): return ys[-1]+(v-xs[-1])
        x0,x1,y0,y1=xs[j-1],xs[j],ys[j-1],ys[j]
        return y0+(y1-y0)*(v-x0)/(x1-x0) if x1>x0 else y0
    if idx==12: return [F(v) for v in gk], None
    gr,n=ref_coords(ref_k, r)
    return [F(v) for v in gr], n

def heading_after(st):
    """for each ayah marker, whether a sura title or basmalah line (a whole line without an ayah
    marker, narrow and centred) follows it before the next marker: the end of a sura"""
    rm=[k for k,t in enumerate(st) if t[6]=='m']
    extent={}; has_marker=set()
    for t in st:
        key=(t[0],t[1])
        if t[6]=='m': has_marker.add(key)
        e=extent.setdefault(key,[1,0]); e[0]=min(e[0],t[2]); e[1]=max(e[1],t[4])
    def heading(key):
        x0,x1=extent[key]
        return key[0]>1 and key not in has_marker and (x1-x0)<0.55 and abs((x0+x1)/2-0.5)<0.04
    flags=[]
    for j,k in enumerate(rm):
        nxt=rm[j+1] if j+1<len(rm) else len(st)
        flags.append(j==len(rm)-1 or any(heading((t[0],t[1])) for t in st[k+1:nxt] if t[6]=='w'))
    return flags
