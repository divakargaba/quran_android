package com.quran.mobile.feature.library.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.DocumentState
import com.quran.mobile.feature.library.model.Document

/**
 * A document of the library: its title, author, language and size; it is opened when it is here,
 * and downloaded (with its progress, and a way to cancel) when it is not.
 */
@Composable
fun DocumentRow(
  document: Document,
  state: DocumentState,
  onOpen: () -> Unit,
  onDownload: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val arabic = isArabicLocale()
  var menu by remember { mutableStateOf(false) }
  val downloaded = state is DocumentState.Downloaded
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .clickable {
        when (state) {
          DocumentState.Downloaded -> onOpen()
          is DocumentState.Downloading -> Unit
          else -> onDownload()
        }
      }
      .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        document.title(arabic),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      val author = document.author(arabic)
      if (author.isNotEmpty()) {
        Text(
          author,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      val language = stringResource(
        if (document.language == "en") R.string.library_language_english else R.string.library_language_arabic
      )
      val details = buildList {
        add(language)
        if (document.pages > 0) add(stringResource(R.string.library_pages, document.pages))
        if (!document.isBundled) add(Formatter.formatShortFileSize(context, document.sizeBytes))
      }.joinToString(" · ")
      Text(
        details,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
      )
      when (state) {
        is DocumentState.Downloading -> {
          val progress = state.progress
          if (progress == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
          } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
          }
        }
        is DocumentState.Failed -> Text(
          stringResource(R.string.library_download_failed),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(top = 2.dp)
        )
        else -> Unit
      }
    }
    when (state) {
      DocumentState.Downloaded -> {
        if (document.isBundled) {
          Icon(
            QuranIcons.ChevronRight,
            contentDescription = stringResource(R.string.library_open),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
          )
        } else {
          IconButton(onClick = { menu = true }) {
            Icon(
              QuranIcons.CheckCircle,
              contentDescription = stringResource(R.string.library_downloaded),
              tint = MaterialTheme.colorScheme.primary
            )
          }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.library_open)) },
              onClick = {
                menu = false
                onOpen()
              }
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.library_delete)) },
              onClick = {
                menu = false
                onDelete()
              }
            )
          }
        }
      }
      is DocumentState.Downloading -> IconButton(onClick = onCancel) {
        if (state.progress == null) {
          CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Icon(QuranIcons.Close, contentDescription = stringResource(R.string.library_cancel))
        }
      }
      else -> IconButton(onClick = onDownload) {
        Icon(
          QuranIcons.ArrowCircleDown,
          contentDescription = stringResource(R.string.library_download),
          tint = if (downloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
