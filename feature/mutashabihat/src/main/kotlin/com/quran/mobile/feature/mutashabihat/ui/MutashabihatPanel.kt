package com.quran.mobile.feature.mutashabihat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.mobile.feature.mutashabihat.R
import com.quran.mobile.feature.mutashabihat.data.Occurrence
import com.quran.mobile.feature.mutashabihat.data.Phrase
import com.quran.mobile.feature.mutashabihat.data.VerseKey

sealed interface MutashabihatState {
  data object NoSelection : MutashabihatState
  data object Loading : MutashabihatState
  data class Loaded(val verse: VerseKey, val phrases: List<Phrase>) : MutashabihatState
}

/**
 * The phrases the selected ayah shares with other ayat, each followed by every ayah it occurs in
 * with the phrase highlighted. Laid out like the translation panel: ayah reference, then text.
 */
@Composable
fun MutashabihatPanel(
  state: MutashabihatState,
  arabicFont: FontFamily,
  suraName: (Int) -> String,
  onVerseClick: (VerseKey) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  when (state) {
    MutashabihatState.NoSelection -> Message(stringResource(R.string.mutashabihat_select), modifier)
    MutashabihatState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    is MutashabihatState.Loaded -> if (state.phrases.isEmpty()) {
      Message(stringResource(R.string.mutashabihat_none, state.verse.sura, state.verse.ayah), modifier)
    } else {
      LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        state.phrases.forEach { phrase ->
          item(key = "phrase_${phrase.id}") {
            PhraseHeader(phrase, arabicFont)
          }
          items(phrase.occurrences, key = { "${phrase.id}_${it.verse}" }) { occurrence ->
            OccurrenceRow(
              occurrence = occurrence,
              isSelected = occurrence.verse == state.verse,
              arabicFont = arabicFont,
              suraName = suraName,
              onClick = { onVerseClick(occurrence.verse) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun Message(text: String, modifier: Modifier) {
  Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
    Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun PhraseHeader(phrase: Phrase, arabicFont: FontFamily) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    Text(
      phrase.text,
      fontFamily = arabicFont,
      fontSize = 26.sp,
      color = MaterialTheme.colorScheme.primary,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth()
    )
    Text(
      stringResource(R.string.mutashabihat_count, phrase.count, phrase.occurrences.size),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth()
    )
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
  }
}

@Composable
private fun OccurrenceRow(
  occurrence: Occurrence,
  isSelected: Boolean,
  arabicFont: FontFamily,
  suraName: (Int) -> String,
  onClick: () -> Unit
) {
  val highlight = MaterialTheme.colorScheme.primary
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 10.dp)
  ) {
    Text(
      "${suraName(occurrence.verse.sura)} ${occurrence.verse.sura}:${occurrence.verse.ayah}",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
      color = if (isSelected) highlight else MaterialTheme.colorScheme.onSurfaceVariant
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Text(
        highlightedText(occurrence, highlight),
        fontFamily = arabicFont,
        fontSize = 22.sp,
        lineHeight = 40.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
      )
    }
  }
}

private fun highlightedText(occurrence: Occurrence, color: Color): AnnotatedString =
  buildAnnotatedString {
    occurrence.words.forEachIndexed { index, word ->
      if (index > 0) append(' ')
      if (occurrence.highlighted.any { index in it }) {
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(word) }
      } else {
        append(word)
      }
    }
  }
