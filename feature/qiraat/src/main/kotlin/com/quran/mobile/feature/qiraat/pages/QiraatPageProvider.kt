package com.quran.mobile.feature.qiraat.pages

import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.common.madani.size.DefaultPageSizeCalculator
import com.quran.mobile.feature.qiraat.R
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.model.Riwayah

/**
 * A riwayah mushaf as a page type of the main reader, like the Madinah (Hafs) mushaf.
 *
 * Nothing is downloaded from a page server: the first time the page type is opened, the riwayah's
 * PDF is downloaded and [QiraatPagePreparer] renders its pages and writes the ayah position and
 * audio timing databases the reader expects.
 */
class QiraatPageProvider(val riwayah: Riwayah) : PageProvider {
  private val riwayahDataSource by lazy { RiwayahDataSource(riwayah.id) }

  /** the riwayah whose recording is played; a sibling riwayah's when this one has none */
  val audioRiwayah: Riwayah
    get() = if (riwayah.audio != null) riwayah else Qiraat.byId(AUDIO_FALLBACK[riwayah.id])!!

  val qari: Qari
    get() = Qari(
      id = QARI_ID_BASE + Qiraat.all.indexOf(riwayah),
      nameResource = RECITER_NAMES.getValue(riwayah.id),
      url = audioRiwayah.audio!!.baseUrl,
      path = "qiraat_${riwayah.id}",
      hasGaplessAlternative = false,
      db = "qiraat_${riwayah.id}_timings"
    )

  override fun getDataSource() = riwayahDataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
    DefaultPageSizeCalculator(displaySize)

  override fun getImageVersion() = 1

  // pages, ayah positions and timings are made on the device, so these are never fetched
  override fun getImagesBaseUrl() = UNUSED_URL
  override fun getImagesZipBaseUrl() = UNUSED_URL
  override fun getPatchBaseUrl() = UNUSED_URL
  override fun getAyahInfoBaseUrl() = UNUSED_URL
  override fun getDatabasesBaseUrl() = "https://android.quran.com/data/databases/"
  override fun getAudioDatabasesBaseUrl() = UNUSED_URL

  override fun getAudioDirectoryName() = "audio"
  override fun getDatabaseDirectoryName() = "databases"
  override fun getAyahInfoDirectoryName() = "qiraat/${riwayah.id}"
  override fun getImagesDirectoryName() = "qiraat/${riwayah.id}"

  override fun getPreviewTitle() = TITLES.getValue(riwayah.id)
  override fun getPreviewDescription() = R.string.qiraat_pagetype_description

  override fun getFallbackPageType() = "madani"

  override fun getQaris(): List<Qari> = listOf(qari)
  override fun getDefaultQariId() = qari.id

  override fun pageType() = PAGE_TYPE_PREFIX + riwayah.id

  companion object {
    const val PAGE_TYPE_PREFIX = "qiraat_"
    private const val QARI_ID_BASE = 2000
    private const val UNUSED_URL = "https://android.quran.com/data/qiraat/unavailable/"

    private val AUDIO_FALLBACK = mapOf(
      "khallad" to "khalaf",
      "ibnwardan" to "ibnjammaz",
      "ishaq" to "khalaf",
      "idris" to "khalaf"
    )

    private val TITLES = mapOf(
    "qalun" to R.string.qiraat_pagetype_qalun,
    "warsh" to R.string.qiraat_pagetype_warsh,
    "bazzi" to R.string.qiraat_pagetype_bazzi,
    "qunbul" to R.string.qiraat_pagetype_qunbul,
    "duri_abuamr" to R.string.qiraat_pagetype_duri_abuamr,
    "susi" to R.string.qiraat_pagetype_susi,
    "hisham" to R.string.qiraat_pagetype_hisham,
    "ibndhakwan" to R.string.qiraat_pagetype_ibndhakwan,
    "shubah" to R.string.qiraat_pagetype_shubah,
    "khalaf" to R.string.qiraat_pagetype_khalaf,
    "khallad" to R.string.qiraat_pagetype_khallad,
    "abulharith" to R.string.qiraat_pagetype_abulharith,
    "duri_kisai" to R.string.qiraat_pagetype_duri_kisai,
    "ibnwardan" to R.string.qiraat_pagetype_ibnwardan,
    "ibnjammaz" to R.string.qiraat_pagetype_ibnjammaz,
    "ruways" to R.string.qiraat_pagetype_ruways,
    "rawh" to R.string.qiraat_pagetype_rawh,
    "ishaq" to R.string.qiraat_pagetype_ishaq,
    "idris" to R.string.qiraat_pagetype_idris
    )

    private val RECITER_NAMES = mapOf(
    "qalun" to R.string.qiraat_reciter_qalun,
    "warsh" to R.string.qiraat_reciter_warsh,
    "bazzi" to R.string.qiraat_reciter_bazzi,
    "qunbul" to R.string.qiraat_reciter_qunbul,
    "duri_abuamr" to R.string.qiraat_reciter_duri_abuamr,
    "susi" to R.string.qiraat_reciter_susi,
    "hisham" to R.string.qiraat_reciter_hisham,
    "ibndhakwan" to R.string.qiraat_reciter_ibndhakwan,
    "shubah" to R.string.qiraat_reciter_shubah,
    "khalaf" to R.string.qiraat_reciter_khalaf,
    "khallad" to R.string.qiraat_reciter_khallad,
    "abulharith" to R.string.qiraat_reciter_abulharith,
    "duri_kisai" to R.string.qiraat_reciter_duri_kisai,
    "ibnwardan" to R.string.qiraat_reciter_ibnwardan,
    "ibnjammaz" to R.string.qiraat_reciter_ibnjammaz,
    "ruways" to R.string.qiraat_reciter_ruways,
    "rawh" to R.string.qiraat_reciter_rawh,
    "ishaq" to R.string.qiraat_reciter_ishaq,
    "idris" to R.string.qiraat_reciter_idris
    )

    /** the page types, keyed as the reader's page type setting stores them (Hafs is "madani") */
    val all: Map<String, QiraatPageProvider> by lazy {
      Qiraat.all.filter { it.id != "hafs" }
        .associate { PAGE_TYPE_PREFIX + it.id to QiraatPageProvider(it) }
    }
  }
}
