"""build_inline.py <books dir> <poems dir> <out dir>: for each translation or commentary shown
under a poem's verses, where on its pages it says what of each verse (numbered as the poem's
text is): <out>/<id>.json = {"w": the width of its text (pt), "e": [[first verse, last verse,
[translation: [page, left, top, bottom]...], [explanation: ...]]]} (pages from 0, pt)."""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import regions, index_commentary, index_abt, index_ghunyah, index_primer

books, poems, out = sys.argv[1:4]

def commentary(name, number=lambda n: n):
    r = index_commentary.index(f'{books}/{name}')
    return regions.pack(r['doc'], r['entries'], *regions.sides(r['spans']), number=number)

built = {}
r = index_abt.index(f'{books}/jazariyyah_en.pdf', f'{poems}/jazariyyah.json')
built['jazariyyah_en'] = regions.pack(r['doc'], r['entries'], *regions.sides(r['spans']))
built['taqrib_laali'] = commentary('taqrib_laali.pdf')
built['aysar_aqwal'] = commentary('aysar_aqwal.pdf')
built['fawaid_ayyubiyyah'] = commentary('fawaid_ayyubiyyah.pdf')
built['anis_ashr'] = commentary('anis_ashr.pdf')
doc, entries, spans = index_ghunyah.index(f'{books}/ghunyah.pdf')
built['ghunyah'] = regions.pack(doc, entries, *regions.sides(spans))
doc, entries, spans = index_primer.index(f'{books}/primer.pdf')
built['primer'] = regions.pack(doc, entries, *regions.sides(spans))
for name, index in built.items():
    json.dump(index, open(f'{out}/{name}.json', 'w'), separators=(',', ':'))
    e = index['e']
    print(name, len(e), 'entries, verses', e[0][0], 'to', e[-1][1], 'width', index['w'],
          'without translation:', sum(1 for x in e if not x[2]), 'without explanation:', sum(1 for x in e if not x[3]))
