package com.quran.mobile.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.audio.PoemPlayer
import com.quran.mobile.feature.library.model.Document
import com.quran.mobile.feature.library.model.InlineText
import com.quran.mobile.feature.library.model.Poem
import com.quran.mobile.feature.library.model.PoemText
import com.quran.mobile.feature.library.model.Recitation
import java.util.Locale

/** a row of the poem's list: a chapter's heading, a verse (numbered from 1), or what is said of verses */
sealed interface PoemRow {
  data class Heading(val chapter: Int, val title: String, val first: Int, val last: Int) : PoemRow
  data class Verse(val number: Int, val verse: PoemText.Verse) : PoemRow
  data class Inline(val book: InlineBook, val entry: InlineText.Entry) : PoemRow
}

/** a book shown under the verses: what it says of each */
class InlineBook(val document: Document, val text: InlineText)

/** the poem's rows, with what each of [books] says of a verse (or run of verses) under it */
fun PoemText.rows(books: List<InlineBook> = emptyList()): List<PoemRow> {
  val after = HashMap<Int, MutableList<PoemRow.Inline>>()
  books.forEach { book ->
    book.text.entries.forEach { after.getOrPut(it.last) { ArrayList() }.add(PoemRow.Inline(book, it)) }
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
  onOpenBook: (Document) -> Unit,
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
              arabicFont = arabicFont,
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
        fontSize = 23.sp,
        lineHeight = 42.sp,
        textAlign = TextAlign.Start,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(end = 28.dp)
      )
      Text(
        row.verse.secondHalf,
        fontFamily = arabicFont,
        fontSize = 23.sp,
        lineHeight = 42.sp,
        textAlign = TextAlign.End,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp)
      )
    }
  }
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/**
 * What a book says of the verse (or verses) above it: the translation, and the explanation when
 * opened. The book's name opens the book itself.
 */
@Composable
private fun InlineRow(
  row: PoemRow.Inline,
  expanded: Boolean,
  arabicFont: FontFamily,
  onExpand: () -> Unit,
  onOpenBook: (Document) -> Unit
) {
  val entry = row.entry
  val book = row.book
  val arabicBook = book.document.language == "ar"
  CompositionLocalProvider(
    LocalLayoutDirection provides if (arabicBook) LayoutDirection.Rtl else LayoutDirection.Ltr
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book.document) }.padding(vertical = 2.dp)
      ) {
        Text(
          book.document.title(arabicBook) + if (entry.first != entry.last) {
            "  ·  " + stringResource(R.string.library_verses_range, entry.first, entry.last)
          } else {
            ""
          },
          style = MaterialTheme.typography.labelMedium,
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
      if (entry.translation.isNotEmpty()) {
        Text(
          text = remember(entry) { withArabic(entry.translation, arabicFont) },
          fontSize = 16.sp,
          lineHeight = 24.sp,
          style = TextStyle(textDirection = TextDirection.Content),
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
      }
      if (entry.explanation.isNotEmpty()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(vertical = 8.dp)
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
        if (expanded) {
          val paragraphs = remember(entry) { entry.explanation.split("\n\n").filter { it.isNotBlank() } }
          paragraphs.forEach { paragraph ->
            if (paragraph.startsWith("# ")) {
              Text(
                paragraph.removePrefix("# "),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
              )
            } else {
              val note = paragraph.startsWith("[")
              Text(
                text = remember(paragraph) { withArabic(paragraph, arabicFont) },
                fontSize = if (note) 14.sp else 15.sp,
                lineHeight = if (note) 21.sp else 23.sp,
                style = TextStyle(textDirection = TextDirection.Content),
                color = if (note) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
              )
            }
          }
        }
      }
    }
  }
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

private val ARABIC_RUN = Regex("[\\u0600-\\u06FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]+(?:[\\s،؛]+[\\u0600-\\u06FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]+)*")

/** the text with its Arabic in the Arabic font, larger (its marks need the room) */
fun withArabic(text: String, arabicFont: FontFamily): AnnotatedString = buildAnnotatedString {
  append(text)
  ARABIC_RUN.findAll(text).forEach { run ->
    addStyle(SpanStyle(fontFamily = arabicFont, fontSize = 1.2.em), run.range.first, run.range.last + 1)
  }
}

/**
 * The bar of the poem's recitation: its reciter (tapped to choose another, when there are more),
 * the verse being recited, and previous verse, play or pause, next verse, and stop.
 */
@Composable
fun PoemPlayerBar(
  recitation: Recitation,
  canChooseReciter: Boolean,
  state: PoemPlayer.State,
  settings: PoemPlayer.Settings,
  onChooseReciter: () -> Unit,
  onToggle: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onStop: () -> Unit,
  /** how many times more each verse is recited: the next of the choices */
  onRepeat: (Int) -> Unit,
  onSpeed: (Float) -> Unit,
  /** to choose a set of verses to repeat, and the rest of how the recitation is repeated */
  onRepeatOptions: () -> Unit,
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
        }?.let { status ->
          // (with the set of verses being repeated)
          if (settings.hasRange && !state.failed) "$status · ${settings.first}–${settings.last}" else status
        } ?: if (settings.hasRange) "${settings.first}–${settings.last}" else null
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
          BarButton(QuranIcons.FastRewind, stringResource(R.string.library_previous_verse), onPrevious, enabled = state.isActive)
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp, 48.dp).clip(CircleShape).clickable(onClick = onToggle)
          ) {
            if (state.isPreparing) {
              CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
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
          BarButton(QuranIcons.FastForward, stringResource(R.string.library_next_verse), onNext, enabled = state.isActive)
          BarButton(QuranIcons.Stop, stringResource(R.string.library_stop), onStop, enabled = state.track != null)
          // each verse again (once more, twice, three times, without end); held: the set of verses
          BarButton(
            QuranIcons.Repeat,
            stringResource(R.string.library_repeat),
            onClick = { onRepeat(REPEATS[(REPEATS.indexOf(settings.verseRepeat) + 1) % REPEATS.size]) },
            onLongClick = onRepeatOptions,
            active = settings.repeats,
            badge = if (settings.verseRepeat != 0) timesLabel(settings.verseRepeat) else null
          )
          BarButton(
            QuranIcons.Speed,
            stringResource(R.string.library_speed),
            onClick = { onSpeed(SPEEDS[(SPEEDS.indexOf(settings.speed) + 1) % SPEEDS.size]) },
            active = settings.speed != 1f,
            badge = if (settings.speed != 1f) speedLabel(settings.speed) else null
          )
        }
      }
    }
  }
}

/** the choices of how many times more a verse (or a set of verses) is recited, and of the speed */
val REPEATS = listOf(0, 1, 2, 3, PoemPlayer.INFINITE)
val SPEEDS = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.5f, 0.75f)

/** how many times a verse is recited in all, for a number of repeats: "2×" */
fun timesLabel(repeat: Int): String = if (repeat == PoemPlayer.INFINITE) "∞" else "${repeat + 1}×"

fun speedLabel(speed: Float): String =
  (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()) + "×"

/** a control of the bar: narrow, so that six are beside the reciter's name; its choice above it */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarButton(
  icon: ImageVector,
  description: String,
  onClick: () -> Unit,
  enabled: Boolean = true,
  onLongClick: (() -> Unit)? = null,
  active: Boolean = false,
  badge: String? = null
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .size(40.dp, 48.dp)
      .clip(CircleShape)
      .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
  ) {
    Icon(
      icon,
      contentDescription = description,
      tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
      },
      modifier = Modifier.size(22.dp).then(if (badge != null) Modifier.padding(top = 4.dp) else Modifier)
    )
    if (badge != null) {
      Text(
        badge,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp)
      )
    }
  }
}
