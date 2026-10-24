"""Builds src/main/assets/qiraat/differences.qdf from the pages fetch_differences.py downloaded:
for every ayah, each word where the twenty riwayat differ, and how each group of them reads it
(nquran.com's descriptions, in Arabic).

format (gzipped json): {"riwayat": [riwayah ids, in the order of the bits below],
  "texts": [descriptions], "ayat": {"<sura>:<ayah>": [[word, [[riwayat bits, text index], ...]],
  ...]}}, where "the other rawis" of a word are given their bits too.

usage: build_differences.py <pages dir> <out file>
"""
import sys, os, re, gzip, glob, json, html

RIWAYAT = ['qalun', 'warsh', 'bazzi', 'qunbul', 'duri_abuamr', 'susi', 'hisham', 'ibndhakwan',
           'shubah', 'hafs', 'khalaf', 'khallad', 'abulharith', 'duri_kisai', 'ibnwardan',
           'ibnjammaz', 'ruways', 'rawh', 'ishaq', 'idris']
BIT = {r: 1 << i for i, r in enumerate(RIWAYAT)}
ALL = (1 << len(RIWAYAT)) - 1

# nquran's ids of the qurra and rawis (Shatibiyyah and Durrah path)
IDS = {
    1: ['qalun', 'warsh'], 2: ['qalun'], 3: ['warsh'],
    4: ['ibnwardan', 'ibnjammaz'], 5: ['ibnwardan'], 6: ['ibnjammaz'],
    7: ['bazzi', 'qunbul'], 8: ['bazzi'], 9: ['qunbul'],
    10: ['duri_abuamr', 'susi'], 11: ['duri_abuamr'], 12: ['susi'],
    13: ['ruways', 'rawh'], 14: ['ruways'], 15: ['rawh'],
    16: ['hisham', 'ibndhakwan'], 17: ['hisham'], 18: ['ibndhakwan'],
    19: ['shubah', 'hafs'], 20: ['shubah'], 21: ['hafs'],
    22: ['khalaf', 'khallad'], 23: ['khalaf'], 24: ['khallad'],
    25: ['abulharith', 'duri_kisai'], 26: ['abulharith'], 27: ['duri_kisai'],
    28: ['ishaq', 'idris'], 29: ['ishaq'], 30: ['idris'],
}
NAMES = {'إسحاق': ['ishaq'], 'إدريس': ['idris']}

def text_of(fragment):
    t = re.sub(r'<[^>]+>', ' ', fragment)
    return re.sub(r'\s+', ' ', html.unescape(t)).strip()

def members(ol):
    """the riwayat a group of a word names"""
    bits = 0
    tokens = re.findall(r'Rid=(\d+)&tpath=\d[^>]*>\s*<font[^>]*><b>([^<]+)</b>|(&nbsp;عن&nbsp;)', ol)
    skip = False
    for rid, name, of in tokens:
        if of:
            skip = True  # the qari after "عن" only says whose rawi it is
            continue
        if skip:
            skip = False
            continue
        ids = IDS.get(int(rid)) or NAMES.get(name.strip())
        if ids is None: raise ValueError(f'unknown reader {rid} {name}')
        for r in ids: bits |= BIT[r]
    return bits

def parse(page):
    words = []
    for block in page.split('<div class="selectquran"><h2>')[1:]:
        block = block[:block.find('</h2>')]
        m = re.search(r'<strong>\{(.*?)\}</strong>', block)
        if not m: continue
        word = text_of(m.group(1))
        groups = []; rest = None
        for part in block.split('<div class="quran-page"')[1:]:
            ol = re.search(r'<ol class="rawa-list".*?</ol>', part, re.S)
            descriptions = [text_of(li) for li in re.findall(r'<li[^>]*>(.*?)</li>', part, re.S)]
            text = '\n'.join(d for d in descriptions if d)
            if not ol or not text: continue
            if 'باقي الرواة' in ol.group(0):
                rest = text
            else:
                groups.append([members(ol.group(0)), text])
        named = 0
        for bits, _ in groups: named |= bits
        if rest is not None and ALL & ~named:
            groups.append([ALL & ~named, rest])
        if len(groups) > 1:
            words.append([word, groups])
    return words

def main(pages, out):
    texts, index, ayat = [], {}, {}
    for f in sorted(glob.glob(f'{pages}/*.html.gz')):
        name = os.path.basename(f)
        sura, ayah = int(name[:3]), int(name[3:6])
        words = parse(gzip.decompress(open(f, 'rb').read()).decode('utf-8', 'replace'))
        for word in words:
            for group in word[1]:
                t = group[1]
                if t not in index: index[t] = len(texts); texts.append(t)
                group[1] = index[t]
        if words: ayat[f'{sura}:{ayah}'] = words
    data = json.dumps({'riwayat': RIWAYAT, 'texts': texts, 'ayat': ayat}, ensure_ascii=False,
                      separators=(',', ':'))
    open(out, 'wb').write(gzip.compress(data.encode(), 9))
    print(len(ayat), 'ayat with differences,', len(texts), 'descriptions,',
          os.path.getsize(out), 'bytes')

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
