"""build_usool.py <dir of the written principles> <out dir (assets/library/usool)>: the principles
of each qira'ah as the app reads them.

They are written chapter by chapter from the books on each qira'ah (<id>.json: {"chapters":
[{"title", "paragraphs", "source": {"file", "page"}}]}, the page counted from 1): each chapter
keeps the book it is drawn from, by the app's name for it, and its page (from 0), so the book is
opened there."""
import sys, os, json, re

# the books, by their files' names
DOCUMENTS = {
    'elibyari_qalun.pdf': 'qalun_en', 'UsulWarsh.pdf': 'usul_warsh_en', 'tanzil_notes-on-warsh.pdf': 'notes_warsh',
    'nasrat_lets_learn_warsh_authorupload.pdf': 'learn_warsh', 'elibyari_ibnkathir.pdf': 'ibn_kathir_en',
    'elibyari_abuamr.pdf': 'abu_amr_en', 'elibyari_ibnamir.pdf': 'ibn_amir_en', 'elibyari_asim.pdf': 'asim_en',
    'rules_shuba_hafs.pdf': 'rules_shuba_hafs', 'elibyari_hamzah.pdf': 'hamzah_en', 'elibyari_kisai.pdf': 'kisai_en',
    'elibyari_abujafar.pdf': 'abu_jafar_en', 'elibyari_khalaf10.pdf': 'khalaf_en',
    'elibyari_ten_authentic_qiraat.pdf': 'ten_authentic', 'rules-of-the-10-qiraat.pdf': 'mujtaba',
    'tanzil_a-simplified-study-of-the-three-variant-readings-via-the-durrah.pdf': 'three_durrah',
    'tanzil_fawaid-ayubiyyah.pdf': 'fawaid_ayyubiyyah',
}

def clean(text):
    text = re.sub(r'\s+', ' ', text).strip()
    return re.sub(r'^[-•*]\s*', '', text)

if __name__ == '__main__':
    source, out = sys.argv[1:3]
    os.makedirs(out, exist_ok=True)
    for name in sorted(os.listdir(source)):
        if not name.endswith('.json'): continue
        written = json.load(open(os.path.join(source, name)))
        chapters = []
        for chapter in written['chapters']:
            paragraphs = [clean(p) for p in chapter['paragraphs'] if clean(p)]
            if not paragraphs: continue
            item = dict(t=clean(chapter['title']), p=paragraphs)
            src = chapter.get('source') or {}
            document = DOCUMENTS.get(os.path.basename(str(src.get('file', ''))))
            if document and isinstance(src.get('page'), int) and src['page'] >= 1:
                item['doc'] = document; item['page'] = src['page'] - 1
            chapters.append(item)
        json.dump({'chapters': chapters}, open(os.path.join(out, name), 'w'), ensure_ascii=False, separators=(',', ':'))
        words = sum(len(p.split()) for c in chapters for p in c['p'])
        print(name, len(chapters), 'chapters', words, 'words', sum(1 for c in chapters if 'doc' in c), 'with a source')
