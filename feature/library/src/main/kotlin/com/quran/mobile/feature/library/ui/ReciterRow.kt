package com.quran.mobile.feature.library.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.RecitationState
import com.quran.mobile.feature.library.model.Recitation

/**
 * A reciter of a poem: chosen when tapped. Their recording is downloaded to be played without a
 * connection (with its size, its progress and a way to stop), and removed again, from here.
 */
@Composable
fun ReciterRow(
  recitation: Recitation,
  selected: Boolean,
  state: RecitationState,
  onSelect: () -> Unit,
  onDownload: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val arabic = isArabicLocale()
  val size = Formatter.formatShortFileSize(context, recitation.offlineBytes)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 6.dp)
  ) {
    RadioButton(selected = selected, onClick = null)
    Column(Modifier.weight(1f).padding(start = 12.dp)) {
      Text(recitation.reciter(arabic), style = MaterialTheme.typography.bodyLarge)
      val status = when {
        recitation.isBundled -> stringResource(R.string.library_offline_bundled)
        state is RecitationState.Downloaded -> stringResource(R.string.library_offline_here, size)
        state is RecitationState.Downloading ->
          stringResource(R.string.library_downloading) + " ${(state.progress * 100).toInt()}%"
        state is RecitationState.Failed -> stringResource(R.string.library_download_failed)
        recitation.canDownload -> stringResource(R.string.library_offline_download, size)
        else -> null
      }
      if (status != null) {
        Text(
          status,
          style = MaterialTheme.typography.labelSmall,
          color = if (state is RecitationState.Failed) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          }
        )
      }
      if (state is RecitationState.Downloading) {
        LinearProgressIndicator(
          progress = { state.progress },
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
      }
    }
    when {
      recitation.isBundled || !recitation.canDownload -> Unit
      state is RecitationState.Downloaded -> IconButton(onClick = onDelete) {
        Icon(
          QuranIcons.Close,
          contentDescription = stringResource(R.string.library_offline_delete),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp)
        )
      }
      state is RecitationState.Downloading -> IconButton(onClick = onCancel) {
        Icon(QuranIcons.Stop, contentDescription = stringResource(R.string.library_cancel))
      }
      else -> IconButton(onClick = onDownload) {
        Icon(
          QuranIcons.ArrowCircleDown,
          contentDescription = stringResource(R.string.library_download),
          tint = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}
