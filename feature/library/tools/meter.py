import unicodedata
SHORT = set('َُِ'); TANWIN = set('ًٌٍ'); SUKUN = 'ْ'; SHADDA = 'ّ'
def weight(text):
    """a rough count of the prosodic letters (moving and still) of vocalized text"""
    w = 0; prev_letter = False
    chars = list(text)
    for i, c in enumerate(chars):
        if c in SHORT: w += 1
        elif c in TANWIN: w += 2
        elif c == SUKUN: w += 1
        elif c == SHADDA: w += 1
        elif c in 'اوىيٰ' :
            # a letter of madd: no mark of its own after it
            nxt = chars[i + 1] if i + 1 < len(chars) else ' '
            if unicodedata.category(nxt) != 'Mn' and c != 'ا' or (c == 'ا' and i > 0 and chars[i - 1] in SHORT | {SHADDA} and unicodedata.category(nxt) != 'Mn'):
                w += 1
    return w
def best_split(words):
    """index k (words[:k] | words[k:]) that balances the two halves best, and the imbalance"""
    ws = [weight(x) for x in words]; total = sum(ws)
    best = None; acc = 0
    for k in range(1, len(words)):
        acc += ws[k - 1]
        d = abs(2 * acc - total)
        if best is None or d < best[1]: best = (k, d)
    return best
