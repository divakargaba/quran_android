"""the lines of the Jazariyyah's translation (abouttajweed.com), its Arabic put right: in the
pdf's text the regular Arabic font gives its fathah as kha' and its sukun as ha', and the bold one
a space as dammah (told apart by their glyphs). The Arabic within the English is given without
its vowel marks, whose order in the text is not that of the letters."""
import sys, re, json, pymupdf
MARKS = re.compile('[ً-ْٰـ]')
def lines(path):
    doc = pymupdf.open(path); out = []
    for pno, page in enumerate(doc):
        gids = {}
        for tr in page.get_texttrace():
            bold = 'Bold' in tr['font']; arabic = 'Arabic' in tr['font']
            for ucs, gid, origin, bbox in tr['chars']:
                gids[(round(origin[0], 1), round(origin[1], 1))] = (gid, bold, arabic)
        for block in page.get_text('rawdict')['blocks']:
            for line in block.get('lines', []):
                text = ''; size = 0; bold_line = False
                for span in line['spans']:
                    size = max(size, span['size'])
                    for ch in span['chars']:
                        c = ch['c']; o = ch['origin']
                        gid, bold, arabic = gids.get((round(o[0], 1), round(o[1], 1)), (None, False, False))
                        if arabic and not bold and (c, gid) in (('خ', 200), ('ح', 204)): continue
                        if arabic and bold and c == 'ُ' and gid == 3: c = ' '
                        if arabic and bold: bold_line = True
                        text += c
                text = re.sub(r'\s+', ' ', text).strip()
                if text: out.append(dict(page=pno, y=round(line['bbox'][1]), size=round(size, 1), verse=bold_line and size >= 13 and not re.search('[A-Za-z]{3}', text), text=text))
    return out
if __name__ == '__main__':
    json.dump(lines(sys.argv[1]), open(sys.argv[2], 'w'), ensure_ascii=False, indent=0)
