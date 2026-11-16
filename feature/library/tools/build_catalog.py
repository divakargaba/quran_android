"""build_catalog.py <out catalog.json>: the catalog of the library (the poems, their recordings
and documents, and the books on the principles of the qira'at).

The documents were each checked (their title pages) to be the book they are listed as; their
sizes and pages are those of the files at their urls when checked. The recordings are items of
archive.org; a recording's tracks are matched to the poem's chapters only where it is divided as
the poem is (checked by the tracks' lengths against the chapters' verses)."""
import sys, json, urllib.parse

ARCHIVE = 'https://archive.org/download/'
def doc(ident, name, size, pages, title_ar, author_ar, title_en='', author_en='', lang='ar'):
    return dict(title_ar=title_ar, title_en=title_en, author_ar=author_ar, author_en=author_en, lang=lang,
                size=size, pages=pages, url=ARCHIVE + ident + '/' + urllib.parse.quote(name))

DAMRA = 'توفيق إبراهيم ضمرة'
DAMRA_EN = 'Tawfiq Ibrahim Damra'
D = ' pdf     اقرا  اونلاين    كتاب       الشيخ توفيق ضمرة ضمره .pdf'
DOCUMENTS = {
    # bundled
    'jazariyyah_en': dict(title_ar='المقدمة الجزرية مع ترجمتها وشرحها بالإنجليزية', title_en='The Jazariyyah Poem: translation and explanation',
                          author_ar='موقع abouttajweed.com', author_en='The staff of abouttajweed.com', lang='en', size=847631, pages=31,
                          asset='library/docs/jazariyyah_en.pdf'),
    'rules_shuba_hafs': dict(title_ar='أحكام روايتي شعبة وحفص عن عاصم (بالإنجليزية)', title_en="Rules of Shu'ba and Hafs 'an 'Aasim",
                             author_ar='مروة الجندي', author_en='Marwa El Gindy', lang='en', size=722696, pages=31,
                             asset='library/docs/rules_shuba_hafs.pdf'),
    # commentaries of the poems
    'wafi': doc('rain_bow2020_yahoo_20180402', 'الوافي في شرح الشاطبية في القراءات السبع.pdf', 16903515, 590,
                'الوافي في شرح الشاطبية في القراءات السبع', 'عبد الفتاح عبد الغني القاضي (ت ١٤٠٣هـ)',
                'Al-Wafi: commentary on the Shatibiyyah', "'Abd al-Fattah al-Qadi (d. 1403 AH)"),
    'siraj': doc('SeragQaree_IbnQaseh', 'سراج القارئ المبتدي وتذكار القارئ المنتهي.pdf', 9608672, 350,
                 'سراج القارئ المبتدي وتذكار المقرئ المنتهي (شرح الشاطبية)', 'ابن القاصح العذري (ت ٨٠١هـ)',
                 'Siraj al-Qari: commentary on the Shatibiyyah', 'Ibn al-Qasih (d. 801 AH)'),
    'ibraz': doc('kamalkhamash_aol_20170528', 'إبراز المعاني من حرز الأماني لأبي شامة.pdf', 18641775, 762,
                 'إبراز المعاني من حرز الأماني', 'أبو شامة المقدسي (ت ٦٦٥هـ)',
                 "Ibraz al-Ma'ani: commentary on the Shatibiyyah", 'Abu Shamah al-Maqdisi (d. 665 AH)'),
    'irshad_murid': doc('0078Pdf', '0078  اقرا اونلاين كتاب    pdf   إرشاد المريد إلى مقصود القصيد في القراءات السبع.pdf', 7312165, 423,
                        'إرشاد المريد إلى مقصود القصيد في القراءات السبع', 'علي محمد الضباع (ت ١٣٨٠هـ)',
                        'Irshad al-Murid: commentary on the Shatibiyyah', "'Ali Muhammad al-Dabba' (d. 1380 AH)"),
    'ghunyah': doc('ghunyah_202307', 'ghunyah.pdf', 2095346, 167,
                   'غنية الطلبة في تيسير السبعة (بالإنجليزية)', 'محمد سليم غيبي',
                   "Ghunyah al-Talabah fi Taysir al-Sab'ah: the principles of the seven qira'at, on the Shatibiyyah",
                   'Muhammad Saleem Gaibie', 'en'),
    'bahjah': doc('AlBohga', 'البهجة-المرضية-علي-محمد-الضباع.pdf', 3111730, 136,
                  'البهجة المرضية شرح الدرة المضية', 'علي محمد الضباع (ت ١٣٨٠هـ)',
                  'Al-Bahjah al-Mardiyyah: commentary on the Durrah', "'Ali Muhammad al-Dabba' (d. 1380 AH)"),
    'idah': doc('AAskZad-1105684', 'Book الإيضاح لمتن الدرة في القراءات الثلاث المتممة للقراءات العشر للإمام ابن الجزري [1105684] --- عبدالفتاح القاضي (مؤلف).pdf',
                27456914, 180, 'الإيضاح لمتن الدرة في القراءات الثلاث المتممة للقراءات العشر', 'عبد الفتاح عبد الغني القاضي (ت ١٤٠٣هـ)',
                'Al-Idah: commentary on the Durrah', "'Abd al-Fattah al-Qadi (d. 1403 AH)"),
    'sharh_tayyibah': doc('sherhaltiba_a56646ljezery', 'sherhaltiba_aljezery.pdf', 6884534, 344,
                          'شرح طيبة النشر في القراءات العشر', 'أحمد بن محمد ابن الجزري، ابن الناظم (ت ٨٣٥هـ)',
                          'Commentary on Tayyibat al-Nashr', "Ahmad Ibn al-Jazari, the author's son (d. 835 AH)"),
    'minah': doc('0743Pdf_201804', '0743  كتاب اقرا اونلاين    pdf  المنح الفكرية شرح المقدمة الجزرية ملا علي القاري.pdf', 3697427, 85,
                 'المنح الفكرية شرح المقدمة الجزرية', 'ملا علي القاري (ت ١٠١٤هـ)',
                 'Al-Minah al-Fikriyyah: commentary on the Jazariyyah', "Mulla 'Ali al-Qari (d. 1014 AH)"),
    'fath_aqfal': doc('20240517_20240517_1001', 'فتح الاقفال شرح متن تحفة الاطفال - سليمان الجمزوري.pdf', 1083575, 87,
                      'فتح الأقفال بشرح تحفة الأطفال', 'سليمان الجمزوري (ناظم التحفة)',
                      "Fath al-Aqfal: the author's own commentary on Tuhfat al-Atfal", 'Sulayman al-Jamzuri'),
    'primer': dict(title_ar='تحفة الأطفال: ترجمة وشرح بالإنجليزية (A Classical Primer on Tajwid)', title_en="A Classical Primer on Tajwid: Tuhfat al-Atfal translated and annotated",
                   author_ar='مصطفى القباني، مؤسسة آل البيت الملكية للفكر الإسلامي', author_en='Moustafa Elqabbany (Royal Aal al-Bayt Institute for Islamic Thought)',
                   lang='en', size=2419467, pages=84, url='https://rissc.jo/wp-content/uploads/2018/11/A_Classical_Primer_on_Tajwid_EN.pdf'),
    # the principles of the qira'at
    'idaah': doc('sa71mir_gmail_20171203', 'eda2h.pdf', 2670301, 142,
                 'الإضاءة في بيان أصول القراءة', 'علي محمد الضباع (ت ١٣٨٠هـ)',
                 "Al-Ida'ah: the principles of recitation", "'Ali Muhammad al-Dabba' (d. 1380 AH)"),
    'budur': doc('bodour.g', 'bodour.g.pdf', 9371497, 458,
                 'البدور الزاهرة في القراءات العشر المتواترة من طريقي الشاطبية والدرة', 'عبد الفتاح عبد الغني القاضي (ت ١٤٠٣هـ)',
                 "Al-Budur al-Zahirah: the ten qira'at, by the Shatibiyyah and the Durrah", "'Abd al-Fattah al-Qadi (d. 1403 AH)"),
    'nayl_watar': doc('2pdf_20191016', '2- أصول القراءات ' + D, 9541193, 437,
                      'نيل الوطر في أصول القراءات الأربعة عشر', DAMRA, "Nayl al-Watar: the principles of the fourteen qira'at", DAMRA_EN),
    'mujtaba': doc('rules-of-the-10-qiraat', "Rules of the 10 Qira'at.pdf", 4047907, 230,
                   'المجتبى في القراءات السبع، وبهامشه المنتقى في القراءات الثلاث (بالإنجليزية)', 'إسماعيل إسحاق ومعاذ الإسلام شودري',
                   "Al-Mujtaba: rules of the seven qira'at, with al-Muntaqa on the three", 'Qari Ismail Essack and Qari Muajul I. Chowdhury', 'en'),
    'qalun': doc('3pdf_20191016', '3- الجسر المأمون إلى رواية قالون من طريقي الشاطبية والطيبة ' + D, 1746430, 154,
                 'الجسر المأمون إلى رواية قالون', DAMRA, "The riwayah of Qalun", DAMRA_EN),
    'warsh': doc('4pdf_20191016', '4- الثمر اليانع في رواية ورش عن نافع من طريقي الشاطبية والطيبة ' + D, 11232497, 432,
                 'الثمر اليانع في رواية ورش عن نافع', DAMRA, "The riwayah of Warsh", DAMRA_EN),
    'usul_warsh_en': doc('UsulWarsh', 'Usul Warsh.pdf', 3753376, 68,
                         'الأصول والفرش في رواية ورش (بالإنجليزية)', 'فاطمة ياقوت الإبياري',
                         'The Usul and Farsh in Rewayat Warsh', 'Fatma Yacout Elibyari', 'en'),
    'ibn_kathir': doc('56pdf_201910', '5-6- الطريق المنير إلى قراءة ابن كثير بروايتي البزي وقنبل من طريقي الشاطبية والطيبة ' + D, 3835551, 194,
                      'الطريق المنير إلى قراءة ابن كثير بروايتي البزي وقنبل', DAMRA, "The qira'ah of Ibn Kathir", DAMRA_EN),
    'duri': doc('7pdf_20191016', '7- غاية سروري في رواية الدوري من قراءة أبي عمرو البصري من طريقي الشاطبية والطيبة ' + D, 3328335, 258,
                'غاية سروري في رواية الدوري عن أبي عمرو البصري', DAMRA, "The riwayah of al-Duri from Abu 'Amr", DAMRA_EN),
    'susi': doc('8pdf_201910xxxxxxxxxxxxxxxxxxxxxxxx', '8- أحلى دروسي في رواية السوسي من قراءة أبي عمرو البصري من طريقي الشاطبية والطيبة ' + D, 6167463, 375,
                'أحلى دروسي في رواية السوسي عن أبي عمرو البصري', DAMRA, "The riwayah of al-Susi from Abu 'Amr", DAMRA_EN),
    'ibn_amir': doc('cccccccccccccccccccccf_201910', '9-10-زاد السائر إلى قراءة ابن عامر بروايتي هشام وابن ذكوان من طريقي الشاطبية والطيبة ' + D, 2524146, 216,
                    'زاد السائر إلى قراءة ابن عامر بروايتي هشام وابن ذكوان', DAMRA, "The qira'ah of Ibn 'Amir", DAMRA_EN),
    'shubah': doc('12_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx20191016', '12- أحسن صحبة في رواية الإمام شعبة من قراءة عاصم بن أبي النجود الكوفي من طريقي الشاطبية والطيبة  للشيخ توفيق ضمرة ضمره .pdf', 4096607, 191,
                  'أحسن صحبة في رواية الإمام شعبة عن عاصم', DAMRA, "The riwayah of Shu'bah", DAMRA_EN),
    'hafs': doc('1pdf_20191016', '1- كتاب الإتقان في نطق بعض ألفاظ القرآن برواية حفص بن سليمان ' + D, 1051584, 50,
                'الإتقان في نطق بعض ألفاظ القرآن برواية حفص بن سليمان', DAMRA, "The riwayah of Hafs: pronouncing some of its words", DAMRA_EN),
    'hamzah': doc('1314vvvvvvvvvvvvvvvvvvvvvvvvvvvvv01910', '13-14- رفعة الدرجات في قراءة حمزة الزيات بروايتي خلف وخلاد من طريقي الشاطبية والطيبة ' + D, 5255048, 327,
                  'رفعة الدرجات في قراءة حمزة الزيات بروايتي خلف وخلاد', DAMRA, "The qira'ah of Hamzah", DAMRA_EN),
    'kisai': doc('1516pdf_201bbbbbbbbbbbbbbbbbbbbbbbb', '15-16- غاية رضائي في قراءة الكسائي من طريقي الشاطبية والطيبة ' + D, 3849247, 342,
                 'غاية رضائي في قراءة الكسائي', DAMRA, "The qira'ah of al-Kisa'i", DAMRA_EN),
    'abu_jafar': doc('bbbbbbbbbbbbbbbbbbbbbbb18pdf_201910', '17-18- اتباع الأثر في قراءة أبي جعفر من طريقي الدرة والطيبة ' + D, 4537876, 266,
                     'اتباع الأثر في قراءة أبي جعفر', DAMRA, "The qira'ah of Abu Ja'far", DAMRA_EN),
    'yaqub': doc('1920nnnnnnnnnnnnnnnnnnnnnn01910', '19-20- تنوير القلوب في قراءة يعقوب بروايتي رويس وروح من طريقي الدرة والطيبة ' + D, 3990198, 264,
                 'تنوير القلوب في قراءة يعقوب بروايتي رويس وروح', DAMRA, "The qira'ah of Ya'qub", DAMRA_EN),
    'khalaf': doc('212nnnnnnnnnnmmmmmmmm201910', '21-22- فرحة الأبرار في قراءة خلف البزار من طريقي الدرة والطيبة ' + D, 3305787, 221,
                  'فرحة الأبرار في قراءة خلف البزار', DAMRA, "The qira'ah of Khalaf", DAMRA_EN),
}

USOOL = [
    ('القراءات العشر عامة', "All ten qira'at", ['idaah', 'budur', 'nayl_watar', 'mujtaba', 'ghunyah']),
    ('نافع: قالون وورش', "Nafi': Qalun and Warsh", ['qalun', 'warsh', 'usul_warsh_en']),
    ('ابن كثير: البزي وقنبل', 'Ibn Kathir: al-Bazzi and Qunbul', ['ibn_kathir']),
    ('أبو عمرو: الدوري والسوسي', "Abu 'Amr: al-Duri and al-Susi", ['duri', 'susi']),
    ('ابن عامر: هشام وابن ذكوان', "Ibn 'Amir: Hisham and Ibn Dhakwan", ['ibn_amir']),
    ('عاصم: شعبة وحفص', "'Asim: Shu'bah and Hafs", ['rules_shuba_hafs', 'shubah', 'hafs']),
    ('حمزة: خلف وخلاد', 'Hamzah: Khalaf and Khallad', ['hamzah']),
    ('الكسائي: أبو الحارث والدوري', "Al-Kisa'i: Abu al-Harith and al-Duri", ['kisai']),
    ('أبو جعفر: ابن وردان وابن جماز', "Abu Ja'far: Ibn Wardan and Ibn Jammaz", ['abu_jafar']),
    ('يعقوب: رويس وروح', "Ya'qub: Ruways and Rawh", ['yaqub']),
    ('خلف العاشر: إسحاق وإدريس', 'Khalaf: Ishaq and Idris', ['khalaf']),
]

SHAMELA_AR = 'نص المتن من المكتبة الشاملة (shamela.ws)'
SHAMELA_EN = 'Text from al-Maktabah al-Shamilah (shamela.ws)'
IBN_JAZARI = ('محمد بن محمد ابن الجزري (ت ٨٣٣هـ)', 'Ibn al-Jazari (d. 833 AH)')
AFASY = ('مشاري بن راشد العفاسي', 'Mishary Rashid al-Afasy')
SUWAID = ('أيمن رشدي سويد', 'Ayman Rushdi Suwaid')

def q(name): return urllib.parse.quote(name)

TAYYIBAH_TRACKS = ['01 - المقدمة.mp3', '02-  الاستعاذة.mp3', '03 - البسملة.mp3', '04 - سورة أم القرآن.mp3', '05 - الإدغام الكبير.mp3',
    '06 - هاء الكناية.mp3', '07 - المد والقصر.mp3', '08 - الهمزتان من كلمة.mp3', '09 - الهمزتان من كلمة.mp3', '10 - الهمز المفرد.mp3',
    '11 - نقل حركة الهمزة إلى الساكن قبلها.mp3', '12 - السكت على الساكن قبل الهمز وغيره.mp3', '13 - وقف حمزة وهشام على الهمز.mp3',
    '14 - ذال إذ.mp3', '15 - دال قد.mp3', '17 - لام هل وبل.mp3', '18 - باب حروف قربت مخارجها.mp3', '19 - النون الساكنة والتنوين.mp3',
    '20 - الفتح والإمالة وبين اللفظين.mp3', '21 - إمالة هاء التأنيث وما قبلها في الوقف.mp3', '22 - باب مذاهبم الراءات.mp3', '23 - باب اللامات.mp3',
    '24 - باب الوقف على أواخر الكلم.mp3', '25 - باب الوقف على مرسوم الخط.mp3', '26 - باب ياءات الإضافة.mp3', '27 - باب ياءات الزوائد.mp3',
    '28 - باب إفراد القراءات وجمعها.mp3', '30-.mp3', '31 - سورة آل عمران.mp3', '32 - سورة النساء.mp3', '33 - سورة المائدة.mp3',
    '34 - سورة الأنعام.mp3', '35 - سورة الأعراف.mp3', '36 - سورة الأنفال.mp3', '37 - سورة التوبة.mp3', '38 - سورة يونس.mp3', '39 - سورة هود.mp3',
    '40 - سورة يوسف.mp3', '41 - سورة الرعد وأختيها.mp3', '42 - سورة النحل.mp3', '43 - سورة الإسراء.mp3', '44 - سورة الكهف.mp3', '45 - سورة مريم.mp3',
    '46 - سورة طه.mp3', '47 - سورة الأنبياء.mp3', '48 - سورة الحج والمؤمنون.mp3', '49 - سورة النور والفرقان.mp3', '50 - سورة الشعراء وأختيها.mp3',
    '51 - سورة العنكبوت والروم.mp3', '52 - ومن سورة لقمان إلى سورة يس.mp3', '53 - سورة يس.mp3', '54 - سورة والصافات.mp3',
    '55 - ومن سورة ص إلى سورة الأحقاف.mp3', '56 - سورة الأحقاف وأختيها.mp3', '57 - ومن سورة الحجرات إلى سورة الرحمن.mp3', '58 - سورة الرحمن.mp3',
    '59 - ومن سورة الواقعة إلى سورة التغابن.mp3', '60 - ومن سورة التغابن إلى سورة الإنسان.mp3', '61 - سورة الإنسان والمرسلات.mp3',
    '62 - ومن سورة النبأ إلى سورة التطفيف.mp3', '63 - ومن سورة التطفيف إلى سورة والشمس.mp3', '64 - ومن سورة الشمس إلى آخر القرآن.mp3', '65 - باب التكبير.mp3']
def tayyibah_title(name):
    t = name[:-4].split('-', 1)[1].strip()
    t = t.replace('الهمزتان من كلمة', 'الهمزتان من كلمة' if name.startswith('08') else 'الهمزتان من كلمتين').replace('مذاهبم', 'مذاهبهم في')
    return t or 'فرش الحروف: سورة البقرة'

POEMS = [
    dict(id='shatibiyyah', title_ar='متن الشاطبية: حرز الأماني ووجه التهاني', title_en='Ash-Shatibiyyah (Hirz al-Amani wa Wajh al-Tahani)',
         author_ar='القاسم بن فِيرُّه الشاطبي (ت ٥٩٠هـ)', author_en='Al-Qasim ibn Firruh al-Shatibi (d. 590 AH)',
         subject_ar='القراءات السبع', subject_en="The seven qira'at", verses=1173, source_ar=SHAMELA_AR, source_en=SHAMELA_EN,
         audio=dict(reciter_ar=AFASY[0], reciter_en=AFASY[1], base=ARCHIVE + 'shatibiyyah_afasy/',
                    # a track for each chapter; the last has the last two
                    tracks=[dict(f='%02d.mp3' % (i + 1), c=[i] if i < 76 else [76, 77]) for i in range(77)]),
         documents=['wafi', 'siraj', 'ibraz', 'irshad_murid', 'ghunyah']),
    dict(id='durrah', title_ar='الدرة المضية في القراءات الثلاث المتممة للعشر', title_en='Ad-Durrah al-Mudiyyah',
         author_ar=IBN_JAZARI[0], author_en=IBN_JAZARI[1],
         subject_ar='القراءات الثلاث المتممة للعشر', subject_en="The three qira'at that complete the ten", verses=241,
         source_ar=SHAMELA_AR, source_en=SHAMELA_EN,
         audio=dict(reciter_ar=AFASY[0], reciter_en=AFASY[1], base=ARCHIVE + 'al-Dura_Afassy-mp3/',
                    tracks=[dict(f='ar_%02d_AlDora_Mishari.mp3' % (i + 1)) for i in range(40)]),
         documents=['bahjah', 'idah']),
    dict(id='tayyibah', title_ar='طيبة النشر في القراءات العشر', title_en='Tayyibat al-Nashr',
         author_ar=IBN_JAZARI[0], author_en=IBN_JAZARI[1],
         subject_ar='القراءات العشر الكبرى', subject_en="The ten qira'at by the ways of al-Nashr", verses=1015,
         source_ar=SHAMELA_AR, source_en=SHAMELA_EN,
         audio=dict(reciter_ar=SUWAID[0], reciter_en=SUWAID[1], base=ARCHIVE + 'ramy_01_201609/',
                    tracks=[dict(f=q(name), t=tayyibah_title(name)) for name in TAYYIBAH_TRACKS]),
         documents=['sharh_tayyibah']),
    dict(id='jazariyyah', title_ar='المقدمة الجزرية: المقدمة فيما يجب على قارئ القرآن أن يعلمه', title_en='Al-Muqaddimah al-Jazariyyah',
         author_ar=IBN_JAZARI[0], author_en=IBN_JAZARI[1],
         subject_ar='التجويد', subject_en='Tajwid', verses=107, source_ar=SHAMELA_AR, source_en=SHAMELA_EN,
         audio=dict(reciter_ar=SUWAID[0], reciter_en=SUWAID[1], base=ARCHIVE + 'sh-ayman-sowaid-matn-aljazareiiah/',
                    tracks=[dict(f='00.mp3', t='المتن كاملًا')]),
         documents=['jazariyyah_en', 'minah'], verse_document='jazariyyah_en'),
    dict(id='tuhfah', title_ar='تحفة الأطفال والغلمان في تجويد القرآن', title_en='Tuhfat al-Atfal',
         author_ar='سليمان بن حسين الجمزوري (ت بعد ١١٩٨هـ)', author_en='Sulayman al-Jamzuri (d. after 1198 AH)',
         subject_ar='التجويد', subject_en='Tajwid', verses=61, source_ar=SHAMELA_AR, source_en=SHAMELA_EN,
         audio=dict(reciter_ar='محمد غندر المصري', reciter_en='Muhammad Ghandar al-Misri', base=ARCHIVE + 'to7ft-ala6fal/',
                    tracks=[dict(f=f, c=[i]) for i, f in enumerate([
                        'A-moqadema.mp3', 'B-noonsakinatanween.mp3', 'C-noonmeem-moshadada.mp3', 'D-almeem-alsakina.mp3',
                        'E-lam-allam-alfe3l.mp3', 'F-almethleenalmotaqarbenalmotagansen.mp3', 'G-aqsam-almad.mp3',
                        'H-a7kam-almad.mp3', 'I-aqsam-almad-allazm.mp3', 'J-5atema.mp3'])]),
         documents=['primer', 'fath_aqfal']),
]
# the page (from 0) of each verse of the Jazariyyah in its translation
JAZARIYYAH_PAGES = [0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7,
    8, 8, 8, 9, 9, 9, 9, 9, 10, 10, 10, 11, 11, 11, 11, 11, 12, 12, 12, 12, 12, 13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15, 16, 16, 16, 16,
    17, 17, 17, 17, 17, 18, 18, 18, 18, 19, 19, 19, 20, 20, 20, 20, 21, 22, 22, 22, 22, 23, 23, 23, 24, 24, 24, 25, 25, 25, 25]
POEMS[3]['verse_pages'] = JAZARIYYAH_PAGES

if __name__ == '__main__':
    used = {d for p in POEMS for d in p['documents']} | {d for g in USOOL for d in g[2]}
    assert used == set(DOCUMENTS), (used ^ set(DOCUMENTS))
    catalog = dict(poems=POEMS, usool=[dict(title_ar=a, title_en=e, documents=d) for a, e, d in USOOL], documents=DOCUMENTS)
    json.dump(catalog, open(sys.argv[1], 'w'), ensure_ascii=False, indent=1)
    print(len(POEMS), 'poems', len(DOCUMENTS), 'documents', len(USOOL), 'groups')
