package com.quran.mobile.feature.qiraat.ui

import android.graphics.RectF
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.qiraat.R
import com.quran.mobile.feature.qiraat.audio.RiwayahPlayer
import com.quran.mobile.feature.qiraat.data.DownloadState
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.model.Riwayah
import com.quran.mobile.feature.qiraat.model.Timings
import kotlinx.coroutines.launch

/**
 * One ayah, cropped out of every riwayah's mushaf, one under the other.
 */
@Composable
fun CompareList(
  sura: Int,
  ayah: Int,
  repository: RiwayahRepository,
  downloadStates: Map<String, DownloadState>,
  player: RiwayahPlayer,
  onOpenInReader: (Riwayah) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val context = LocalContext.current
  val arabic = isArabicLocale()
  val missing = Qiraat.all.filter { downloadStates[it.id] !is DownloadState.Downloaded }
  val playback by player.state.collectAsState()
  val scope = rememberCoroutineScope()

  LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
    if (missing.isNotEmpty()) {
      item(key = "download_all") {
        val idle = missing.filter { downloadStates[it.id] !is DownloadState.Downloading }
        if (idle.isNotEmpty()) {
          OutlinedButton(
            onClick = { idle.forEach(repository::download) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
          ) {
            val bytes = idle.sumOf { it.pdfSizeBytes }
            Text(
              stringResource(
                R.string.qiraat_download_all,
                Formatter.formatShortFileSize(context, bytes)
              )
            )
          }
        }
      }
    }

    Qiraat.qurra.forEach { qari ->
      items(qari.riwayat, key = { it.id }) { riwayah ->
        val isPlaying = playback.riwayahId == riwayah.id && playback.sura == sura &&
          playback.ayah == ayah && playback.singleAyah && (playback.isPlaying || playback.isBuffering)
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp)
          ) {
            Text(
              stringResource(
                R.string.qiraat_riwayah_label,
                riwayah.displayName(arabic),
                qari.displayName(arabic)
              ),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f)
            )
            AyahPlayButton(
              riwayah = riwayah,
              sura = sura,
              isPlaying = isPlaying,
              onPlay = {
                scope.launch {
                  val times = repository.timings(riwayah)?.timesFor(sura, ayah)
                  if (times != null) player.play(riwayah, sura, ayah, times, singleAyah = true)
                }
              },
              onStop = player::stop
            )
          }
          Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            when (val state = downloadStates[riwayah.id]) {
              DownloadState.Downloaded -> ComparedAyah(
                riwayah = riwayah,
                sura = sura,
                ayah = ayah,
                repository = repository,
                onClick = { onOpenInReader(riwayah) }
              )
              else -> DownloadRow(riwayah, state, onDownload = { repository.download(riwayah) })
            }
          }
          HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
        }
      }
    }
  }
}

/** plays just this ayah in the riwayah, when its recording has timings for it */
@Composable
private fun AyahPlayButton(
  riwayah: Riwayah,
  sura: Int,
  isPlaying: Boolean,
  onPlay: () -> Unit,
  onStop: () -> Unit
) {
  val audio = riwayah.audio
  val available = audio != null && audio.timings != Timings.NONE && audio.hasSura(sura)
  if (isPlaying) {
    IconButton(onClick = onStop) {
      Icon(QuranIcons.Stop, contentDescription = stringResource(R.string.qiraat_stop))
    }
  } else {
    IconButton(onClick = onPlay, enabled = available) {
      Icon(QuranIcons.PlayArrow, contentDescription = stringResource(R.string.qiraat_play_ayah))
    }
  }
}

@Composable
private fun ComparedAyah(
  riwayah: Riwayah,
  sura: Int,
  ayah: Int,
  repository: RiwayahRepository,
  onClick: () -> Unit
) {
  var rects by remember(riwayah.id, sura, ayah) { mutableStateOf<Map<Int, List<RectF>>?>(null) }
  LaunchedEffect(riwayah.id, sura, ayah) {
    rects = repository.locations(riwayah)?.rectsFor(sura, ayah).orEmpty()
  }
  val renderer = repository.renderer(riwayah) ?: return
  val ayahRects = rects ?: return
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
  ) {
    ayahRects.forEach { (page, pageRects) ->
      AyahCrop(renderer = renderer, page = page, rects = pageRects)
    }
  }
}

@Composable
fun DownloadRow(riwayah: Riwayah, state: DownloadState?, onDownload: () -> Unit) {
  val context = LocalContext.current
  Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
    when (state) {
      is DownloadState.Downloading -> {
        Text(
          stringResource(R.string.qiraat_downloading, (state.progress * 100).toInt()),
          style = MaterialTheme.typography.bodySmall
        )
        LinearProgressIndicator(
          progress = { state.progress },
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
      }
      else -> Row(verticalAlignment = Alignment.CenterVertically) {
        val message = if (state is DownloadState.Failed) {
          stringResource(R.string.qiraat_download_failed, state.message.orEmpty())
        } else {
          stringResource(R.string.qiraat_not_downloaded)
        }
        Text(
          message,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.weight(1f)
        )
        Button(onClick = onDownload) {
          Text(
            if (state is DownloadState.Failed) {
              stringResource(R.string.qiraat_retry)
            } else {
              stringResource(
                R.string.qiraat_download,
                Formatter.formatShortFileSize(context, riwayah.pdfSizeBytes)
              )
            }
          )
        }
      }
    }
  }
}
