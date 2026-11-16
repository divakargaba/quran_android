"""emit.py <audio files...>: the frame-wise letter log-probabilities (MMS_FA, 50 frames/s) of each
recording, saved beside it as <file>.emit.npy (float16)"""
import sys, os, subprocess, numpy as np, torch, torchaudio
torch.set_num_threads(4)
bundle = torchaudio.pipelines.MMS_FA
model = bundle.get_model(with_star=False).eval()
if os.environ.get('QUANT'):
    # (several times faster, and near enough the same for aligning)
    model = torch.ao.quantization.quantize_dynamic(model, {torch.nn.Linear}, dtype=torch.qint8)
# (in long pieces the model stops recognising part of the way through)
SR = 16000; CHUNK = 15 * SR; CONTEXT = 2 * SR; STRIDE = 320

def audio(path):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-ac', '1', '-ar', str(SR), '-f', 's16le', '-'],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768

def emissions(wave):
    out = []
    n = len(wave)
    for start in range(0, n, CHUNK):
        a, b = max(0, start - CONTEXT), min(n, start + CHUNK + CONTEXT)
        piece = torch.from_numpy(wave[a:b]).unsqueeze(0)
        if piece.shape[1] < 400: continue
        with torch.inference_mode():
            e, _ = model(piece)
        e = torch.log_softmax(e[0], dim=-1).numpy()
        first = (start - a) // STRIDE
        count = (min(n, start + CHUNK) - start) // STRIDE
        out.append(e[first:first + count])
    return np.concatenate(out)

if __name__ == '__main__':
    for path in sys.argv[1:]:
        target = path + '.emit.npy'
        if os.path.exists(target): continue
        e = emissions(audio(path))
        np.save(target + '.tmp.npy', e.astype(np.float16)); os.rename(target + '.tmp.npy', target)
        print(path, e.shape, flush=True)
