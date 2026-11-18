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

  /**
   * How the recitation is repeated, and its speed. A verse is recited [verseRepeat] times more
   * after its first ([INFINITE]: until stopped). With a set of verses ([first] to [last]), only
   * these are recited, the set [rangeRepeat] times more ([INFINITE]: until stopped).
   */
  data class Settings(
    val verseRepeat: Int = 0,
    val first: Int? = null,
    val last: Int? = null,
    val rangeRepeat: Int = 0,
    val speed: Float = 1f
  ) {
    val hasRange: Boolean get() = first != null && last != null
    val repeats: Boolean get() = verseRepeat != 0 || hasRange
    fun withoutRange(): Settings = copy(first = null, last = null, rangeRepeat = 0)
  }

  private val context = context.applicationContext
  private val audioManager = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val attributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  private val _settings = MutableStateFlow(Settings())
  val settings: StateFlow<Settings> = _settings.asStateFlow()

  private var recitation: Recitation? = null
  private var timings: VerseTimings? = null
  private var player: MediaPlayer? = null
  private var prepared = false
  /** a verse sought, until the player is there */
  private var sought: Int? = null
  /** the verse being recited, and how many times it and the set of verses have been repeated */
  private var current: Int? = null
  private var verseRepeated = 0
  private var rangeRepeated = 0
  /** the speed the player has been set to */
  private var appliedSpeed = 1f
  /**
   * whether the player has been seen in the verse sought (it may tell where it was for a moment
   * after it is sent elsewhere, which is not the verse's end), and how long this has been awaited
   */
  private var arrived = true
  private var awaited = 0

  private val handler = Handler(Looper.getMainLooper())
  private val tick = object : Runnable {
    override fun run() {
      val playing = player
      val now = _state.value
      var delay = TICK_MS
      if (playing != null && prepared && now.track != null) {
        if (sought == null) {
          val position = playing.currentPosition
          val verse = current
          val end = verse?.let { endOf(it, now.track) }
          if (!arrived) {
            arrived = end == null || position < end || ++awaited >= AWAITED_TICKS
          }
          when {
            !arrived -> Unit
            // the verse has ended: it is repeated, or the set of verses is, or the next follows
            verse != null && end != null && position >= end -> {
              if (ended(verse)) return
              current = timings?.verseAt(now.track, position)
              verseRepeated = 0
            }
            verse == null -> current = timings?.verseAt(now.track, position)
          }
          // (where a verse is to be repeated, its end is met closely)
          val next = current?.let { endOf(it, now.track) }
          if (next != null && _settings.value.repeats && playing.isPlaying) {
            delay = ((next - position) / _settings.value.speed).toLong().coerceIn(MIN_TICK_MS, TICK_MS)
          }
        }
        if (player === playing) _state.value = _state.value.copy(verse = sought ?: current, isPlaying = playing.isPlaying)
      }
      handler.postDelayed(this, delay)
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

  /**
   * recites from a verse (from the first after it that the recording has); a verse out of the set
   * of verses being repeated ends the set
   */
  fun playVerse(verse: Int) {
    val now = _settings.value
    if (now.hasRange && verse !in now.first!!..now.last!!) _settings.value = now.withoutRange()
    rangeRepeated = 0
    recite(verse)
  }

  /** how the recitation is repeated, and its speed: from now on */
  fun set(settings: Settings) {
    val before = _settings.value
    _settings.value = settings
    if (settings.copy(speed = before.speed) != before) {
      verseRepeated = 0
      rangeRepeated = 0
    }
    val playing = player
    if (playing == null || !prepared || !playing.isPlaying) return
    if (settings.speed != before.speed) applySpeed(playing)
    // (what is being recited out of the set of verses now asked for gives way to the set)
    val verse = _state.value.verse
    val first = settings.first
    val last = settings.last
    if (first != null && last != null && verse != null && verse !in first..last) recite(first)
  }

  private fun recite(verse: Int) {
    val timings = timings ?: return
    val target = timings.from(verse) ?: return
    val track = timings.trackOf(target)
    val playing = player
    verseRepeated = 0
    if (playing != null && prepared && _state.value.track == track) {
      seek(playing, target)
      start(playing)
      _state.value = State(verse = target, track = track, isPlaying = true)
      handler.removeCallbacks(tick)
      handler.post(tick)
    } else {
      open(track, target)
    }
  }

  /** when the verse being recited ends, in this track, if a verse follows it there */
  private fun endOf(verse: Int, track: Int): Int? {
    val timings = timings ?: return null
    val next = timings.from(verse + 1) ?: return null
    return if (timings.trackOf(next) == track) timings.startOf(next) else null
  }

  /**
   * A verse has been recited to its end: recites it again, or the set of verses again, or stops
   * at the end of the set, as is asked. False when the recitation is just to go on.
   */
  private fun ended(verse: Int): Boolean {
    val asked = _settings.value
    if (asked.verseRepeat == INFINITE || verseRepeated < asked.verseRepeat) {
      val times = verseRepeated + 1
      recite(verse)
      verseRepeated = times
      return true
    }
    val first = asked.first
    val last = asked.last
    if (first != null && last != null && (timings?.from(verse + 1)?.let { it > last } != false)) {
      if (asked.rangeRepeat == INFINITE || rangeRepeated < asked.rangeRepeat) {
        rangeRepeated++
        recite(first)
      } else {
        stop()
      }
      return true
    }
    return false
  }

  /** pauses or resumes; recites from [verse] when nothing is being played */
  fun toggle(verse: Int = 1) {
    val playing = player
    val now = _state.value
    // (the set of verses being repeated is recited from its first, when the recitation is not in it)
    val asked = _settings.value
    val from = (now.verse ?: verse).let { if (asked.hasRange && it !in asked.first!!..asked.last!!) asked.first!! else it }
    when {
      playing == null || now.track == null || now.failed -> playVerse(from)
      !prepared -> Unit
      playing.isPlaying -> pause()
      from != (now.verse ?: verse) -> playVerse(from)
      else -> {
        start(playing)
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
    // (within the set of verses being repeated: from its last to its first)
    val asked = _settings.value
    val first = asked.first
    val last = asked.last
    if (first != null && last != null && (timings.from(next)?.let { it > last } != false)) {
      playVerse(first)
    } else if (timings.from(next) != null) {
      playVerse(next)
    }
  }

  fun previousVerse() {
    val timings = timings ?: return
    val now = _state.value
    val track = now.track ?: return
    val previous = (now.verse ?: timings.firstVerseOf(track) ?: return) - 1
    val first = _settings.value.first
    if (first != null && _settings.value.hasRange && previous < first) {
      playVerse(first)
    } else {
      timings.upTo(previous)?.let(::playVerse)
    }
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
        current = verse
        if (verse != null) seek(it, verse)
        start(it)
        _state.value = State(verse = verse, track = track, isPlaying = true)
        handler.removeCallbacks(tick)
        handler.post(tick)
      }
      next.setOnSeekCompleteListener { if (player === it) sought = null }
      next.setOnCompletionListener {
        if (player !== it) return@setOnCompletionListener
        // (the track's last verse has ended with it)
        val verse = current
        if (verse != null && ended(verse)) return@setOnCompletionListener
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
    current = verse
    arrived = false
    awaited = 0
    player.seekTo(start.toLong(), MediaPlayer.SEEK_CLOSEST)
  }

  private fun start(player: MediaPlayer) {
    audioManager.requestAudioFocus(focusRequest)
    player.start()
    if (_settings.value.speed != appliedSpeed) applySpeed(player)
  }

  /** (the speed is set while playing: setting it starts a player) */
  private fun applySpeed(player: MediaPlayer) {
    try {
      val speed = _settings.value.speed
      player.playbackParams = player.playbackParams.setSpeed(speed)
      appliedSpeed = speed
    } catch (e: Exception) {
      // the speed cannot be changed on this device: the recitation goes on as it is
    }
  }

  private fun stopPlayer() {
    handler.removeCallbacks(tick)
    prepared = false
    sought = null
    current = null
    verseRepeated = 0
    appliedSpeed = 1f
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

  companion object {
    /** of a number of repeats: until stopped */
    const val INFINITE = -1
    private const val TICK_MS = 200L
    private const val MIN_TICK_MS = 15L
    private const val AWAITED_TICKS = 5
  }
}
