"""repair.py <audio files...>: where a recording's letters (emit.py) are blank though there is
sound (the model at times stops recognising within a piece), the place is recognised again in
pieces cut otherwise, and the best taken. Rewrites <file>.emit.npy; prints what was mended."""
import sys, os, numpy as np, torch, torchaudio
os.environ.pop('QUANT', None)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import emit
SR = 16000; FPS = 50

def letters(e):  # how much of each frame is a letter (not blank)
    return 1 - np.exp(e[:, 0].astype(np.float32))

def dead_regions(e, wave):
    n = min(e.shape[0], len(wave) // 320)
    rms = np.sqrt((wave[:n * 320].reshape(n, 320) ** 2).mean(axis=1))
    sound = (rms > 0.08 * np.percentile(rms, 90)).astype(np.float32)
    said = (letters(e)[:n] > 0.5).astype(np.float32)
    w = 4 * FPS; kernel = np.ones(w) / w
    quiet = (np.convolve(sound, kernel, 'same') > 0.6) & (np.convolve(said, kernel, 'same') < 0.02)
    regions = []; start = None
    for i, q in enumerate(np.append(quiet, False)):
        if q and start is None: start = i
        elif not q and start is not None:
            a, b = max(0, start - 2 * FPS), min(n, i + 2 * FPS)
            if regions and a <= regions[-1][1]: regions[-1][1] = b
            else: regions.append([a, b])
            start = None
    return regions

def run(wave, a, b):
    """the letters of the frames a to b, recognised in a piece from a - before to b + after (frames)"""
    piece = torch.from_numpy(wave[a * 320:b * 320 + 80]).unsqueeze(0)
    with torch.inference_mode(): out, _ = emit.model(piece)
    return torch.log_softmax(out[0], dim=-1).numpy()

def mend(path):
    target = path + '.emit.npy'
    e = np.load(target).astype(np.float32); wave = emit.audio(path)
    mended = []
    for a, b in dead_regions(e, wave):
        best = None
        for before, after in ((3, 3), (6, 1), (1, 6), (0, 0), (9, 2), (2, 9)):
            x, y = max(0, a - before * FPS), min(e.shape[0], b + after * FPS)
            if y - x > 40 * FPS: continue
            got = run(wave, x, y)[a - x:b - x]
            if len(got) < b - a: got = np.concatenate([got, e[a + len(got):b]])
            score = (letters(got) > 0.5).mean()
            if best is None or score > best[0]: best = (score, got[:b - a])
        was = (letters(e[a:b]) > 0.5).mean()
        if best and best[0] > was + 0.03:
            e[a:b] = best[1]; mended.append((round(a / FPS, 1), round(b / FPS, 1), round(float(was), 3), round(float(best[0]), 3)))
        else: mended.append((round(a / FPS, 1), round(b / FPS, 1), round(float(was), 3), 'not mended'))
    if mended:
        np.save(target + '.tmp.npy', e.astype(np.float16)); os.rename(target + '.tmp.npy', target)
    return mended

if __name__ == '__main__':
    torch.set_num_threads(2)
    for path in sys.argv[1:]:
        m = mend(path)
        if m: print(path, m, flush=True)
    print('REPAIRED', flush=True)
