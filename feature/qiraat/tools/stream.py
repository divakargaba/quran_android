import pymupdf, re, sys, pickle
def norm(t):
    return ''.join(chr(ord(c)-0xF000) if 0xF020<=ord(c)<=0xF0FF else c for c in t)
MAPS=[
 dict(zip('⊃⊇⊄⊂⊆∈∉∠∇®', '0123456789')),
 dict(zip('ÉÊËÌÍÎÏÐÑÒ', '0123456789')),
 {str(i):str(i) for i in range(10)},
]
MRE=re.compile(r'(∩([^∪∩]{1,3})∪|Ç([^ÇÈ]{1,3})È)')
EMBEDDED=re.compile(r'(∩([^∪∩]{1,3})∪|Ç([ÉÊËÌÍÎÏÐÑÒ0-9]{1,3})È)')
def decode(inner):
    for m in MAPS:
        if all(c in m for c in inner): return int(''.join(m[c] for c in inner))
    return None
def lines_of(words, H):
    words=sorted(words, key=lambda w:(w[1]+w[3])/2)
    lines=[]
    for w in words:
        yc=(w[1]+w[3])/2
        if lines and abs(yc-lines[-1]['yc'])< H*0.012:
            l=lines[-1]; l['w'].append(w); l['yc']=(l['yc']*(len(l['w'])-1)+yc)/len(l['w'])
        else: lines.append({'yc':yc,'w':[w]})
    return [sorted(l['w'], key=lambda w:-w[2]) for l in lines]
def extract(path):
    doc=pymupdf.open(path); out=[]
    for pi in range(len(doc)):
        p=doc[pi]; W,H=p.rect.width,p.rect.height
        ws=[]
        for w in p.get_text('words'):
            x0,y0,x1,y1,t=w[:5]
            if y1<H*0.085 or y0>H*0.865: continue
            t=norm(t)
            # a marker can be glued to the neighbouring words; split it out, giving each piece a
            # share of the box proportional to its length (pdf text is in visual, left-to-right order)
            pieces=[]; pos=0
            for m in EMBEDDED.finditer(t):
                if m.start()>pos: pieces.append((t[pos:m.start()],None))
                pieces.append((m.group(0),decode(m.group(2) or m.group(3))))
                pos=m.end()
            if pos<len(t): pieces.append((t[pos:],None))
            if len(pieces)>1 or (pieces and pieces[0][0]!=t):
                n=len(t); cx=x0
                for text,num in pieces:
                    nx=cx+(x1-x0)*len(text)/n
                    if MRE.fullmatch(text): ws.append((cx,y0,nx,y1,'m',num))
                    elif text.strip(): ws.append((cx,y0,nx,y1,'w',text))
                    cx=nx
                continue
            m=MRE.fullmatch(t)
            if m:
                ws.append((x0,y0,x1,y1,'m',decode(m.group(2) or m.group(3)))); continue
            if not t.strip() or re.fullmatch(r'[\u0660-\u0669\d]+',t): continue
            ws.append((x0,y0,x1,y1,'w',t))
        for li,l in enumerate(lines_of(ws,H)):
            for w in l: out.append((pi+1, li, w[0]/W, w[1]/H, w[2]/W, w[3]/H, w[4], w[5]))
    return out
if __name__=='__main__':
    src,dst=sys.argv[1],sys.argv[2]
    pickle.dump(extract(src), open(dst,'wb'))
