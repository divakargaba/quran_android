package com.quran.mobile.feature.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.model.Poem

/** the poems of the qira'at and of tajwid: each opens to its text, recitation and commentaries */
@Composable
fun PoemsList(
  poems: List<Poem>,
  arabicFont: FontFamily,
  onOpen: (Poem) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val arabic = isArabicLocale()
  LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
    item(key = "intro") {
      Text(
        stringResource(R.string.library_poems_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
      )
    }
    items(poems, key = { it.id }) { poem ->
      Column(
        Modifier
          .fillMaxWidth()
          .clickable { onOpen(poem) }
          .padding(horizontal = 16.dp, vertical = 12.dp)
      ) {
        Text(
          poem.titleArabic,
          fontFamily = arabicFont,
          fontSize = 22.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
        if (!arabic) {
          Text(
            poem.titleEnglish,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        Text(
          poem.author(arabic),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 2.dp)
        )
        Text(
          poem.subject(arabic) + " · " +
            pluralStringResource(R.plurals.library_verses, poem.verseCount, poem.verseCount),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
      HorizontalDivider()
    }
  }
}
