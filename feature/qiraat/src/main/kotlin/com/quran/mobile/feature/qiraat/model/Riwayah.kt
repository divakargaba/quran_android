package com.quran.mobile.feature.qiraat.model

import android.net.Uri

/**
 * One of the ten mutawatir qira'at (seven via ash-Shatibiyyah, three via ad-Durrah),
 * each with its two rawis.
 */
data class Qari(
  val id: String,
  val nameEn: String,
  val nameAr: String,
  val riwayat: List<Riwayah>
)

/**
 * A single riwayah. The mushaf pages come from islamweb's riwayah mushaf series (mirrored on
 * archive.org), which follows the 604-page Madinah layout. [locationsAsset] holds, for every
 * Hafs ayah, the page and line rectangles of the corresponding text in this riwayah's mushaf.
 */
data class Riwayah(
  val id: String,
  val nameEn: String,
  val nameAr: String,
  private val pdfFileName: String,
  val pdfSizeBytes: Long,
  val pdfMd5: String,
  val audio: RiwayahAudioSource?
) {
  val pdfUrl: String
    get() = ARCHIVE_BASE + Uri.encode(pdfFileName)

  val locationsAsset: String
    get() = "qiraat/$id.bin.gz"

  companion object {
    private const val ARCHIVE_BASE = "https://archive.org/download/quran-islamweb.net/"
  }
}

/**
 * A streamed (never bulk downloaded) recitation, one mp3 file per sura.
 * @param suras the suras that are available, or null if all 114 are.
 */
data class RiwayahAudioSource(
  val reciterEn: String,
  val reciterAr: String,
  private val baseUrl: String,
  val suras: Set<Int>? = null
) {
  fun hasSura(sura: Int): Boolean = suras?.contains(sura) ?: true

  fun urlForSura(sura: Int): String = baseUrl + sura.toString().padStart(3, '0') + ".mp3"
}

private const val QURANIC_AUDIO = "https://download.quranicaudio.com/quran/"
private const val MP3_QURAN = "https://cdn.mp3quran.net/audio/"

object Qiraat {

  val qurra: List<Qari> = listOf(
    Qari(
      "nafi", "Nafi' al-Madani", "نافع المدني", listOf(
        Riwayah(
          "qalun", "Qalun", "قالون",
          "القرآن الكريم برواية قالون عن نافع.pdf", 5410737, "665ba345afa60ea529ab52980ffef113",
          RiwayahAudioSource("Ali al-Huthaify", "علي الحذيفي", QURANIC_AUDIO + "huthayfi_qaloon/")
        ),
        Riwayah(
          "warsh", "Warsh", "ورش",
          "القرآن الكريم برواية ورش عن نافع.pdf", 6145219, "5f10f0c3fcdf7ecc995cb779dd3046f2",
          RiwayahAudioSource(
            "AbdulBaset AbdulSamad", "عبد الباسط عبد الصمد", QURANIC_AUDIO + "abdulbaset_warsh/"
          )
        )
      )
    ),
    Qari(
      "ibnkathir", "Ibn Kathir al-Makki", "ابن كثير المكي", listOf(
        Riwayah(
          "bazzi", "Al-Bazzi", "البزي",
          "القرآن الكريم برواية البزي عن ابن كثير.pdf", 11180079, "8f6add8c847b00bf03b67be2fb615b32",
          RiwayahAudioSource("Ahmad Deban", "أحمد ديبان", MP3_QURAN + "ahmad-deeban/r4/")
        ),
        Riwayah(
          "qunbul", "Qunbul", "قنبل",
          "القرآن الكريم برواية قنبل عن ابن كثير.pdf", 10972224, "4794e8315c1965939e686731125f73af",
          RiwayahAudioSource("Ahmad Deban", "أحمد ديبان", MP3_QURAN + "ahmad-deeban/r3/")
        )
      )
    ),
    Qari(
      "abuamr", "Abu 'Amr al-Basri", "أبو عمرو البصري", listOf(
        Riwayah(
          "duri_abuamr", "Ad-Duri", "الدوري",
          "القرآن الكريم برواية الدوري عن أبي عمرو.pdf", 5730161, "4e90fd7f0d9a907fbbdb0f8daf33e36d",
          RiwayahAudioSource(
            "Abdur-Rashid Sufi", "عبد الرشيد صوفي", QURANIC_AUDIO + "abdurrashid_sufi_doori/"
          )
        ),
        Riwayah(
          "susi", "As-Susi", "السوسي",
          "القرآن الكريم برواية السوسي عن أبي عمرو.pdf", 5575954, "b6610e53fcaf04134e9ad72274d0932d",
          RiwayahAudioSource(
            "Abdur-Rashid Sufi", "عبد الرشيد صوفي", QURANIC_AUDIO + "abdurrashid_sufi_soosi_2020/"
          )
        )
      )
    ),
    Qari(
      "ibnamir", "Ibn 'Amir ash-Shami", "ابن عامر الشامي", listOf(
        Riwayah(
          "hisham", "Hisham", "هشام",
          "القرآن الكريم برواية هشام عن ابن عامر.pdf", 10792674, "a9540c8fb7f376b7f4d17c576ee22e1b",
          RiwayahAudioSource(
            "Ahmad Deban", "أحمد ديبان", MP3_QURAN + "ahmad-deeban/r12/",
            setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 13, 85, 90, 92, 93, 94, 95, 99, 100, 101, 103, 105, 107,
              108, 110, 112, 113, 114)
          )
        ),
        Riwayah(
          "ibndhakwan", "Ibn Dhakwan", "ابن ذكوان",
          "القرآن الكريم برواية ابن ذكوان عن ابن عامر.pdf", 10718818, "4017e03e3f9db1fa65b36371773cf329",
          RiwayahAudioSource("Muftah al-Saltany", "مفتاح السلطني", MP3_QURAN + "muftah-saltani/r5/")
        )
      )
    ),
    Qari(
      "asim", "'Asim al-Kufi", "عاصم الكوفي", listOf(
        Riwayah(
          "shubah", "Shu'bah", "شعبة",
          "القرآن الكريم برواية شعبة عن عاصم.pdf", 10744558, "0912637757d1e9a6fd0bdf25892f9fcb",
          RiwayahAudioSource(
            "Abdur-Rashid Sufi", "عبد الرشيد صوفي", QURANIC_AUDIO + "abdurrashid_sufi_shu3ba/"
          )
        ),
        Riwayah(
          "hafs", "Hafs", "حفص",
          "القرآن الكريم برواية حفص عن عاصم.pdf", 10424221, "8d7239673e2a398002cd1c6092224f4c",
          RiwayahAudioSource(
            "Mahmoud Khalil Al-Husary", "محمود خليل الحصري",
            QURANIC_AUDIO + "mahmood_khaleel_al-husaree/"
          )
        )
      )
    ),
    Qari(
      "hamzah", "Hamzah az-Zayyat", "حمزة الزيات", listOf(
        Riwayah(
          "khalaf", "Khalaf", "خلف",
          "القرآن الكريم برواية خلف عن حمزة.pdf", 11026466, "9ac4abfa86d1b521c31cbc969e29c375",
          RiwayahAudioSource(
            "Abdur-Rashid Sufi", "عبد الرشيد صوفي",
            QURANIC_AUDIO + "abdurrashid_sufi_-_khalaf_3an_7amza_recitation/"
          )
        ),
        Riwayah(
          "khallad", "Khallad", "خلاد",
          "القرآن الكريم برواية خلاد عن حمزة.pdf", 10812397, "f70f17e4d34e533d1f685407e8da7fef",
          null
        )
      )
    ),
    Qari(
      "kisai", "Al-Kisa'i", "الكسائي", listOf(
        Riwayah(
          "abulharith", "Abu al-Harith", "أبو الحارث",
          "القرآن الكريم برواية أبي الحارث عن الكسائي.pdf", 10895140, "80c0835c1e952b7ae8bd02d7d4a8f470",
          RiwayahAudioSource(
            "Abdur-Rashid Sufi", "عبد الرشيد صوفي", QURANIC_AUDIO + "abdurrashid_sufi_abi_al7arith/"
          )
        ),
        Riwayah(
          "duri_kisai", "Ad-Duri", "الدوري",
          "القرآن الكريم برواية الدوري عن الكسائي.pdf", 15203681, "0098c890585589c642387cade238b28c",
          RiwayahAudioSource("Muftah al-Saltany", "مفتاح السلطني", MP3_QURAN + "muftah-saltani/r2/")
        )
      )
    ),
    Qari(
      "abujafar", "Abu Ja'far al-Madani", "أبو جعفر المدني", listOf(
        Riwayah(
          "ibnwardan", "Ibn Wardan", "ابن وردان",
          "القرآن الكريم برواية ابن وردان عن أبي جعفر.pdf", 4460329, "a73b5a47b3de84d0cf21b9dbe24f13f1",
          null
        ),
        Riwayah(
          "ibnjammaz", "Ibn Jammaz", "ابن جماز",
          "القرآن الكريم برواية ابن جماز عن أبي جعفر.pdf", 4532816, "31e25e85f1a52b5b95269c14068585ce",
          RiwayahAudioSource(
            "Ahmad Deban", "أحمد ديبان", MP3_QURAN + "ahmad-deeban/r13/",
            setOf(1, 93, 100, 102, 103, 109, 110, 111, 112, 113, 114)
          )
        )
      )
    ),
    Qari(
      "yaqub", "Ya'qub al-Hadrami", "يعقوب الحضرمي", listOf(
        Riwayah(
          "ruways", "Ruways", "رويس",
          "القرآن الكريم برواية رويس عن يعقوب الحضرمي.pdf", 4267310, "b7fa4ecf98d8c5af2adfbe9e7a7f9d5e",
          // this recording combines the riwayat of Ruways and Rawh
          RiwayahAudioSource(
            "Yasser Al-Mazroyee (Ruways & Rawh)", "ياسر المزروعي (رويس وروح)",
            MP3_QURAN + "yasser-mazrouei/r1/"
          )
        ),
        Riwayah(
          "rawh", "Rawh", "روح",
          "القرآن الكريم برواية روح عن يعقوب الحضرمي.pdf", 4170469, "2e3ce52ed7f83bfd7e27af405e014255",
          RiwayahAudioSource(
            "Yasser Al-Mazroyee (Ruways & Rawh)", "ياسر المزروعي (رويس وروح)",
            MP3_QURAN + "yasser-mazrouei/r1/"
          )
        )
      )
    ),
    Qari(
      "khalafashir", "Khalaf al-'Ashir", "خلف العاشر", listOf(
        Riwayah(
          "ishaq", "Ishaq", "إسحاق",
          "القرآن الكريم برواية إسحاق الوراق عن خلف البزار.pdf", 8137508, "b648f9b564cae2d29e8c6b8a4427f14d",
          null
        ),
        Riwayah(
          "idris", "Idris", "إدريس",
          "القرآن الكريم برواية إدريس الحداد عن خلف البزار.pdf", 8198511, "81dcf3b02ce553e089182af83d3ed610",
          null
        )
      )
    )
  )

  val all: List<Riwayah> = qurra.flatMap { it.riwayat }

  fun byId(id: String?): Riwayah? = all.firstOrNull { it.id == id }

  fun qariOf(riwayah: Riwayah): Qari = qurra.first { riwayah in it.riwayat }
}
