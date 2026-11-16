package com.quran.mobile.feature.library.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.quran.mobile.feature.library.model.Recitation
import com.quran.mobile.feature.library.model.VerseTimings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Plays a recording of a poem (its tracks one after the other: read from the device when they are
 * kept there or are in the app, streamed from where they are published otherwise), from any
 * verse, and tells which verse is being recited. Used from the main thread, while the poem is open.
 */
class PoemPlayer(
  context: Context,
  /** the copy of a recording's track kept on the device, if there is one: played rather than streamed */
  private val localFile: (Recitation, Int) -> File? = { _, _ -> null }
) {

  data class State(
    /** the verse being recited (from 1), if one is */
    val verse: Int? = null,
    /** the track being played (or prepared, or paused), if any */
    val track: Int? = null,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val failed: Boolean = false
  ) {
    val isActive: Boolean get() = track != null && !failed
  }

  private val context = context.applicationContext
  private val audioManager = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val attributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  private var recitation: Recitation? = null
  private var timings: VerseTimings? = null
  private var player: MediaPlayer? = null
  private var prepared = false
  /** a verse sought, until the player is there */
  private var sought: Int? = null

  private val handler = Handler(Looper.getMainLooper())
  private val tick = object : Runnable {
    override fun run() {
      val current = player
      val now = _state.value
      if (current != null && prepared && now.track != null) {
        val verse = sought ?: timings?.verseAt(now.track, current.currentPosition)
        _state.value = now.copy(verse = verse, isPlaying = current.isPlaying)
      }
      handler.postDelayed(this, TICK_MS)
    }
  }

  private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
  }
  private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(attributes)
    .setOnAudioFocusChangeListener(focusListener)
    .build()

  /** the recording to play: stops what is being played */
  fun setRecitation(recitation: Recitation, timings: VerseTimings) {
    stop()
    this.recitation = recitation
    this.timings = timings
  }

  /** recites from a verse (from the first after it that the recording has) */
  fun playVerse(verse: Int) {
    val timings = timings ?: return
    val target = timings.from(verse) ?: return
    val track = timings.trackOf(target)
    val current = player
    if (current != null && prepared && _state.value.track == track) {
      seek(current, target)
      start(current)
      _state.value = State(verse = target, track = track, isPlaying = true)
    } else {
      open(track, target)
    }
  }

  /** pauses or resumes; recites from [verse] when nothing is being played */
  fun toggle(verse: Int = 1) {
    val current = player
    val now = _state.value
    when {
      current == null || now.track == null || now.failed -> playVerse(now.verse ?: verse)
      !prepared -> Unit
      current.isPlaying -> pause()
      else -> {
        start(current)
        _state.value = now.copy(isPlaying = true)
      }
    }
  }

  fun pause() {
    val current = player ?: return
    if (prepared && current.isPlaying) {
      current.pause()
      _state.value = _state.value.copy(isPlaying = false)
    }
  }

  fun nextVerse() {
    val timings = timings ?: return
    val now = _state.value
    val track = now.track ?: return
    val next = now.verse?.plus(1) ?: timings.firstVerseOf(track) ?: return
    if (timings.from(next) != null) playVerse(next)
  }

  fun previousVerse() {
    val timings = timings ?: return
    val now = _state.value
    val track = now.track ?: return
    val previous = (now.verse ?: timings.firstVerseOf(track) ?: return) - 1
    timings.upTo(previous)?.let(::playVerse)
  }

  fun stop() {
    stopPlayer()
    audioManager.abandonAudioFocusRequest(focusRequest)
    _state.value = State()
  }

  fun release() = stop()

  private fun open(track: Int, verse: Int?, streamed: Boolean = false) {
    val recitation = recitation ?: return
    val source = recitation.tracks.getOrNull(track) ?: return
    val local = if (streamed) null else localFile(recitation, track)
    stopPlayer()
    _state.value = State(verse = verse, track = track, isPreparing = true)
    val next = MediaPlayer()
    player = next
    try {
      next.setAudioAttributes(attributes)
      next.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
      next.setOnPreparedListener {
        if (player !== it) return@setOnPreparedListener
        prepared = true
        if (verse != null) seek(it, verse)
        start(it)
        _state.value = State(verse = verse, track = track, isPlaying = true)
        handler.removeCallbacks(tick)
        handler.post(tick)
      }
      next.setOnSeekCompleteListener { if (player === it) sought = null }
      next.setOnCompletionListener {
        if (player !== it) return@setOnCompletionListener
        if (track + 1 < recitation.tracks.size) open(track + 1, null) else stop()
      }
      next.setOnErrorListener { failed, _, _ ->
        if (player === failed) {
          stopPlayer()
          if (local != null && source.url != null) {
            // (the copy kept here cannot be played: the track is streamed)
            open(track, verse, streamed = true)
          } else {
            _state.value = State(verse = verse, track = track, failed = true)
          }
        }
        true
      }
      val asset = source.asset
      when {
        local != null -> next.setDataSource(local.path)
        asset != null ->
          context.assets.openFd(asset).use { next.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        else -> next.setDataSource(source.url)
      }
      next.prepareAsync()
    } catch (e: Exception) {
      stopPlayer()
      if (local != null && source.url != null) {
        open(track, verse, streamed = true)
      } else {
        _state.value = State(verse = verse, track = track, failed = true)
      }
    }
  }

  private fun seek(player: MediaPlayer, verse: Int) {
    val start = timings?.startOf(verse) ?: return
    sought = verse
    player.seekTo(start.toLong(), MediaPlayer.SEEK_CLOSEST)
  }

  private fun start(player: MediaPlayer) {
    audioManager.requestAudioFocus(focusRequest)
    player.start()
  }

  private fun stopPlayer() {
    handler.removeCallbacks(tick)
    prepared = false
    sought = null
    player?.let {
      it.setOnPreparedListener(null)
      it.setOnCompletionListener(null)
      it.setOnErrorListener(null)
      it.setOnSeekCompleteListener(null)
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
    const val TICK_MS = 200L
  }
}
