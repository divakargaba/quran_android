package com.quran.mobile.feature.qiraat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.qiraat.R
import com.quran.mobile.feature.qiraat.audio.PlaybackState
import com.quran.mobile.feature.qiraat.model.Riwayah
import com.quran.mobile.common.ui.core.R as coreR

@Composable
fun AudioBar(
  riwayah: Riwayah,
  sura: Int?,
  playback: PlaybackState,
  onPlay: (Int) -> Unit,
  onPause: () -> Unit,
  onStop: () -> Unit
) {
  val arabic = isArabicLocale()
  val suraNames = stringArrayResource(coreR.array.sura_names)
  val audio = riwayah.audio
  Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .navigationBarsPadding()
        .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
      val isActive = playback.riwayahId != null
      val isCurrent = playback.riwayahId == riwayah.id && playback.sura == sura
      val canPlay = audio != null && sura != null && audio.hasSura(sura)

      when {
        isCurrent && playback.isBuffering ->
          CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp))
        isCurrent && playback.isPlaying ->
          IconButton(onClick = onPause) {
            Icon(QuranIcons.Pause, contentDescription = stringResource(R.string.qiraat_pause))
          }
        else ->
          IconButton(onClick = { sura?.let(onPlay) }, enabled = canPlay) {
            Icon(QuranIcons.PlayArrow, contentDescription = stringResource(R.string.qiraat_play))
          }
      }

      Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
        val title = when {
          audio == null -> stringResource(R.string.qiraat_audio_unavailable)
          sura == null -> ""
          !audio.hasSura(sura) ->
            stringResource(R.string.qiraat_audio_sura_unavailable, suraNames[sura - 1])
          isCurrent && playback.error -> stringResource(R.string.qiraat_audio_error)
          else -> suraNames[sura - 1]
        }
        Text(
          title,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (audio != null) {
          Text(
            if (arabic) audio.reciterAr else audio.reciterEn,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      if (isActive) {
        IconButton(onClick = onStop) {
          Icon(QuranIcons.Stop, contentDescription = stringResource(R.string.qiraat_stop))
        }
      }
    }
  }
}
