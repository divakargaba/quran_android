package com.quran.mobile.feature.qiraat.data

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.qiraat.model.Qiraat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

// org.json comes from the android platform
@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QiraatDifferencesTest {
  private val differences = File("src/main/assets/qiraat/differences.qdf").inputStream()
    .use { QiraatDifferences.load(it) }

  @Test
  fun jibrilAndMikal() {
    // al-Baqarah 98: Ibn Kathir reads "jabrīl", Shu'bah "jabraʾil"; Nafi' "mīkāʾil"
    val bazzi = differences.between("hafs", "bazzi", 2, 98).map { it.word }
    assertThat(bazzi).contains("وجبريل")
    val qalun = differences.between("hafs", "qalun", 2, 98)
    assertThat(qalun.map { it.word }).contains("وميكال")
    val mikal = qalun.first { it.word == "وميكال" }
    assertThat(mikal.other.arabic).contains("همزة مكسورة")
    assertThat(mikal.other.english).contains("hamzah")
    assertThat(mikal.firstWord).isAtLeast(0)
  }

  @Test
  fun everyDescriptionHasEnglish() {
    (1..286).forEach { ayah ->
      differences.between("hafs", "qalun", 2, ayah).forEach {
        assertThat(it.base.english).isNotEmpty()
        assertThat(it.other.english).isNotEmpty()
      }
    }
  }

  @Test
  fun wordsAreShownAsEachRiwayahWritesThem() {
    val texts = RiwayahTexts.forTesting { sura ->
      File("src/main/assets/qiraat/text/%03d.qtx".format(sura)).inputStream()
    }
    // al-Baqarah 6: Qalun eases the second hamzah of "a-andhartahum"
    val word = differences.between("hafs", "qalun", 2, 6).first { it.word == "أأنذرتهم" }
    val ayah = texts.ayah(2, 6)
    assertThat(ayah.keys).containsAtLeastElementsIn(Qiraat.all.map { it.id })
    assertThat(ayah.getValue("hafs").words(word.firstWord, word.wordCount)).isEqualTo("ءَأَنذَرۡتَهُمۡ")
    assertThat(ayah.getValue("qalun").font).isEqualTo("qaloon")
    assertThat(ayah.getValue("qalun").words(word.firstWord, word.wordCount)).isNotEqualTo("ءَأَنذَرۡتَهُمۡ")
    assertThat(ayah.getValue("hafs").text.split(" ")).hasSize(11)
  }

  @Test
  fun aRiwayahHasNoDifferenceWithItself() {
    assertThat(differences.between("warsh", "warsh", 2, 98)).isEmpty()
  }

  @Test
  fun everyRiwayahIsKnown() {
    Qiraat.all.forEach { riwayah ->
      // every riwayah differs from Hafs somewhere in al-Baqarah
      val any = (1..286).any { differences.between("hafs", riwayah.id, 2, it).isNotEmpty() }
      assertThat(any || riwayah.id == "hafs").isTrue()
    }
  }
}
