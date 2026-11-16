package com.quran.mobile.feature.library

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.library.model.VerseTimings
import org.junit.Test

class VerseTimingsTest {
  // six verses: two in the first track, one the recording has not, two in the second track,
  // and one more it has not between them and the last
  private val timings = VerseTimings(
    tracks = intArrayOf(0, 0, -1, 1, -1, 1),
    starts = intArrayOf(1000, 5000, 0, 200, 0, 4000)
  )

  @Test
  fun theVerseRecitedAtATime() {
    assertThat(timings.verseAt(0, 0)).isNull()
    assertThat(timings.verseAt(0, 1000)).isEqualTo(1)
    assertThat(timings.verseAt(0, 4999)).isEqualTo(1)
    assertThat(timings.verseAt(0, 60000)).isEqualTo(2)
    assertThat(timings.verseAt(1, 100)).isNull()
    assertThat(timings.verseAt(1, 3999)).isEqualTo(4)
    assertThat(timings.verseAt(1, 4000)).isEqualTo(6)
    assertThat(timings.verseAt(2, 0)).isNull()
  }

  @Test
  fun versesTheRecordingHasNotAreSkipped() {
    assertThat(timings.has(3)).isFalse()
    assertThat(timings.has(0)).isFalse()
    assertThat(timings.has(7)).isFalse()
    assertThat(timings.from(3)).isEqualTo(4)
    assertThat(timings.from(5)).isEqualTo(6)
    assertThat(timings.upTo(3)).isEqualTo(2)
    assertThat(timings.from(7)).isNull()
    assertThat(timings.upTo(0)).isNull()
    assertThat(timings.firstVerseOf(1)).isEqualTo(4)
    assertThat(timings.firstVerseOf(2)).isNull()
  }
}
