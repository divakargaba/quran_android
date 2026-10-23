"""Lays each riwayah's text out on the 15-line Madinah mushaf: the same 604 pages, the same lines
and the same line breaks as the Hafs mushaf of the reader.

The words of the riwayah mushaf PDF (segment.py) are matched, ayah by ayah, to the Hafs words of
each Madinah line (from api.quran.com's line data for the 1405 Madinah mushaf): a Madinah line
break inside an ayah is placed at the word gap in the riwayah text closest to the same share of
the ayah's text. The device then draws the words of each line from the PDF, justified across the
line (QiraatPagePreparer).

usage: build_layout.py <work dir> <line data dir> <out dir> <pdf index>=<riwayah> ...
format (gzipped, big endian): "QRL2", u16 typical line height, u16 line pitch, u16 typical line
width, u32 page width, u32 page height (all in 1/100 pt), then per page: u8 slot count, and per
slot: u8 kind (0 empty, 1 text, 2 sura title, 3 basmalah), then for a title or basmalah: u8 sura,
u16 pdf page (0: none), u16 left, u16 right, u16 top, u16 bottom of its line in the riwayah
mushaf; for text: u8 strip count, and per strip:
u16 pdf page, u16 top, u16 bottom (page fractions * 65535), u16 hafs ayah index of its first word,
u8 word count, and per word: u16 left, u16 right, u8 flags (bit 0: ayah marker, bits 1-7: change
of hafs ayah index from the previous word).
"""
import sys, os, json, glob, gzip, struct, pickle, collections, unicodedata
import numpy as np, pymupdf
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import align, segment
from madani import AYAHS

STARTS = [0]
for c in AYAHS: STARTS.append(STARTS[-1] + c)
def index(s, a): return STARTS[s - 1] + a - 1

STRIP_GAP = 5.5  # pt between words of a madinah line that come from two lines of the mushaf
# a madinah line break inside an ayah is drawn to the start of a line of the mushaf, when it is
# this close (x 0.02 of the ayah text)
LINE_START_BONUS = 1.0
TITLE_WIDTH = 170  # pt: a wider sura title line holds more than the title

def madinah_lines(line_dir):
    """hafs words in order: (sura, ayah, letters, page, line); and per page {line: kind/sura}"""
    words = []; text_lines = {}
    for f in sorted(glob.glob(f'{line_dir}/*.json')):
        page = int(os.path.basename(f)[:3])
        for v in json.load(open(f)):
            s, a = map(int, v['verse_key'].split(':'))
            for w in v['words']:
                text_lines.setdefault(page, set()).add(w['line_number'])
                if w['char_type_name'] != 'word': continue
                letters = sum(1 for ch in w['text_uthmani'] if unicodedata.category(ch) == 'Lo')
                words.append((s, a, max(1, letters), page, w['line_number']))
    # empty slots are the sura header (then the basmalah) of the next sura to start
    slots = {}
    starts = {}  # sura -> (page, line) of its first word
    for s, a, _, p, l in words:
        if a == 1 and s not in starts: starts[s] = (p, l)
    order = [(p, l) for p in range(1, 605) for l in range(1, (9 if p <= 2 else 16))]
    pending = []
    for p, l in order:
        if l in text_lines.get(p, ()):
            slots.setdefault(p, {})[l] = ('text',)
        else:
            pending.append((p, l))
            slots.setdefault(p, {})[l] = None
    # walk the empty slots, giving each sura start (after al-fatiha) its header and basmalah
    empty = [x for x in order if slots[x[0]][x[1]] is None]
    for s in range(1, 115):
        first = starts[s]
        before = [x for x in empty if x < first and slots[x[0]][x[1]] is None]
        need = 1 if s in (1, 9) else 2
        mine = before[-need:]
        for k, (p, l) in enumerate(mine):
            slots[p][l] = ('header', s) if k == 0 else ('basmalah', s)
    return words, slots

def headings(R, res, fatiha_basmalah):
    """{sura: (title line, basmalah line)} (either can be None): the lines without an ayah marker,
    narrow and before the first ayah of each sura; and the keys of all the lines that are not
    text: those, and the running header and footer of the pages"""
    has_m = {(t[0], t[1]) for t in R if t[6] == 'm'}
    ext, text = {}, {}
    for t in R:
        key = (t[0], t[1])
        e = ext.setdefault(key, [1, 0, 1, 0])
        e[0] = min(e[0], t[3]); e[1] = max(e[1], t[5]); e[2] = min(e[2], t[2]); e[3] = max(e[3], t[4])
        text[key] = text.get(key, '') + str(t[7])
    freq = collections.Counter(text.values())
    body_width = float(np.median([ext[k][3] - ext[k][2] for k in has_m]))
    def running(key):
        e = ext[key]
        return key not in has_m and ((e[0] + e[1]) / 2 < 0.11 or freq[text[key]] > 300)
    rm = [k for k, t in enumerate(R) if t[6] == 'm']
    last_ayah = {}
    for (s, a) in res: last_ayah[s] = max(a, last_ayah.get(s, 0))
    out = {}; all_keys = set()
    for s in range(1, 115):
        k0 = rm[res[(s - 1, last_ayah[s - 1])][4]] + 1 if s > 1 else 0
        found = []
        for t in R[k0:]:
            key = (t[0], t[1])
            if running(key) or (found and found[-1] == key): continue
            e = ext[key]
            if key in has_m or e[3] - e[2] > 0.75 * body_width: break
            found.append(key)
            if len(found) > 5: break
        out[s] = found
    # the basmalah is the same on every sura; a mushaf without sura titles has only it. It is
    # the last line before the text (the narrow first lines of page 2 are text)
    common = collections.Counter(text[f[-1]] for f in out.values() if f).most_common(1)[0][0]
    for s, found in out.items():
        ends = [i for i, key in enumerate(found) if text[key] == common]
        if ends: found = out[s] = found[:ends[0] + 1]
        # al-Fatiha's basmalah is its first ayah (or, outside the Kufi count, still its text)
        if s == 1:
            cut = [i for i, key in enumerate(found) if text[key][2:10] == fatiha_basmalah[2:10]]
            if cut: found = out[s] = found[:cut[0]]
        all_keys.update(found)
    for s, found in out.items():
        title = found[0] if found and text[found[0]] != common else None
        basmalah = found[-1] if found and text[found[-1]] == common and s not in (1, 9) else None
        out[s] = (title, basmalah)
    all_keys.update(key for key in ext if running(key))
    return out, all_keys

def ink_extent(doc, R, key, pw):
    """(x0, x1, y0, y1) of the ink of a line, as page fractions: its first (rightmost) part, which
    leaves out the "makkiyyah, n ayat" that follows some sura titles on the same line"""
    runs = segment.words_on_page(doc, [t for t in R if (t[0], t[1]) == key], key[0], 2.6)[key[1]]
    geo = segment.lines_of_page(R, key[0])[key[1]]
    if not runs: return None
    end = len(runs) - 1
    if (runs[0][1] - runs[-1][0]) * pw > TITLE_WIDTH and len(runs) > 1:
        # a title and more: cut at the widest gap
        end = max(range(len(runs) - 1), key=lambda i: runs[i][0] - runs[i + 1][1])
    return (runs[end][0], runs[0][1], geo[0], geo[1])

def build(work, line_dir, out_dir, pdf, name, words, slots):
    h = align.body_filter(pickle.load(open(f'{work}/st/r10.pkl', 'rb')))
    K = align.body_filter(pickle.load(open(f'{work}/st/r12.pkl', 'rb')))
    Q = align.body_filter(pickle.load(open(f'{work}/st/r17.pkl', 'rb')))
    r = align.body_filter(pickle.load(open(f'{work}/st/r{pdf}.pkl', 'rb')))
    g, _ = align.hafs_coords_for(h, K, r, pdf, Q if pdf == 6 else None)
    res, _ = align.regions(h, r, g)
    doc = pymupdf.open(f'{work}/r{pdf}.pdf')
    pw, ph = doc[10].rect.width, doc[10].rect.height
    R = pickle.load(open(f'{work}/st/r{pdf}.pkl', 'rb'))
    # the text of the first line of al-Fatiha in the Hafs mushaf (its basmalah)
    hafs = pickle.load(open(f'{work}/st/r10.pkl', 'rb'))
    first = next(t for t in hafs if t[6] == 'm')
    fatiha_basmalah = ''.join(str(t[7]) for t in hafs if (t[0], t[1]) == (first[0], first[1]))
    heads, head_keys = headings(R, res, fatiha_basmalah)
    line_text = {}
    for t in R: line_text[(t[0], t[1])] = line_text.get((t[0], t[1]), '') + str(t[7])
    basmalat = [b for _, b in heads.values() if b]
    common_basmalah = basmalat[len(basmalat) // 2] if basmalat else None
    head_ink = {}
    for s, (t, b) in heads.items():
        if b is None and s not in (1, 9): b = common_basmalah
        heads[s] = (t, b)
        for key in (t, b):
            if key and key not in head_ink: head_ink[key] = ink_extent(doc, R, key, pw)

    # word segments of every line, and the line geometry
    lines = {}      # (page, line) -> (y0, y1)
    segs = {}       # (page, line) -> [(x0, x1, is_marker)] right to left
    markers = {}
    for t in r:
        if t[6] == 'm': markers.setdefault((t[0], t[1]), []).append((t[2], t[4]))
    for p in range(1, 605):
        geo = segment.lines_of_page(r, p)
        page_words = segment.words_on_page(doc, r, p, 2.6)
        for line, ws in page_words.items():
            y0, y1 = geo[line][0], geo[line][1]
            if (p, line) in head_keys or not ws: continue
            lines[(p, line)] = (y0, y1)
            mk = markers.get((p, line), [])
            segs[(p, line)] = [(a, b, any(a - 0.004 <= (m0 + m1) / 2 <= b + 0.004 for m0, m1 in mk))
                               for a, b in ws]

    # hafs ayat sharing a riwayah ayah are matched as one group
    keys = list(res)
    groups = []
    for k in keys:
        if groups and res[groups[-1][-1]][4] == res[k][4]: groups[-1].append(k)
        else: groups.append([k])
    hafs_by_ayah = {}
    for w in words: hafs_by_ayah.setdefault((w[0], w[1]), []).append(w)

    page_lines = {}
    for key in lines: page_lines.setdefault(key[0], []).append(key)
    used = set()
    placed = {}  # (page, line) -> list of (pdf page, pdf line, x0, x1, marker, ayah index)
    widths = []
    for grp in groups:
        # the riwayah words of the group: segments inside its line rectangles
        units = []
        for (p, x0, y0, x1, y1) in res[grp[0]][1]:
            yc = (y0 + y1) / 2
            key = next((k for k in page_lines.get(p, ()) if lines[k][0] <= yc <= lines[k][1]), None)
            if key is None: continue  # a sura title or basmalah
            for i, (a, b, m) in enumerate(segs[key]):
                if x0 - 0.003 <= (a + b) / 2 <= x1 + 0.003 and (key, i) not in used:
                    used.add((key, i))
                    units.append((key, a, b, m))
        jobs = [(grp, units)]
        if (1, 1) in grp and len(grp) > 1:
            # outside the Kufi count al-Fatiha's basmalah is not an ayah, but it has a line of its own
            basmalah = [u for u in units if line_text[u[0]][2:10] == fatiha_basmalah[2:10]]
            if basmalah:
                jobs = [([(1, 1)], basmalah),
                        ([k for k in grp if k != (1, 1)], [u for u in units if u not in basmalah])]
        for keys, units in jobs:
            hafs = [w for k in keys for w in hafs_by_ayah[k]]
            if not units or not hafs: continue
            text = [u for u in units if not u[3]]
            total_w = sum(u[2] - u[1] for u in text) or 1
            total_h = sum(w[2] for w in hafs)
            # cumulative share of the text before each riwayah word, and the gap before it
            cum = []; acc = 0
            for i, u in enumerate(text):
                cum.append(acc / total_w); acc += u[2] - u[1]
            def gap_before(i):
                if i == 0: return 1.0
                a, b = text[i - 1], text[i]
                if a[0] != b[0]: return LINE_START_BONUS  # the start of a line of the mushaf
                return min(1.0, (a[1] - b[2]) * pw / 6)
            # each hafs word's (madinah page, line, ayah) is given to the riwayah words by text share
            breaks = []   # (riwayah word index where it starts, hafs word index)
            acc = 0; last = 0
            for i, w in enumerate(hafs):
                share = acc / total_h; acc += w[2]
                if i == 0: breaks.append((0, 0)); continue
                prev = hafs[i - 1]
                if (prev[3], prev[4], prev[1]) == (w[3], w[4], w[1]): continue
                # a line or ayah change: the riwayah word starting nearest the same share (a new
                # madinah line gets a word at least, and starts with a line of the mushaf if close)
                new_line = (prev[3], prev[4]) != (w[3], w[4])
                first = last + 1 if new_line and last < len(text) else last
                def cost(j):
                    if j >= len(text): return abs(1.0 - share) + 1
                    bonus = gap_before(j)
                    if bonus > 1 and not new_line: bonus = 1.0
                    return abs(cum[j] - share) - 0.02 * bonus
                best = min(range(first, len(text) + 1), key=cost)
                breaks.append((best, i)); last = best
            assignment = []
            bi = 0
            for j in range(len(text)):
                while bi + 1 < len(breaks) and breaks[bi + 1][0] <= j: bi += 1
                assignment.append(hafs[breaks[bi][1]])
            # ayah markers go with the word before them
            ti = 0; current = assignment[0] if assignment else hafs[-1]
            for u in units:
                if u[3]:
                    w = current
                else:
                    w = assignment[ti]; current = w; ti += 1
                s, a, _, p, l = w
                placed.setdefault((p, l), []).append((u[0], u[1], u[2], u[3], index(s, a)))
    # typical line geometry, for the device's scale: the height of the text band, the distance
    # between lines, and the width of a line with its natural word spacing
    heights = [(y1 - y0) * ph for (y0, y1) in lines.values()]
    line_h = float(np.median(heights))
    pitches = []
    for p, keys in page_lines.items():
        centers = sorted((lines[k][0] + lines[k][1]) / 2 for k in keys)
        pitches += [(b - a) * ph for a, b in zip(centers, centers[1:])]
    pitch = float(np.median(pitches))
    for key, units in placed.items():
        width = 0; strip = None
        for u in units:
            if strip and strip[0] == u[0]: strip[1] = min(strip[1], u[1]); strip[2] = max(strip[2], u[2])
            else:
                if strip: width += (strip[2] - strip[1]) * pw + STRIP_GAP
                strip = [u[0], u[1], u[2]]
        widths.append(width + (strip[2] - strip[1]) * pw)
    line_w = float(np.percentile(widths, 75))

    buf = bytearray(b'QRL2') + struct.pack('>HHHII', *(int(v * 100) for v in (line_h, pitch, line_w, pw, ph)))
    q = lambda v: max(0, min(65535, int(round(v * 65535))))
    for p in range(1, 605):
        page_slots = slots[p]
        buf += struct.pack('>B', len(page_slots))
        for l in sorted(page_slots):
            kind = page_slots[l]
            if kind is None:
                buf += struct.pack('>B', 0)
            elif kind[0] in ('header', 'basmalah'):
                key = heads[kind[1]][0 if kind[0] == 'header' else 1]
                buf += struct.pack('>BB', 2 if kind[0] == 'header' else 3, kind[1])
                if key is None or head_ink[key] is None: buf += struct.pack('>HHHHH', 0, 0, 0, 0, 0)
                else:
                    x0, x1, y0, y1 = head_ink[key] or (0, 0, 0, 0)
                    buf += struct.pack('>HHHHH', key[0], q(x0), q(x1), q(y0), q(y1))
            else:
                units = placed.get((p, l), [])
                strips = []
                for u in units:
                    if strips and strips[-1][0] == u[0]: strips[-1][1].append(u)
                    else: strips.append((u[0], [u]))
                buf += struct.pack('>BB', 1, len(strips))
                for (pp, ln), us in strips:
                    y0, y1 = lines[(pp, ln)]
                    buf += struct.pack('>HHHHB', pp, q(y0), q(y1), us[0][4], len(us))
                    prev = us[0][4]
                    for u in us:
                        delta = min(127, max(0, u[4] - prev)); prev = u[4]
                        buf += struct.pack('>HHB', q(u[1]), q(u[2]), (delta << 1) | (1 if u[3] else 0))
    open(f'{out_dir}/{name}.qrl', 'wb').write(gzip.compress(bytes(buf), 9))
    empty_lines = sum(1 for p in range(1, 605) for l, k in slots[p].items() if k == ('text',) and not placed.get((p, l)))
    print(name, 'line height %.1fpt pitch %.1fpt width %.0fpt' % (line_h, pitch, line_w),
          'missing titles', sum(1 for t, _ in heads.values() if t is None), 'empty text lines', empty_lines,
          'raw', len(buf), flush=True)

if __name__ == '__main__':
    work, line_dir, out_dir = sys.argv[1:4]
    words, slots = madinah_lines(line_dir)
    for spec in sys.argv[4:]:
        pdf, name = spec.split('=')
        build(work, line_dir, out_dir, int(pdf), name, words, slots)
