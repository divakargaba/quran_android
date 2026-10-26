"""Checks a riwayah's 15-line layout (build_layout.py) against its mushaf, for what would show as
a misprint on the pages the device draws from it (preview_layout.py draws them the same way),
piece of ink by piece of ink (as crops.py finds them):

- left out: a piece of the text that no word draws (a mark, a letter, a word or a line)
- cut: a piece that a word draws only part of
- drawn twice: a piece (or part of one) that two words draw
- the ayat out of order or missing, ayah markers missing or doubled
- lines squeezed (text drawn much smaller than the rest) or sura titles missing

usage: check_layout.py <layout.qrl> <mushaf.pdf> <ayah markers in the mushaf> <out.json>
"""
import sys, json, collections
import numpy as np, pymupdf
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from preview_layout import read_layout, layout_line, HEADING_MARGIN, WIDTH, HEIGHT, MARGIN_X, MARGIN_Y, LINES
from madani import AYAHS
# the fonts of the text of the mushafs (the HQPB fonts, and those of the marks some use)
QURAN_FONTS = ('HQPB', 'MSH-Quraan', 'MshQuraan', 'Hamd')
from scipy import ndimage
import crops



def ayah_of(index):
    s = 0
    while index >= AYAHS[s]: index -= AYAHS[s]; s += 1
    return s + 1, index + 1


def check(qrl, pdf, marker_count):
    L = read_layout(qrl)
    doc = pymupdf.open(pdf)
    pw, ph, half = L['pw'], L['ph'], L['pitch'] / 2
    issues = collections.defaultdict(list)

    def add(kind, page, line, detail, **extra):
        issues[kind].append(dict(page=page, line=line, detail=detail, **extra))

    # the words of each line of the mushaf, and the order of the ayat
    by_pdf = collections.defaultdict(list)  # pdf page -> [(y0, y1, x0, x1, page, line, marker)]
    headings = collections.defaultdict(list)  # pdf page -> [(y0, y1)]
    head_crops = collections.defaultdict(list)  # pdf page -> [(y0, y1, x0, x1)]
    seen_ayat = set(); last_ayah = -1; markers = 0; titles = []
    mx = WIDTH * MARGIN_X; avail = WIDTH - 2 * mx
    pitch_px = HEIGHT * (1 - 2 * MARGIN_Y) / LINES
    scale = min(pitch_px / L['pitch'], avail / L['line_w'])
    for p, slots in enumerate(L['pages'], 1):
        for li, slot in enumerate(slots, 1):
            if slot is None: continue
            if slot[0] != 'text':
                kind, sura, pdf_page, x0, x1, y0, y1 = slot
                if kind == 'title': titles.append(sura)
                if pdf_page:
                    headings[pdf_page].append((y0, y1))
                    head_crops[pdf_page].append((y0, y1, x0, x1, kind == 'title'))
                continue
            strips = slot[1]
            for pdf_page, y0, y1, words in strips:
                for x0, x1, marker, ayah, slices in words:
                    by_pdf[pdf_page].append((y0, y1, x0, x1, p, li, marker,
                                             [(e, t * crops.UNIT, b * crops.UNIT) for e, t, b in slices]))
                    if ayah < last_ayah:
                        add('ayah order', p, li, f'{ayah_of(ayah)} after {ayah_of(last_ayah)}')
                    last_ayah = max(last_ayah, ayah); seen_ayat.add(ayah)
                    markers += marker
            placed, s = layout_line(strips, L, scale, avail, p <= 2)
            if p > 2 and s < scale * 0.8:
                add('squeezed line', p, li, f'text drawn at {s / scale:.0%} of its size')
    missing = [ayah_of(i) for i in range(sum(AYAHS)) if i not in seen_ayat]
    if missing: issues['ayat without words'].append(dict(detail=missing))
    if markers != marker_count:
        issues['ayah markers'].append(dict(detail=f'{markers} markers drawn of the {marker_count} in the mushaf'))
    expected = list(range(1, 115))
    if titles != expected:
        issues['sura titles'].append(dict(detail=f'{len(titles)} titles, missing '
                                          f'{sorted(set(expected) - set(titles))}'))

    # where the text of a page is, from the first to the last line, on most pages
    tops = sorted(min(w[0] for w in ws) for ws in by_pdf.values())
    bottoms = sorted(max(w[1] for w in ws) for ws in by_pdf.values())
    usual_top, usual_bottom = tops[len(tops) // 10], bottoms[len(bottoms) * 9 // 10]
    # the mushaf's pages: how many times the words draw each pixel of its ink
    for pdf_page in sorted(by_pdf):
        words = by_pdf[pdf_page]
        ink = crops.page_ink(doc, pdf_page)
        H, W = ink.shape
        px = H / ph
        labels, n = ndimage.label(ink, structure=np.ones((3, 3), bool))
        drawn = np.zeros((H, W), np.uint8)
        for y0, y1, x0, x1, p, li, marker, slices in words:
            yc = (y0 + y1) / 2 * ph
            for sx0, sx1, sy0, sy1 in crops.slice_rects(x0 * pw, x1 * pw, slices, yc, half):
                a0, a1 = int(round(sx0 * px)), int(round(sx1 * px)); b0, b1 = int(round(sy0 * px)), int(round(sy1 * px))
                if a1 > a0 and b1 > b0: drawn[max(0, b0):b1, max(0, a0):a1] += 1
        # (and the sura titles and basmalahs, from their lines: PageLayout.placeHeading)
        for a, b, x0, x1, title in set(head_crops[pdf_page]):  # (a basmalah can be drawn for more suras)
            yc = (a + b) / 2 * ph
            top, bottom = (yc - half, yc + half) if title else (a * ph - HEADING_MARGIN, b * ph + HEADING_MARGIN)
            drawn[max(0, int(round(top * px))):int(round(bottom * px)),
                  max(0, int(round(x0 * pw * px))):int(round(x1 * pw * px))] += 1
        objects = ndimage.find_objects(labels)
        # the text of the page: between its first and last lines, and across its words
        top = min(min(w[0] for w in words), usual_top) * H
        bottom = max(max(w[1] for w in words), usual_bottom) * H
        left = min(w[2] for w in words) * W; right = max(w[3] for w in words) * W
        heads = [(a * H, b * H) for a, b in headings[pdf_page]]
        # what is written in the page's other fonts (its running header and footer, the page
        # number) is not text of the mushaf
        other_text = np.zeros((H, W), bool)
        for block in doc[pdf_page - 1].get_text('dict')['blocks']:
            for line in block.get('lines', []):
                for span in line['spans']:
                    if not span['font'].split('+')[-1].startswith(QURAN_FONTS) and span['text'].strip():
                        x0, y0, x1, y1 = span['bbox']
                        other_text[max(0, int(y0 * px)):int(y1 * px) + 1, max(0, int(x0 * px)):int(x1 * px) + 1] = True
        # nor are the pictures on it (the logo of the site)
        for info in doc[pdf_page - 1].get_image_info():
            x0, y0, x1, y1 = info['bbox']
            if (x1 - x0) * (y1 - y0) < pw * ph * 0.5:
                other_text[max(0, int(y0 * px)):int(y1 * px) + 1, max(0, int(x0 * px)):int(x1 * px) + 1] = True
        lines_at = sorted({round((w[0] + w[1]) / 2 * H) for w in words})
        def where(sl):
            yc = (sl[0].start + sl[0].stop) / 2
            near = min(lines_at, key=lambda y: abs(y - yc))
            w = next((w for w in words if abs((w[0] + w[1]) / 2 * H - near) < 2), words[0])
            return w[4], w[5]
        for c, sl in enumerate(objects, 1):
            if sl[0].stop - sl[0].start > L['pitch'] * px * 1.6 or sl[1].stop - sl[1].start > W * 0.4:
                continue  # a frame or ornament of the page
            part = labels[sl] == c
            size = int(part.sum())
            if size < 4 or other_text[sl][part].mean() > 0.5: continue
            counts = drawn[sl][part]
            lost = int((counts == 0).sum()); twice = int((counts >= 2).sum())
            box = [sl[1].start / W, sl[0].start / H, sl[1].stop / W, sl[0].stop / H]
            yc = (sl[0].start + sl[0].stop) / 2; xc = (sl[1].start + sl[1].stop) / 2
            if lost == size:
                inside = top - L['pitch'] * px <= yc <= bottom + L['pitch'] * px and left <= xc <= right
                in_heading = any(a - L['pitch'] * px * 0.5 <= yc <= b + 2.2 * L['pitch'] * px for a, b in heads)
                if inside and not in_heading and size >= 8:
                    p, li = where(sl)
                    add('left out', p, li, f'{size} px of ink', pdf_page=pdf_page, box=box)
            elif lost >= 3:
                p, li = where(sl)
                add('cut', p, li, f'{lost} of {size} px left out', pdf_page=pdf_page, box=box)
            if twice >= 3:
                p, li = where(sl)
                add('drawn twice', p, li, f'{twice} of {size} px', pdf_page=pdf_page, box=box)
    return {k: v for k, v in issues.items()}


if __name__ == '__main__':
    qrl, pdf, counts, out = sys.argv[1:5]
    result = check(qrl, pdf, int(counts))
    json.dump(result, open(out, 'w'), ensure_ascii=False, indent=1)
    for k, v in result.items(): print(f'{k}: {len(v)}')
