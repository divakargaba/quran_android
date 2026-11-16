"""encode_offline.py <catalog.json> <audio dir> <out dir>: compact copies of the recordings' tracks
(Opus in Ogg, mono, 22 kbit/s) for keeping them on the device: <out>/<recording>/<track>.ogg.
The audio dir has each recording's tracks as <dir>/<track>.mp3 (DIRS: the dir of a recording)."""
import sys, os, json, subprocess, concurrent.futures as cf
catalog, audio, out = sys.argv[1:4]
DIRS = {'shatibiyyah_afasy': 'shatibiyyah', 'durrah_afasy': 'durrah', 'jazariyyah_suwaid': 'jazariyyah', 'tuhfah_ghandar': 'tuhfah'}
jobs = []
for poem in json.load(open(catalog))['poems']:
    for r in poem['recitations']:
        if not r.get('base'): continue  # bundled with the app
        for i in range(len(r['tracks'])):
            jobs.append((f"{audio}/{DIRS.get(r['id'], r['id'])}/{i:03d}.mp3", f"{out}/{r['id']}/{i:03d}.ogg"))
def encode(job):
    src, dst = job
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if not os.path.exists(dst):
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-vn', '-map_metadata', '-1', '-ac', '1', '-c:a', 'libopus',
                        '-b:a', '22k', '-application', 'audio', '-f', 'ogg', dst + '.part'], check=True)
        os.rename(dst + '.part', dst)
    return dst
with cf.ThreadPoolExecutor(4) as ex: done = list(ex.map(encode, jobs))
print(len(done), 'tracks')
