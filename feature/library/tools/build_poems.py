"""build_poems.py <shamela pages dir> <out dir>: the poems' texts as json, from the pages of their
editions on shamela.ws (fetch_shamela.py): {"chapters": [{"t": title, "v": [[first half, second
half], ...]}]}; the verses are numbered in order from 1.

The editions print the number of verses of each chapter beside its heading: the chapters are
checked against these, and set by them where a heading is misplaced in the digital edition."""
import sys, os, re, json, html, unicodedata
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import meter
D = str.maketrans('٠١٢٣٤٥٦٧٨٩', '0123456789')
# names an edition adds (unvocalized) after a pronoun or a code of the poem, to say whom it means
NAMES = ['أبوجعفر', 'أبو جعفر', 'أبوعمرو', 'ابن ذكوان', 'يعقوب', 'رويس', 'البزي', 'خلف', 'روح']
BASMALAH = re.compile(r'^بِ?سْ?مِ? الل')
# where an edition's split of a verse into its halves is misplaced or missing: words in the first
SPLITS = {'shatibiyyah': {90: 6, 96: 8, 107: 5, 174: 6, 444: 5, 651: 6, 733: 6, 919: 6}}
# headings the digital edition has in the wrong order
ORDER = {'tayyibah': [('بَابُ السَّكْتِ', 'بَابُ وَقفِ حَمْزَةَ')]}
# (name, shamela book, its first page of the poem, whether it prints the verses of each chapter)
POEMS = [('shatibiyyah', '7754', 7, True), ('durrah', '7749', 2, True), ('tayyibah', '7795', 2, True),
         ('jazariyyah', '581', 51, False), ('tuhfah', '9632', 2, False)]

def clean(t):
    for name in NAMES:
        t = re.sub(r'(?<=[ً-ْ])' + name + r'(?=\s|$)', '', t)
        t = re.sub(r'(?:(?<=\s)|^)' + name + r'(?=\s|$)', '', t)
    return re.sub(r'\s+', ' ', t).strip()

def paragraphs(d, first, counts):
    """[(text, kind)] of the poem's paragraphs: kind 'v' (a verse), 'h' (a heading)"""
    out = []
    for n in sorted(int(f[:-5]) for f in os.listdir(d)):
        if n < first: continue
        s = open(f'{d}/{n}.html', encoding='utf-8').read()
        i = s.find('<div class="nass'); j = s.find('<div id="appended_pages"', i)
        for attrs, p in re.findall(r'<p([^>]*)>(.*?)</p>', s[i:j], re.S):
            if 'hamesh' in attrs: continue
            p = re.sub(r'<a [^>]*class="btn_tag.*?</a>', '', p, flags=re.S)
            raw = re.sub(r'\s+', ' ', html.unescape(re.sub(r'<[^>]+>', '', p))).strip()
            # footnote marks go; the words an edition sets apart stay
            p = re.sub(r'<span class="c2">\s*\(?[٠-٩0-9]+\)?\s*</span>', '', p)
            p = re.sub(r'<span class="c2">\((.*?)\)</span>', r'\1', p)
            t = re.sub(r'\s+', ' ', html.unescape(re.sub(r'<[^>]+>', '', p))).strip()
            if not t or re.fullmatch(r'[\s*٭.…]+', t): continue
            numbered = re.match(r'^([٠-٩]+)\s*[-ـ–]\s*(.+)$', t)
            # (a verse the edition gives without the mark between its halves has the next number)
            if '...' in t or '…' in t or (numbered and len(numbered.group(2).split()) >= 7 and
                                          int(numbered.group(1).translate(D)) == sum(1 for x in out if x[1] == 'v') + 1):
                out.append((t, 'v', None))
            else:
                m = re.search(r'\(?([٠-٩]+)\)?\s*:?\s*$', raw)
                out.append((t, 'h', int(m.group(1).translate(D)) if m and counts else None))
    return out

def title(t):
    t = re.sub(r'^[٠-٩0-9]+\s*[-ـ–]\s*', '', t)
    t = re.sub(r'[\s:]*\(?[٠-٩0-9]*\)?\s*:?\s*$', '', t)
    t = re.sub(r'\s*\[أي:.*?\]', '', t)
    return t.strip(' []():')

def build(name, d, first, counts):
    verses = []; heads = []  # heads: [title, index of its first verse, stated count]
    for t, kind, count in paragraphs(d, first, counts):
        if kind == 'h':
            h = title(t)
            if BASMALAH.match(h) or re.search(r'تم بحمد|تَمَّ|مُنَقَّحٌ|^الكتاب|^تَأْلِيفُ|^ملحوظة', h) or not h: continue
            heads.append([h, len(verses), count])
            continue
        m = re.match(r'^(?:[٠-٩0-9]+\s*[-ـ–]\s*)?(.+)$', t)
        body = m.group(1)
        number = len(verses) + 1
        words = clean(re.sub(r'\s*(\.\.\.|…)\s*', ' ', body)).split()
        if number in SPLITS.get(name, {}):
            k = SPLITS[name][number]
            halves = [' '.join(words[:k]), ' '.join(words[k:])]
        else:
            parts = re.split(r'\s*(?:\.\.\.|…)\s*', body, maxsplit=1)
            if len(parts) == 2: halves = [clean(parts[0]), clean(parts[1])]
            else:
                k, _ = meter.best_split(words)
                halves = [' '.join(words[:k]), ' '.join(words[k:])]
                print('  no mark between the halves of', name, number, ':', halves)
        verses.append(halves)
    for a, b in ORDER.get(name, []):
        ia = next(i for i, h in enumerate(heads) if h[0].startswith(a))
        ib = next(i for i, h in enumerate(heads) if h[0].startswith(b))
        if ia > ib: heads[ia], heads[ib] = heads[ib], heads[ia]; heads[ia][1], heads[ib][1] = heads[ib][1], heads[ia][1]
    # chapters by the stated counts, where the headings are misplaced
    n = len(heads); moved = 0
    i = 0
    while i < n:
        def parsed(k): return (heads[k + 1][1] if k + 1 < n else len(verses)) - heads[k][1]
        if heads[i][2] is None or heads[i][2] == parsed(i) or parsed(i) == 0: i += 1; continue
        j = i
        while j < n and sum(h[2] or 0 for h in heads[i:j + 1]) != (heads[j + 1][1] if j + 1 < n else len(verses)) - heads[i][1]:
            j += 1
            if j < n and heads[j][2] is None: break
        if j < n and all(h[2] is not None for h in heads[i:j + 1]) and \
                sum(h[2] for h in heads[i:j + 1]) == (heads[j + 1][1] if j + 1 < n else len(verses)) - heads[i][1]:
            start = heads[i][1]
            for k in range(i, j + 1):
                if heads[k][1] != start: moved += 1
                heads[k][1] = start; start += heads[k][2]
            i = j + 1
        else:
            i += 1
    chapters = []; wrong = []
    if heads and heads[0][1] > 0: heads.insert(0, ['', 0, None])
    pending = ''
    for k, (h, start, count) in enumerate(heads):
        end = heads[k + 1][1] if k + 1 < len(heads) else len(verses)
        if end == start:  # a heading over other headings (as "باب فرش الحروف")
            pending = h; continue
        if count is not None and count != end - start: wrong.append((h, count, end - start))
        chapters.append({'t': (pending + ' — ' if pending else '') + h, 'v': verses[start:end]})
        pending = ''
    print(name, len(chapters), 'chapters', len(verses), 'verses;', moved, 'headings moved;',
          'counts that differ from the edition:', wrong)
    return {'chapters': chapters}

if __name__ == '__main__':
    src, out = sys.argv[1:3]
    os.makedirs(out, exist_ok=True)
    for name, book, first, counts in POEMS:
        data = build(name, f'{src}/{book}', first, counts)
        json.dump(data, open(f'{out}/{name}.json', 'w'), ensure_ascii=False, separators=(',', ':'))
