"""Parts of a book's pages, as the app shows them under a poem's verses (build_inline.py)."""
import numpy as np, pymupdf

def sides(spans):
    """spans: [(page, x0, x1)] of the lines of a book's text. Returns (left of even pages, left of
    odd pages, width): books bound in two sides have their text nearer the fold."""
    left = {}; right = {}
    for parity in (0, 1):
        x0 = sorted(a for p, a, b in spans if p % 2 == parity); x1 = sorted(b for p, a, b in spans if p % 2 == parity)
        if not x0: continue
        left[parity] = x0[len(x0) // 50]; right[parity] = x1[-1 - len(x1) // 50]
    for parity in (0, 1):
        if parity not in left: left[parity], right[parity] = left[1 - parity], right[1 - parity]
    width = max(right[p] - left[p] for p in (0, 1)) + 6
    return left[0] - 3, left[1] - 3, width

def trim(doc, page, top, bottom, left, width):
    """a part of a page (pt), cut to its ink, and to within any rule across it by its top or bottom
    (the frame of the verse before or after); None if it has no ink"""
    scale = 2.0
    clip = pymupdf.Rect(left, max(0, top), left + width, min(doc[page].rect.height, bottom))
    if clip.height <= 1: return None
    pix = doc[page].get_pixmap(matrix=pymupdf.Matrix(scale, scale), clip=clip, colorspace=pymupdf.csGRAY)
    img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width)
    dark = img < 160
    rows = dark.mean(axis=1)
    h = len(rows); edge = int(8 * scale)
    a, b = 0, h
    for y in range(min(edge, h)):
        if rows[y] > 0.5: a = y + 1
    for y in range(h - 1, max(h - edge, 0) - 1, -1):
        if rows[y] > 0.5: b = y
    ink = np.nonzero(rows[a:b] > 0)[0]
    if len(ink) == 0: return None
    a, b = a + ink[0], a + ink[-1] + 1
    return [page, round(clip.y0 + a / scale - 2), round(clip.y0 + b / scale + 2)]

def pack(doc, entries, left_even, left_odd, width, number=lambda n: n):
    """entries: [[first, last, [[page, top, bottom]...] (translation), [...] (explanation)]] with the
    book's numbers; number(n): the poem's number of the book's verse n (None: not in the poem).
    Returns the app's: {"w": width, "e": [[first, last, [[page, left, top, bottom]...], [...]]]}"""
    out = []
    for first, last, translation, explanation in entries:
        numbers = [number(n) for n in range(first, last + 1)]
        numbers = [n for n in numbers if n is not None]
        if not numbers: continue
        parts = []
        for regions in (translation, explanation):
            kept = []
            for page, top, bottom in regions:
                left = left_odd if page % 2 else left_even
                r = trim(doc, page, top, bottom, left, width)
                if r: kept.append([r[0], round(left), r[1], r[2]])
            parts.append(kept)
        if parts[0] or parts[1]: out.append([numbers[0], numbers[-1], parts[0], parts[1]])
    return dict(w=round(width), e=out)
