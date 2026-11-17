"""The text of a pdf by its glyphs. Some pdfs give wrong characters for their Arabic (their fonts'
tables of characters are wrong), but embed the fonts with their own tables: a glyph is then the
characters its font says it is (cmap), or that the glyphs it stands for are (GSUB: the forms of a
letter by its place in a word, ligatures).

lines(doc, page) gives the page's lines, each a list of runs (text, is Arabic, size, font, bold or
italic), with the lines' boxes; Arabic runs are in reading order."""
import io, re, unicodedata, collections
import pymupdf
from fontTools.ttLib import TTFont

def _rank(text):
    """which of the characters a glyph can be is taken: Arabic letters themselves before their
    presentation forms, and the lower before the higher (ya' before the Persian one)"""
    c = ord(text[0])
    form = 0xFB50 <= c <= 0xFDFF or 0xFE70 <= c <= 0xFEFF
    return (len(text), form, c)

def _plain(text):
    """presentation forms as the letters they are of (but not the words written as one sign)"""
    return ''.join(c if 0xFDF0 <= ord(c) <= 0xFDFD else unicodedata.normalize('NFKC', c)
                   if 0xFB50 <= ord(c) <= 0xFEFF else c for c in text)

class FontMap:
    """glyph id -> characters, from an embedded font's own tables"""
    def __init__(self):
        self.map = {}
        self.alts = {}        # glyph id -> the other things it can be (a ligature drawn without its dots)
        self.joins = set()    # glyph ids of letters joined to the letter after them
    def add(self, buf):
        try:
            tt = TTFont(io.BytesIO(buf), fontNumber=0, lazy=True)
            order = tt.getGlyphOrder()
        except Exception:
            return
        gid = {n: i for i, n in enumerate(order)}
        direct = {}; forms = {}
        if 'cmap' in tt:
            for table in tt['cmap'].tables:
                if not table.isUnicode(): continue
                for code, name in table.cmap.items():
                    if 0xE000 <= code <= 0xF8FF: continue
                    target = forms if (0xFB50 <= code <= 0xFDFF or 0xFE70 <= code <= 0xFEFF) else direct
                    if name not in target or _rank(chr(code)) < _rank(target[name]): target[name] = chr(code)
        chars = dict(direct)
        rules = []  # (output glyph, [input glyphs], feature)
        feature = {}
        try:
            table = tt['GSUB'].table if 'GSUB' in tt else None
            lookups = table.LookupList.Lookup if table and table.LookupList else []
            for record in (table.FeatureList.FeatureRecord if table else []):
                if record.FeatureTag in ('init', 'medi', 'fina', 'isol'):
                    for index in record.Feature.LookupListIndex: feature[index] = record.FeatureTag
        except Exception:
            lookups = []
        for index, lookup in enumerate(lookups):
            tag = feature.get(index)
            for sub in lookup.SubTable:
                if sub.LookupType == 7: sub = sub.ExtSubTable
                t = sub.LookupType
                if t == 1:
                    for a, b in sub.mapping.items(): rules.append((b, [a], tag))
                elif t == 2:
                    for a, bs in sub.mapping.items():
                        if len(bs) == 1: rules.append((bs[0], [a], tag))
                elif t == 3:
                    for a, bs in sub.alternates.items():
                        for b in bs: rules.append((b, [a], tag))
                elif t == 4:
                    for first, ligs in sub.ligatures.items():
                        for lig in ligs: rules.append((lig.LigGlyph, [first] + list(lig.Component), None))
        form = {}  # glyph -> 'init' / 'medi' / 'fina' / 'isol' (of its last letter)
        options = collections.defaultdict(set)
        changed = True
        while changed:
            changed = False
            for out, ins, tag in rules:
                if not all(i in chars for i in ins): continue
                text = ''.join(chars[i] for i in ins)
                kind = tag or form.get(ins[-1])
                if kind and form.get(out) != kind and out not in form: form[out] = kind; changed = True
                if out in direct: continue
                if text not in options[out]: options[out].add(text); changed = True
                if out not in chars or _rank(text) < _rank(chars[out]): chars[out] = text; changed = True
        for name, text in forms.items():
            if name not in chars: chars[name] = text
        for name, text in chars.items():
            if name in gid and gid[name] not in self.map: self.map[gid[name]] = _plain(text)
        for name, texts in options.items():
            if name in gid and len(texts) > 1: self.alts.setdefault(gid[name], set()).update(_plain(t) for t in texts)
        for name, kind in form.items():
            if name in gid and kind in ('init', 'medi'): self.joins.add(gid[name])

ARABIC = re.compile('[؀-ۿݐ-ݿﭐ-﷿ﹰ-﻿]')
def is_arabic(s): return bool(ARABIC.search(s))
def is_mark(c): return unicodedata.category(c) in ('Mn', 'Me') or 'ً' <= c <= 'ْ' or c in 'ٰٕٓٔ'

class Doc:
    def __init__(self, path):
        self.doc = pymupdf.open(path); self.fonts = {}
    def fontmap(self, page, name):
        """the map of the font named so in the pdf (a font is embedded in pieces, each with the
        glyphs some pages use, all with the font's own numbering of its glyphs: they make one map)"""
        if not self.fonts:
            seen = set()
            for pno in range(len(self.doc)):
                for f in self.doc.get_page_fonts(pno):
                    xref, base = f[0], f[3]
                    if xref in seen: continue
                    seen.add(xref)
                    try: buf = self.doc.extract_font(xref)[3]
                    except Exception: buf = None
                    if buf: self.fonts.setdefault(base.split('+')[-1], FontMap()).add(buf)
        return self.fonts.get(name)

MIRROR = {'(': ')', ')': '(', '[': ']', ']': '[', '«': '»', '»': '«', '{': '}', '}': '{'}

def _kind(text):
    if not text.strip(): return 'S'
    if is_arabic(text): return 'R'
    if any(c.isalnum() for c in text): return 'L'
    return 'N'

OPEN = '([{«'; CLOSE = ')]}»'

def logical(units):
    """units: the glyphs' texts of a line as they are seen, from the left. Returns its text as it
    is read: the runs of Arabic (with what is between its words) turned about."""
    kinds = [_kind(u) for u in units]
    n = len(units)
    # a comma or stop set close after an Arabic word, with a space after it, is of the Latin text
    # about it: the Arabic words on its two sides are read one after the other from the left
    for i, u in enumerate(units):
        if u in (',', '.', ';', ':') and 0 < i < n - 1 and kinds[i - 1] == 'R' and kinds[i + 1] == 'S': kinds[i] = 'L'
    out = []; i = 0
    while i < n:
        if kinds[i] != 'R': out.append(units[i]); i += 1; continue
        j = i; last = i
        while j < n and kinds[j] != 'L':
            if kinds[j] == 'R': last = j
            j += 1
        # a bracket of the run left outside it goes with its fellow within
        inside = ''.join(u for u in units[i:last + 1] if u in OPEN + CLOSE)
        if out and len(out[-1]) == 1 and out[-1] in OPEN and sum(c in CLOSE for c in inside) > sum(c in OPEN for c in inside):
            i -= 1; out.pop()
        elif last + 1 < n and len(units[last + 1]) == 1 and units[last + 1] in CLOSE and sum(c in OPEN for c in inside) > sum(c in CLOSE for c in inside):
            last += 1
        run = units[i:last + 1][::-1]
        # (brackets given as they were typed, about a run read from the right, are seen turned about)
        if out and len(out[-1]) == 1 and out[-1] in CLOSE and last + 1 < n and len(units[last + 1]) == 1 and units[last + 1] in OPEN:
            out[-1] = MIRROR[out[-1]]; units[last + 1] = MIRROR[units[last + 1]]
        out.append(''.join(MIRROR.get(u, u) for u in run))
        i = last + 1
    text = tidy(''.join(out).replace(chr(0x640), ''))
    return re.sub('[ \t' + chr(0xa0) + ']+', ' ', text).strip()

VOWELS = ''.join(chr(c) for c in range(0x64b, 0x651))
SUKUN = chr(0x652); FATHAH = chr(0x64e); SHADDAH = chr(0x651); ALIF = chr(0x627)
LETTER = '[' + chr(0x621) + '-' + chr(0x64a) + ']'
ANY_MARK = '[' + chr(0x64b) + '-' + chr(0x652) + ']'
TANWIN_FATH = chr(0x64b)
def tidy(text):
    """marks set twice, or on the letter beside theirs, as they cannot be: a vowel with a sukun on
    one letter (the sukun stays); a vowel or sukun on an alif after a letter with a vowel; the
    vowel of the letter after the article's lam on the lam; the two fathahs of an ending on the
    letter before the one they are on"""
    text = re.sub('[' + VOWELS + ']*' + SUKUN + '[' + VOWELS + ']*', SUKUN, text)
    text = re.sub('([' + VOWELS + ']' + SHADDAH + '?|' + SHADDAH + '[' + VOWELS + '])' + ALIF + '[' + VOWELS[3:] + SUKUN + ']',
                  lambda m: m.group(1) + ALIF, text)
    text = re.sub('([' + VOWELS + SUKUN + SHADDAH + '])\\1+', lambda m: m.group(1), text)
    text = re.sub('(?<!' + LETTER + ')(?<!' + ANY_MARK + ')' + ALIF + 'ل([' + VOWELS[3:] + '])(' + LETTER + ')(?!' + ANY_MARK + ')',
                  lambda m: ALIF + 'ل' + m.group(2) + m.group(1), text)
    text = re.sub(TANWIN_FATH + '(' + LETTER + ')' + ALIF + SUKUN + '?(?!' + LETTER + ')', lambda m: m.group(1) + TANWIN_FATH + ALIF, text)
    text = text.replace(ALIF + SUKUN, ALIF)
    # a letter with two vowels has taken that of the letter before it, drawn close by: it is that
    # letter's (which has it already)
    def twice(m):
        before, marks = m.group(2), m.group(4)
        vowels = [c for c in marks if c in VOWELS]
        if len(vowels) < 2: return m.group(0)
        own = [c for c in vowels if c not in before] or vowels[-1:]
        return m.group(1) + before + m.group(3) + ''.join(c for c in marks if c not in VOWELS) + own[-1]
    text = re.sub('(' + LETTER + ')(' + ANY_MARK + '*)(' + LETTER + ')(' + ANY_MARK + '{2,})', twice, text)
    text = re.sub('(?<!' + LETTER + ')' + ANY_MARK + '+(?!' + LETTER + ')', '', text)
    # (some fonts draw a letter's dots as marks of their own, and the ya' within a word without its dots)
    text = re.sub('[' + chr(0x656) + '-' + chr(0x65f) + ']', '', text)
    text = re.sub('(?<![' + ALIF + chr(0x648) + chr(0x649) + '])' + chr(0x655), '', text)
    text = re.sub(chr(0x649) + '(' + ANY_MARK + '*' + LETTER + ')', lambda m: chr(0x64a) + m.group(1), text)
    # (a number among Arabic words, within brackets, is read from its left)
    text = re.sub(r'\)(\d{1,3})\(', lambda m: '(' + m.group(1)[::-1] + ')', text)
    return text

# the signs of the books' fonts of symbols (told by looking at them)
SALLA = chr(0xfdfa)
SYMBOLS = {
    ('AGA Arabesque', 0xf072): SALLA, ('Alawi Symbols', 0xf053): SALLA,
    ('AGA Arabesque', 0xf075): ' عليه السلام', ('KFGQPC Arabic Symbols 01', 0xf06e): ' عليه السلام',
    ('KFGQPC Arabic Symbols 01', 0xf06f): ' عليها السلام', ('KFGQPC Arabic Symbols 01', 0xf070): ' عليهم السلام',
    ('AGA Arabesque', 0xf074): ' رضي الله عنه', ('AGA Arabesque', 0xf079): ' رضي الله عنهم',
    ('KFGQPC Arabic Symbols 01', 0xf072): ' رحمه الله',
    ('AGA Arabesque', 0xf05d): chr(0xfd3f), ('AGA Arabesque', 0xf05b): chr(0xfd3e),
    ('Symbol', 0xf0b7): '•',
}

class Line:
    __slots__ = ('page', 'x0', 'y0', 'x1', 'y1', 'size', 'font', 'units', 'sizes', 'fonts', 'flags', 'missing')
    @property
    def text(self): return logical(self.units)

def _lines(self, pno):
    """the lines of a page, from its top: Line(box, size and font of most of it, units: its glyphs'
    texts as seen from the left, with the size, font and flags of each)"""
    page = self.doc[pno]
    boxes = []
    for block in page.get_text('rawdict')['blocks']:
        for line in block.get('lines', []):
            ys = [c['origin'][1] for s in line['spans'] for c in s['chars']]
            if ys: boxes.append((line['bbox'], sorted(ys)[len(ys) // 2]))
    groups = collections.defaultdict(list)  # line -> [spans: [(x, x1, text, size, font, flags)]]
    drawn = set()
    for tr in page.get_texttrace():
        # (bold made by drawing the text again, in outline: once is enough)
        key = (tuple(round(v, 1) for v in tr['bbox']), len(tr['chars']), tr['font'])
        if key in drawn: continue
        drawn.add(key)
        if tr.get('type') == 3: continue  # not shown
        fm = self.fontmap(pno, tr['font'])
        row = []
        for ucs, gid, origin, bbox in tr['chars']:
            plain = ucs > 0 and ucs != 0xFFFD and not 0xE000 <= ucs <= 0xF8FF and not is_arabic(chr(ucs))
            # (the font's own tables are for its Arabic, which the pdf's characters get wrong: its
            # other characters are right as the pdf gives them, and some fonts' tables are not)
            if fm is not None and gid in fm.map and (is_arabic(fm.map[gid]) or not plain): text = fm.map[gid]
            elif (tr['font'], ucs) in SYMBOLS: text = SYMBOLS[(tr['font'], ucs)]
            elif (tr['font'], gid) in self.overrides: text = self.overrides[(tr['font'], gid)]
            elif ucs > 0 and ucs != 0xFFFD and not 0xE000 <= ucs <= 0xF8FF: text = _plain(chr(ucs))
            else: text = self.unknown(tr['font'], gid)
            if (tr['font'], gid) in self.overrides: text = self.overrides[(tr['font'], gid)]
            x, y = origin
            best = None
            for k, (b, base) in enumerate(boxes):
                if b[0] - 3 <= x <= b[2] + 3 and b[1] - 2 <= y <= b[3] + 2:
                    d = abs(base - y)
                    if best is None or d < best[0]: best = (d, k)
            row.append([best[1] if best else -1, y, (x, bbox[2], text, tr['size'], tr['font'], tr['flags'],
                        fm.alts.get(gid) if fm is not None else None, fm is not None and gid in fm.joins)])
        # a mark is on the line of its letter (the one drawn after it, or else before it), though
        # it may be set lower or higher
        for i, item in enumerate(row):
            text = item[2][2]
            if not (text and all(is_mark(c) for c in text)): continue
            for j in list(range(i + 1, min(i + 4, len(row)))) + list(range(i - 1, max(i - 4, -1), -1)):
                other = row[j][2][2]
                if other.strip() and not all(is_mark(c) for c in other) and abs(row[j][1] - item[1]) < tr['size']:
                    item[0] = row[j][0]; break
        current = None; span = None
        for k, y, glyph in row:
            if k != current or span is None:
                span = []; groups[k].append(span); current = k
            span.append(glyph)
    # what is on one baseline is one line (the cells of a table, the words of a verse set apart)
    keys = sorted((k for k in groups if k >= 0), key=lambda k: boxes[k][1])
    merged = []
    for k in keys:
        if merged and abs(boxes[k][1] - merged[-1][0]) <= 2.5: merged[-1][1].append(k)
        else: merged.append([boxes[k][1], [k]])
    out = []
    for base, ks in merged:
        spans = [s for k in ks for s in groups[k]]
        spans.sort(key=lambda s: min(g[0] for g in s))
        glyphs = [g[:6] + (n,) + g[6:] for n, s in enumerate(spans) for g in s]
        line = Line(); line.page = pno
        line.x0 = min(boxes[k][0][0] for k in ks); line.y0 = min(boxes[k][0][1] for k in ks)
        line.x1 = max(boxes[k][0][2] for k in ks); line.y1 = max(boxes[k][0][3] for k in ks)
        units = []; sizes = []; fonts = []; flags = []
        # an Arabic mark is drawn before or after its letter: it goes with the letter it is over.
        # So does a letter's dot drawn apart from it (it tells which letter a ligature has).
        def attached(g):
            t = g[2]
            if not t: return False
            if all(is_mark(c) for c in t): return True
            return is_arabic(t) and len(t) == 1 and g[1] - g[0] < 0.01 * g[3]
        # (a space drawn where a letter is, to carry the letter's mark, is no space)
        inked = [(g[0], g[1]) for g in glyphs if g[2].strip() and g[1] - g[0] > 0.01 * g[3]]
        def carrier(g):
            if g[2].strip(): return False
            if g[1] - g[0] < 0.12 * g[3]: return True  # (a sliver between joined letters)
            return any(min(g[1], b) - max(g[0], a) > 0.5 * min(g[1] - g[0], b - a) for a, b in inked)
        glyphs = [g for g in glyphs if not carrier(g)]
        bases = [i for i, g in enumerate(glyphs) if not attached(g)]
        base_set = set(bases)
        marks = collections.defaultdict(list)
        loose = []
        for i, g in enumerate(glyphs):
            if i in base_set or not g[2]: continue
            x = g[0]; size = g[3]
            # it is drawn just before its letter (as seen from the left), a little to its left
            j = i + 1
            while j < len(glyphs) and j not in base_set and glyphs[j][6] == g[6]: j += 1
            if j < len(glyphs) and j in base_set and glyphs[j][6] == g[6] and is_arabic(glyphs[j][2]) \
                    and glyphs[j][0] - 0.2 * size <= x <= glyphs[j][1] + 0.5:
                marks[j].append((x, g[2])); continue
            shifted = x + 0.06 * size
            near = [j for j in bases if is_arabic(glyphs[j][2]) and abs(glyphs[j][0] - x) < 3 * size]
            over = [j for j in near if glyphs[j][0] - 0.3 <= shifted <= glyphs[j][1] + 0.3]
            if over: j = min(over, key=lambda j: abs((glyphs[j][0] + glyphs[j][1]) / 2 - shifted))
            elif near: j = min(near, key=lambda j: min(abs(glyphs[j][0] - shifted), abs(glyphs[j][1] - shifted)))
            else:
                if all(is_mark(c) for c in g[2]): loose.append(i)
                continue
            marks[j].append((x, g[2]))
        previous = None
        for i in sorted(base_set | set(loose)):
            x, x1, text, size, font, flag, _, alts, joins = glyphs[i]
            if i in marks:
                over = marks[i]
                dots = [t for _, t in over if not all(is_mark(c) for c in t)]
                over = [(mx, t) for mx, t in over if all(is_mark(c) for c in t)]
                if alts:
                    # the ligature is of the letter its dot is of; without one, of the letter without dots
                    chosen = [a for a in alts if any(d in a for d in dots)] if dots else \
                             [a for a in alts if not any(c in a for c in 'جخ')]
                    if chosen: text = sorted(chosen, key=_rank)[0]
                # of a ligature's letters the first is the rightmost: its marks are given from the
                # right, a letter taking one vowel (or sukun) and one doubling, the next the rest
                letters = len(text)
                slots = [[] for _ in range(letters)]
                k = 0
                # (a mark drawn twice, for bold, is one)
                once = []
                for mx, t in sorted(over, key=lambda m: -m[0]):
                    if not any(t == u and abs(mx - ux) < 0.04 * size for ux, u in once): once.append((mx, t))
                for mx, t in once:
                    for c in t:
                        vowel = c != SHADDAH
                        while k < letters - 1 and (text[k] == ALIF or c in slots[k]
                                                   or vowel and any(m != SHADDAH for m in slots[k])):
                            k += 1
                        if c not in slots[k]: slots[k].append(c)
                text = ''.join(c + ''.join(sorted(m, key=lambda c: c != SHADDAH)) for c, m in zip(text, slots))
            arabic = is_arabic(text)
            if previous is not None and text.strip() and units and units[-1].strip():
                gap = x - previous
                # (this letter is read before the one on its left: if it is joined to it there is no
                # space between them; and Arabic words are set apart by spaces, their letters at
                # times by more than a little)
                if gap > 0.25 * size and not (arabic and (joins or gap < 0.6 * size and is_arabic(units[-1]))):
                    units.append(' '); sizes.append(size); fonts.append(font); flags.append(flag)
            units.append(text); sizes.append(size); fonts.append(font); flags.append(flag)
            if text.strip() or x1 > x: previous = max(x1, x)
        line.units, line.sizes, line.fonts, line.flags = units, sizes, fonts, flags
        # (glyphs that could not be read: of a font without tables of its own)
        line.missing = sum(1 for g in glyphs if g[2] == '' and g[1] - g[0] > 0.12 * g[3])
        weight = collections.Counter()
        for u, s, f in zip(units, sizes, fonts):
            if u.strip(): weight[(round(s, 1), f)] += len(u)
        if not weight: continue
        (line.size, line.font), _ = weight.most_common(1)[0]
        out.append(line)
    out.sort(key=lambda l: (round(l.y0 / 4), l.x0))
    return out

Doc.lines = _lines
Doc.overrides = {}
Doc.unknown = lambda self, font, gid: ''
