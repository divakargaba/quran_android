package com.quran.mobile.feature.qiraat.audio

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.qiraat.data.AyahIndex
import com.quran.mobile.feature.qiraat.data.RiwayahLocations
import com.quran.mobile.feature.qiraat.model.Qiraat
import org.junit.Test
import java.io.File

class NquranAudioTest {

  @Test
  fun everyRiwayahAyahIsPlayedOnceInOrder() {
    Qiraat.all.filter { NquranAudio.has(it) }.forEach { riwayah ->
      val locations = File("src/main/assets/${riwayah.locationsAsset}").inputStream()
        .use { RiwayahLocations.load(it) }
      for (sura in 1..114) {
        val files = (1..AyahIndex.ayahCount(sura)).flatMap { ayah ->
          NquranAudio.filesFor(riwayah, locations, sura, ayah)
        }
        assertThat(files).isInStrictOrder()
        assertThat(files.first()).isAtMost(1)
      }
    }
  }

  @Test
  fun alFatihaStartsWithTheBasmalah() {
    val riwayah = Qiraat.byId("ibnwardan")!!
    val locations = File("src/main/assets/${riwayah.locationsAsset}").inputStream()
      .use { RiwayahLocations.load(it) }
    assertThat(NquranAudio.filesFor(riwayah, locations, 1, 1)).containsExactly(0)
    assertThat(NquranAudio.filesFor(riwayah, locations, 1, 2)).containsExactly(1)
  }

  @Test
  fun stripsTags() {
    val id3 = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 2)
    val audio = ByteArray(300) { 7 }
    val tag = ByteArray(128).also {
      it[0] = 'T'.code.toByte(); it[1] = 'A'.code.toByte(); it[2] = 'G'.code.toByte()
    }
    val bytes = id3 + byteArrayOf(0, 0) + audio + tag
    assertThat(NquranAudio.audioFrames(bytes)).isEqualTo(12 to 312)
  }
}
