package com.quran.mobile.feature.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.library.data.RecitationRepository
import com.quran.mobile.feature.library.data.RecitationState
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
  fun recordingsReciteTheVersesInOrder() {
    catalog.poems.forEach { poem ->
      assertThat(poem.recitations).isNotEmpty()
      poem.recitations.forEach { recitation ->
        assertThat(recitation.tracks).isNotEmpty()
        recitation.tracks.forEach { track ->
          val asset = track.asset
          if (asset != null) context.assets.open(asset).close() else assertThat(track.url).startsWith("https://")
        }
        val timings = LibraryCatalog.timings(context, recitation)
        assertThat(timings.verseCount).isEqualTo(poem.verseCount)
        // most verses are in the recording, each after the one before it
        val timed = (1..poem.verseCount).filter(timings::has)
        assertThat(timed.size).isAtLeast(poem.verseCount * 3 / 4)
        timed.zipWithNext().forEach { (a, b) ->
          val trackA = timings.trackOf(a)
          val trackB = timings.trackOf(b)
          assertThat(trackB).isAtLeast(trackA)
          if (trackA == trackB) assertThat(timings.startOf(b)).isGreaterThan(timings.startOf(a))
        }
        timed.forEach { verse ->
          assertThat(timings.trackOf(verse)).isIn(recitation.tracks.indices)
          assertThat(timings.verseAt(timings.trackOf(verse), timings.startOf(verse))).isEqualTo(verse)
        }
      }
    }
  }

  @Test
  fun recordingsCanBeKeptOnTheDevice() {
    val repository = RecitationRepository.get(context)
    catalog.poems.flatMap { it.recitations }.forEach { recitation ->
      if (recitation.isBundled) {
        assertThat(repository.states.value[recitation.id]).isEqualTo(RecitationState.Downloaded)
      } else {
        assertThat(recitation.canDownload).isTrue()
        assertThat(repository.states.value[recitation.id]).isEqualTo(RecitationState.NotDownloaded)
        recitation.tracks.forEachIndexed { index, track ->
          assertThat(track.offlineUrl).startsWith("https://")
          assertThat(track.offlineUrl).endsWith("/${recitation.id}/${LibraryCatalog.offlineName(index)}")
          assertThat(track.offlineBytes).isGreaterThan(1000)
          assertThat(repository.file(recitation, index)).isNull()
        }
        // far smaller than what is streamed: under 12 MB an hour of it
        assertThat(recitation.offlineBytes).isLessThan(45_000_000)
      }
    }
    // a track is here once its copy is whole
    val recitation = catalog.poem("tuhfah")!!.recitations.first { !it.isBundled }
    val file = java.io.File(context.filesDir, "library/audio/${recitation.id}/${LibraryCatalog.offlineName(0)}")
    file.parentFile!!.mkdirs()
    file.writeBytes(ByteArray(100))
    assertThat(repository.file(recitation, 0)).isNull()
    file.writeBytes(ByteArray(recitation.tracks[0].offlineBytes.toInt()))
    assertThat(repository.file(recitation, 0)).isEqualTo(file)
    repository.delete(recitation)
    assertThat(file.exists()).isFalse()
  }

  @Test
  fun whatIsShownUnderTheVersesIsOfVersesOfThePoem() {
    catalog.poems.forEach { poem ->
      assertThat(poem.inlineDocuments).isNotEmpty()
      poem.inlineDocuments.forEach { id ->
        val document = catalog.document(id)!!
        assertThat(poem.documents).contains(id)
        val index = LibraryCatalog.inlineIndex(context, id)
        assertThat(index.width).isGreaterThan(100)
        assertThat(index.entries).isNotEmpty()
        var last = 0
        index.entries.forEach { entry ->
          assertThat(entry.first).isGreaterThan(last)
          assertThat(entry.last).isAtLeast(entry.first)
          assertThat(entry.last).isAtMost(poem.verseCount)
          assertThat(entry.translation + entry.explanation).isNotEmpty()
          (entry.translation + entry.explanation).forEach { region ->
            assertThat(region.page).isIn(0 until document.pages)
            assertThat(region.bottom).isGreaterThan(region.top)
            assertThat(region.left).isAtLeast(0)
          }
          last = entry.last
        }
      }
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
}
