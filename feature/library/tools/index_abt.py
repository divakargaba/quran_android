"""index_abt.py <jazariyyah pdf of abouttajweed.com> <out.json> <jazariyyah.json>: where the translation of each
verse of the Jazariyyah is on its pages (as index_commentary.py gives): the lines from "n. ..."
to the next verse's number (or a chapter's title). The numbers are the book's (109 verses)."""
import sys, re, json
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
import jaz_lines, pymupdf
from rapidfuzz import fuzz
PAD = 1
LETTERS = re.compile('[^\u0621-\u064a]')
def index(path, poem):
    L = jaz_lines.lines(path)
    doc = pymupdf.open(path)
    expected = 1; entries = []; current = None; lefts = []; rights = []; spans = []
    raw = {}
    for page in doc:
        for block in page.get_text('dict')['blocks']:
            for line in block.get('lines', []):
                raw[(page.number, round(line['bbox'][1]))] = line['bbox']
    previous_verse = False
    for l in L:
        text = l['text']
        box = raw.get((l['page'], l['y']))
        if l['verse']:
            # a verse's lines: its translation follows (the printed numbers have misprints, so the
            # verses are counted)
            if not previous_verse:
                current = [len(entries) + 1, len(entries) + 1, [], [], text]; entries.append(current)
            else: current[4] += ' ' + text
            previous_verse = True; continue
        if re.fullmatch(r'\d{1,3}', text): continue  # (a verse's number is by it, on its line)
        previous_verse = False
        if l['size'] <= 11.5: continue  # a footnote, the page's number
        if current is not None and (re.search(r'[Cc]hapter', text) or text.startswith('Copyright')
                                    or l['size'] >= 18 and not re.search('[A-Za-z]{2}', text)):
            current = None  # a title
        if current is not None and box:
            current[2].append([l['page'], box[1] - PAD, box[3] + PAD]); lefts.append(box[0]); rights.append(box[2]); spans.append((l['page'], box[0], box[2]))
    for e in entries:
        merged = []
        for page, top, bottom in e[2]:
            if merged and merged[-1][0] == page: merged[-1][1] = min(merged[-1][1], top); merged[-1][2] = max(merged[-1][2], bottom)
            else: merged.append([page, top, bottom])
        e[2] = [[p, round(t), round(b)] for p, t, b in merged]
    # each verse is the poem's that its Arabic is (the pdf's text of it is rough, but enough to tell)
    verses = [LETTERS.sub('', a + b) for c in json.load(open(poem))['chapters'] for a, b in c['v']]
    kept = []
    for e in entries:
        arabic = LETTERS.sub('', e.pop())
        best = max(range(len(verses)), key=lambda i: fuzz.ratio(arabic, verses[i]))
        if fuzz.ratio(arabic, verses[best]) >= 60 and e[2] and (not kept or best + 1 > kept[-1][0]):
            e[0] = e[1] = best + 1; kept.append(e)
    entries = kept
    return dict(left=round(min(lefts) - PAD), right=round(max(rights) + PAD), page_width=round(doc[0].rect.width),
                page_height=round(doc[0].rect.height), entries=entries, doc=doc, spans=spans)
if __name__ == '__main__':
    result = index(sys.argv[1], sys.argv[3]); result.pop('doc'); result.pop('spans'); json.dump(result, open(sys.argv[2], 'w'), separators=(',', ':'))
    e = result['entries']; print(len(e), 'entries to', e[-1][0], 'left', result['left'], 'right', result['right'], 'of', result['page_width'])
    heights = sorted(sum(b - t for _, t, b in x[2]) for x in e); print('heights', heights[0], heights[len(heights) // 2], heights[-5:])
    print('on two pages:', [x[0] for x in e if len(x[2]) > 1])
    have = {x[0] for x in e}; print('verses without:', [n for n in range(1, 108) if n not in have])
