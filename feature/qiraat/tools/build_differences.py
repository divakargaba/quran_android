"""Builds src/main/assets/qiraat/differences.qdf from the pages fetch_differences.py downloaded:
for every ayah, each word where the twenty riwayat differ, and how each group of them reads it
(nquran.com's descriptions, in Arabic, and their English from descriptions_en.tsv), and where the
word is in the Hafs ayah, to show it as each riwayah writes it (build_texts.py).

format (gzipped json): {"riwayat": [riwayah ids, in the order of the bits below],
  "texts": [descriptions], "texts_en": [their english], "ayat": {"<sura>:<ayah>": [[word,
  [[riwayat bits, text index], ...], first hafs word, hafs words], ...]}}, where "the other rawis"
  of a word are given their bits too, and the first hafs word is -1 if the word was not found.

It also repairs the texts read from the mushafs (build_texts.py): a word read there that no
KFGQPC text has is taken from a riwayah with KFGQPC's text that nquran says reads it alike, or,
where all twenty read it alike, from Shu'bah's. A word left that has other letters than every
KFGQPC text there is marked unsure ("~" before it).

usage: build_differences.py <pages dir> <texts dir (build_texts.py)> <out file>
"""
import sys, os, re, gzip, glob, json, html, difflib
from build_texts import skeleton, read_hafs
from text_decoder import canon

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

def place(word, hafs_words, start):
    """(first, count) of the hafs words of the ayah (from start on) that nquran's word is"""
    target = skeleton(word.replace(' ', ''))
    count = len(word.split())
    best, first = 0.0, -1
    for i in range(start, len(hafs_words) - count + 1):
        candidate = skeleton(''.join(hafs_words[i:i + count]))
        ratio = difflib.SequenceMatcher(None, target, candidate, autojunk=False).ratio()
        if ratio > best + 1e-9: best, first = ratio, i
    return (first, count) if best >= 0.6 else (-1, count)

def main(pages, texts_dir, out):
    english = dict(line.rstrip('\n').split('\t') for line in
                   open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'descriptions_en.tsv')))
    hafs = read_hafs(texts_dir)
    starts = [0]
    from madani import AYAHS
    for n in AYAHS: starts.append(starts[-1] + n)
    texts, index, ayat = [], {}, {}
    missing = 0
    for f in sorted(glob.glob(f'{pages}/*.html.gz')):
        name = os.path.basename(f)
        sura, ayah = int(name[:3]), int(name[3:6])
        words = parse(gzip.decompress(open(f, 'rb').read()).decode('utf-8', 'replace'))
        hafs_words = hafs[starts[sura - 1] + ayah - 1]
        cursor = 0
        for word in words:
            first, count = place(word[0], hafs_words, cursor)
            if first < 0:
                first, count = place(word[0], hafs_words, 0)
            if first < 0: missing += 1
            else: cursor = first
            word += [first, count]
            for group in word[1]:
                t = group[1]
                if t not in index: index[t] = len(texts); texts.append(t)
                group[1] = index[t]
        if words: ayat[f'{sura}:{ayah}'] = words
    repair(texts_dir, ayat)
    texts_en = ['\n'.join(english[line] for line in t.split('\n')) for t in texts]
    data = json.dumps({'riwayat': RIWAYAT, 'texts': texts, 'texts_en': texts_en, 'ayat': ayat},
                      ensure_ascii=False, separators=(',', ':'))
    open(out, 'wb').write(gzip.compress(data.encode(), 9))
    print(len(ayat), 'ayat with differences,', len(texts), 'descriptions,', missing,
          'words not found in the hafs text,', os.path.getsize(out), 'bytes')

# riwayat with KFGQPC's text, in the order their words are taken for others (the Mashriqi texts,
# whose conventions the others' text follows, first)
UNSURE = '~'
KFGQPC = ['shubah', 'hafs', 'bazzi', 'qunbul', 'duri_abuamr', 'susi', 'qalun', 'warsh']

def repair(texts_dir, ayat):
    from madani import AYAHS
    suras = []
    for sura in range(1, 115):
        lines = gzip.decompress(open(f'{texts_dir}/{sura:03d}.qtx', 'rb').read()).decode().split('\n')
        suras.append(lines[:-1])
    names = [x.split('=')[0] for x in suras[0][0].split()]
    fonts = dict(x.split('=') for x in suras[0][0].split())
    decoded = [n for n in names if n not in KFGQPC]
    rows = lambda lines, a: [line.replace(UNSURE, '').split('|')
                             for line in lines[1 + a * len(names):1 + (a + 1) * len(names)]]
    known = set()
    for lines in suras:
        for a in range((len(lines) - 1) // len(names)):
            for name, slots in zip(names, rows(lines, a)):
                if name in KFGQPC:
                    known.update(canon(w) for slot in slots for w in slot.split())
    fixed = total = unsure = 0
    for sura, lines in enumerate(suras, 1):
        for a in range(AYAHS[sura - 1]):
            slots = dict(zip(names, rows(lines, a)))
            # the riwayat that read each hafs word alike, where they differ
            groups = {}
            for word in ayat.get(f'{sura}:{a + 1}', []):
                if word[2] < 0: continue
                for i in range(word[2], word[2] + word[3]):
                    groups.setdefault(i, []).extend(bits for bits, _ in word[1])
            for name in decoded:
                bit = BIT[name]
                for i, slot in enumerate(slots[name]):
                    words = slot.split()
                    if len(words) != 1 or canon(words[0]) in known: continue
                    total += 1
                    if i in groups:
                        alike = [bits for bits in groups[i] if bits & bit]
                        source = next((k for k in KFGQPC if alike and all(bits & BIT[k] for bits in alike)
                                       and len(slots[k]) > i and slots[k][i]), None)
                    else:
                        source = 'shubah' if len(slots['shubah']) > i and slots['shubah'][i] else None
                    if source:
                        slots[name][i] = slots[source][i]; fixed += 1
                    elif skeleton(words[0]) not in {skeleton(slots[k][i]) for k in KFGQPC if len(slots[k]) > i}:
                        slots[name][i] = UNSURE + words[0]; unsure += 1
            base = 1 + a * len(names)
            for j, name in enumerate(names):
                lines[base + j] = '|'.join(slots[name])
        with open(f'{texts_dir}/{sura:03d}.qtx', 'wb') as f:
            f.write(gzip.compress(('\n'.join(lines) + '\n').encode(), 9, mtime=0))
    print(f'{fixed} of {total} words read from the mushafs that no KFGQPC text has were repaired,',
          unsure, 'are unsure')

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3])
