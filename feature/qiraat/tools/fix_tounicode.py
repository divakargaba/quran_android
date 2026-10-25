"""Rewrites ToUnicode targets in the C1 control range (U+0080-009F) to the symbol-font PUA
(U+F080-F09F), as in the other islamweb mushafs, so text extraction keeps those glyphs."""
import pymupdf, re, sys
src, dst = sys.argv[1], sys.argv[2]
d = pymupdf.open(src); done = set(); n = 0
for i in range(len(d)):
    for f in d.get_page_fonts(i):
        xref = f[0]
        if xref in done: continue
        done.add(xref)
        t, v = d.xref_get_key(xref, 'ToUnicode')
        if t != 'xref': continue
        tx = int(v.split()[0])
        s = d.xref_stream(tx).decode('latin1')
        s2, k = re.subn(r'<00([89][0-9a-fA-F])>', lambda m: '<f0' + m.group(1) + '>', s)
        if k: d.update_stream(tx, s2.encode('latin1')); n += k
d.save(dst, garbage=0)
print(src, n, 'mappings fixed')
