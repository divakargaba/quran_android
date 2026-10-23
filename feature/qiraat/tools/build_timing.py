"""Builds per-ayah audio timings (src/main/assets/qiraat/<riwayah>.qrt) from mp3quran.net's
ayat_timing data.

A recording numbers ayat in its own count, which is not always the count of our mushaf for that
riwayah. For each sura we map Hafs ayat to the recording's ayat through the first riwayah mushaf
whose ayah count for that sura equals the recording's (the riwayah's own mushaf first), falling
back to a proportional mapping.

usage: build_timing.py <index dir with *.meta.json> <timing json dir> <out dir> <riwayah>=<read id> ...
format (gzipped, big endian): "QRT1", u16 ayah count, then per hafs ayah: u32 start ms, u32 end ms
(both 0xFFFFFFFF when the recording does not have that sura).
"""
import sys, os, json, gzip, struct
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from madani import AYAHS

meta_dir, timing_dir, out_dir = sys.argv[1:4]
jobs = [a.split('=') for a in sys.argv[4:]]
ORDER = ['hafs','shubah','khalaf','khallad','abulharith','duri_kisai','ishaq','idris','warsh','qalun',
         'ibnwardan','ibnjammaz','bazzi','qunbul','duri_abuamr','susi','ruways','rawh','hisham','ibndhakwan']
metas = {n: json.load(open(f'{meta_dir}/{n}.meta.json')) for n in ORDER}
starts = [0]
for c in AYAHS: starts.append(starts[-1] + c)

def numbering(name, sura):
    return metas[name]['number'][starts[sura-1]:starts[sura]]

def recording(read, sura):
    p = f'{timing_dir}/{read}_{sura:03d}.json'
    if not os.path.exists(p): return None
    entries = json.load(open(p))
    if not entries: return None
    if sura == 1 and len(entries) == 7 and entries[0]['ayah'] == 0:
        # basmalah numbered 0 and the rest 1..6: in the kufi count the basmalah is ayah 1
        return [(e['start_time'], e['end_time']) for e in entries]
    return [(e['start_time'], e['end_time']) for e in entries if e['ayah'] >= 1]

for name, read in jobs:
    out = bytearray(b'QRT1') + struct.pack('>H', starts[-1])
    stats = {'own': 0, 'other': 0, 'proportional': 0, 'missing': 0}
    for sura in range(1, 115):
        times = recording(read, sura)
        n_hafs = AYAHS[sura-1]
        if times is None:
            out += struct.pack('>II', 0xFFFFFFFF, 0xFFFFFFFF) * n_hafs
            stats['missing'] += 1
            continue
        mapping = None
        for candidate in [name] + ORDER:
            numbers = numbering(candidate, sura)
            if max(numbers) == len(times) and numbers[-1] == len(times):
                mapping = numbers
                stats['own' if candidate == name else 'other'] += 1
                break
        if mapping is None:
            mapping = [min(len(times), -(-(k * len(times)) // n_hafs)) for k in range(1, n_hafs + 1)]
            stats['proportional'] += 1
        for number in mapping:
            start, end = times[number - 1]
            out += struct.pack('>II', start, end)
    open(f'{out_dir}/{name}.qrt', 'wb').write(gzip.compress(bytes(out), 9))
    print(name, read, stats)
