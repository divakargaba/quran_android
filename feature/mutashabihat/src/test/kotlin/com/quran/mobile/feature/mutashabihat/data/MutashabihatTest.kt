package com.quran.mobile.feature.mutashabihat.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class MutashabihatTest {
  private val data = File("src/main/assets/mutashabihat.mtj").inputStream().use { Mutashabihat.load(it) }

  @Test
  fun phrasesForAnAyah() {
    val phrases = data.phrasesFor(2, 23)
    assertThat(phrases).isNotEmpty()
    val minDuniAllah = phrases.first { it.id == "50" }
    // "مِّن دُونِ ٱللَّهِ" (escaped, as the order of the marks matters)
    val text = "\u0645\u0651\u0650\u0646 \u062f\u064f\u0648\u0646\u0650 " +
      "\u0671\u0644\u0644\u0651\u064e\u0647\u0650"
    assertThat(minDuniAllah.text).isEqualTo(text)
    val occurrence = minDuniAllah.occurrences.first { it.verse == VerseKey(2, 107) }
    assertThat(occurrence.highlighted.map { occurrence.words.slice(it).joinToString(" ") })
      .containsExactly(text)
  }

  @Test
  fun ayahWithoutPhrases() {
    assertThat(data.phrasesFor(1, 1)).isEmpty()
  }
}
