package com.quran.mobile.feature.tafsir.model

/**
 * A tafsir that can be downloaded. The texts are those of quran.com (through QUL, tarteel.ai),
 * as published, one file per sura, by the open spa5k/tafsir_api project.
 *
 * @param id the tafsir's name in that project
 * @param isArabic whether the tafsir is in Arabic (otherwise English)
 */
data class Tafsir(
  val id: String,
  val isArabic: Boolean,
  val nameEn: String,
  val nameAr: String,
  val authorEn: String,
  val authorAr: String,
  /** about how much is downloaded */
  val downloadMb: Int
) {
  fun name(arabic: Boolean) = if (arabic) nameAr else nameEn
  fun author(arabic: Boolean) = if (arabic) authorAr else authorEn

  /** where a sura's tafsir can be downloaded from, in order of preference */
  fun suraUrls(sura: Int): List<String> = BASE_URLS.map { "$it$id/$sura.json" }

  companion object {
    // jsDelivr refuses files of this (large) repository that it has not cached yet
    private val BASE_URLS = listOf(
      "https://raw.githubusercontent.com/spa5k/tafsir_api/main/tafsir/",
      "https://cdn.jsdelivr.net/gh/spa5k/tafsir_api@main/tafsir/"
    )
  }
}

object Tafaseer {
  val all = listOf(
    Tafsir(
      "ar-tafsir-ibn-kathir", true,
      "Tafsir Ibn Kathir", "تفسير ابن كثير",
      "Ibn Kathir", "ابن كثير",
      90
    ),
    Tafsir(
      "ar-tafseer-al-saddi", true,
      "Tafsir as-Sa'di", "تفسير السعدي",
      "Abdur-Rahman as-Sa'di", "عبد الرحمن السعدي",
      6
    ),
    Tafsir(
      "ar-tafsir-muyassar", true,
      "At-Tafsir al-Muyassar", "التفسير الميسر",
      "King Fahd Complex", "مجمع الملك فهد",
      3
    ),
    Tafsir(
      "ar-tafsir-al-mukhtasar", true,
      "Al-Mukhtasar fi Tafsir al-Quran", "المختصر في تفسير القرآن",
      "Tafsir Center for Quranic Studies", "مركز تفسير للدراسات القرآنية",
      2
    ),
    Tafsir(
      "ar-tafsir-al-baghawi", true,
      "Tafsir al-Baghawi", "تفسير البغوي",
      "Al-Baghawi", "البغوي",
      39
    ),
    Tafsir(
      "ar-tafseer-al-qurtubi", true,
      "Tafsir al-Qurtubi", "تفسير القرطبي",
      "Al-Qurtubi", "القرطبي",
      78
    ),
    Tafsir(
      "ar-tafsir-al-tabari", true,
      "Tafsir at-Tabari", "تفسير الطبري",
      "At-Tabari", "الطبري",
      61
    ),
    Tafsir(
      "ar-tafsir-al-wasit", true,
      "At-Tafsir al-Wasit", "التفسير الوسيط",
      "Muhammad Sayyid Tantawi", "محمد سيد طنطاوي",
      18
    ),
    Tafsir(
      "ar-tafsir-al-jalalayn", true,
      "Tafsir al-Jalalayn", "تفسير الجلالين",
      "Al-Mahalli and as-Suyuti", "المحلي والسيوطي",
      3
    ),
    Tafsir(
      "en-tafisr-ibn-kathir", false,
      "Tafsir Ibn Kathir (abridged)", "تفسير ابن كثير (مختصر، بالإنجليزية)",
      "Ibn Kathir", "ابن كثير",
      45
    ),
    Tafsir(
      "en-tafsir-al-mukhtasar", false,
      "Al-Mukhtasar (Abridged Explanation)", "المختصر في التفسير (بالإنجليزية)",
      "Tafsir Center for Quranic Studies", "مركز تفسير للدراسات القرآنية",
      2
    ),
    Tafsir(
      "en-tafsir-maarif-ul-quran", false,
      "Ma'arif al-Qur'an", "معارف القرآن (بالإنجليزية)",
      "Mufti Muhammad Shafi", "المفتي محمد شفيع",
      22
    ),
    Tafsir(
      "tafsir-al-jalalayn", false,
      "Tafsir al-Jalalayn", "تفسير الجلالين (بالإنجليزية)",
      "Al-Mahalli and as-Suyuti", "المحلي والسيوطي",
      3
    ),
    Tafsir(
      "en-tazkirul-quran", false,
      "Tazkirul Quran", "تذكير القرآن (بالإنجليزية)",
      "Wahiduddin Khan", "وحيد الدين خان",
      7
    )
  )

  fun byId(id: String?): Tafsir? = all.firstOrNull { it.id == id }
}
