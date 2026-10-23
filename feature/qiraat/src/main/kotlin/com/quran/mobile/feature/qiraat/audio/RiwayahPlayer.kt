package com.quran.mobile.feature.qiraat.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.quran.mobile.feature.qiraat.model.Riwayah
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
  val riwayahId: String? = null,
  val sura: Int = 0,
  /** the ayah playback started from, or 0 for the beginning of the sura */
  val ayah: Int = 0,
  /** whether only [ayah] is being played */
  val singleAyah: Boolean = false,
  val isPlaying: Boolean = false,
  val isBuffering: Boolean = false,
  val error: Boolean = false
)

/**
 * Streams a riwayah's recitation of a sura. Nothing is saved to disk.
 */
class RiwayahPlayer(context: Context) {
  private val player = ExoPlayer.Builder(context.applicationContext).build()
  private val _state = MutableStateFlow(PlaybackState())
  val state: StateFlow<PlaybackState> = _state.asStateFlow()

  init {
    player.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        _state.value = _state.value.copy(isPlaying = isPlaying)
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        _state.value = _state.value.copy(
          isBuffering = playbackState == Player.STATE_BUFFERING,
          isPlaying = player.isPlaying
        )
        if (playbackState == Player.STATE_ENDED) stop()
      }

      override fun onPlayerError(error: PlaybackException) {
        _state.value = _state.value.copy(isPlaying = false, isBuffering = false, error = true)
      }
    })
  }

  /**
   * plays the sura, from the start of [ayah] when [times] (its start and end in the recording, in
   * milliseconds) are known; with [singleAyah], stops at the end of that ayah.
   */
  fun play(
    riwayah: Riwayah,
    sura: Int,
    ayah: Int = 0,
    times: LongRange? = null,
    singleAyah: Boolean = false
  ) {
    val audio = riwayah.audio ?: return
    if (!audio.hasSura(sura)) return
    val current = _state.value
    val target = PlaybackState(riwayah.id, sura, ayah, singleAyah && times != null)
    if (current.copy(isPlaying = false, isBuffering = false) == target && !player.isPlaying &&
      player.mediaItemCount > 0 && player.playbackState != Player.STATE_ENDED
    ) {
      // resume after a pause
      player.play()
      return
    }
    _state.value = target.copy(isBuffering = true)
    val item = MediaItem.Builder().setUri(audio.urlForSura(sura))
    if (times != null && singleAyah) {
      item.setClippingConfiguration(
        MediaItem.ClippingConfiguration.Builder()
          .setStartPositionMs(times.first)
          .setEndPositionMs(times.last)
          .build()
      )
    }
    player.setMediaItem(item.build(), if (times != null && !singleAyah) times.first else 0L)
    player.prepare()
    player.play()
  }

  fun pause() = player.pause()

  fun stop() {
    player.stop()
    player.clearMediaItems()
    _state.value = PlaybackState()
  }

  fun release() = player.release()
}
