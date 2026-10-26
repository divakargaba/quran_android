"""Where each word of a riwayah mushaf is cut out of its page (build_layout.py), so that every
piece of ink of the text is drawn once, whole, with the word it belongs to: a pause mark set over
the gap after a word, or high between two lines, is drawn with that word, and the bits of the
words of the lines above and below that reach into a word's band are not.

Each connected piece of ink goes with the line whose band (its pitch, around its middle) holds its
middle, and on it with the word it overlaps most (or, between words, the nearest; the one before
it when as near). A word is cut out as its box, widened to its pieces, in vertical slices: in each,
from above its own ink to below it, and clear of ink that is not its own where it can be.
"""
import numpy as np, pymupdf
from scipy import ndimage

DPI = 150
NEAR_PT = 8  # how far a piece between words can be from the word it goes with
MAX_SLICES = 8
UNIT = 100  # the slices' top and bottom are given in 1/UNIT of the line's band


def page_ink(doc, page):
    """the inked pixels of a pdf page (as segment.py finds them)"""
    pix = doc[page - 1].get_pixmap(dpi=DPI)
    img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)[:, :, :3]
    lo, hi = img.min(axis=2).astype(int), img.max(axis=2).astype(int)
    return (lo + hi < 340) | (hi - lo > 80)


def word_crops(ink, words, centers, pitch, pw, ph, headings=()):
    """words: [(line, x0, x1)] (page fractions) of a pdf page; centers: {line: middle (fraction)};
    headings: the middles of its sura titles and basmalahs, whose ink no word takes.
    Returns for each word (x0, x1, [(slice end, top, bottom)]): its extent (fractions), and its
    slices from the left, each to [slice end] (a fraction of the word's width) with its top and
    bottom as distances in from those of the line's band (in 1/UNIT of the band; out if < 0)."""
    H, W = ink.shape
    px = H / ph  # px per pt
    band = pitch * px
    labels, n = ndimage.label(ink, structure=np.ones((3, 3), bool))
    if n == 0: return [(x0, x1, [(1.0, 0, 0)]) for _, x0, x1 in words]
    ids = np.arange(1, n + 1)
    rows = np.array(ndimage.center_of_mass(ink, labels, ids))[:, 0]
    objects = ndimage.find_objects(labels)
    centers = dict(centers)
    for i, middle in enumerate(headings): centers[('heading', i)] = middle
    lines = sorted(centers, key=lambda l: centers[l])
    mids = np.array([centers[l] * H for l in lines])
    by_line = {}
    for i, (line, x0, x1) in enumerate(words):
        by_line.setdefault(line, []).append((i, x0 * W, x1 * W))
    owner = np.zeros(n + 1, int) - 1  # word index of each piece (-2: a heading's, -1: none)
    near = NEAR_PT * px
    for c in range(n):
        sl = objects[c]
        if sl[0].stop - sl[0].start > band * 1.6 or sl[1].stop - sl[1].start > W * 0.4:
            continue  # a frame or ornament of the page, not text
        line = lines[int(np.argmin(np.abs(mids - rows[c])))]
        if abs(centers[line] * H - rows[c]) > band * 0.75: continue
        if line not in by_line: owner[c + 1] = -2; continue  # a heading's
        cols = objects[c][1]
        a, b = cols.start, cols.stop
        best, best_score = -1, None
        for i, x0, x1 in by_line[line]:
            overlap = min(b, x1) - max(a, x0)
            if overlap > 0: score = (2, overlap, 0)
            else:
                gap = max(x0 - b, a - x1)
                if gap > near: continue
                # as near: the word before it (to its right)
                score = (1, -gap, x0)
            if best_score is None or score > best_score: best, best_score = i, score
        owner[c + 1] = best
    result = []
    for i, (line, x0, x1) in enumerate(words):
        yc = centers[line] * H
        e0, e1 = yc - band / 2, yc + band / 2
        r0, r1 = max(0, int(e0 - band)), min(H, int(e1 + band) + 1)
        own_ids = np.nonzero(owner == i)[0]
        c0, c1 = int(x0 * W), int(np.ceil(x1 * W))
        for c in own_ids:
            sl = objects[c - 1]
            c0 = min(c0, sl[1].start); c1 = max(c1, sl[1].stop)
            r0 = min(r0, sl[0].start); r1 = max(r1, sl[0].stop)
        c0, c1 = max(0, c0), min(W, c1)
        if c1 <= c0: result.append((x0, x1, [(1.0, 0, 0)])); continue
        lab = labels[r0:r1, c0:c1]
        own = np.isin(lab, own_ids)
        other = (lab > 0) & ~own & (owner[lab] != -1)  # the ink of other words, and of headings
        h = r1 - r0
        idx = np.arange(h)[:, None]
        has_own = own.any(axis=0)
        top_own = np.where(has_own, own.argmax(axis=0), h)
        bot_own = np.where(has_own, h - 1 - own[::-1].argmax(axis=0), -1)
        middle = int(yc) - r0
        upper_limit = np.where(has_own, top_own, middle)
        lower_limit = np.where(has_own, bot_own, middle)
        above = other & (idx < upper_limit)
        below = other & (idx > lower_limit)
        # the highest top and lowest bottom each column allows
        lo_top = np.where(above.any(axis=0), h - above[::-1].argmax(axis=0), 0)
        hi_bot = np.where(below.any(axis=0), below.argmax(axis=0) - 1, h - 1)
        want0, want1 = e0 - r0, e1 - r0
        slices = []
        start = 0
        while start < c1 - c0:
            t_lo, t_hi, b_lo, b_hi = lo_top[start], top_own[start], bot_own[start], hi_bot[start]
            end = start + 1
            while end < c1 - c0 and len(slices) < MAX_SLICES - 1:
                nt_lo = max(t_lo, lo_top[end]); nt_hi = min(t_hi, top_own[end])
                nb_lo = max(b_lo, bot_own[end]); nb_hi = min(b_hi, hi_bot[end])
                if nt_lo > nt_hi or nb_lo > nb_hi: break
                t_lo, t_hi, b_lo, b_hi = nt_lo, nt_hi, nb_lo, nb_hi
                end += 1
            if len(slices) == MAX_SLICES - 1: end = c1 - c0
            top = min(max(want0, t_lo), max(t_hi, t_lo)) if t_lo <= t_hi else want0
            bottom = max(min(want1 - 1, b_hi), min(b_lo, b_hi)) if b_lo <= b_hi else want1 - 1
            slices.append([end, top, bottom + 1])
            start = end
        out = []
        for end, top, bottom in slices:
            t = int(round((top - want0) / band * UNIT)); b = int(round((want1 - bottom) / band * UNIT))
            out.append((end / (c1 - c0), max(-127, min(127, t)), max(-127, min(127, b))))
        # neighbouring slices alike are one
        merged = [out[0]]
        for s in out[1:]:
            if s[1:] == merged[-1][1:]: merged[-1] = s
            else: merged.append(s)
        result.append((c0 / W, c1 / W, merged))
    return result


def slice_rects(x0, x1, slices, yc, half):
    """the source rectangles (pt: x0, x1, y0, y1) of a word's slices (x0, x1 in pt)"""
    rects = []; start = x0
    for end, top, bottom in slices:
        x = x0 + (x1 - x0) * end
        rects.append((start, x, yc - half + top / UNIT * 2 * half, yc + half - bottom / UNIT * 2 * half))
        start = x
    return rects
