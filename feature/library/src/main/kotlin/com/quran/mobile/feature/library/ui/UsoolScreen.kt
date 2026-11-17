package com.quran.mobile.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.model.Document
import com.quran.mobile.feature.library.model.UsoolText

/** a row of the list of a qira'ah's principles: a chapter's heading, a rule, or the chapter's source */
sealed interface UsoolRow {
  data class Heading(val chapter: Int, val title: String) : UsoolRow
  data class Rule(val text: String) : UsoolRow
  data class Source(val document: Document, val page: Int) : UsoolRow
}

fun UsoolText.rows(document: (String) -> Document?): List<UsoolRow> = buildList {
  chapters.forEachIndexed { index, chapter ->
    add(UsoolRow.Heading(index, chapter.title))
    chapter.paragraphs.forEach { add(UsoolRow.Rule(it)) }
    chapter.sourceDocument?.let(document)?.let { add(UsoolRow.Source(it, chapter.sourcePage)) }
  }
}

/** the index, in [rows], of a chapter's heading */
fun rowOfUsoolChapter(rows: List<UsoolRow>, chapter: Int): Int =
  rows.indexOfFirst { it is UsoolRow.Heading && it.chapter == chapter }.coerceAtLeast(0)

/**
 * The principles of a qira'ah, chapter by chapter: each rule a paragraph (what it is of in bold),
 * its Arabic in the Arabic font; under a chapter, the book it is drawn from, opened at its page.
 */
@Composable
fun UsoolChapters(
  rows: List<UsoolRow>,
  listState: LazyListState,
  arabicFont: FontFamily,
  onOpenBook: (Document, Int) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val arabic = isArabicLocale()
  LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
    items(rows) { row ->
      when (row) {
        is UsoolRow.Heading -> Text(
          row.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
        )
        is UsoolRow.Rule -> Text(
          text = remember(row.text) { rule(row.text, arabicFont) },
          fontSize = 17.sp,
          lineHeight = 27.sp,
          style = TextStyle(textDirection = TextDirection.Content),
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)
        )
        is UsoolRow.Source -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenBook(row.document, row.page) }
              .padding(horizontal = 16.dp, vertical = 8.dp)
          ) {
            Text(
              stringResource(R.string.library_usool_source, row.document.title(arabic), row.page + 1),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            Icon(
              QuranIcons.OpenInNew,
              contentDescription = stringResource(R.string.library_open),
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp)
            )
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
      }
    }
  }
}

private val LABEL = Regex("^[^:\\n]{2,70}:(?= )")

/** a rule's text: what it is of (before its first colon) in bold, its Arabic in the Arabic font */
private fun rule(text: String, arabicFont: FontFamily): AnnotatedString = buildAnnotatedString {
  append(withArabic(text, arabicFont))
  LABEL.find(text)?.let { addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1) }
}
