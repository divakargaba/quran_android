"""Renders pages of a riwayah's 15-line layout (build_layout.py) the way the device does
(QiraatPagePreparer), to check a layout without building the app.

usage: preview_layout.py <layout.qrl> <mushaf.pdf> <out dir> <page> ...
"""
import sys, gzip, struct
import pymupdf
from PIL import Image, ImageDraw

WIDTH = 1260
HEIGHT = round(WIDTH * 1.6175)
MARGIN_X = 0.03
MARGIN_Y = 0.012
LINES = 15
STRIP_GAP = 5.5  # pt, as in build_layout.py
WORD_GAP = 3.2  # pt: narrower gaps are within a word, and are not widened
MAX_STRETCH = 0.05  # how much wider than in the mushaf the words of a line can be drawn
HEADING_MARGIN = 3.0  # pt of room above and below the ink of a basmalah


def read_layout(path):
    data = gzip.decompress(open(path, 'rb').read())
    assert data[:4] == b'QRL4'
    line_h, pitch, line_w, pw, ph = (v / 100 for v in struct.unpack_from('>HHHII', data, 4))
    off = 18
    pages = []
    for _ in range(604):
        (n,) = struct.unpack_from('>B', data, off); off += 1
        slots = []
        for _ in range(n):
            (kind,) = struct.unpack_from('>B', data, off); off += 1
            if kind == 0:
                slots.append(None)
            elif kind in (2, 3):
                sura, page, x0, x1, y0, y1 = struct.unpack_from('>BHHHHH', data, off); off += 11
                slots.append(('title' if kind == 2 else 'basmalah', sura, page,
                              x0 / 65535, x1 / 65535, y0 / 65535, y1 / 65535))
            else:
                (count,) = struct.unpack_from('>B', data, off); off += 1
                strips = []
                for _ in range(count):
                    page, y0, y1, ayah, nw = struct.unpack_from('>HHHHB', data, off); off += 9
                    words = []
                    for _ in range(nw):
                        x0, x1, flags, n = struct.unpack_from('>HHBB', data, off); off += 6
                        slices = []
                        for _ in range(n):
                            end, top, bottom = struct.unpack_from('>Bbb', data, off); off += 3
                            slices.append((end / 255, top / 100, bottom / 100))
                        ayah += flags >> 1
                        words.append((x0 / 65535, x1 / 65535, flags & 1, ayah, slices))
                    strips.append((page, y0 / 65535, y1 / 65535, words))
                slots.append(('text', strips))
        pages.append(slots)
    return dict(line_h=line_h, pitch=pitch, line_w=line_w, pw=pw, ph=ph, pages=pages)


def layout_line(strips, L, scale, avail, centered):
    """[(strip index, word, left px, width px)] of a text line, and the scale its words are drawn at"""
    pw, gap_between = L['pw'], STRIP_GAP
    pieces = []  # (strip, word, width pt, gap before pt), right to left
    for si, (_, _, _, words) in enumerate(strips):
        for wi, w in enumerate(words):
            if wi > 0: gap = (words[wi - 1][0] - w[1]) * pw
            elif si > 0: gap = gap_between
            else: gap = 0
            pieces.append((si, w, (w[1] - w[0]) * pw, max(0.0, gap)))
    if not pieces: return [], scale
    ink = sum(p[2] for p in pieces) * scale
    gaps = [p[3] * scale for p in pieces]
    natural = ink + sum(gaps)
    offset = 0.0
    s = scale
    stretch = 1.0
    if centered:
        offset = (avail - natural) / 2
    elif natural > avail:
        # tighten the word spacing (to half), then shrink the line
        need = natural - avail
        total = sum(gaps)
        cut = min(need, total * 0.5)
        gaps = [g - cut * g / total for g in gaps] if total else gaps
        if need > cut:
            f = avail / (ink + sum(gaps))
            s *= f; gaps = [g * f for g in gaps]; ink *= f
    else:
        # widen the words a little, then the spaces between them
        extra = avail - natural
        stretch = min(1 + MAX_STRETCH, 1 + extra / ink) if ink else 1
        extra -= ink * (stretch - 1)
        weights = [1.0 if g >= WORD_GAP * scale else 0.0 for g in gaps[1:]]
        total = sum(weights)
        if total == 0:
            offset = extra / 2
        else:
            used = min(extra, total * L['line_h'] * scale * 2)
            gaps = gaps[:1] + [g + used * w / total for g, w in zip(gaps[1:], weights)]
            offset = (extra - used) / 2
    out = []
    x = avail - offset
    for (si, w, width, _), g in zip(pieces, gaps):
        x -= g
        wpx = width * s * stretch
        out.append((si, w, x - wpx, wpx))
        x -= wpx
    return out, s


def render_page(L, doc, page):
    img = Image.new('RGB', (WIDTH, HEIGHT), 'white')
    draw = ImageDraw.Draw(img)
    slots = L['pages'][page - 1]
    mx = WIDTH * MARGIN_X
    pitch_px = HEIGHT * (1 - 2 * MARGIN_Y) / LINES
    top = HEIGHT * MARGIN_Y if len(slots) == LINES else (HEIGHT - len(slots) * pitch_px) / 2
    avail = WIDTH - 2 * mx
    scale = min(pitch_px / L['pitch'], avail / L['line_w'])
    pw, ph, half = L['pw'], L['ph'], L['pitch'] / 2

    def crop(pdf_page, x0, x1, y0, y1, s):
        """the pdf region [x0, x1] x [y0, y1] (pt) at s px/pt"""
        pix = doc[pdf_page - 1].get_pixmap(matrix=pymupdf.Matrix(s, s), clip=pymupdf.Rect(x0, y0, x1, y1))
        return Image.frombytes('RGB', (pix.width, pix.height), pix.samples)

    for i, slot in enumerate(slots):
        y_mid = top + (i + 0.5) * pitch_px
        if slot is None: continue
        if slot[0] == 'text':
            strips = slot[1]
            placed, s = layout_line(strips, L, scale, avail, page <= 2)
            for si, w, left, width in placed:
                pdf_page, y0, y1, _ = strips[si]
                yc = (y0 + y1) / 2 * ph
                start = 0.0
                for end, cut_top, cut_bottom in w[4]:
                    sy0, sy1 = yc - half + cut_top * 2 * half, yc + half - cut_bottom * 2 * half
                    if end > start and sy1 > sy0:
                        sx0 = (w[0] + (w[1] - w[0]) * start) * pw; sx1 = (w[0] + (w[1] - w[0]) * end) * pw
                        im = crop(pdf_page, sx0, sx1, sy0, sy1, s)
                        im = im.resize((max(1, round(width * (end - start))), im.height))
                        mask = im.convert('L').point(lambda v: 255 if v < 250 else 0)
                        img.paste(im, (round(mx + left + width * start), round(y_mid - half * s + cut_top * 2 * half * s)), mask)
                    start = end
        else:
            kind, sura, pdf_page, x0, x1, y0, y1 = slot
            if kind == 'title':
                fy0, fy1 = y_mid - pitch_px * 0.42, y_mid + pitch_px * 0.42
                draw.rounded_rectangle((mx, fy0, WIDTH - mx, fy1), radius=pitch_px * 0.2, outline=(60, 60, 60), width=3)
                draw.rounded_rectangle((mx + 8, fy0 + 8, WIDTH - mx - 8, fy1 - 8), radius=pitch_px * 0.15, outline=(60, 60, 60), width=1)
            if pdf_page == 0: continue
            s = min(scale, avail * 0.8 / ((x1 - x0) * pw))
            yc = (y0 + y1) / 2 * ph
            if kind == 'title': im = crop(pdf_page, x0 * pw, x1 * pw, yc - half, yc + half, s)
            else: im = crop(pdf_page, x0 * pw, x1 * pw, y0 * ph - HEADING_MARGIN, y1 * ph + HEADING_MARGIN, s)
            img.paste(im, (round(WIDTH / 2 - im.width / 2), round(y_mid - im.height / 2)))
    return img


if __name__ == '__main__':
    layout, pdf, out = sys.argv[1:4]
    L = read_layout(layout)
    doc = pymupdf.open(pdf)
    for p in sys.argv[4:]:
        render_page(L, doc, int(p)).save(f'{out}/page{int(p):03d}.png')
