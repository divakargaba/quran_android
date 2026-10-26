package com.quran.mobile.feature.qiraat.data

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.model.Timings
import org.junit.Test
import java.io.File

class RiwayahLocationsTest {

  private fun load(id: String): RiwayahLocations =
    File("src/main/assets/qiraat/$id.qrx").inputStream().use { RiwayahLocations.load(it) }

  @Test
  fun everyRiwayahHasLocationsForEveryAyah() {
    assertThat(Qiraat.all).hasSize(20)
    Qiraat.all.forEach { riwayah ->
      val locations = load(riwayah.id)
      for (index in 0 until AyahIndex.AYAH_COUNT) {
        val (sura, ayah) = AyahIndex.suraAyahOf(index)
        assertThat(locations.pageFor(sura, ayah)).isIn(1..604)
      }
    }
  }

  @Test
  fun anAyahIsPlayedAsTheWholeRiwayahAyahContainingIt() {
    // Warsh's first ayah of al-Baqarah is the first two of Hafs
    val warsh = load("warsh")
    assertThat(warsh.hafsAyatOfRiwayahAyah(2, 1)).isEqualTo(1..2)
    assertThat(warsh.hafsAyatOfRiwayahAyah(2, 2)).isEqualTo(1..2)
    assertThat(warsh.hafsAyatOfRiwayahAyah(2, 3)).isEqualTo(3..3)
    val hafs = load("hafs")
    for (ayah in 1..286) assertThat(hafs.hafsAyatOfRiwayahAyah(2, ayah)).isEqualTo(ayah..ayah)
  }

  @Test
  fun hafsPagesMatchTheMadinahMushaf() {
    val hafs = load("hafs")
    assertThat(hafs.pageFor(1, 7)).isEqualTo(1)
    assertThat(hafs.pageFor(2, 5)).isEqualTo(2)
    assertThat(hafs.pageFor(2, 255)).isEqualTo(42)
    assertThat(hafs.pageFor(18, 54)).isEqualTo(300)
    assertThat(hafs.pageFor(114, 6)).isEqualTo(604)
  }

  @Test
  fun pagesAreInMushafOrder() {
    Qiraat.all.forEach { riwayah ->
      val locations = load(riwayah.id)
      var previous = 1
      for (index in 0 until AyahIndex.AYAH_COUNT) {
        val (sura, ayah) = AyahIndex.suraAyahOf(index)
        val page = locations.pageFor(sura, ayah)
        assertThat(page).isAtLeast(previous)
        previous = page
      }
    }
  }

  @Test
  fun ayahIndexRoundTrips() {
    assertThat(AyahIndex.indexOf(1, 1)).isEqualTo(0)
    assertThat(AyahIndex.indexOf(114, 6)).isEqualTo(AyahIndex.AYAH_COUNT - 1)
    for (index in 0 until AyahIndex.AYAH_COUNT) {
      val (sura, ayah) = AyahIndex.suraAyahOf(index)
      assertThat(AyahIndex.indexOf(sura, ayah)).isEqualTo(index)
    }
  }

  @Test
  fun timingsStartWithinEachSuraInOrder() {
    Qiraat.all.forEach { riwayah ->
      val asset = riwayah.timingsAsset ?: return@forEach
      val timings = File("src/main/assets/$asset").inputStream().use { RiwayahTimings.load(it) }
      for (sura in 1..114) {
        var previous = -1L
        for (ayah in 1..AyahIndex.ayahCount(sura)) {
          val times = timings.timesFor(sura, ayah) ?: continue
          assertThat(times.first).isAtLeast(previous)
          assertThat(times.last).isGreaterThan(times.first)
          previous = times.first
        }
      }
      if (riwayah.audio?.timings == Timings.EXACT) {
        assertThat(timings.timesFor(2, 255)).isNotNull()
      }
    }
  }

  @Test
  fun riwayahNumbersFollowTheirCount() {
    // outside the kufi count, "الم" is not an ayah of its own: 2:1 and 2:2 are one ayah
    val warsh = load("warsh")
    assertThat(warsh.riwayahAyahNumber(2, 1)).isEqualTo(1)
    assertThat(warsh.riwayahAyahNumber(2, 2)).isEqualTo(1)
    assertThat(warsh.riwayahAyahNumber(2, 3)).isEqualTo(2)
    assertThat(load("shubah").riwayahAyahNumber(2, 255)).isEqualTo(255)
  }
}
