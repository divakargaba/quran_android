"""build_text.py <books dir> <poems dir> <out dir> <dir of the indexes of build_inline.py>: the translations and commentaries shown under
the poems' verses, as text: <out>/<id>.json = {"e": [[first verse, last verse, translation,
explanation]]}, numbered as the poem's text is. The text is read from the books' pdfs by their
glyphs (pdftext.py, extract_text.py); the verses each part is of are those found from the books'
layout (index_commentary.py), which the two readings must agree on."""
import sys, os, json, re
here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, here)
import extract_text, index_commentary, pdftext

books, poems, out = sys.argv[1:4]

def commentary(name, number=lambda n: n, **options):
    entries = extract_text.extract(f'{books}/{name}.pdf', **options)
    reference = [(e[0], e[1]) for e in index_commentary.index(f'{books}/{name}.pdf')['entries']]
    assert len(entries) == len(reference), (name, len(entries), len(reference))
    result = []
    for e, (first, last) in zip(entries, reference):
        numbers = [number(n) for n in range(first, last + 1)]
        numbers = [n for n in numbers if n is not None]
        if not numbers: continue
        result.append([numbers[0], numbers[-1], e['translation'], '\n\n'.join(e['explanation'])])
    return result

def by_regions(name, index, overrides=None, strict=False, notes=True):
    """a book whose parts for each verse are known by their places on its pages (the index made
    for showing them): [[first, last, translation, explanation]] with the text of those places"""
    d = pdftext.Doc(f'{books}/{name}.pdf')
    if overrides: d.overrides = overrides
    pages = {}
    def text(regions):
        rows = []; missing = 0
        for page, left, top, bottom in regions:
            if page not in pages: pages[page] = d.lines(page)
            for line in pages[page]:
                middle = (line.y0 + line.y1) / 2
                if top - 1 <= middle <= bottom + 1 and line.size > 10.5:
                    t = re.sub(r'\s*\[\d+\]', '', extract_text.marked(line, 12))
                    if t: rows.append((page, line.y0, line.x0, line.x1, t)); missing += line.missing
        return rows, missing
    result = []
    for first, last, translation, explanation in json.load(open(index))['e']:
        rows, unread = text(translation)
        if unread and strict: continue  # (it has Arabic that cannot be read)
        t = ' '.join(extract_text.paragraphs(rows, 16))
        t = re.sub(r'^\d+\s*[.\-]\s*', '', t)
        t = re.sub(r'\s*[\u0600-\u06ff]+ \(some other tajweed rules.*$', '', t)  # (the title of what follows the last verse)
        # (the marks of its notes, which are not given)
        if not notes: t = re.sub(r'\s+([,.;:])', r'\1', re.sub(r'(?<!yah )(?<!\d)\d{1,2}(?![\d)])', '', t))
        rows, missing = text(explanation)
        e = '\n\n'.join(extract_text.paragraphs(rows, 16)) if rows and not missing else ''
        e = re.sub(r'^EXPLANATION:?\s*', '', e)
        if t or e: result.append([first, last, t, e])
    return result

FATHAH, SUKUN = chr(0x64e), chr(0x652)
built = {
    # (its regular Arabic font gives its fathah and sukun as letters, its bold one a space as a dammah)
    'jazariyyah_en': by_regions('jazariyyah_en', sys.argv[4] + '/jazariyyah_en.json', overrides={
        ('Traditional Arabic', 200): FATHAH, ('Traditional Arabic', 204): SUKUN, ('Traditional Arabic,Bold', 3): ' '}, notes=False),
    # (the Arabic of its explanations cannot be read from the pdf: those without any are given)
    'ghunyah': by_regions('ghunyah', sys.argv[4] + '/ghunyah.json', strict=True),
    'taqrib_laali': commentary('taqrib_laali'),
    'aysar_aqwal': commentary('aysar_aqwal'),
    'fawaid_ayyubiyyah': commentary('fawaid_ayyubiyyah'),
    'anis_ashr': commentary('anis_ashr', number_by_verse=True),
}
for name, entries in built.items():
    json.dump({'e': entries}, open(f'{out}/{name}.json', 'w'), ensure_ascii=False, separators=(',', ':'))
    print(name, len(entries), 'entries, verses', entries[0][0], 'to', entries[-1][1], os.path.getsize(f'{out}/{name}.json') >> 10, 'KB')
