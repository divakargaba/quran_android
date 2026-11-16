package com.quran.mobile.feature.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.library.model.LibraryCatalog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class LibraryCatalogTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val catalog = LibraryCatalog.get(context)

  @Test
  fun poemsHaveTheirVerses() {
    val counts = catalog.poems.associate { it.id to it.verseCount }
    assertThat(counts).containsExactly(
      "shatibiyyah", 1173, "durrah", 241, "tayyibah", 1015, "jazariyyah", 107, "tuhfah", 61
    )
    catalog.poems.forEach { poem ->
      val text = LibraryCatalog.poemText(context, poem.id)
      assertThat(text.chapters.sumOf { it.verses.size }).isEqualTo(poem.verseCount)
      text.chapters.forEach { chapter ->
        assertThat(chapter.title).isNotEmpty()
        assertThat(chapter.verses).isNotEmpty()
        chapter.verses.forEach { verse ->
          assertThat(verse.firstHalf).isNotEmpty()
          assertThat(verse.secondHalf).isNotEmpty()
        }
      }
      // the chapters follow one another
      var next = 1
      text.chapters.forEach { assertThat(it.first).isEqualTo(next); next += it.verses.size }
    }
  }

  @Test
  fun tracksOfChaptersAreChaptersOfThePoem() {
    catalog.poems.forEach { poem ->
      val chapters = LibraryCatalog.poemText(context, poem.id).chapters.size
      val audio = poem.audio ?: return@forEach
      assertThat(audio.tracks).isNotEmpty()
      val mapped = audio.tracks.flatMap { it.chapters }
      mapped.forEach { assertThat(it).isIn(0 until chapters) }
      assertThat(mapped).containsNoDuplicates()
      // a recording matched to the chapters has a track for each
      if (mapped.isNotEmpty()) assertThat(mapped).hasSize(chapters)
      audio.tracks.forEach { assertThat(it.url).startsWith("https://") }
    }
  }

  @Test
  fun everyDocumentIsListedAndCanBeHad() {
    val listed = catalog.poems.flatMap { it.documents } + catalog.usool.flatMap { it.documents }
    listed.forEach { assertThat(catalog.document(it)).isNotNull() }
    assertThat(catalog.documents.map { it.id }).containsExactlyElementsIn(listed.toSet())
    catalog.documents.forEach { document ->
      assertThat(document.titleArabic).isNotEmpty()
      assertThat(document.pages).isGreaterThan(0)
      val asset = document.asset
      if (asset != null) {
        assertThat(context.assets.open(asset).use { it.readBytes() }.decodeToString(0, 5)).isEqualTo("%PDF-")
      } else {
        assertThat(document.url).startsWith("https://")
      }
    }
  }

  @Test
  fun versesOfTheJazariyyahOpenInItsTranslation() {
    val poem = catalog.poem("jazariyyah")!!
    val document = catalog.document(poem.verseDocument!!)!!
    assertThat(poem.versePages).hasSize(poem.verseCount)
    assertThat(poem.versePages).isInOrder()
    poem.versePages.forEach { assertThat(it).isIn(0 until document.pages) }
  }
}
