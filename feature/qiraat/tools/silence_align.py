"""Estimates ayah start/end times in a sura recording that has no published timings.

Finds pauses (and, where a reciter joins ayat without pausing, dips in loudness) in the audio and
picks one per ayah boundary with dynamic programming, scoring each ayah's duration against its
share of the sura's text (the width of its words in the mushaf) and preferring real pauses. The
recording may open with an isti'adha and basmalah before the first ayah.
"""
import subprocess, numpy as np

FRAME = 0.02  # seconds

def energy_db(url_or_path):
    raw = subprocess.run(['ffmpeg', '-v', 'quiet', '-i', url_or_path, '-ac', '1', '-ar', '8000',
                          '-f', 's16le', '-'], capture_output=True, check=True).stdout
    pcm = np.frombuffer(raw, dtype=np.int16).astype(np.float32)
    n = int(8000 * FRAME)
    frames = pcm[: len(pcm) // n * n].reshape(-1, n)
    rms = np.sqrt((frames ** 2).mean(axis=1) + 1e-6)
    db = 20 * np.log10(rms + 1e-6)
    return np.convolve(db, np.ones(3) / 3, mode='same')

def candidates(db, threshold_ratio=0.28, min_pause=0.2, dip_window=0.6):
    """[(time, pause length)], speech start, speech end"""
    lo, hi = np.percentile(db, 10), np.percentile(db, 95)
    silent = db < lo + threshold_ratio * (hi - lo)
    speech = np.nonzero(~silent)[0]
    start, end = speech[0] * FRAME, (speech[-1] + 1) * FRAME
    out = []
    run = None
    for i, s in enumerate(silent):
        if s and run is None: run = i
        if not s and run is not None:
            length = (i - run) * FRAME
            t = (run + i) / 2 * FRAME
            if length >= min_pause and start < t < end: out.append((t, length))
            run = None
    # quieter moments that are not pauses, for ayat joined without one
    w = int(dip_window / FRAME)
    smooth = np.convolve(db, np.ones(5) / 5, mode='same')
    for i in range(w, len(db) - w, w // 2):
        seg = smooth[i - w // 2: i + w // 2]
        j = i - w // 2 + int(np.argmin(seg))
        t = j * FRAME
        if start < t < end and not silent[j] and smooth[j] < np.percentile(smooth[max(0, j - 50): j + 50], 15):
            out.append((t, 0.0))
    out.sort()
    times = np.array([start] + [t for t, _ in out]); lengths = np.array([0.0] + [l for _, l in out])
    return times, lengths, start, end

def align(db, widths, intro_width, sigma=0.35, pause_weight=1.5, dip_cost=2.5, intro_sigma=0.5):
    """returns [(start, end)] seconds for each ayah"""
    times, lengths, start, end = candidates(db)
    widths = np.asarray(widths, dtype=float)
    widths = np.maximum(widths, widths.mean() * 0.15)
    rate = (end - start) / (widths.sum() + intro_width)
    best = None
    for _ in range(3):
        result = _dp(times, lengths, end, widths, intro_width, rate, sigma, pause_weight, dip_cost,
                     intro_sigma)
        if result is None: break
        best = result
        rate = (end - result[0][0]) / widths.sum()
    return best

def _boundary_cost(lengths, pause_weight, dip_cost):
    return np.where(lengths > 0, -pause_weight * np.minimum(lengths, 1.0), dip_cost)

def _dp(times, lengths, end, widths, intro_width, rate, sigma, pause_weight, dip_cost, intro_sigma):
    n = len(widths); m = len(times)
    expected = rate * widths
    bcost = _boundary_cost(lengths, pause_weight, dip_cost)
    INF = 1e18
    cost = np.full((n, m), INF); back = np.full((n, m), -1, dtype=int)
    # the first ayah starts after an optional opening (isti'adha and/or basmalah)
    lead = times - times[0]
    intro = rate * intro_width
    for j in range(m):
        if lead[j] > 60: break
        if j == 0:
            cost[0, j] = 0.5
        else:
            # a basmalah, or an isti'adha and a basmalah
            fit = min(abs(np.log(lead[j] / (f * intro))) for f in (1.0, 2.2, 3.0))
            cost[0, j] = (fit / intro_sigma) ** 2 + bcost[j]
    for k in range(1, n):
        prev = cost[k - 1]
        longest = expected[k - 1] * 4 + 8
        for j in range(1, m):
            lo = np.searchsorted(times, times[j] - longest)
            hi = np.searchsorted(times, times[j] - 0.3, side='right')
            if hi <= lo: continue
            p = prev[lo:hi]
            if p.min() >= INF / 2: continue
            d = times[j] - times[lo:hi]
            total = p + (np.log(d / expected[k - 1]) / sigma) ** 2
            i = int(np.argmin(total))
            if total[i] >= INF / 2: continue
            cost[k, j] = total[i] + bcost[j]
            back[k, j] = lo + i
    d = end - times
    final = cost[n - 1] + np.where(d > 0.3, (np.log(np.maximum(d, 1e-3) / expected[-1]) / sigma) ** 2, INF)
    j = int(np.argmin(final))
    if final[j] >= INF / 2: return None
    starts = [0] * n; idx = j
    for k in range(n - 1, -1, -1):
        starts[k] = idx; idx = back[k, idx]
    return [(times[starts[k]], times[starts[k + 1]] if k + 1 < n else end) for k in range(n)]
