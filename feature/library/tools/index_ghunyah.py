"""the Ghunyah's commentary on the opening of the Shatibiyyah (verses 1 to 94): each verse (in the
fonts of the poem, "n −"), "n. <translation>", and often "EXPLANATION:". As index_commentary.py."""
import re, collections, pymupdf
PAD = 2
def index(path, last=94):
    doc = pymupdf.open(path)
    pages = []
    for page in doc:
        rows = []
        for block in page.get_text('dict')['blocks']:
            for line in block.get('lines', []):
                spans = [s for s in line['spans'] if s['text'].strip()]
                if not spans: continue
                rows.append(dict(y0=line['bbox'][1], y1=line['bbox'][3], x0=line['bbox'][0], x1=line['bbox'][2], size=spans[0]['size'],
                                 font=spans[0]['font'], text=re.sub(r'\s+', ' ', ''.join(s['text'] for s in spans)).strip()))
        rows.sort(key=lambda r: (round(r['y0'] / 8), r['x0']))
        pages.append(rows)
    # the fonts of the verses: of the lines "n −"
    verse_fonts = collections.Counter(r['font'] for rows in pages for r in rows if re.fullmatch(r'\d+ ?[−-]', r['text']))
    verse_fonts = {f for f, n in verse_fonts.items() if n >= 3}
    entries = []; current = None; state = None; expected = 1; spans = []
    for pno, rows in enumerate(pages):
        for r in rows:
            text = r['text']
            if r['size'] < 8 or re.fullmatch(r'\d{1,3}', text) and r['y0'] > 600: continue  # footnotes, the page's number
            if r['font'] in verse_fonts or r['size'] > 14:
                current = None; continue  # a verse (the next one), a heading
            m = re.match(r'^(\d+)\. ', text)
            if m and int(m.group(1)) == expected and expected <= last:
                current = [expected, expected, [], []]; entries.append(current); state = 2; expected += 1
            elif current and text.startswith('EXPLANATION'): state = 3
            if current:
                current[state].append([pno, r['y0'] - PAD, r['y1'] + PAD]); spans.append((pno, r['x0'], r['x1']))
    for e in entries:
        for k in (2, 3):
            merged = []
            for page, top, bottom in e[k]:
                if merged and merged[-1][0] == page: merged[-1][1] = min(merged[-1][1], top); merged[-1][2] = max(merged[-1][2], bottom)
                else: merged.append([page, top, bottom])
            e[k] = merged
    return doc, entries, spans
