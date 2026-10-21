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

  fun play(riwayah: Riwayah, sura: Int) {
    val audio = riwayah.audio ?: return
    if (!audio.hasSura(sura)) return
    val current = _state.value
    if (current.riwayahId == riwayah.id && current.sura == sura && !current.error) {
      player.play()
      return
    }
    _state.value = PlaybackState(riwayahId = riwayah.id, sura = sura, isBuffering = true)
    player.setMediaItem(MediaItem.fromUri(audio.urlForSura(sura)))
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
