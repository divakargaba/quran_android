"""Forced alignment of a poem's verses to a recording's emissions (emit.py)."""
import re, numpy as np, torch, torchaudio, uroman as ur
LABELS = torchaudio.pipelines.MMS_FA.get_labels(star='*')
INDEX = {c: i for i, c in enumerate(LABELS)}
STAR = INDEX['*']
FPS = 50.0
_uroman = ur.Uroman()
TASHKEEL = re.compile('[ً-ْٰـۖ-ۭ]')

def tokens(text, vowels=True):
    if not vowels: text = TASHKEEL.sub('', text)
    r = _uroman.romanize_string(text).lower()
    return [INDEX[c] for c in r if c in INDEX and c not in '-*']

def align(emission, verses, vowels=True):
    """emission: [T, C] log-probs; verses: texts. Returns [(start s, end s, score)] per verse."""
    T = emission.shape[0]
    e = np.concatenate([emission.astype(np.float32), np.zeros((T, 1), np.float32)], axis=1)
    seq = [STAR]; spans = []
    for v in verses:
        t = tokens(v, vowels)
        spans.append((len(seq), len(seq) + len(t)))
        seq += t + [STAR]
    aligned, scores = torchaudio.functional.forced_align(torch.from_numpy(e).unsqueeze(0), torch.tensor([seq]), blank=0)
    aligned, scores = aligned[0].numpy(), scores[0].numpy()
    # the token of each frame (-1 on blanks): a token starts where a frame's label is not blank
    # and differs from the frame before
    change = (aligned != 0) & (aligned != np.concatenate([[0], aligned[:-1]]))
    pos = np.where(aligned != 0, np.cumsum(change) - 1, -1)
    assert change.sum() == len(seq), (change.sum(), len(seq))
    out = []
    for a, b in spans:
        frames = np.nonzero((pos >= a) & (pos < b))[0]
        if len(frames) == 0: out.append((None, None, -99.0)); continue
        f0, f1 = frames[0], frames[-1]
        inside = scores[f0:f1 + 1]
        out.append((f0 / FPS, (f1 + 1) / FPS, float(np.mean(inside))))
    return out
