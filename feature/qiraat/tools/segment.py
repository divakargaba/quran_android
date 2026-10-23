"""Splits the text lines of a riwayah mushaf PDF into words, from the rendered page (where the
words actually are) rather than the text layer (whose glyph boxes are unreliable in some of the
PDFs): a word is a run of inked columns, and gaps narrower than a space join pieces of a word."""
import numpy as np, pymupdf

DPI = 150

def lines_of_page(stream, page):
    """{line id: (y0, y1, x0, x1, has marker)} from the text layer, as fractions of the page; the
    glyph boxes of some mushafs reach into the next line, so a line is kept within 0.45 of the
    distance between lines from its middle"""
    out = {}
    for t in stream:
        if t[0] != page: continue
        l = out.setdefault(t[1], [1, 0, 1, 0, False])
        l[0] = min(l[0], t[3]); l[1] = max(l[1], t[5]); l[2] = min(l[2], t[2]); l[3] = max(l[3], t[4])
        if t[6] == 'm': l[4] = True
    centers = sorted((l[0] + l[1]) / 2 for l in out.values())
    steps = sorted(b - a for a, b in zip(centers, centers[1:]) if b - a > 0.01)
    if steps:
        half = steps[len(steps) // 2] * 0.45
        for l in out.values():
            c = (l[0] + l[1]) / 2
            l[0] = max(l[0], c - half); l[1] = min(l[1], c + half)
    return out

def words_on_page(doc, stream, page, gap_pt=2.0):
    """{line id: [(x0, x1)]} word extents (fractions of the page width), right to left"""
    p = doc[page - 1]
    pix = p.get_pixmap(dpi=DPI)
    img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)[:, :, :3]
    lo, hi = img.min(axis=2).astype(int), img.max(axis=2).astype(int)
    # dark, or coloured (the letters some mushafs colour are light in grey)
    ink = (lo + hi < 340) | (hi - lo > 80)
    gap_px = gap_pt * DPI / 72
    result = {}
    for line, (y0, y1, x0, x1, _) in lines_of_page(stream, page).items():
        r0, r1 = int(y0 * pix.height), int(np.ceil(y1 * pix.height))
        c0, c1 = max(0, int(x0 * pix.width) - 3), min(pix.width, int(np.ceil(x1 * pix.width)) + 3)
        cols = ink[r0:r1, c0:c1].any(axis=0)
        runs = []; start = None; blank = 0
        for i, c in enumerate(cols):
            if c:
                if start is None: start = i
                elif blank >= gap_px:
                    runs.append((start, last + 1)); start = i
                last = i; blank = 0
            else:
                blank += 1
        if start is not None: runs.append((start, last + 1))
        result[line] = [((c0 + a) / pix.width, (c0 + b) / pix.width) for a, b in reversed(runs)]
    return result
