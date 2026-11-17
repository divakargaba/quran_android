"""extract_text.py <book.pdf> <out.json> [options]: what a commentary laid out as M. Saleem
Gaibie's are says of each verse, as text: under "TEXT: n" (or "TEXT:" with the number by the
verse) the verse, then its sections: "TRANSLATION" is the verse's translation, the others
(vocabulary, commentary, explanation) explain it. Gives

  [[first verse, last verse, translation, explanation]]

with the book's numbers. An explanation's paragraphs are set apart by blank lines; a section's
name is a paragraph that begins "# "; the notes at the foot of the pages are given after what
they are notes to, each beginning with its number in brackets, as its mark in the text is."""
import sys, re, json, collections, statistics
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
import pdftext

SECTION = re.compile(r'^(TEXT|TRANSLATION|VOCABULARY|COMMENTARY|EXPLANATION)\b\s*:?\s*(.*)$')
ITEM = re.compile(r'^(\(?\d{1,2}[\).]|[•\-–]|\(?[a-z]\))\s')

def marked(line, body):
    """the line's text, the marks of its notes (small raised figures) as [n]"""
    units = list(line.units)
    i = 0
    while i < len(units):
        if units[i].strip().isdigit() and line.sizes[i] < 0.8 * body and line.size >= body - 1.5:
            j = i
            while j < len(units) and (units[j].strip().isdigit() and line.sizes[j] < 0.8 * body): j += 1
            units[i:j] = ['[' + ''.join(u.strip() for u in units[i:j]) + ']']
        i += 1
    return pdftext.logical(units)

def paragraphs(lines, pitch, vocabulary=False):
    """lines: [(page, y0, x0, x1, text)] of a section, in order. Their paragraphs."""
    out = []; previous = None
    right = max((l[3] for l in lines), default=0)
    for page, y0, x0, x1, text in lines:
        new = previous is None
        if previous is not None:
            gap = y0 - previous[1] if page == previous[0] else 0
            new = gap > 1.45 * pitch or bool(ITEM.match(text)) or previous[3] < right - 60 and previous[4].rstrip().endswith(('.', ':', '”', ')'))
            if vocabulary and pdftext.is_arabic(text[:3]) and (' – ' in text or ' - ' in text or ' − ' in text): new = True
        if new: out.append(text)
        elif out[-1].endswith('-') and not out[-1].endswith(' -'): out[-1] += text
        else: out[-1] += ' ' + text
        previous = (page, y0, x0, x1, text)
    return [re.sub(r'\s+([,.;:)\]])', r'\1', re.sub(r'([(\[])\s+', r'\1', p)).strip() for p in out]

def extract(path, number_by_verse=False):
    d = pdftext.Doc(path)
    pages = [d.lines(p) for p in range(len(d.doc))]
    sizes = collections.Counter()
    for lines in pages:
        for l in lines: sizes[round(l.size)] += len(l.text)
    body = sizes.most_common(1)[0][0]
    gaps = []
    for lines in pages:
        rows = [l for l in lines if round(l.size) == body]
        gaps += [b.y0 - a.y0 for a, b in zip(rows, rows[1:]) if 0 < b.y0 - a.y0 < 3 * body]
    pitch = statistics.median(gaps)
    height = d.doc[0].rect.height
    entries = []; block = None; last = 0; notes = {}
    def close():
        nonlocal block, last
        if block and (block['translation'] or block['sections']):
            translation = ' '.join(paragraphs(block['translation'], pitch))
            translation = re.sub(r'^\d+\.\s*', '', translation)
            parts = []
            for name, lines in block['sections']:
                parts.append('# ' + name)
                parts += paragraphs(lines, pitch, 'VOCAB' in name.upper())
            numbers = block['numbers'] or [last + 1]
            entries.append(dict(first=numbers[0], last=numbers[-1], translation=translation,
                                explanation=parts, pages=sorted(block['pages'])))
            last = numbers[-1]
        block = None
    for pno, lines in enumerate(pages):
        note = None
        for line in lines:
            text = marked(line, body)
            if not text: continue
            if re.fullmatch(r'\d{1,3}', text) and (line.y0 < 45 or line.y1 > height - 75) and not (block and block['state'] == 'verse'): continue
            # the notes at the foot of the page
            if round(line.size) < body - 1 and line.y0 > height * 0.3:
                m = re.match(r'^(\d{1,3})\s+(.*)$', text)
                if m: note = (pno, int(m.group(1))); notes[note] = m.group(2); continue
                if note: notes[note] += ' ' + text; continue
            head = SECTION.match(text)
            if head and len(text) < 40 and round(line.size) >= body - 1:
                kind, rest = head.groups()
                if kind == 'TEXT':
                    if block is None or block['state'] != 'verse': close(); block = dict(numbers=[], state='verse', translation=[], sections=[], pages=set())
                    found = [int(n) for n in re.findall(r'\d+', rest)]
                    expected = (block['numbers'][-1] if block['numbers'] else last) + 1
                    if len(found) == 2 and found[1] == expected and found[0] != expected: found = [expected]
                    if len(found) == 2 and found[1] > found[0]: found = list(range(found[0], found[1] + 1))
                    block['numbers'] += found; block['numbered'] = bool(found); block['pages'].add(pno)
                elif block:
                    if kind == 'TRANSLATION': block['state'] = 'translation'
                    else:
                        block['state'] = 'section'; block['sections'].append((text.rstrip(':. ').title(), []))
                continue
            if block is None: continue
            if re.fullmatch(r'[=*_~-]{5,}', text.replace(' ', '')): close(); continue
            if block['state'] == 'verse':
                if not block.get('numbered') and round(line.size) > body:
                    n = re.search(r'(?<!\d)(\d{1,4})(?!\d)', text)
                    if n and re.fullmatch(r'[\d\s]+', text): block['numbers'].append(int(n.group(1)))
                    elif n and number_by_verse and not block['numbers']: block['numbers'].append(int(n.group(1)))
                continue
            if round(line.size) >= body + 3 and not re.search(r'[a-z]{3}', text) and re.search(r'[A-Z]{4}', text): close(); continue
            row = (pno, line.y0, line.x0, line.x1, text)
            block['pages'].add(pno)
            if block['state'] == 'translation': block['translation'].append(row)
            else: block['sections'][-1][1].append(row)
    close()
    # the notes, after what they are notes to
    for e in entries:
        text = e['translation'] + ' ' + ' '.join(e['explanation'])
        seen = []
        for n in re.findall(r'\[(\d{1,3})\]', text):
            n = int(n)
            for page in e['pages'] + [e['pages'][-1] + 1]:
                if (page, n) in notes and n not in seen:
                    seen.append(n); e['explanation'].append(f'[{n}] ' + re.sub(r'\s+', ' ', notes[(page, n)])); break
    return entries

if __name__ == '__main__':
    entries = extract(sys.argv[1], number_by_verse='--number-by-verse' in sys.argv)
    json.dump(entries, open(sys.argv[2], 'w'), ensure_ascii=False, indent=0)
    covered = [v for e in entries for v in range(e['first'], e['last'] + 1)]
    print(len(entries), 'entries; verses', covered[0], 'to', covered[-1], '; breaks', [(a, b) for a, b in zip(covered, covered[1:]) if b != a + 1][:10])
    print('  no translation:', len([e for e in entries if not e['translation']]), 'no explanation:', len([e for e in entries if not e['explanation']]))
    print('  characters: translations', sum(len(e['translation']) for e in entries), 'explanations', sum(len(p) for e in entries for p in e['explanation']))
