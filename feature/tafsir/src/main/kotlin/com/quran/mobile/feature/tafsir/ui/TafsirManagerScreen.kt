package com.quran.mobile.feature.tafsir.ui

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.tafsir.R
import com.quran.mobile.feature.tafsir.data.TafsirState
import com.quran.mobile.feature.tafsir.model.Tafaseer
import com.quran.mobile.feature.tafsir.model.Tafsir

/**
 * The tafaseer that can be downloaded, in Arabic and in English, as the translation manager.
 */
@Composable
fun TafsirManagerScreen(
  states: Map<String, TafsirState>,
  onDownload: (Tafsir) -> Unit,
  onCancel: (Tafsir) -> Unit,
  onDelete: (Tafsir) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val arabic = isArabicLocale()
  LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
    listOf(true, false).forEach { inArabic ->
      item(key = "header_$inArabic") {
        Text(
          stringResource(if (inArabic) R.string.tafsir_arabic else R.string.tafsir_english),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
      }
      items(Tafaseer.all.filter { it.isArabic == inArabic }, key = { it.id }) { tafsir ->
        TafsirRow(
          tafsir = tafsir,
          state = states[tafsir.id] ?: TafsirState.NotDownloaded,
          arabic = arabic,
          onDownload = { onDownload(tafsir) },
          onCancel = { onCancel(tafsir) },
          onDelete = { onDelete(tafsir) }
        )
        HorizontalDivider()
      }
    }
    item(key = "source") {
      Text(
        stringResource(R.string.tafsir_source),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
      )
    }
  }
}

@Composable
private fun TafsirRow(
  tafsir: Tafsir,
  state: TafsirState,
  arabic: Boolean,
  onDownload: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(tafsir.name(arabic), style = MaterialTheme.typography.bodyLarge)
        Text(
          tafsir.author(arabic) + " · " + stringResource(R.string.tafsir_size, tafsir.downloadMb),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      when (state) {
        TafsirState.Downloaded -> OutlinedButton(onClick = onDelete) {
          Text(stringResource(R.string.tafsir_delete))
        }
        is TafsirState.Downloading -> TextButton(onClick = onCancel) {
          Text(stringResource(R.string.tafsir_cancel))
        }
        is TafsirState.Failed -> Button(onClick = onDownload) {
          Text(stringResource(R.string.tafsir_retry))
        }
        TafsirState.NotDownloaded -> Button(onClick = onDownload) {
          Text(stringResource(R.string.tafsir_download))
        }
      }
    }
    when (state) {
      is TafsirState.Downloading -> {
        Text(
          stringResource(R.string.tafsir_downloading, (state.progress * 100).toInt()),
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(top = 4.dp)
        )
        LinearProgressIndicator(
          progress = { state.progress },
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
      }
      is TafsirState.Failed -> Text(
        stringResource(R.string.tafsir_failed),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp)
      )
      else -> Unit
    }
  }
}
