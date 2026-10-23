#!/usr/bin/env bash
# Regenerates src/main/assets/qiraat/*.qrx (where each Hafs ayah sits in each riwayah's mushaf)
# and *.qrl (each riwayah's text laid out on the 15 line pages of the Madinah mushaf).
#
# 1. downloads the islamweb riwayah mushafs from archive.org (item quran-islamweb.net)
# 2. extracts words and ayah markers with PyMuPDF (stream.py)
# 3. aligns every riwayah's ayah markers to the Hafs mushaf (align.py). Most riwayat are aligned
#    by text against Khalaf's mushaf (same font encoding) and through it to Hafs, which handles
#    the places where a riwayah's page breaks drift from the Madinah layout.
# 4. writes one compact file per riwayah (build_index.py)
# 5. splits the lines of each mushaf into words (segment.py) and places them, ayah by ayah, on
#    the lines of the Madinah mushaf (build_layout.py, with api.quran.com's line data);
#    preview_layout.py draws pages of a layout the way the app does
#
# Audio timings (*.qrt) are built separately: build_timing.py from mp3quran.net's ayat_timing data
# (using the *.meta.json files written here), and build_estimated_timing.py, with
# silence_align.py, for recordings without published timings.
#
# requires: python3, pymupdf (pip install pymupdf), numpy, pillow (for previews), ffmpeg, curl
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
work="${1:-$(mktemp -d)}"
mkdir -p "$work/st" "$work/idx"
curl -sS "https://archive.org/metadata/quran-islamweb.net" > "$work/meta.json"
python3 -I - "$work" <<'PY'
import json, sys, urllib.parse
work = sys.argv[1]
d = json.load(open(f'{work}/meta.json'))
names = sorted(f['name'] for f in d['files'] if f['name'].endswith('.pdf') and 'اخرى' not in f['name'])
with open(f'{work}/urls.txt', 'w') as out:
    for i, n in enumerate(names):
        out.write(f"{i} https://archive.org/download/quran-islamweb.net/{urllib.parse.quote(n)}\n")
PY
while read -r i url; do
  [ -s "$work/r$i.pdf" ] || curl -sSL --retry 3 -o "$work/r$i.pdf" "$url"
  python3 -I -W ignore "$here/stream.py" "$work/r$i.pdf" "$work/st/r$i.pkl"
done < "$work/urls.txt"
# pdf index (sorted archive file names) -> riwayah id; 19 (Warsh via al-Asbahani) is not used
python3 -I -W ignore "$here/build_index.py" "$work" "$work/idx" \
  10,16,20,6,17,7,9,18,4,15,12,11,0,8,5,3,14,13,2,1 \
  hafs,qalun,warsh,bazzi,qunbul,duri_abuamr,susi,hisham,ibndhakwan,shubah,khalaf,khallad,abulharith,duri_kisai,ibnwardan,ibnjammaz,ruways,rawh,ishaq,idris
cp "$work"/idx/*.qrx "$here/../src/main/assets/qiraat/"
python3 -I "$here/fetch_madinah_lines.py" "$work/lines"
mkdir -p "$work/layout"
python3 -I -W ignore "$here/build_layout.py" "$work" "$work/lines" "$work/layout" \
  16=qalun 20=warsh 6=bazzi 17=qunbul 7=duri_abuamr 9=susi 18=hisham 4=ibndhakwan 15=shubah \
  12=khalaf 11=khallad 0=abulharith 8=duri_kisai 5=ibnwardan 3=ibnjammaz 14=ruways 13=rawh \
  2=ishaq 1=idris
cp "$work"/layout/*.qrl "$here/../src/main/assets/qiraat/"
