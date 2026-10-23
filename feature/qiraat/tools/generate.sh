#!/usr/bin/env bash
# Regenerates src/main/assets/qiraat/*.qrx (where each Hafs ayah sits in each riwayah's mushaf).
#
# 1. downloads the islamweb riwayah mushafs from archive.org (item quran-islamweb.net)
# 2. extracts words and ayah markers with PyMuPDF (stream.py)
# 3. aligns every riwayah's ayah markers to the Hafs mushaf (align.py). Most riwayat are aligned
#    by text against Khalaf's mushaf (same font encoding) and through it to Hafs, which handles
#    the places where a riwayah's page breaks drift from the Madinah layout.
# 4. writes one compact file per riwayah (build_index.py)
#
# Audio timings (*.qrt) are built separately: build_timing.py from mp3quran.net's ayat_timing data
# (using the *.meta.json files written here), and build_estimated_timing.py, with
# silence_align.py, for recordings without published timings.
#
# requires: python3, pymupdf (pip install pymupdf), numpy, ffmpeg, curl
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
