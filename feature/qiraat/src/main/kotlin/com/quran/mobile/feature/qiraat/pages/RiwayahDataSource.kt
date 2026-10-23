package com.quran.mobile.feature.qiraat.pages

import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource

/**
 * Page data of a riwayah mushaf. Ayat are numbered as in Hafs (so bookmarks, translations and
 * everything else keyed by ayah keep working); the riwayah mushafs follow the Madinah layout, but
 * a few of their page breaks fall an ayah or two away from it.
 */
class RiwayahDataSource(riwayahId: String) : MadaniDataSource() {
  private val starts = RiwayahPages.pageStarts.getValue(riwayahId)
    .split(',')
    .map { entry -> entry.split(':').let { it[0].toInt() to it[1].toInt() } }

  override val suraForPageArray: IntArray = starts.map { it.first }.toIntArray()

  override val ayahForPageArray: IntArray = starts.map { it.second }.toIntArray()

  /** the page each sura starts on: the last page that starts at or before its first ayah */
  override val pageForSuraArray: IntArray = IntArray(114) { index ->
    val sura = index + 1
    starts.indexOfLast { (startSura, startAyah) ->
      startSura < sura || (startSura == sura && startAyah == 1)
    } + 1
  }
}
