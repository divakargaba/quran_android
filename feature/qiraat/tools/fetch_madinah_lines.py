"""Downloads the words of each page of the 15 line Madinah mushaf, with the line each is on, from
api.quran.com (mushaf 2: the 1405 Madinah mushaf of the reader's page images), for
build_layout.py.

usage: fetch_madinah_lines.py <out dir>
"""
import json, sys, urllib.request, concurrent.futures, os, time

out = sys.argv[1]
os.makedirs(out, exist_ok=True)

def get(page):
    path = f'{out}/{page:03d}.json'
    if os.path.exists(path): return
    url = (f'https://api.quran.com/api/v4/verses/by_page/{page}?words=true'
           '&word_fields=line_number,page_number,text_uthmani&per_page=50&mushaf=2')
    for attempt in range(6):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'curl/8.5.0'})
            data = json.loads(urllib.request.urlopen(request, timeout=60).read())
            json.dump(data['verses'], open(path, 'w'), ensure_ascii=False)
            return
        except Exception:
            time.sleep(3 + 5 * attempt)
    sys.exit(f'unable to fetch page {page}')

with concurrent.futures.ThreadPoolExecutor(3) as pool:
    list(pool.map(get, range(1, 605)))
