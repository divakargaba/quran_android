"""Builds estimated per-ayah timings (<riwayah>.qrt) for recordings without published ones.

usage: build_estimated_timing.py <index dir with *.meta.json> <energy dir> <out dir> <riwayah> ...
The energy dir holds <riwayah>_<sura>.npy loudness envelopes (silence_align.energy_db) of the
riwayah's recording; a sura without one is marked as not recorded.
"""
import sys, os, json, gzip, struct, numpy as np
from concurrent.futures import ProcessPoolExecutor
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import silence_align as sa
from madani import AYAHS

meta_dir, energy_dir, out_dir = sys.argv[1:4]
starts = [0]
for c in AYAHS: starts.append(starts[-1] + c)
intro = json.load(open(f'{meta_dir}/hafs.meta.json'))['widths'][0]  # the basmalah

def sura_times(args):
    name, sura = args
    path = f'{energy_dir}/{name}_{sura:03d}.npy'
    if not os.path.exists(path): return None
    meta = json.load(open(f'{meta_dir}/{name}.meta.json'))
    marks = meta['marker'][starts[sura - 1]:starts[sura]]
    first = meta['marker'][starts[sura - 1] - 1] + 1 if sura > 1 else 0
    widths = meta['widths'][first:marks[-1] + 1]
    result = sa.align(np.load(path), widths, intro)
    if result is None: return None
    numbers = meta['number'][starts[sura - 1]:starts[sura]]
    return [(int(result[n - 1][0] * 1000), int(result[n - 1][1] * 1000)) for n in numbers]

if __name__ == '__main__':
    for name in sys.argv[4:]:
        with ProcessPoolExecutor(4) as ex:
            suras = list(ex.map(sura_times, [(name, s) for s in range(1, 115)]))
        out = bytearray(b'QRT1') + struct.pack('>H', starts[-1])
        for sura, times in enumerate(suras, 1):
            if times is None:
                out += struct.pack('>II', 0xFFFFFFFF, 0xFFFFFFFF) * AYAHS[sura - 1]
            else:
                for start, end in times: out += struct.pack('>II', start, end)
        open(f'{out_dir}/{name}.qrt', 'wb').write(gzip.compress(bytes(out), 9))
        print(name, 'suras', sum(t is not None for t in suras), flush=True)
