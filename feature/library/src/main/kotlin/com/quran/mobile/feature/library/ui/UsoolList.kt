package com.quran.mobile.feature.library.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.DocumentState
import com.quran.mobile.feature.library.model.Document
import com.quran.mobile.feature.library.model.LibraryCatalog

/** books on the principles (usul) of the qira'at: of them all, then of each qira'ah */
@Composable
fun UsoolList(
  catalog: LibraryCatalog,
  states: Map<String, DocumentState>,
  onOpen: (Document) -> Unit,
  onDownload: (Document) -> Unit,
  onCancel: (Document) -> Unit,
  onDelete: (Document) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val arabic = isArabicLocale()
  LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
    item(key = "intro") {
      Text(
        stringResource(R.string.library_usool_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
      )
    }
    catalog.usool.forEachIndexed { index, group ->
      item(key = "group$index") {
        Text(
          group.title(arabic),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
      }
      items(catalog.documentsOf(group.documents), key = { "$index:${it.id}" }) { document ->
        DocumentRow(
          document = document,
          state = states[document.id] ?: DocumentState.NotDownloaded,
          onOpen = { onOpen(document) },
          onDownload = { onDownload(document) },
          onCancel = { onCancel(document) },
          onDelete = { onDelete(document) }
        )
      }
      item(key = "divider$index") { HorizontalDivider() }
    }
  }
}
