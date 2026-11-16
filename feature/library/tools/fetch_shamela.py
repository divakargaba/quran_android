"""fetch_shamela.py <out dir> <book id> ...: saves each page of the books as <out>/<book>/<page>.html"""
import sys, os, re, time, urllib.request
out = sys.argv[1]
def get(url):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            return urllib.request.urlopen(req, timeout=60).read().decode('utf-8', 'replace')
        except Exception as e:
            err = e; time.sleep(2 * (attempt + 1))
    raise err
for book in sys.argv[2:]:
    d = f'{out}/{book}'; os.makedirs(d, exist_ok=True)
    page = 1; missing = 0
    while True:
        f = f'{d}/{page}.html'
        if not os.path.exists(f):
            try: s = get(f'https://shamela.ws/book/{book}/{page}')
            except Exception as e:
                s = ''
            if 'class="nass' not in s:
                missing += 1
                if missing >= 3: break
                page += 1; continue
            open(f, 'w', encoding='utf-8').write(s); time.sleep(0.3)
        missing = 0; page += 1
    print(book, len(os.listdir(d)), 'pages', flush=True)
