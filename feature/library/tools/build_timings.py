"""build_timings.py <poem.json> <out.json> <emission files of the recording's tracks, in order>

Where each verse of a poem is recited in a recording. The tracks' letters are recognised
(emit.py); each verse is given to the track that has it (by its text in what is recognised of the
track, the verses being in order across the tracks); then the verses of each track are aligned to
its sound (align.py). Every verse's alignment is checked against what is recognised in its span.
Writes {"v": [[track, start ms] or null for each verse]} and prints what is doubtful."""
import sys, json, subprocess, numpy as np
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
import align
from rapidfuzz import fuzz

LETTERS = align.LABELS
ABSENT = 50.0  # a verse found nowhere this well is not in the recording
HEARD = 45     # a verse is where it is aligned if what is recognised there is this like it (of 100)
LEAD = 0.30    # a verse is played from this long before its first letter (s), or from the middle of the pause before it

def voiced(emission_file):
    """for each frame of a track, whether there is sound in it (against the loud of the track)"""
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', emission_file[:-len('.emit.npy')], '-ac', '1', '-ar', '16000', '-f', 's16le', '-'],
                         capture_output=True, check=True).stdout
    wave = np.frombuffer(raw, dtype=np.int16).astype(np.float32)
    n = len(wave) // 320
    rms = np.sqrt((wave[:n * 320].reshape(n, 320) ** 2).mean(axis=1))
    return rms > 0.08 * np.percentile(rms, 90)

def greedy(e):
    ids = e.argmax(1)
    keep = (ids != 0) & (ids != np.concatenate([[0], ids[:-1]]))
    frames = np.nonzero(keep)[0]
    return ''.join(LETTERS[i] for i in ids[frames]), frames

def romanized(verse):
    return ''.join(LETTERS[i] for i in align.tokens(verse, False))

def assign(verses, decodes):
    """the track of each verse (None: in none), the verses being in order across the tracks"""
    V, K = len(verses), len(decodes)
    score = np.zeros((V, K + 1), np.float32)
    for v, text in enumerate(verses):
        for k, d in enumerate(decodes):
            score[v, k] = fuzz.partial_ratio(text, d) if d else 0
    score[:, K] = ABSENT
    # best[v][k]: the verses to v, v in track k (K: absent, which keeps the last track's place)
    best = np.full((V, K, 2), -1e9, np.float32); back = np.zeros((V, K, 2), np.int32)
    # state (k, present?) : present=1 verse v is in track k; present=0 verse v absent, tracks up to k used
    best[0, :, 1] = score[0, :K]; best[0, 0, 0] = ABSENT
    for v in range(1, V):
        run = -1e9; arg = 0
        for k in range(K):
            for p in (1, 0):
                if best[v - 1, k, p] > run: run, arg = best[v - 1, k, p], k * 2 + p
            best[v, k, 1] = run + score[v, k]; back[v, k, 1] = arg
            # absent after tracks up to k: from the best state at exactly k
            b = max((best[v - 1, k, 1], k * 2 + 1), (best[v - 1, k, 0], k * 2))
            best[v, k, 0] = b[0] + ABSENT; back[v, k, 0] = b[1]
    k, p = divmod(int(best[V - 1].argmax()), 2)
    out = [None] * V
    for v in range(V - 1, -1, -1):
        out[v] = k if p else None
        if v: k, p = divmod(int(back[v, k, p]), 2)
    # a verse found badly between verses of one track is in it; one between two tracks is in the
    # one it is found better in, if found at all
    known = [(v, k) for v, k in enumerate(out) if k is not None]
    for (v0, k0), (v1, k1) in zip(known, known[1:]):
        for v in range(v0 + 1, v1):
            if k0 == k1: out[v] = k0
    changed = True
    while changed:
        changed = False
        for v in range(V):
            if out[v] is not None: continue
            before = out[v - 1] if v else None; after = out[v + 1] if v + 1 < V else None
            options = [(score[v, k], k) for k in (before, after) if k is not None and score[v, k] >= ABSENT - 5]
            if options: out[v] = max(options)[1]; changed = True
    return out, score

def build(poem, emission_files):
    chapters = json.load(open(poem))['chapters']
    verses = [a + ' ' + b for c in chapters for a, b in c['v']]
    texts = [romanized(v) for v in verses]
    emissions = [np.load(f).astype(np.float32) for f in emission_files]
    decodes = [greedy(e) for e in emissions]
    if len(emissions) == 1: track_of = [0] * len(verses)
    else: track_of, _ = assign(texts, [d for d, _ in decodes])
    result = [None] * len(verses); report = []
    for k, e in enumerate(emissions):
        mine = [v for v, t in enumerate(track_of) if t == k]
        if not mine: continue
        assert mine == list(range(mine[0], mine[-1] + 1)), (k, mine[:5])
        spans = align.align(e, [verses[v] for v in mine], False)
        decoded, frames = decodes[k]
        previous_end = 0.0
        usual = np.median([b - a for a, b, _ in spans if a is not None])
        sound = None
        for v, (start, end, score) in zip(mine, spans):
            if start is None: report.append((v + 1, k, 'not aligned')); continue
            a, b = np.searchsorted(frames, start * align.FPS - 2), np.searchsorted(frames, end * align.FPS + 2)
            ratio = fuzz.ratio(texts[v], decoded[a:b])
            if ratio < HEARD:
                # the verse is not recognised where it is aligned: it is there all the same if it is
                # as long there as the others and is sound (the recognition is weak in places);
                # otherwise the recording has not got it (a cut, a silence)
                if sound is None: sound = voiced(emission_files[k])
                there = sound[int(start * align.FPS):int(end * align.FPS)]
                if not (0.6 * usual <= end - start <= 1.7 * usual and len(there) and there.mean() >= 0.6):
                    report.append((v + 1, k, 'not heard', round(start, 2), round(end, 2), round(ratio))); continue
            play = max((previous_end + start) / 2, start - LEAD)
            result[v] = [k, int(round(play * 1000))]
            report.append((v + 1, k, round(start, 2), round(end, 2), round(ratio), round(score, 2)))
            previous_end = end
    return result, report, track_of, [e.shape[0] / align.FPS for e in emissions]

if __name__ == '__main__':
    poem, out = sys.argv[1:3]
    result, report, track_of, lengths = build(poem, sys.argv[3:])
    json.dump({'v': result}, open(out, 'w'), separators=(',', ':'))
    json.dump(report, open(out + '.report.json', 'w'))
    missing = [i + 1 for i, r in enumerate(result) if r is None]
    rows = [r for r in report if len(r) == 6 and r[2] != 'not heard']
    ratios = np.array([r[4] for r in rows]); durations = np.array([r[3] - r[2] for r in rows])
    print(f'{len(rows)} of {len(result)} verses timed; missing: {missing[:40]}{"..." if len(missing) > 40 else ""} ({len(missing)})')
    print(f'match of what is recognised: median {np.median(ratios):.0f}, 5th percentile {np.percentile(ratios, 5):.0f}, lowest {ratios.min():.0f}')
    print(f'verse length: median {np.median(durations):.1f}s, shortest {durations.min():.1f}s, longest {durations.max():.1f}s')
    used = sorted({r[1] for r in rows})
    print('tracks without verses:', [k for k in range(len(lengths)) if k not in used])
    print('not heard:', [(r[0], r[5]) for r in report if len(r) == 6 and r[2] == 'not heard'])
    rows = [r for r in rows if r[2] != 'not heard']
    for r in rows:
        d = r[3] - r[2]
        if r[4] < 60 or d < np.median(durations) * 0.6 or d > np.median(durations) * 1.8: print('  doubtful:', r[0], r[1], float(r[2]), float(r[3]), r[4])
