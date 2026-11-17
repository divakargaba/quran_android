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

# the chapters' names in Arabic, shown beside them
ARABIC = {
    'Abū ʿAmr al-Baṣrī and his two transmitters': 'أبو عمرو البصري وراوياه',
    'Basmalah and Sūrat al-Fātiḥah': 'البسملة وسورة الفاتحة',
    'Basmalah between two surahs, and Sūrat al-Fātiḥah': 'البسملة بين السورتين وسورة الفاتحة',
    'Fatḥ and imālah': 'الفتح والإمالة',
    'Fatḥ, imālah and taqlīl': 'الفتح والإمالة والتقليل',
    'Fatḥ, taqlīl and imālah': 'الفتح والتقليل والإمالة',
    "Hishām's waqf on a word-final hamzah": 'وقف هشام على الهمز المتطرف',
    'Hāʾ al-kināyah': 'هاء الكناية',
    'Hāʾ al-kināyah (the pronoun hāʾ)': 'هاء الكناية',
    'Idghām and iẓhār': 'الإدغام والإظهار',
    'Idghām kabīr': 'الإدغام الكبير',
    'Idghām kabīr (al-Sūsī)': 'الإدغام الكبير للسوسي',
    'Idghām ṣaghīr': 'الإدغام الصغير',
    'Imālah and sakt': 'الإمالة والسكت',
    'Imālah of hāʾ al-taʾnīth and what precedes it in waqf': 'إمالة هاء التأنيث وما قبلها في الوقف',
    'Imālah particular to al-Dūrī': 'ما انفرد الدوري بإمالته',
    'Individual words in which Shuʿbah differs from Ḥafṣ': 'كلمات خالف فيها شعبة حفصًا',
    'Ishmām': 'الإشمام',
    'Istiʿādhah and basmalah': 'الاستعاذة والبسملة',
    'Madd and qaṣr': 'المد والقصر',
    'Madd and qaṣr, and leaving sakt': 'المد والقصر وترك السكت',
    'Madd, qaṣr and the places of sakt': 'المد والقصر ومواضع السكت',
    'Mīm al-jamʿ': 'ميم الجمع',
    'Mīm al-jamʿ (ṣilah)': 'صلة ميم الجمع',
    'Mīm al-jamʿ and the hāʾ before it': 'ميم الجمع والهاء قبلها',
    'Mīm al-jamʿ and the meeting of two sākin letters': 'ميم الجمع والتقاء الساكنين',
    'Mīm al-jamʿ and the ḍamm of the hāʾ and mīm before a sākin': 'ميم الجمع وضم الهاء والميم قبل الساكن',
    'Mīm al-jamʿ and the ḍammah of the hāʾ': 'ميم الجمع وضم الهاء',
    'Naql': 'النقل',
    'Naql and sakt': 'النقل والسكت',
    'Notable individual words': 'كلمات مخصوصة',
    'Nāfiʿ and his two transmitters': 'نافع وراوياه',
    'Nūn sākinah and tanwīn': 'النون الساكنة والتنوين',
    'Sakt': 'السكت',
    'Sakt before the hamzah': 'السكت قبل الهمز',
    'Sūrat al-Fātiḥah': 'سورة الفاتحة',
    'Sūrat al-Fātiḥah and the ishmām of ṣād': 'سورة الفاتحة وإشمام الصاد',
    'Takbīr': 'التكبير',
    'The basmalah and joining two surahs': 'البسملة ووصل السورتين',
    'The basmalah between two surahs': 'البسملة بين السورتين',
    'The basmalah between two sūrahs': 'البسملة بين السورتين',
    'The hamzahs': 'الهمزات',
    'The hāʾ of the plural and dual pronouns, and mīm al-jamʿ': 'هاء ضمير الجمع والتثنية وميم الجمع',
    "The lāms (Warsh's taghlīẓ)": 'اللامات (تغليظ ورش)',
    'The reader and his two transmitters': 'القارئ وراوياه',
    'The reader Ḥamzah and his two transmitters': 'حمزة وراوياه',
    'The reader, his two transmitters and the way of the Durrah': 'القارئ وراوياه وطريق الدرة',
    'The repeated question (istifhām mukarrar)': 'الاستفهام المكرر',
    "The rāʾs (Warsh's tarqīq)": 'الراءات (ترقيق ورش)',
    'The single hamzah': 'الهمز المفرد',
    'The single hamzah (ibdāl)': 'الهمز المفرد (الإبدال)',
    'The single hamzah and naql': 'الهمز المفرد والنقل',
    'The tāʾāt of al-Bazzī': 'تاءات البزي',
    'The waqf of Ḥamzah on the hamzah': 'وقف حمزة على الهمز',
    'Two hamzahs in one word': 'الهمزتان من كلمة',
    'Two hamzahs in two words': 'الهمزتان من كلمتين',
    'Waqf': 'الوقف',
    'Waqf according to the rasm': 'الوقف على مرسوم الخط',
    'Waqf according to the script': 'الوقف على مرسوم الخط',
    'Waqf according to the script of the muṣḥaf': 'الوقف على مرسوم الخط',
    'Waqf with rawm and ishmām': 'الوقف بالروم والإشمام',
    'Yaʿqūb and his two transmitters': 'يعقوب وراوياه',
    'Yāʾāt al-iḍāfah': 'ياءات الإضافة',
    'Yāʾāt al-zawāʾid': 'ياءات الزوائد',
    'ʿĀṣim and his two transmitters': 'عاصم وراوياه',
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
            item['ta'] = ARABIC[item['t']]  # (every chapter has its Arabic name)
            src = chapter.get('source') or {}
            document = DOCUMENTS.get(os.path.basename(str(src.get('file', ''))))
            if document and isinstance(src.get('page'), int) and src['page'] >= 1:
                item['doc'] = document; item['page'] = src['page'] - 1
            chapters.append(item)
        json.dump({'chapters': chapters}, open(os.path.join(out, name), 'w'), ensure_ascii=False, separators=(',', ':'))
        words = sum(len(p.split()) for c in chapters for p in c['p'])
        print(name, len(chapters), 'chapters', words, 'words', sum(1 for c in chapters if 'doc' in c), 'with a source')
