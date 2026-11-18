package com.quran.labs.androidquran.ui.library

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quran.mobile.feature.library.audio.PoemPlayer
import com.quran.mobile.feature.library.data.RecitationRepository
import com.quran.mobile.feature.library.data.RecitationState
import com.quran.mobile.feature.library.model.Poem
import com.quran.mobile.feature.library.model.Recitation
import com.quran.mobile.feature.library.model.VerseTimings
import kotlinx.coroutines.flow.StateFlow

/**
 * The recitation of a poem being played, which goes on when the poem's screen is left (with
 * [PoemPlaybackService]'s notification to pause it, skip a verse or stop it). One poem is played
 * at a time. Used from the main thread.
 */
class PoemPlayback private constructor(context: Context) {
  private val appContext = context.applicationContext
  private val recordings = RecitationRepository.get(appContext)
  private val player = PoemPlayer(appContext, recordings::file)

  /**
   * the poem and the recording being played (or last played): observable, so that a screen
   * showing a poem follows the recitation from the moment it starts it
   */
  var poem: Poem? by mutableStateOf(null)
    private set
  var recitation: Recitation? by mutableStateOf(null)
    private set

  val state: StateFlow<PoemPlayer.State> get() = player.state

  /** how the recitation is repeated (a verse, a set of verses), and its speed */
  val settings: StateFlow<PoemPlayer.Settings> get() = player.settings

  /** the poem the set of verses being repeated is of */
  var rangePoem: String? by mutableStateOf(null)
    private set

  /** how the recitation is repeated, and its speed, as chosen in a poem's screen */
  fun set(poem: Poem, settings: PoemPlayer.Settings) {
    // (a set of verses is not asked of another poem's recitation)
    if (settings.hasRange && this.poem?.id != poem.id) player.stop()
    rangePoem = poem.id.takeIf { settings.hasRange }
    player.set(settings)
  }

  /** recites a poem's recording from a verse (in place of what is being played) */
  fun playVerse(poem: Poem, recitation: Recitation, timings: VerseTimings, verse: Int) {
    use(poem, recitation, timings)
    player.playVerse(verse)
    started(recitation)
  }

  /** pauses or resumes a poem's recording; recites it from [verse] when it is not being played */
  fun toggle(poem: Poem, recitation: Recitation, timings: VerseTimings, verse: Int) {
    use(poem, recitation, timings)
    player.toggle(verse)
    if (player.state.value.track != null) started(recitation)
  }

  /** pauses or resumes what is being played */
  fun toggle() {
    player.toggle()
    recitation?.let { if (player.state.value.track != null) started(it) }
  }

  fun pause() = player.pause()
  fun nextVerse() = player.nextVerse()
  fun previousVerse() = player.previousVerse()

  /**
   * stops the recitation, and what was asked of how it is repeated (a verse, a set of verses):
   * it is recited plainly when played again, at the speed chosen
   */
  fun stop() {
    player.stop()
    rangePoem = null
    player.set(PoemPlayer.Settings(speed = player.settings.value.speed))
  }

  private fun use(poem: Poem, recitation: Recitation, timings: VerseTimings) {
    if (this.recitation?.id != recitation.id) {
      // (a set of verses to repeat is of the poem it was chosen in)
      if (rangePoem != poem.id) {
        rangePoem = null
        player.set(player.settings.value.withoutRange())
      }
      player.setRecitation(recitation, timings)
      this.poem = poem
      this.recitation = recitation
    }
  }

  private fun started(recitation: Recitation) {
    PoemPlaybackService.start(appContext)
    // a recording played is kept on the device (a compact copy of it), to be played without a
    // connection from then on
    val state = recordings.states.value[recitation.id]
    if (recitation.canDownload && (state == null || state is RecitationState.NotDownloaded)) {
      recordings.download(recitation)
    }
  }

  companion object {
    @Volatile private var instance: PoemPlayback? = null

    fun get(context: Context): PoemPlayback =
      instance ?: synchronized(this) {
        instance ?: PoemPlayback(context).also { instance = it }
      }
  }
}
