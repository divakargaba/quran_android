package com.quran.mobile.feature.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.model.UsoolGroup

/**
 * The principles (usul) of the qira'at: of them all, then of each qira'ah. Each opens to its
 * principles as text (for a qira'ah) and the books on them.
 */
@Composable
fun UsoolList(
  groups: List<UsoolGroup>,
  arabicFont: FontFamily,
  onOpen: (Int) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val arabic = isArabicLocale()
  LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
    itemsIndexed(groups) { index, group ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onOpen(index) }
          .padding(horizontal = 16.dp, vertical = 12.dp)
      ) {
        Column(Modifier.weight(1f)) {
          Text(
            group.titleArabic,
            fontFamily = arabicFont,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (!arabic) {
            Text(
              group.titleEnglish,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
          val books = pluralStringResource(R.plurals.library_books_count, group.documents.size, group.documents.size)
          Text(
            if (group.id != null) stringResource(R.string.library_usool_rules) + " · " + books else books,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp)
          )
        }
        Icon(
          QuranIcons.ChevronRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      HorizontalDivider()
    }
  }
}
