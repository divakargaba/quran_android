"""Downloads, for every ayah, nquran.com's account of how the ten qurra and their twenty rawis
read each of its words where they differ (the Shatibiyyah and Durrah path), for
build_differences.py.

usage: fetch_differences.py <out dir>
"""
import sys, os, gzip, time, urllib.request, concurrent.futures
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from madani import AYAHS

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
URL = 'https://www.nquran.com/ar/index.php?group=tb1&tpath=2&aya_no={a}&sorano={s}&mRwai='

def get(sa):
    s, a = sa
    path = f'{out}/{s:03d}{a:03d}.html.gz'
    if os.path.exists(path): return
    for attempt in range(6):
        try:
            request = urllib.request.Request(URL.format(s=s, a=a), headers={'User-Agent': 'Mozilla/5.0'})
            page = urllib.request.urlopen(request, timeout=90).read()
            # only the part after the list of ayat is needed
            i = page.find(b'<div id="detail">')
            if i < 0: raise IOError('no detail')
            open(path, 'wb').write(gzip.compress(page[i:]))
            return
        except Exception:
            time.sleep(3 + 5 * attempt)
    print('failed', s, a, flush=True)

with concurrent.futures.ThreadPoolExecutor(4) as pool:
    list(pool.map(get, [(s, a) for s in range(1, 115) for a in range(1, AYAHS[s - 1] + 1)]))
