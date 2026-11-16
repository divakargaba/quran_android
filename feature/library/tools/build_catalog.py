"""build_catalog.py <out catalog.json>: the catalog of the library (the poems, their recordings
and documents, and the books on the principles of the qira'at).

A book listed as a poem's "inline" is shown under its verses, verse by verse, from its own pages
(src/main/assets/library/inline, made by tools/build_inline.py from the very file at its url: the
app shows it only if the file it downloads is of the size given here).

The documents were each checked (their title pages) to be the book they are listed as; their
sizes and pages are those of the files at their urls when checked. The recordings are items of
archive.org (one is bundled); the times of a recording's verses are in assets/library/timings
(tools/build_timings.py)."""
import sys, os, json, urllib.parse

ARCHIVE = 'https://archive.org/download/'
def doc(ident, name, size, pages, title_ar, author_ar, title_en='', author_en='', lang='ar'):
    return dict(title_ar=title_ar, title_en=title_en, author_ar=author_ar, author_en=author_en, lang=lang,
                size=size, pages=pages, url=ARCHIVE + ident + '/' + urllib.parse.quote(name))

ELIBYARI = ('فاطمة ياقوت الإبياري', 'Fatma Yacout Elibyari')
TANZIL = 'https://assets.al-tanzil.co.za/books/'
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
    # english books on the principles of the qira'at
    'ten_authentic': dict(title_ar='القراءات العشر المتواترة (بالإنجليزية)', title_en="The Ten Authentic Qiraa'aat",
                          author_ar=ELIBYARI[0], author_en=ELIBYARI[1], lang='en', size=16243503, pages=320,
                          url='https://islammessage.org/book_file/3686.pdf'),
    'anis_ashr': dict(title_ar='أنيس العشر: شرح طيبة النشر (بالإنجليزية)',
                      title_en="Anis al-'Ashr: a companion to the major ten qira'at, a commentary on the Tayyibah",
                      author_ar='محمد سليم غيبي', author_en='M. Saleem Gaibie', lang='en', size=5439878, pages=460,
                      url=TANZIL + 'anis-al-ashr/anis-al-ashr.pdf'),
    'taysir_en': doc('obp.0475', 'obp.0475.pdf', 8871889, 359,
                     'التيسير في القراءات السبع لأبي عمرو الداني: ترجمة إنجليزية مع تعليق لغوي', 'مارين فان بوتن',
                     "Al-Dani's al-Taysir fi al-Qira'at al-Sab': a translation with linguistic commentary",
                     'Marijn van Putten (Open Book Publishers, CC BY-NC 4.0)', 'en'),
    'fawaid_ayyubiyyah': dict(title_ar='فوائد أيوبية على الدرة المضية (بالإنجليزية)',
                              title_en="Fawa'id Ayyubiyyah: the Ayyubi instruction on al-Durrah al-Mudiyyah",
                              author_ar='محمد سليم غيبي', author_en='M. Saleem Gaibie', lang='en', size=1976519, pages=97,
                              url=TANZIL + 'fawaid-ayubiyyah/fawaid-ayubiyyah.pdf'),
    'three_durrah': dict(title_ar='دراسة ميسرة للقراءات الثلاث من طريق الدرة (بالإنجليزية)',
                         title_en='A Simplified Study of the Three Variant Readings via the Durrah',
                         author_ar='محمد رياض أوباراي', author_en='Muhammad Riyaadh Obaray', lang='en', size=4816751, pages=219,
                         url=TANZIL + 'a-simplified-study-of-the-three-variant-readings-via-the-durrah/'
                                      'a-simplified-study-of-the-three-variant-readings-via-the-durrah.pdf'),
    'qalun_en': doc('UsulRewayatQalun', 'Usul Rewayat Qalun.pdf', 1784422, 42,
                    'الطريق المضمون إلى رواية قالون (بالإنجليزية)', ELIBYARI[0], 'The Secure Way to Rewayat Qalun', ELIBYARI[1], 'en'),
    'notes_warsh': dict(title_ar='مذكرات في رواية ورش (بالإنجليزية)', title_en='Notes on Warsh',
                        author_ar='إعجاز مقدم، مجلس القضاء الإسلامي بجنوب إفريقيا',
                        author_en='Ijaaz Mukaddam (Muslim Judicial Council, Department of Quranic Affairs)',
                        lang='en', size=1440892, pages=76, url=TANZIL + 'notes-on-warsh/notes-on-warsh.pdf'),
    'learn_warsh': doc('lets-learn-riwayat-warsh', "Let's learn Riwayat Warsh.pdf", 3154613, 80,
                       'لنتعلم رواية ورش (بالإنجليزية)', 'ريهام نصرت', "Let's Learn Riwayat Warsh", 'Dr. Reham Nasrat', 'en'),
    'ibn_kathir_en': doc('QiratIbnKathir', "Qira't Ibn Kathir.pdf", 1622205, 30,
                         'الشرح المستنير بقراءة ابن كثير (بالإنجليزية)', ELIBYARI[0],
                         'The Enlightenment with Qira-at Ibn Kathir', ELIBYARI[1], 'en'),
    'abu_amr_en': doc('UsulAbuAmrUlBasri', "Usul Abu 'Amr ulBasri.pdf", 3236324, 54,
                      'الشرح الوصفي لقراءة أبي عمرو البصري (بالإنجليزية)', ELIBYARI[0],
                      "The Explanatory Description of Qira-at Abi 'Amr al-Basri", ELIBYARI[1], 'en'),
    'ibn_amir_en': doc('IbnAamirAshShami', "Ibn 'Aamir Ash-shami.pdf", 3525057, 38,
                       'إفهام الأنام قراءة ابن عامر الشامي (بالإنجليزية)', ELIBYARI[0],
                       "Qira-at Ibn 'Amir ash-Shami", ELIBYARI[1], 'en'),
    'asim_en': doc('UsulAasimHafsAndShuBah', "Usul 'Aasim (Hafs and Shu'Bah).pdf", 3204036, 57,
                   'البيان الواسم لروايتي شعبة وحفص عن عاصم (بالإنجليزية)', ELIBYARI[0],
                   "The Clarification of Riwayati Shu'bah and Hafs 'an 'Asim", ELIBYARI[1], 'en'),
    'bayan_hafs': dict(title_ar='البيان لطرق الإمام حفص بن سليمان (بالإنجليزية)',
                       title_en='Al-Bayan: an explanation of the turuq of Imam Hafs ibn Sulayman',
                       author_ar='أبو يوسف منور هارنكر', author_en='Abu Yusuf Munowar Harneker', lang='en', size=2331877, pages=129,
                       url=TANZIL + 'al-bayaan/al-bayaan.pdf'),
    'golden_collection': dict(title_ar='المجموعة الذهبية في تعليم طريقي الروضة والشاطبية (بالإنجليزية)',
                              title_en='Al-Majmu\'ah al-Dhahabiyyah: the ways of the Rawdah and the Shatibiyyah for Hafs',
                              author_ar='محمد سليم غيبي', author_en='M. Saleem Gaibie', lang='en', size=1359279, pages=97,
                              url=TANZIL + 'the-golden-collection/the-golden-collection.pdf'),
    'hafs_misbah': dict(title_ar='رواية حفص من طريق المصباح (بالإنجليزية)', title_en='The Narration of Hafs via the Tariq of Misbah',
                        author_ar='عائشة بنت حنيف أبراهامز، مجلس القضاء الإسلامي بجنوب إفريقيا',
                        author_en='Ayesha bint Hanif Abrahams (Muslim Judicial Council, Department of Quranic Affairs)',
                        lang='en', size=1408090, pages=50, url=TANZIL + 'hafs-via-misbah/hafs-via-misbah.pdf'),
    'nafais_bayan': dict(title_ar='نفائس البيان: شرح بعض منظومات حفص بن سليمان (بالإنجليزية)',
                         title_en="Nafa'is al-Bayan: instruction on selected poems regarding the riwayah of Hafs",
                         author_ar='محمد رياض أوباراي', author_en='Muhammad Riyaadh Obaray', lang='en', size=2858853, pages=67,
                         url=TANZIL + 'nafais-al-bayan/nafais-al-bayan.pdf'),
    'hamzah_en': doc('QiraatHamzaAzzayat', "Qira'at Hamza Azzayat.pdf", 6939522, 68,
                     'هداية الغريب إلى قراءة حمزة بن حبيب (بالإنجليزية)', ELIBYARI[0],
                     "The Stranger's Guide to the Recitation of Hamza ibn Habib", ELIBYARI[1], 'en'),
    'kisai_en': doc('QiraatAlkisai', "Qira'at Alkisa'i.pdf", 3309117, 31,
                    'جمال عين الرائي في قراءة الكسائي (بالإنجليزية)', ELIBYARI[0],
                    "The Beauty in the Eyes of the Qari in Qira-at al-Kisa'i", ELIBYARI[1], 'en'),
    'abu_jafar_en': doc('QiratAbuJafar', "Qira't Abu Ja'far.pdf", 1595972, 25,
                        'الشرح الميسر لقراءة أبي جعفر (بالإنجليزية)', ELIBYARI[0],
                        "The Simplified Explanation of Qira-at Abi Ja'far", ELIBYARI[1], 'en'),
    'khalaf_en': doc('UsulQiraatKhalaf10', "Usul Qira'at Khalaf 10.pdf", 2325316, 17,
                     'تبصرة الأبصار بقراءة خلف البزار (بالإنجليزية)', ELIBYARI[0],
                     'An Insight to the Vision: Qira-at Khalaf al-Bazzar', ELIBYARI[1], 'en'),
    # english commentaries of the poems of tajwid
    'taqrib_laali': dict(title_ar='تقريب اللآلي النقية: شرح مختصر للمقدمة الجزرية (بالإنجليزية)',
                         title_en="Taqrib al-La'ali al-Naqiyyah: an abridged commentary on the Jazariyyah",
                         author_ar='محمد سليم غيبي', author_en='Muhammad Saleem Gaibie', lang='en', size=3446828, pages=157,
                         url='https://old.duai.org.za/downloads/books/Jazariyah%20Sharh-abridged.pdf'),
    'laali_naqiyyah': dict(title_ar='اللآلي النقية شرح المقدمة الجزرية (بالإنجليزية)',
                           title_en="Al-La'ali al-Naqiyyah: a commentary on the Jazariyyah",
                           author_ar='محمد سليم غيبي', author_en='Muhammad Saleem Gaibie', lang='en', size=1943126, pages=216,
                           url=TANZIL + 'jazariyya/jazariyya.pdf'),
    'aysar_aqwal': dict(title_ar='أيسر الأقوال شرح تحفة الأطفال (بالإنجليزية)',
                        title_en='Aysar al-Aqwal: a commentary on Tuhfat al-Atfal',
                        author_ar='محمد سليم غيبي', author_en='Muhammad Saleem Gaibie', lang='en', size=1896603, pages=101,
                        url=TANZIL + 'murshid-al-qari-2/murshid-al-qari-2.pdf'),
}

USOOL = [
    ('القراءات العشر عامة', "All ten qira'at",
     ['ten_authentic', 'mujtaba', 'ghunyah', 'anis_ashr', 'taysir_en', 'idaah', 'budur', 'nayl_watar']),
    ('نافع: قالون وورش', "Nafi': Qalun and Warsh", ['qalun_en', 'usul_warsh_en', 'notes_warsh', 'learn_warsh', 'qalun', 'warsh']),
    ('ابن كثير: البزي وقنبل', 'Ibn Kathir: al-Bazzi and Qunbul', ['ibn_kathir_en', 'ibn_kathir']),
    ('أبو عمرو: الدوري والسوسي', "Abu 'Amr: al-Duri and al-Susi", ['abu_amr_en', 'duri', 'susi']),
    ('ابن عامر: هشام وابن ذكوان', "Ibn 'Amir: Hisham and Ibn Dhakwan", ['ibn_amir_en', 'ibn_amir']),
    ('عاصم: شعبة وحفص', "'Asim: Shu'bah and Hafs",
     ['rules_shuba_hafs', 'asim_en', 'bayan_hafs', 'golden_collection', 'hafs_misbah', 'nafais_bayan', 'shubah', 'hafs']),
    ('حمزة: خلف وخلاد', 'Hamzah: Khalaf and Khallad', ['hamzah_en', 'hamzah']),
    ('الكسائي: أبو الحارث والدوري', "Al-Kisa'i: Abu al-Harith and al-Duri", ['kisai_en', 'kisai']),
    ('القراءات الثلاث المتممة للعشر', "The three that complete the ten (Abu Ja'far, Ya'qub, Khalaf)",
     ['fawaid_ayyubiyyah', 'three_durrah']),
    ('أبو جعفر: ابن وردان وابن جماز', "Abu Ja'far: Ibn Wardan and Ibn Jammaz", ['abu_jafar_en', 'abu_jafar']),
    ('يعقوب: رويس وروح', "Ya'qub: Ruways and Rawh", ['yaqub']),
    ('خلف العاشر: إسحاق وإدريس', 'Khalaf: Ishaq and Idris', ['khalaf_en', 'khalaf']),
]

SHAMELA_AR = 'نص المتن من المكتبة الشاملة (shamela.ws)'
SHAMELA_EN = 'Text from al-Maktabah al-Shamilah (shamela.ws)'
IBN_JAZARI = ('محمد بن محمد ابن الجزري (ت ٨٣٣هـ)', 'Ibn al-Jazari (d. 833 AH)')
AFASY = ('مشاري بن راشد العفاسي', 'Mishary Rashid al-Afasy')
SUWAID = ('أيمن رشدي سويد', 'Ayman Rushdi Suwaid')
GHAMIDI = ('سعد الغامدي', "Sa'd al-Ghamidi")
ABU_MALIK = ('أبو مالك المصري', 'Abu Malik al-Misri')
HASHIM = ('عبد الباسط هاشم', 'Abdul Basit Hashim')

def q(name): return urllib.parse.quote(name)

# recordings whose files are many or oddly named: {id: {base, tracks}}
RECORDINGS = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'recordings.json')))
def recording(rid, reciter): return dict(id=rid, reciter_ar=reciter[0], reciter_en=reciter[1], **RECORDINGS[rid])

POEMS = [
    dict(id='shatibiyyah', title_ar='متن الشاطبية: حرز الأماني ووجه التهاني', title_en='Ash-Shatibiyyah (Hirz al-Amani wa Wajh al-Tahani)',
         author_ar='القاسم بن فِيرُّه الشاطبي (ت ٥٩٠هـ)', author_en='Al-Qasim ibn Firruh al-Shatibi (d. 590 AH)',
         subject_ar='القراءات السبع', subject_en="The seven qira'at", verses=1173, source_ar=SHAMELA_AR, source_en=SHAMELA_EN,
         recitations=[recording('shatibiyyah_suwaid', SUWAID),
                      dict(id='shatibiyyah_afasy', reciter_ar=AFASY[0], reciter_en=AFASY[1], base=ARCHIVE + 'shatibiyyah_afasy/',
                           tracks=['%02d.mp3' % (i + 1) for i in range(77)])],
         documents=['ghunyah', 'wafi', 'siraj', 'ibraz', 'irshad_murid'], inline=['ghunyah']),
    dict(id='durrah', title_ar='الدرة المضية في القراءات الثلاث المتممة للعشر', title_en='Ad-Durrah al-Mudiyyah',
         author_ar=IBN_JAZARI[0], author_en=IBN_JAZARI[1],
         subject_ar='القراءات الثلاث المتممة للعشر', subject_en="The three qira'at that complete the ten", verses=241,
         source_ar=SHAMELA_AR, source_en=SHAMELA_EN,
         # (al-Afasy's recording has some verses cut or silent)
         recitations=[recording('durrah_suwaid', SUWAID),
                      dict(id='durrah_afasy', reciter_ar=AFASY[0], reciter_en=AFASY[1], base=ARCHIVE + 'al-Dura_Afassy-mp3/',
                           tracks=['ar_%02d_AlDora_Mishari.mp3' % (i + 1) for i in range(40)])],
         documents=['fawaid_ayyubiyyah', 'three_durrah', 'bahjah', 'idah'], inline=['fawaid_ayyubiyyah']),
    dict(id='tayyibah', title_ar='طيبة النشر في القراءات العشر', title_en='Tayyibat al-Nashr',
         author_ar=IBN_JAZARI[0], author_en=IBN_JAZARI[1],
         subject_ar='القراءات العشر الكبرى', subject_en="The ten qira'at by the ways of al-Nashr", verses=1015,
         source_ar=SHAMELA_AR, source_en=SHAMELA_EN,
         recitations=[recording('tayyibah_abumalik', ABU_MALIK)],
         documents=['anis_ashr', 'sharh_tayyibah'], inline=['anis_ashr']),
    dict(id='jazariyyah', title_ar='المقدمة الجزرية: المقدمة فيما يجب على قارئ القرآن أن يعلمه', title_en='Al-Muqaddimah al-Jazariyyah',
         author_ar=IBN_JAZARI[0], author_en=IBN_JAZARI[1],
         subject_ar='التجويد', subject_en='Tajwid', verses=107, source_ar=SHAMELA_AR, source_en=SHAMELA_EN,
         recitations=[dict(id='jazariyyah_suwaid', reciter_ar=SUWAID[0], reciter_en=SUWAID[1],
                           base=ARCHIVE + 'sh-ayman-sowaid-matn-aljazareiiah/', tracks=['00.mp3']),
                      # (bundled with the app: no base)
                      dict(id='jazariyyah_ghamidi', reciter_ar=GHAMIDI[0], reciter_en=GHAMIDI[1],
                           tracks=['library/audio/jazariyyah_ghamidi.mp3']),
                      recording('jazariyyah_hashim', HASHIM)],
         documents=['jazariyyah_en', 'taqrib_laali', 'laali_naqiyyah', 'minah'], inline=['jazariyyah_en', 'taqrib_laali']),
    dict(id='tuhfah', title_ar='تحفة الأطفال والغلمان في تجويد القرآن', title_en='Tuhfat al-Atfal',
         author_ar='سليمان بن حسين الجمزوري (ت بعد ١١٩٨هـ)', author_en='Sulayman al-Jamzuri (d. after 1198 AH)',
         subject_ar='التجويد', subject_en='Tajwid', verses=61, source_ar=SHAMELA_AR, source_en=SHAMELA_EN,
         recitations=[dict(id='tuhfah_ghandar', reciter_ar='محمد غندر المصري', reciter_en='Muhammad Ghandar al-Misri',
                           base=ARCHIVE + 'to7ft-ala6fal/',
                           tracks=['A-moqadema.mp3', 'B-noonsakinatanween.mp3', 'C-noonmeem-moshadada.mp3', 'D-almeem-alsakina.mp3',
                                   'E-lam-allam-alfe3l.mp3', 'F-almethleenalmotaqarbenalmotagansen.mp3', 'G-aqsam-almad.mp3',
                                   'H-a7kam-almad.mp3', 'I-aqsam-almad-allazm.mp3', 'J-5atema.mp3']),
                      recording('tuhfah_ghamidi', GHAMIDI)],
         documents=['aysar_aqwal', 'primer', 'fath_aqfal'], inline=['primer', 'aysar_aqwal']),
]
if __name__ == '__main__':
    # a recording is listed once the times of its verses are made (tools/build_timings.py)
    timings = os.path.join(os.path.dirname(os.path.abspath(sys.argv[1])), 'timings')
    for poem in POEMS:
        listed = [r for r in poem['recitations'] if os.path.exists(os.path.join(timings, r['id'] + '.json'))]
        if len(listed) < len(poem['recitations']): print('without times yet:', [r['id'] for r in poem['recitations'] if r not in listed])
        poem['recitations'] = listed
    used = {d for p in POEMS for d in p['documents']} | {d for g in USOOL for d in g[2]}
    assert used == set(DOCUMENTS), (used ^ set(DOCUMENTS))
    catalog = dict(poems=POEMS, usool=[dict(title_ar=a, title_en=e, documents=d) for a, e, d in USOOL], documents=DOCUMENTS)
    json.dump(catalog, open(sys.argv[1], 'w'), ensure_ascii=False, indent=1)
    print(len(POEMS), 'poems', len(DOCUMENTS), 'documents', len(USOOL), 'groups')
