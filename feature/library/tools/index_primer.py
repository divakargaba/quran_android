""""A Classical Primer on Tajwid" (M. Elqabbany), on Tuhfat al-Atfal: under "(Lines a–b)" come the
verses, the translation of each (in italics, after its number), and what explains them together:
this is given with the last of them. As index_commentary.py."""
import re, pymupdf
PAD = 2
def index(path, last=61):
    doc = pymupdf.open(path)
    entries = {}; spans = []; group = None; verse = None; state = None
    for pno, page in enumerate(doc):
        rows = []
        for block in page.get_text('dict')['blocks']:
            for line in block.get('lines', []):
                sp = [s for s in line['spans'] if s['text'].strip()]
                if not sp: continue
                rows.append(dict(y0=line['bbox'][1], y1=line['bbox'][3], x0=line['bbox'][0], x1=line['bbox'][2], size=sp[0]['size'],
                                 font=sp[0]['font'], text=re.sub(r'\s+', ' ', ''.join(s['text'] for s in sp)).strip()))
        rows.sort(key=lambda r: (round(r['y0'] / 5), r['x0']))
        margin = min((r['x0'] for r in rows if 60 <= r['y0'] <= 530 and r['size'] > 10.5 and r['x1'] - r['x0'] > 150), default=91)
        for r in rows:
            text = r['text']
            if r['y0'] < 60 or r['y0'] > 530: continue  # the page's heading and number
            head = re.fullmatch(r'\(Lines? (\d+)(?:–(\d+))?\)', text)
            if head:
                a = int(head.group(1)); b = int(head.group(2) or a)
                if entries and a <= max(entries): return doc, finish(entries), spans  # (the lines of another poem follow)
                group = (a, b); verse = None; state = 'verses'; continue
            if group is None: continue
            if r['size'] >= 13: group = None; continue  # the next section's title
            if 'Scheherazade' in r['font'] or 'Arab' in r['font']:
                if state == 'verses': continue
            footnote = r['size'] < 8.5
            italic_small = 8.5 <= r['size'] < 10.5
            start = re.match(r'^(\d{1,2})\b\s*(.*)$', text) if italic_small and r['x0'] < margin + 12 else None
            if start and group[0] <= int(start.group(1)) <= group[1]:
                verse = int(start.group(1)); entries.setdefault(verse, [verse, verse, [], []]); state = 'translation'
                if not start.group(2): continue
            elif italic_small and re.fullmatch(r'\d{1,2}', text): continue  # (a verse's number beside it)
            if footnote and state != 'explanation': continue
            if state == 'translation' and italic_small and verse:
                entries[verse][2].append([pno, r['y0'] - PAD, r['y1'] + PAD]); spans.append((pno, r['x0'], r['x1'])); continue
            if state in ('translation', 'explanation') and not italic_small and group[1] in entries:
                state = 'explanation'
                entries[group[1]][3].append([pno, r['y0'] - PAD, r['y1'] + PAD]); spans.append((pno, r['x0'], r['x1']))
            elif state == 'explanation' and group[1] in entries:  # (a note in small type within it)
                entries[group[1]][3].append([pno, r['y0'] - PAD, r['y1'] + PAD])
    return doc, finish(entries), spans

def finish(entries):
    out = []
    for n in sorted(entries):
        e = entries[n]
        for k in (2, 3):
            merged = []
            for page, top, bottom in e[k]:
                if merged and merged[-1][0] == page: merged[-1][1] = min(merged[-1][1], top); merged[-1][2] = max(merged[-1][2], bottom)
                else: merged.append([page, top, bottom])
            e[k] = merged
        out.append(e)
    return out
