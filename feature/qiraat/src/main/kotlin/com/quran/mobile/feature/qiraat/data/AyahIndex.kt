package com.quran.mobile.feature.qiraat.data

/**
 * Maps a Hafs (Kufi count) sura and ayah to its position in the mushaf (0 until 6236) and back.
 * Riwayah locations are keyed by this index, independent of the riwayah's own ayah count.
 */
object AyahIndex {
  private val ayahsPerSura = intArrayOf(
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53,
    89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12,
    12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26,
    30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
  )

  private val suraStart = IntArray(115).also {
    for (i in 1..114) it[i] = it[i - 1] + ayahsPerSura[i - 1]
  }

  const val AYAH_COUNT = 6236

  fun ayahCount(sura: Int): Int = ayahsPerSura[sura - 1]

  fun indexOf(sura: Int, ayah: Int): Int = suraStart[sura - 1] + ayah - 1

  fun suraAyahOf(index: Int): Pair<Int, Int> {
    var sura = 1
    while (suraStart[sura] <= index) sura++
    return sura to (index - suraStart[sura - 1] + 1)
  }
}
