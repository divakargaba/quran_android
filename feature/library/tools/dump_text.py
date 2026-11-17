"""dump_text.py <pdf> <out.txt> [first page] [last page]: a pdf's text (read by its glyphs), page by page"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pdftext
d = pdftext.Doc(sys.argv[1])
a = int(sys.argv[3]) if len(sys.argv) > 3 else 1
b = int(sys.argv[4]) if len(sys.argv) > 4 else len(d.doc)
with open(sys.argv[2], 'w') as out:
    for pno in range(a - 1, b):
        out.write(f'\n===== page {pno + 1} =====\n')
        for line in d.lines(pno):
            t = line.text
            if t: out.write(t + '\n')
