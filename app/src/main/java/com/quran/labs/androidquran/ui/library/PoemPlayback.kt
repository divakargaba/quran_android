package com.quran.labs.androidquran.ui.library

import android.content.Context
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

  /** the poem and the recording being played (or last played) */
  var poem: Poem? = null
    private set
  var recitation: Recitation? = null
    private set

  val state: StateFlow<PoemPlayer.State> get() = player.state

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
  fun stop() = player.stop()

  private fun use(poem: Poem, recitation: Recitation, timings: VerseTimings) {
    if (this.recitation?.id != recitation.id) {
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
