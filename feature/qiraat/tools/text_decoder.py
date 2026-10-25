"""Reads the words of the islamweb riwayah mushafs as Unicode text.

The mushafs' text layer is in the encoding of their (KFGQPC "HQPB") fonts: one token per word,
one character per glyph (a letter in its contextual form, or a mark), in visual order. A table
from each glyph to the Unicode it stands for is learned (EM, with each glyph standing for 0 to 3
characters) from the riwayat whose text KFGQPC publishes in Unicode, and a word is read either
as a whole (when the same glyphs were seen in that text) or glyph by glyph, with a character
language model choosing between the readings of each glyph (beam search).
"""
import math, collections, unicodedata

MARK_ORDER = {c: i for i, c in enumerate(
    'ًٌٍَُِّْٰٕٖۡٓٔ'
    'ٜ۪ۭٗٞ۬ۢۥۦۧۨ۟۠ۤ')}
# pause marks, rub el hizb, sajdah, joiners: not part of a word
PAUSES = set('ۖۗۘۙۚۛۜ۞۩‍‏')

def canon(word):
    """the word with the marks of each letter in a fixed order, without pause marks"""
    out, marks = [], []
    for ch in word:
        if ch in PAUSES: continue
        if unicodedata.category(ch) == 'Mn' or ch == 'ٰ':
            marks.append(ch)
        else:
            out += sorted(marks, key=lambda c: MARK_ORDER.get(c, 99)); marks = []
            out.append(ch)
    out += sorted(marks, key=lambda c: MARK_ORDER.get(c, 99))
    return ''.join(out)

MAX_OUTPUT = 3

def train(pairs, iterations=10, log=print):
    """the glyph -> Unicode table, from (glyphs, unicode word) pairs"""
    t = collections.defaultdict(lambda: collections.defaultdict(lambda: 1e-3))
    for it in range(iterations):
        counts = collections.defaultdict(collections.Counter)
        total = 0.0
        for code, word in pairs:
            c = code[::-1]; I, J = len(c), len(word)
            if I == 0 or J == 0 or J > 4 * I: continue
            A = [[0.0] * (J + 1) for _ in range(I + 1)]; A[0][0] = 1.0
            for i in range(I):
                ti = t[c[i]]
                for j in range(J + 1):
                    a = A[i][j]
                    if a == 0: continue
                    for l in range(MAX_OUTPUT + 1):
                        if j + l > J: break
                        A[i + 1][j + l] += a * ti[word[j:j + l]]
            Z = A[I][J]
            if Z <= 0: continue
            total += math.log(Z)
            B = [[0.0] * (J + 1) for _ in range(I + 1)]; B[I][J] = 1.0
            for i in range(I - 1, -1, -1):
                ti = t[c[i]]
                for j in range(J + 1):
                    s = 0.0
                    for l in range(MAX_OUTPUT + 1):
                        if j + l > J: break
                        s += ti[word[j:j + l]] * B[i + 1][j + l]
                    B[i][j] = s
            for i in range(I):
                ti = t[c[i]]
                for j in range(J + 1):
                    a = A[i][j]
                    if a == 0: continue
                    for l in range(MAX_OUTPUT + 1):
                        if j + l > J: break
                        p = a * ti[word[j:j + l]] * B[i + 1][j + l] / Z
                        if p > 1e-6: counts[c[i]][word[j:j + l]] += p
        nt = collections.defaultdict(lambda: collections.defaultdict(lambda: 1e-6))
        for cc, cnt in counts.items():
            tot = sum(cnt.values())
            for s, v in cnt.items(): nt[cc][s] = v / tot
        t = nt
        log(f'iteration {it}: log likelihood {total:.0f}')
    return {cc: dict(v) for cc, v in t.items()}

N = 6

class LanguageModel:
    """character N-gram model of words (Witten-Bell smoothing)"""
    def __init__(self, words):
        self.c = collections.defaultdict(collections.Counter)
        for w in words:
            s = '^' * (N - 1) + w + '$'
            for i in range(N - 1, len(s)):
                for k in range(N):
                    self.c[s[i - k:i]][s[i]] += 1
        self.total = {h: sum(v.values()) for h, v in self.c.items()}
        self.types = {h: len(v) for h, v in self.c.items()}
        self.V = len(self.c[''])

    def p(self, history, ch):
        h = history[-(N - 1):]
        pr = 1.0 / (self.V + 1)
        for k in range(len(h) + 1):
            ctx = h[len(h) - k:]
            if ctx not in self.c: break
            t, ty = self.total[ctx], self.types[ctx]
            lam = t / (t + ty)
            pr = lam * self.c[ctx][ch] / t + (1 - lam) * pr
        return pr

class Decoder:
    def __init__(self, table, lm, known, width=40, bonus=3.0):
        self.table, self.lm, self.known, self.width, self.bonus = table, lm, known, width, bonus

    def _candidates(self, ch, k=8):
        if ch not in self.table: return [('', 1.0)]
        items = sorted(self.table[ch].items(), key=lambda kv: -kv[1])
        return [(s, p) for s, p in items[:k] if p > 0.003] or items[:1]

    def decode(self, code):
        """(word, whether it was read as a whole)"""
        if code in self.known: return self.known[code], True
        beams = [('^' * (N - 1), 0.0)]
        for ch in code[::-1]:
            nb = {}
            for hist, sc in beams:
                for s, p in self._candidates(ch):
                    h = hist; lp = sc + math.log(p)
                    for x in s:
                        lp += math.log(self.lm.p(h, x)) + self.bonus; h += x
                    if h not in nb or nb[h] < lp: nb[h] = lp
            beams = sorted(nb.items(), key=lambda kv: -kv[1])[:self.width]
        best = max(beams, key=lambda kv: kv[1] + math.log(self.lm.p(kv[0], '$')))
        return best[0][N - 1:], False
