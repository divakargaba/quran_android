"""Splits the text lines of a riwayah mushaf PDF into words, from the rendered page (where the
words actually are) rather than the text layer (whose glyph boxes are unreliable in some of the
PDFs): a word is a run of inked columns, and gaps narrower than a space join pieces of a word."""
import numpy as np, pymupdf

DPI = 150
REACH_PT = 12  # how far past the text layer's extent of a line a letter can reach
PAD_PT = 0.6  # room left around a word's ink
# the fonts of the text of the mushafs (the HQPB fonts, and those of the marks some use)
QURAN_FONTS = ('HQPB', 'MSH-Quraan', 'MshQuraan', 'Hamd')

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

def words_in_band(ink, r0, r1, t0, t1, gap_px, reach, pad):
    """word extents (pixel columns, left to right) of the rows r0:r1 of a page's ink, of the words
    that reach into columns t0:t1"""
    W = ink.shape[1]
    c0, c1 = max(0, t0 - reach), min(W, t1 + reach)
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
    # only the words of the line (what reaches past it from them, not the frame of the page)
    runs = [(a, b) for a, b in runs if c0 + b > t0 and c0 + a < t1]
    # a little room around each word for the edges of its letters, up to half way to the next
    padded = []
    for i, (a, b) in enumerate(runs):
        left = min(pad, (a - runs[i - 1][1]) / 2) if i > 0 else pad
        right = min(pad, (runs[i + 1][0] - b) / 2) if i + 1 < len(runs) else pad
        padded.append((max(0, c0 + a - left), min(W, c0 + b + right)))
    return padded

def page_ink(doc, page):
    pix = doc[page - 1].get_pixmap(dpi=DPI)
    img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)[:, :, :3]
    lo, hi = img.min(axis=2).astype(int), img.max(axis=2).astype(int)
    # dark, or coloured (the letters some mushafs colour are light in grey)
    return (lo + hi < 340) | (hi - lo > 80)

def words_on_page(doc, stream, page, gap_pt=2.0):
    """{line id: [(x0, x1)]} word extents (fractions of the page width), right to left"""
    ink = page_ink(doc, page)
    H, W = ink.shape
    gap_px = gap_pt * DPI / 72
    reach = int(REACH_PT * DPI / 72)
    pad = PAD_PT * DPI / 72
    result = {}
    lines = lines_of_page(stream, page)
    # the text layer's extent of the lines of the page (that of a line can leave out some of its
    # words), and as far again as a letter can reach past it (the head of an initial ain, the
    # tail of a final ya)
    x0 = min((l[2] for l in lines.values()), default=0); x1 = max((l[3] for l in lines.values()), default=1)
    t0, t1 = max(0, int(x0 * W) - 3), min(W, int(np.ceil(x1 * W)) + 3)
    for line, (y0, y1, _, _, _) in lines.items():
        r0, r1 = int(y0 * H), int(np.ceil(y1 * H))
        result[line] = [(a / W, b / W) for a, b in reversed(words_in_band(ink, r0, r1, t0, t1, gap_px, reach, pad))]
    return result

def unlisted_lines(doc, stream, all_lines, page, skip):
    """lines of text on the page that its text layer does not have (some pdfs leave out a line):
    {(y0, y1): [(x0, x1)] right to left}, away from the lines it has and from [skip] (fractions of
    the page height: the places of sura titles, whose frames have no text in some pdfs)"""
    body = lines_of_page(stream, page)
    if len(body) < 2: return {}
    ink = page_ink(doc, page)
    H, W = ink.shape
    centers = sorted((l[0] + l[1]) / 2 for l in body.values())
    step = float(np.median([b - a for a, b in zip(centers, centers[1:])]))
    height = float(np.median([l[1] - l[0] for l in body.values()]))
    x0 = min(l[2] for l in body.values()); x1 = max(l[3] for l in body.values())
    t0, t1 = int(x0 * W), int(np.ceil(x1 * W))
    inked = ink[:, t0:t1].sum(axis=1) > 3 * DPI / 72
    covered = np.zeros(H, bool)
    for l in all_lines.values():
        covered[max(0, int((l[0] - step * 0.1) * H)):int((l[1] + step * 0.1) * H) + 1] = True
    for a, b in skip: covered[max(0, int(a * H)):int(b * H) + 1] = True
    # and what the pdf has there in other fonts than the text's (headers, footers) or as pictures
    # (the site's logo). (A line in the text's fonts can still be one stream.py left out, at the
    # foot of a page.)
    pg = doc[page - 1]
    boxes = [span['bbox'] for block in pg.get_text('dict')['blocks'] for line in block.get('lines', [])
             for span in line['spans'] if span['text'].strip()
             and not span['font'].split('+')[-1].startswith(QURAN_FONTS)]
    boxes += [info['bbox'] for info in pg.get_image_info()]
    for x0_, y0_, x1_, y1_ in boxes:
        if x1_ - x0_ < pg.rect.width * 0.9:
            covered[max(0, int(y0_ / pg.rect.height * H)):int(y1_ / pg.rect.height * H) + 1] = True
    top = max(0, int((centers[0] - step) * H)); bottom = min(H, int((centers[-1] + step * 1.2) * H))
    result = {}
    run = 0
    for y in range(top, bottom + 1):
        if y < bottom and inked[y] and not covered[y]: run += 1; continue
        if run > height * H * 0.4:
            yc = (y - run / 2) / H
            ya, yb = yc - step * 0.45, yc + step * 0.45
            words = words_in_band(ink, int(ya * H), int(np.ceil(yb * H)), t0, t1, 2.6 * DPI / 72,
                                  int(REACH_PT * DPI / 72), PAD_PT * DPI / 72)
            if words: result[(ya, yb)] = [(a / W, b / W) for a, b in reversed(words)]
        run = 0
    return result
