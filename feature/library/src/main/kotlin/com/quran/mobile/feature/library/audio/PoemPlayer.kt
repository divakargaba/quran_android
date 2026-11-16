package com.quran.mobile.feature.library.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.quran.mobile.feature.library.model.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays the tracks of a poem's recording, streamed from where they are published, one after the
 * other. Used from the main thread, while the poem is on screen.
 */
class PoemPlayer(private val tracks: List<AudioTrack>) {

  data class State(
    /** the track being played (or prepared, or paused), if any */
    val track: Int? = null,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val failed: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0
  )

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  private var player: MediaPlayer? = null
  private var prepared = false
  private val handler = Handler(Looper.getMainLooper())
  private val tick = object : Runnable {
    override fun run() {
      val current = player
      if (current != null && prepared) {
        _state.value = _state.value.copy(positionMs = current.currentPosition, isPlaying = current.isPlaying)
      }
      handler.postDelayed(this, TICK_MS)
    }
  }

  fun play(track: Int) {
    if (track !in tracks.indices) return
    stopPlayer()
    _state.value = State(track = track, isPreparing = true)
    val next = MediaPlayer()
    player = next
    try {
      next.setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build()
      )
      next.setOnPreparedListener {
        if (player !== it) return@setOnPreparedListener
        prepared = true
        it.start()
        _state.value = State(track = track, isPlaying = true, durationMs = it.duration)
        handler.removeCallbacks(tick)
        handler.post(tick)
      }
      next.setOnCompletionListener {
        if (player !== it) return@setOnCompletionListener
        if (track + 1 < tracks.size) play(track + 1) else stop()
      }
      next.setOnErrorListener { failed, _, _ ->
        if (player === failed) {
          stopPlayer()
          _state.value = State(track = track, failed = true)
        }
        true
      }
      next.setDataSource(tracks[track].url)
      next.prepareAsync()
    } catch (e: Exception) {
      stopPlayer()
      _state.value = State(track = track, failed = true)
    }
  }

  /** pauses or resumes; starts the first track (or [track]) when nothing is being played */
  fun toggle(track: Int = 0) {
    val current = player
    val now = _state.value
    when {
      current == null || now.track == null || now.failed -> play(now.track ?: track)
      !prepared -> Unit
      current.isPlaying -> {
        current.pause()
        _state.value = now.copy(isPlaying = false)
      }
      else -> {
        current.start()
        _state.value = now.copy(isPlaying = true)
      }
    }
  }

  fun next() {
    val track = _state.value.track ?: return
    if (track + 1 < tracks.size) play(track + 1)
  }

  fun previous() {
    val track = _state.value.track ?: return
    if (track > 0) play(track - 1)
  }

  fun seekTo(positionMs: Int) {
    val current = player ?: return
    if (prepared) {
      current.seekTo(positionMs)
      _state.value = _state.value.copy(positionMs = positionMs)
    }
  }

  fun stop() {
    stopPlayer()
    _state.value = State()
  }

  fun release() = stop()

  private fun stopPlayer() {
    handler.removeCallbacks(tick)
    prepared = false
    player?.let {
      it.setOnPreparedListener(null)
      it.setOnCompletionListener(null)
      it.setOnErrorListener(null)
      try {
        it.reset()
      } catch (e: IllegalStateException) {
        // already released
      }
      it.release()
    }
    player = null
  }

  private companion object {
    const val TICK_MS = 500L
  }
}
