package com.quran.mobile.feature.library.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.audio.PoemPlayer
import com.quran.mobile.feature.library.model.Document
import com.quran.mobile.feature.library.model.InlineIndex
import com.quran.mobile.feature.library.model.Poem
import com.quran.mobile.feature.library.model.PoemText
import com.quran.mobile.feature.library.model.Recitation
import java.util.Locale

/** a row of the poem's list: a chapter's heading, a verse (numbered from 1), or what is said of verses */
sealed interface PoemRow {
  data class Heading(val chapter: Int, val title: String, val first: Int, val last: Int) : PoemRow
  data class Verse(val number: Int, val verse: PoemText.Verse) : PoemRow
  data class Inline(val book: InlineBook, val entry: InlineIndex.Entry) : PoemRow
}

/** a book shown under the verses: its pages, and where on them it says what of each verse */
class InlineBook(val document: Document, val pages: PdfPages, val index: InlineIndex)

/** the poem's rows, with what each of [books] says of a verse (or run of verses) under it */
fun PoemText.rows(books: List<InlineBook> = emptyList()): List<PoemRow> {
  val after = HashMap<Int, MutableList<PoemRow.Inline>>()
  books.forEach { book ->
    book.index.entries.forEach { after.getOrPut(it.last) { ArrayList() }.add(PoemRow.Inline(book, it)) }
  }
  return buildList {
    chapters.forEachIndexed { index, chapter ->
      if (chapter.title.isNotEmpty()) {
        add(PoemRow.Heading(index, chapter.title, chapter.first, chapter.first + chapter.verses.size - 1))
      }
      chapter.verses.forEachIndexed { i, verse ->
        add(PoemRow.Verse(chapter.first + i, verse))
        after[chapter.first + i]?.let(::addAll)
      }
    }
  }
}

/** the index, in [rows], of a chapter's heading (or of its first verse) */
fun PoemText.rowOfChapter(rows: List<PoemRow>, chapter: Int): Int {
  val first = chapters[chapter].first
  return rows.indexOfFirst {
    it is PoemRow.Heading && it.chapter == chapter || it is PoemRow.Verse && it.number == first
  }.coerceAtLeast(0)
}

/** the index, in [rows], of a verse */
fun rowOfVerse(rows: List<PoemRow>, verse: Int): Int =
  rows.indexOfFirst { it is PoemRow.Verse && it.number == verse }

/**
 * The text of a poem, chapter by chapter, each verse in its two halves, with the translations and
 * explanations chosen under the verses they are of. The verse being recited is marked; a verse
 * is recited from when tapped, and a chapter from its heading.
 */
@Composable
fun PoemVerses(
  poem: Poem,
  rows: List<PoemRow>,
  listState: LazyListState,
  recitedVerse: Int?,
  arabicFont: FontFamily,
  onPlayVerse: ((Int) -> Unit)?,
  onOpenBook: (Document, Int) -> Unit,
  /** to choose a translation to show under the verses, while there are some and none is shown */
  onChooseInline: (() -> Unit)?,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val arabic = isArabicLocale()
  // the explanations opened (by book and verse)
  val expanded = remember { mutableStateMapOf<String, Boolean>() }
  // the poem reads right to left in any language of the app
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
      item(key = "about") {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            poem.titleArabic,
            fontFamily = arabicFont,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            poem.author(arabic),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
          )
          val source = poem.source(arabic)
          if (source.isNotEmpty()) {
            Text(
              source,
              style = MaterialTheme.typography.labelSmall,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 4.dp)
            )
          }
          if (onChooseInline != null) {
            Text(
              stringResource(R.string.library_inline_prompt),
              style = MaterialTheme.typography.labelLarge,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(top = 4.dp).clickable(onClick = onChooseInline).padding(8.dp)
            )
          }
        }
      }
      itemsIndexed(rows) { _, row ->
        when (row) {
          is PoemRow.Heading -> ChapterHeading(
            title = row.title,
            playing = recitedVerse != null && recitedVerse in row.first..row.last,
            arabicFont = arabicFont,
            onPlay = onPlayVerse?.let { play -> { play(row.first) } }
          )
          is PoemRow.Verse -> VerseRow(row, row.number == recitedVerse, arabicFont, onPlayVerse)
          is PoemRow.Inline -> {
            val key = row.book.document.id + ":" + row.entry.first
            InlineRow(
              row = row,
              expanded = expanded[key] == true,
              onExpand = { expanded[key] = expanded[key] != true },
              onOpenBook = onOpenBook
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ChapterHeading(
  title: String,
  playing: Boolean,
  arabicFont: FontFamily,
  onPlay: (() -> Unit)?
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 12.dp)
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .padding(start = 16.dp, end = 4.dp)
  ) {
    Text(
      title,
      fontFamily = arabicFont,
      fontSize = 19.sp,
      lineHeight = 34.sp,
      color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f).padding(vertical = 8.dp)
    )
    if (onPlay != null) {
      IconButton(onClick = onPlay) {
        Icon(
          QuranIcons.PlayArrow,
          contentDescription = stringResource(R.string.library_play_chapter),
          tint = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

@Composable
private fun VerseRow(
  row: PoemRow.Verse,
  recited: Boolean,
  arabicFont: FontFamily,
  onPlayVerse: ((Int) -> Unit)?
) {
  val number = remember(row.number) { String.format(Locale.forLanguageTag("ar"), "%d", row.number) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (recited) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) else Modifier)
      .then(if (onPlayVerse != null) Modifier.clickable { onPlayVerse(row.number) } else Modifier)
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Text(
      number,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
      textAlign = TextAlign.Center,
      modifier = Modifier.width(34.dp).padding(top = 10.dp)
    )
    // the first half from the start of the line, the second to its end
    Column(Modifier.weight(1f)) {
      Text(
        row.verse.firstHalf,
        fontFamily = arabicFont,
        fontSize = 20.sp,
        lineHeight = 38.sp,
        textAlign = TextAlign.Start,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(end = 28.dp)
      )
      Text(
        row.verse.secondHalf,
        fontFamily = arabicFont,
        fontSize = 20.sp,
        lineHeight = 38.sp,
        textAlign = TextAlign.End,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp)
      )
    }
  }
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/**
 * What a book says of the verse (or verses) above it, as its pages have it: the translation, and
 * the rest (vocabulary, commentary) when opened. The book's name opens it at that page.
 */
@Composable
private fun InlineRow(
  row: PoemRow.Inline,
  expanded: Boolean,
  onExpand: () -> Unit,
  onOpenBook: (Document, Int) -> Unit
) {
  val entry = row.entry
  val book = row.book
  val arabicBook = book.document.language == "ar"
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val page = (entry.translation.firstOrNull() ?: entry.explanation.firstOrNull())?.page ?: 0
  CompositionLocalProvider(
    LocalLayoutDirection provides if (arabicBook) LayoutDirection.Rtl else LayoutDirection.Ltr
  ) {
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onOpenBook(book.document, page) }
          .padding(horizontal = 12.dp, vertical = 4.dp)
      ) {
        Text(
          book.document.title(arabicBook) + if (entry.first != entry.last) {
            "  ·  " + stringResource(R.string.library_verses_range, entry.first, entry.last)
          } else {
            ""
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
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
      entry.translation.forEach { RegionImage(book, it, dark) }
      if (entry.explanation.isNotEmpty()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Text(
            stringResource(R.string.library_explanation),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
          )
          Icon(
            QuranIcons.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f)
          )
        }
        if (expanded) entry.explanation.forEach { RegionImage(book, it, dark) }
      }
    }
  }
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/** a part of a page of a book, as wide as the screen (light on dark in the dark theme) */
@Composable
private fun RegionImage(book: InlineBook, region: InlineIndex.Region, dark: Boolean) {
  val width = book.index.width
  val height = (region.bottom - region.top).coerceAtLeast(1)
  BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
    val pixels = constraints.maxWidth
    val bitmap by produceState<Bitmap?>(null, book, region, pixels) {
      value = book.pages.renderRegion(region.page, region.left, region.top, region.left + width, region.bottom, pixels)
    }
    Box(Modifier.fillMaxWidth().aspectRatio(width.toFloat() / height)) {
      bitmap?.let {
        Image(
          bitmap = it.asImageBitmap(),
          contentDescription = null,
          contentScale = ContentScale.FillBounds,
          colorFilter = if (dark) INVERTED else null,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}

private val INVERTED = ColorFilter.colorMatrix(
  ColorMatrix(
    floatArrayOf(
      -1f, 0f, 0f, 0f, 255f,
      0f, -1f, 0f, 0f, 255f,
      0f, 0f, -1f, 0f, 255f,
      0f, 0f, 0f, 1f, 0f
    )
  )
)

/**
 * The bar of the poem's recitation: its reciter (tapped to choose another, when there are more),
 * the verse being recited, and previous verse, play or pause, next verse, and stop.
 */
@Composable
fun PoemPlayerBar(
  recitation: Recitation,
  canChooseReciter: Boolean,
  state: PoemPlayer.State,
  onChooseReciter: () -> Unit,
  onToggle: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onStop: () -> Unit,
  modifier: Modifier = Modifier
) {
  val arabic = isArabicLocale()
  Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
      Column(
        Modifier
          .weight(1f)
          .then(if (canChooseReciter) Modifier.clickable(onClick = onChooseReciter) else Modifier)
          .padding(horizontal = 8.dp, vertical = 6.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            recitation.reciter(arabic),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          if (canChooseReciter) {
            Icon(
              QuranIcons.ExpandMore,
              contentDescription = stringResource(R.string.library_choose_reciter),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }
        }
        val status = when {
          state.failed -> stringResource(R.string.library_audio_failed)
          state.verse != null -> stringResource(R.string.library_verse_number, state.verse)
          else -> null
        }
        if (status != null) {
          Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
      // the controls keep their order in any direction of the app
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onPrevious, enabled = state.isActive) {
            Icon(QuranIcons.FastRewind, contentDescription = stringResource(R.string.library_previous_verse))
          }
          IconButton(onClick = onToggle) {
            if (state.isPreparing) {
              Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
              }
            } else {
              Icon(
                if (state.isPlaying) QuranIcons.Pause else QuranIcons.PlayArrow,
                contentDescription = stringResource(
                  if (state.isPlaying) R.string.library_pause else R.string.library_play
                ),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
              )
            }
          }
          IconButton(onClick = onNext, enabled = state.isActive) {
            Icon(QuranIcons.FastForward, contentDescription = stringResource(R.string.library_next_verse))
          }
          IconButton(onClick = onStop, enabled = state.track != null) {
            Icon(QuranIcons.Stop, contentDescription = stringResource(R.string.library_stop))
          }
        }
      }
    }
  }
}
