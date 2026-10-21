package com.quran.mobile.feature.qiraat.data

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.qiraat.model.Qiraat
import org.junit.Test
import java.io.File

class RiwayahLocationsTest {

  private fun load(id: String): RiwayahLocations =
    File("src/main/assets/qiraat/$id.bin.gz").inputStream().use { RiwayahLocations.load(it) }

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
}
