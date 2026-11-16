"""index_commentary.py <book.pdf> <out.json>: where a commentary laid out as M. Saleem Gaibie's
are says what of each verse: "TEXT: n" (or "TEXT:" with the number by the verse), the verse,
then its sections, of which "TRANSLATION" is the verse's translation and the others (vocabulary,
commentary or explanation) explain it. Gives the parts of the book's pages to show for a verse:

  {"left", "right": the sides of the text on a page (pt), "entries":
     [[first verse, last verse, [translation: [page, top, bottom]...], [explanation: ...]]]}

(pages from 0; a verse's own lines are in neither). The numbers are the book's."""
import sys, re, json, collections, pymupdf

SECTION = re.compile(r'^(TEXT|TRANSLATION|VOCABULARY|COMMENTARY|EXPLANATION)\b\s*:?\s*(.*)$')
PAD = 3

def page_lines(doc):
    sizes = collections.Counter(); pages = []
    for page in doc:
        rows = []
        for block in page.get_text('dict')['blocks']:
            for line in block.get('lines', []):
                spans = [s for s in line['spans'] if s['text'].strip()]
                if not spans: continue
                size = max(spans, key=lambda s: len(s['text']))['size']
                text = re.sub(r'\s+', ' ', ''.join(s['text'] for s in spans)).strip()
                rows.append(dict(y0=line['bbox'][1], y1=line['bbox'][3], x0=line['bbox'][0], x1=line['bbox'][2],
                                 size=size, text=text))
                sizes[round(size)] += len(text)
        rows.sort(key=lambda r: (round(r['y0'] / 6), r['x0']))
        pages.append(rows)
    return pages, sizes.most_common(1)[0][0]

def index(path):
    doc = pymupdf.open(path)
    pages, body = page_lines(doc)
    height = doc[0].rect.height
    entries = []; block = None; last = 0
    lefts = []; rights = []; spans = []
    def close():
        nonlocal block, last
        if block and block['numbers'] and (block['translation'] or block['explanation']):
            numbers = block['numbers']
            entries.append([numbers[0], numbers[-1], block['translation'], block['explanation']])
            last = numbers[-1]
        block = None
    for pno, rows in enumerate(pages):
        for row in rows:
            text = row['text']
            # the page's number, at its top or bottom
            if re.fullmatch(r'\d{1,3}', text) and (row['y0'] < 45 or row['y1'] > height - 75) and not (block and block['state'] == 'verse'): continue
            head = SECTION.match(text)
            if head and len(text) < 40:
                kind, rest = head.groups()
                if kind == 'TEXT':
                    # (verses given together have a header each, or one for them all)
                    if block is None or block['state'] != 'verse': close(); block = dict(numbers=[], state='verse', translation=[], explanation=[])
                    found = [int(n) for n in re.findall(r'\d+', rest)]
                    expected = (block['numbers'][-1] if block['numbers'] else last) + 1
                    if len(found) == 2 and found[1] == expected and found[0] != expected: found = [expected]  # a misprint ("40 25")
                    if len(found) == 2 and found[1] > found[0]: found = list(range(found[0], found[1] + 1))
                    block['numbers'] += found; block['numbered'] = bool(found)
                elif block:
                    block['state'] = 'translation' if kind == 'TRANSLATION' else 'explanation'
                    if kind != 'TRANSLATION': add(block['explanation'], pno, row)
                continue
            if block is None: continue
            if re.fullmatch(r'[=*_~-]{5,}', text.replace(' ', '')): close(); continue  # the end of the book's commentary
            if block['state'] == 'verse':
                if not block.get('numbered') and re.fullmatch(r'\d{1,4}', text) and round(row['size']) > body: block['numbers'].append(int(text))
                continue
            # a heading (of a chapter) ends what is said of the verse
            if round(row['size']) >= body + 3 and not re.search(r'[a-z]{3}', text) and re.search(r'[A-Z]{4}', text): close(); continue
            if block['state'] == 'translation' and round(row['size']) < body - 1: continue  # a footnote under it
            add(block[block['state']], pno, row)
            if round(row['size']) >= body - 1: lefts.append(row['x0']); rights.append(row['x1']); spans.append((pno, row['x0'], row['x1']))
    close()
    lefts.sort(); rights.sort()
    return dict(left=round(lefts[len(lefts) // 100] - PAD), right=round(rights[-1 - len(rights) // 100] + PAD),
                page_width=round(doc[0].rect.width), page_height=round(height), doc=doc, spans=spans,
                entries=[[a, b, merge(t), merge(e)] for a, b, t, e in entries])

def add(regions, page, row):
    regions.append([page, row['y0'] - PAD, row['y1'] + PAD])

def merge(regions):
    """the lines' parts of pages, joined into one for each page they are on (in order)"""
    out = []
    for page, top, bottom in regions:
        if out and out[-1][0] == page and top <= out[-1][2] + 60: out[-1][1] = min(out[-1][1], top); out[-1][2] = max(out[-1][2], bottom)
        else: out.append([page, top, bottom])
    return [[p, round(max(0, t)), round(b)] for p, t, b in out]

if __name__ == '__main__':
    result = index(sys.argv[1]); result.pop('doc'); result.pop('spans')
    json.dump(result, open(sys.argv[2], 'w'), separators=(',', ':'))
    entries = result['entries']
    covered = [v for e in entries for v in range(e[0], e[1] + 1)]
    print(len(entries), 'entries; verses', covered[0], 'to', covered[-1], '; left', result['left'], 'right', result['right'], 'of', result['page_width'])
    print('  breaks:', [(a, b) for a, b in zip(covered, covered[1:]) if b != a + 1][:20])
    print('  together:', [(e[0], e[1]) for e in entries if e[1] != e[0]][:40])
    print('  no explanation:', [e[0] for e in entries if not e[3]][:30])
    print('  no translation:', [e[0] for e in entries if not e[2]][:40])
    heights = sorted(sum(b - t for _, t, b in e[2]) for e in entries if e[2]); print('  translation heights: median', heights[len(heights) // 2], 'max', heights[-1], 'min', heights[0])
