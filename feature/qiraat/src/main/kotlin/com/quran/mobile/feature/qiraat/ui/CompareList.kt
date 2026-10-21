package com.quran.mobile.feature.qiraat.ui

import android.graphics.RectF
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.qiraat.R
import com.quran.mobile.feature.qiraat.data.DownloadState
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.model.Riwayah

/**
 * One ayah, cropped out of every riwayah's mushaf, one under the other.
 */
@Composable
fun CompareList(
  sura: Int,
  ayah: Int,
  repository: RiwayahRepository,
  downloadStates: Map<String, DownloadState>,
  onOpenInReader: (Riwayah) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val arabic = isArabicLocale()
  val missing = Qiraat.all.filter { downloadStates[it.id] !is DownloadState.Downloaded }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    if (missing.isNotEmpty()) {
      item(key = "download_all") {
        val idle = missing.filter { downloadStates[it.id] !is DownloadState.Downloading }
        if (idle.isNotEmpty()) {
          OutlinedButton(
            onClick = { idle.forEach(repository::download) },
            modifier = Modifier.fillMaxWidth()
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
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              stringResource(
                R.string.qiraat_riwayah_label,
                riwayah.displayName(arabic),
                qari.displayName(arabic)
              ),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary
            )
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
        }
      }
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
    rects = repository.locations(riwayah).rectsFor(sura, ayah)
  }
  val renderer = repository.renderer(riwayah) ?: return
  val ayahRects = rects ?: return
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp)
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
