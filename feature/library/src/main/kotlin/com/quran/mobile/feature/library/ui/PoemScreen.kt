package com.quran.mobile.feature.library.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.quran.mobile.feature.library.model.Poem
import com.quran.mobile.feature.library.model.PoemText
import java.util.Locale

/** a row of the poem's list: a chapter's heading or a verse (numbered from 1) */
sealed interface PoemRow {
  data class Heading(val chapter: Int, val title: String) : PoemRow
  data class Verse(val number: Int, val verse: PoemText.Verse) : PoemRow
}

fun PoemText.rows(): List<PoemRow> = buildList {
  chapters.forEachIndexed { index, chapter ->
    if (chapter.title.isNotEmpty()) add(PoemRow.Heading(index, chapter.title))
    chapter.verses.forEachIndexed { i, verse -> add(PoemRow.Verse(chapter.first + i, verse)) }
  }
}

/** the index, in [rows], of a chapter's heading (or of its first verse) */
fun PoemText.rowOfChapter(rows: List<PoemRow>, chapter: Int): Int {
  val first = chapters[chapter].first
  return rows.indexOfFirst {
    it is PoemRow.Heading && it.chapter == chapter || it is PoemRow.Verse && it.number == first
  }.coerceAtLeast(0)
}

/**
 * The text of a poem, chapter by chapter, each verse in its two halves: a chapter's recitation
 * is played from its heading (where the recording is divided as the poem is), and a verse opens
 * its place in the poem's translation, if it has one with every verse.
 */
@Composable
fun PoemVerses(
  poem: Poem,
  rows: List<PoemRow>,
  listState: LazyListState,
  playingChapters: List<Int>,
  arabicFont: FontFamily,
  onPlayChapter: (Int) -> Unit,
  onVerse: ((Int) -> Unit)?,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val arabic = isArabicLocale()
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
          if (onVerse != null) {
            Text(
              stringResource(R.string.library_verse_hint),
              style = MaterialTheme.typography.labelMedium,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(top = 8.dp)
            )
          }
        }
      }
      itemsIndexed(rows) { _, row ->
        when (row) {
          is PoemRow.Heading -> ChapterHeading(
            title = row.title,
            playing = row.chapter in playingChapters,
            canPlay = poem.audio?.trackOfChapter(row.chapter) != null,
            arabicFont = arabicFont,
            onPlay = { onPlayChapter(row.chapter) }
          )
          is PoemRow.Verse -> VerseRow(row, arabicFont, onVerse)
        }
      }
    }
  }
}

@Composable
private fun ChapterHeading(
  title: String,
  playing: Boolean,
  canPlay: Boolean,
  arabicFont: FontFamily,
  onPlay: () -> Unit
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
    if (canPlay) {
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
private fun VerseRow(row: PoemRow.Verse, arabicFont: FontFamily, onVerse: ((Int) -> Unit)?) {
  val number = remember(row.number) { String.format(Locale.forLanguageTag("ar"), "%d", row.number) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (onVerse != null) Modifier.clickable { onVerse(row.number) } else Modifier)
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

/** the bar of the poem's recitation: the track, its progress, and previous, play or pause, next */
@Composable
fun PoemPlayerBar(
  poem: Poem,
  text: PoemText?,
  state: PoemPlayer.State,
  onToggle: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onSeek: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val audio = poem.audio ?: return
  val arabic = isArabicLocale()
  val track = state.track?.let(audio.tracks::getOrNull)
  val title = when {
    state.failed -> stringResource(R.string.library_audio_failed)
    track == null -> stringResource(R.string.library_recitation_of, audio.reciter(arabic))
    track.title != null -> track.title
    else -> track.chapters.firstOrNull()?.let { text?.chapters?.getOrNull(it)?.title }
      ?: stringResource(R.string.library_track, (state.track ?: 0) + 1, audio.tracks.size)
  }
  Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
      if (state.durationMs > 0) {
        Slider(
          value = state.positionMs.coerceIn(0, state.durationMs).toFloat(),
          onValueChange = { onSeek(it.toInt()) },
          valueRange = 0f..state.durationMs.toFloat(),
          modifier = Modifier.fillMaxWidth()
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
          Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          if (track != null && !state.failed) {
            Text(
              audio.reciter(arabic) + if (state.durationMs > 0) {
                "  ·  " + time(state.positionMs) + " / " + time(state.durationMs)
              } else {
                ""
              },
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
        // the controls keep their order in any direction of the app
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (audio.tracks.size > 1) {
              IconButton(onClick = onPrevious, enabled = (state.track ?: 0) > 0) {
                Icon(QuranIcons.FastRewind, contentDescription = stringResource(R.string.library_previous_track))
              }
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
            if (audio.tracks.size > 1) {
              IconButton(
                onClick = onNext,
                enabled = state.track != null && state.track + 1 < audio.tracks.size
              ) {
                Icon(QuranIcons.FastForward, contentDescription = stringResource(R.string.library_next_track))
              }
            }
          }
        }
      }
    }
  }
}

private fun time(ms: Int): String {
  val seconds = ms / 1000
  return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
