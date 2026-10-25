"""Builds src/main/assets/qiraat/text/: the text (Unicode, with all its marks) of every ayah in
each of the twenty riwayat, for showing an ayah or a word in any riwayah without its mushaf.

Eight riwayat have KFGQPC's own Unicode text (Hafs, Qalun, Warsh, al-Bazzi, Qunbul, ad-Duri and
as-Susi of Abu 'Amr, Shu'bah); the other twelve are read from the text layer of their islamweb
mushafs (text_decoder.py), learning the glyphs from the mushafs of Shu'bah and Qunbul and their
KFGQPC text.

Each riwayah's words are aligned (by their letters) to the Hafs words of their sura, so that an
ayah or a word can be looked up by its Hafs number and position in any riwayah.

format: a file for each sura, <out dir>/<sura, 3 digits>.qtx (gzipped text; not named .gz, which
the android build would unzip): a first line with "<riwayah>=<KFGQPC font of its text>" for each
riwayah, separated by spaces, then for each (Hafs) ayah of the sura, a line for each riwayah (in
the order of the first line): for each Hafs word of the ayah, the riwayah's word(s) read in its
place, separated by "|".

The mushafs of Ishaq and Idris map some glyphs to control characters, which text extraction
drops (splitting the word), so their pdfs are read with fix_tounicode.py's copies.

usage: build_texts.py <work dir> <index dir (build_index.py)> <kfgqpc data dir> <out dir>
"""
import sys, os, re, json, gzip, pickle, collections, difflib, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import unicodedata
import align, build_layout, text_decoder
from madani import AYAHS

PDF = {'hafs': 10, 'qalun': 16, 'warsh': 20, 'bazzi': 6, 'qunbul': 17, 'duri_abuamr': 7,
       'susi': 9, 'hisham': 18, 'ibndhakwan': 4, 'shubah': 15, 'khalaf': 12, 'khallad': 11,
       'abulharith': 0, 'duri_kisai': 8, 'ibnwardan': 5, 'ibnjammaz': 3, 'ruways': 14, 'rawh': 13,
       'ishaq': 2, 'idris': 1}
KFGQPC = {'hafs': ('hafs/data/hafsData_v18.json', 'hafs'),
          'qalun': ('qaloon/data/QaloonData_v10.json', 'qaloon'),
          'warsh': ('warsh/data/warshData_v10.json', 'warsh'),
          'bazzi': ('bazzi/data/BazziData_v07.json', 'bazzi'),
          'qunbul': ('qumbul/data/QumbulData_v07.json', 'qumbul'),
          'duri_abuamr': ('doori/data/DooriData_v09.json', 'doori'),
          'susi': ('soosi/data/SoosiData09.json', 'soosi'),
          'shubah': ('shouba/data/ShoubaData08.json', 'shouba')}
# the decoded text follows the conventions of the Hafs/Shu'bah text; Shu'bah's font also has the
# dot below of imalah
DECODED_FONT = 'shouba'
DIGITS = set('٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹')
# tokens that are a mark of their own in the mushafs: rub el hizb (a word of its own, as in
# KFGQPC's text), sajdah (joins the word before it) and pause marks (dropped, since only some of
# them are tokens of their own)
RUB, SAJDAH, PAUSE_TOKENS = '*', ')', {'(', '¢'}
# private use glyphs of the mushafs of Ishaq and Idris, as the other mushafs encode them
PRIVATE = dict(zip('\uf8fa\uf8f4\uf8fc\uf8fb\uf8e5\uf8ea\uf6d9\uf8e7\uf8f1\uf8ef\uf8ed\uf8f9\uf8f5'
                   '\uf8f6\uf8f3\uf8f7\uf8f8', '⎥⎪⎫⎦⎯™©⎯⎧⎢⎝⎤⎮⎞⎩⎟⎠'))

def kfgqpc_suras(path):
    suras = [[] for _ in range(114)]
    for x in json.load(open(path)):
        words = x['aya_text'].replace('\xa0', ' ').split()
        if words and all(c in DIGITS for c in words[-1]): words = words[:-1]
        suras[x.get('sura_no', x.get('sora')) - 1].append(words)
    return suras

def pdf_suras(work, index, name):
    """the word tokens of each riwayah ayah of the mushaf, by sura"""
    pdf = PDF[name]
    h = align.body_filter(pickle.load(open(f'{work}/st/r10.pkl', 'rb')))
    K = align.body_filter(pickle.load(open(f'{work}/st/r12.pkl', 'rb')))
    Q = align.body_filter(pickle.load(open(f'{work}/st/r17.pkl', 'rb')))
    R = pickle.load(open(f'{work}/st/r{pdf}.pkl', 'rb'))
    r = align.body_filter(R)
    g, _ = align.hafs_coords_for(h, K, r, pdf, Q if pdf == 6 else None)
    res, _ = align.regions(h, r, g)
    hafs = pickle.load(open(f'{work}/st/r10.pkl', 'rb'))
    first = next(t for t in hafs if t[6] == 'm')
    basmalah = ''.join(str(t[7]) for t in hafs if (t[0], t[1]) == (first[0], first[1]))
    _, head_keys = build_layout.headings(R, res, basmalah)
    ayat, cur = [], []
    for t in r:
        if t[6] == 'm':
            ayat.append(cur); cur = []
        elif (t[0], t[1]) not in head_keys and t[7] not in PAUSE_TOKENS:
            cur.append(''.join(PRIVATE.get(c, c) for c in t[7]))
    # each marker's sura, from the hafs ayat it ends
    meta = json.load(open(f'{index}/{name}.meta.json'))
    sura_of = {}
    k = 0
    for s, count in enumerate(AYAHS, 1):
        for _ in range(count):
            sura_of.setdefault(meta['marker'][k], s); k += 1
    suras = [[] for _ in range(114)]
    s = 1
    for m, tokens in enumerate(ayat):
        s = sura_of.get(m, s)
        suras[s - 1].append(tokens)
    return suras

def main(work, index, kf_dir, out_dir):
    texts = {}
    for name, (path, font) in KFGQPC.items():
        texts[name] = {'font': font, 'suras': kfgqpc_suras(f'{kf_dir}/{path}')}
    tokens = {name: pdf_suras(work, index, name) for name in PDF if name not in ('hafs', 'bazzi')}
    # glyphs to unicode, learned where the mushaf and KFGQPC's text agree word for word
    pairs = []
    for name in ('shubah', 'qunbul'):
        for s in range(114):
            for a, b in zip(tokens[name][s], texts[name]['suras'][s]):
                b = [w for w in b if text_decoder.canon(w)]
                a = [c for c in a if c != RUB]
                if a and a[-1] == SAJDAH: a = a[:-1]
                if len(a) == len(b):
                    pairs += [(c, text_decoder.canon(u)) for c, u in zip(a, b)]
    print(len(pairs), 'words to learn from', flush=True)
    t0 = time.time()
    table = text_decoder.train(pairs, iterations=10)
    print('learned in', round(time.time() - t0), 's', flush=True)
    words = [u for _, u in pairs]
    for name in ('hafs', 'bazzi'):
        words += [text_decoder.canon(w) for sura in texts[name]['suras'] for ayah in sura for w in ayah]
    lm = text_decoder.LanguageModel(words)
    known = {}
    for code, counter in _counts(pairs).items(): known[code] = counter.most_common(1)[0][0]
    decoder = text_decoder.Decoder(table, lm, known)
    for name in PDF:
        if name in texts: continue
        whole = total = 0
        suras = []
        for sura in tokens[name]:
            ayat = []
            for ayah in sura:
                decoded = []
                for code in ayah:
                    if code == RUB: decoded.append('۞'); continue
                    if code == SAJDAH:
                        if decoded: decoded[-1] += '۩'
                        continue
                    word, was_known = decoder.decode(code)
                    decoded.append(word); whole += was_known; total += 1
                ayat.append(decoded)
            suras.append(ayat)
        texts[name] = {'font': DECODED_FONT, 'suras': suras}
        print(name, total, 'words,', f'{whole / total:.1%} read whole', flush=True)
    hafs = texts['hafs']['suras']
    out = {'fonts': {name: t['font'] for name, t in texts.items()},
           'ayat': {name: by_hafs_ayah(hafs, t['suras']) for name, t in texts.items()}}
    write(out, out_dir)

def write(texts, out_dir):
    """texts: {"fonts": {riwayah: font}, "ayat": {riwayah: [6236 ayat]}}"""
    os.makedirs(out_dir, exist_ok=True)
    names = list(texts['fonts'])
    header = ' '.join(f'{name}={texts["fonts"][name]}' for name in names)
    k, size = 0, 0
    for sura, count in enumerate(AYAHS, 1):
        lines = [header]
        for a in range(k, k + count):
            lines += [texts['ayat'][name][a] for name in names]
        k += count
        path = f'{out_dir}/{sura:03d}.qtx'
        with open(path, 'wb') as f:
            f.write(gzip.compress(('\n'.join(lines) + '\n').encode(), 9, mtime=0))
        size += os.path.getsize(path)
    print(out_dir, size, 'bytes')

def read_hafs(out_dir):
    """the hafs words of each of the 6236 ayat, from the files write() made"""
    ayat = []
    for sura in range(1, 115):
        lines = gzip.decompress(open(f'{out_dir}/{sura:03d}.qtx', 'rb').read()).decode().split('\n')
        names = [x.split('=')[0] for x in lines[0].split()]
        i = names.index('hafs')
        body = lines[1:-1]
        ayat += [body[k + i].split('|') for k in range(0, len(body), len(names))]
    return ayat

def skeleton(word):
    """the letters of a word that every riwayah and spelling shares: no marks, alifs or hamzahs"""
    word = ''.join(c for c in word if unicodedata.category(c) != 'Mn' and c not in 'ٰـ')
    word = word.translate(SKELETON)
    return re.sub('[اٱآأإءٔ]', '', word)

SKELETON = str.maketrans('ىیۦؤئۥةۨ', 'يييويوهن')

def by_hafs_ayah(hafs, suras):
    """the words of a riwayah in the places of the Hafs words they align with: for each Hafs ayah,
    the riwayah's words of each Hafs word, separated by '|'"""
    ayat = []
    for h, r in zip(hafs, suras):
        hw = [w for ayah in h for w in ayah]
        rw = [w for ayah in r for w in ayah]
        slots = [[] for _ in hw]
        matcher = difflib.SequenceMatcher(None, [skeleton(w) for w in hw],
                                          [skeleton(w) for w in rw], autojunk=False)
        for op, i1, i2, j1, j2 in matcher.get_opcodes():
            if op == 'insert':  # words the riwayah adds join the word before them
                slots[max(i1 - 1, 0)] += rw[j1:j2]
            elif op != 'delete':
                n, m = i2 - i1, j2 - j1
                for j in range(m): slots[i1 + j * n // m].append(rw[j1 + j])
        k = 0
        for ayah in h:
            ayat.append('|'.join(' '.join(slot) for slot in slots[k:k + len(ayah)]))
            k += len(ayah)
    return ayat

def _counts(pairs):
    counts = collections.defaultdict(collections.Counter)
    for c, u in pairs: counts[c][u] += 1
    return counts

if __name__ == '__main__':
    main(*sys.argv[1:5])
