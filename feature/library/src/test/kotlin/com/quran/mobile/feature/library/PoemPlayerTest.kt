package com.quran.mobile.feature.library

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.library.audio.PoemPlayer
import com.quran.mobile.feature.library.model.AudioTrack
import com.quran.mobile.feature.library.model.Recitation
import com.quran.mobile.feature.library.model.VerseTimings
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.util.concurrent.TimeUnit

/** how a recitation is repeated: a verse, a set of verses (the player's time is the test's) */
@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class PoemPlayerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val file = File("/recording/000.ogg")
  // six verses of five seconds each, in one track
  private val timings = VerseTimings(IntArray(6), IntArray(6) { it * 5000 })
  private lateinit var player: PoemPlayer

  @Before
  fun setUp() {
    ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(file.path), ShadowMediaPlayer.MediaInfo(30000, 0))
    player = PoemPlayer(context) { _, _ -> file }
    player.setRecitation(recitation, timings)
  }

  private fun after(ms: Long): Int? {
    shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)
    return player.state.value.verse
  }

  @Test
  fun theRecitationGoesOnFromVerseToVerse() {
    player.playVerse(2)
    assertThat(after(4000)).isEqualTo(2)
    assertThat(after(2000)).isEqualTo(3)
    assertThat(after(5000)).isEqualTo(4)
  }

  @Test
  fun aVerseIsRepeatedAsManyTimesAsAsked() {
    player.set(PoemPlayer.Settings(verseRepeat = 1))
    player.playVerse(2)
    assertThat(after(4000)).isEqualTo(2)
    // (again)
    assertThat(after(2000)).isEqualTo(2)
    assertThat(after(3000)).isEqualTo(2)
    // then the next, which is repeated too
    assertThat(after(2000)).isEqualTo(3)
    assertThat(after(5000)).isEqualTo(3)
    assertThat(after(5000)).isEqualTo(4)
  }

  @Test
  fun aVerseIsRepeatedWithoutEnd() {
    player.set(PoemPlayer.Settings(verseRepeat = PoemPlayer.INFINITE))
    player.playVerse(3)
    repeat(6) { assertThat(after(4900)).isEqualTo(3) }
    // until asked otherwise
    player.set(PoemPlayer.Settings())
    assertThat(after(2000)).isEqualTo(4)
    assertThat(after(5000)).isEqualTo(5)
  }

  @Test
  fun aSetOfVersesIsRepeated() {
    player.set(PoemPlayer.Settings(first = 2, last = 3, rangeRepeat = 1))
    player.toggle(5)
    // (from the first of the set, wherever the screen is)
    assertThat(after(1000)).isEqualTo(2)
    assertThat(after(5000)).isEqualTo(3)
    assertThat(after(5000)).isEqualTo(2)
    assertThat(after(5000)).isEqualTo(3)
    // and no more than asked
    after(5000)
    assertThat(player.state.value.track).isNull()
  }

  @Test
  fun theNextVerseOfASetsLastIsItsFirst() {
    player.set(PoemPlayer.Settings(first = 2, last = 3, rangeRepeat = PoemPlayer.INFINITE))
    player.playVerse(3)
    after(1000)
    player.nextVerse()
    assertThat(after(1000)).isEqualTo(2)
    player.previousVerse()
    assertThat(after(1000)).isEqualTo(2)
    // a verse out of the set ends it
    player.playVerse(5)
    assertThat(player.settings.value.hasRange).isFalse()
    assertThat(after(6000)).isEqualTo(6)
  }

  @Test
  fun theLastVerseOfTheRecordingIsRepeated() {
    player.set(PoemPlayer.Settings(verseRepeat = 1))
    player.playVerse(6)
    assertThat(after(4000)).isEqualTo(6)
    // (the track has ended, and the verse is recited again)
    assertThat(after(2000)).isEqualTo(6)
    assertThat(player.state.value.isPlaying).isTrue()
    after(5000)
    assertThat(player.state.value.track).isNull()
  }

  private val recitation = Recitation(
    id = "test", reciterArabic = "", reciterEnglish = "",
    tracks = listOf(AudioTrack(url = "https://example.invalid/000.mp3", asset = null, offlineUrl = null, offlineBytes = 0))
  )
}
