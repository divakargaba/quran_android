package com.quran.mobile.feature.qiraat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.qiraat.R
import com.quran.mobile.feature.qiraat.data.QiraatDifferences.WordDifference
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.model.Riwayah

sealed interface DifferencesState {
  data object NoSelection : DifferencesState
  data object Loading : DifferencesState
  data class Loaded(val words: List<WordDifference>) : DifferencesState
}

/**
 * The words of the selected ayah that another riwayah ("compare to") reads differently from the
 * riwayah of the mushaf being read, and how each of the two reads them.
 */
@Composable
fun DifferencesPanel(
  state: DifferencesState,
  base: Riwayah,
  compareTo: Riwayah,
  onCompareToChange: (Riwayah) -> Unit,
  arabicFont: FontFamily,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val arabic = isArabicLocale()
  val baseName = label(base, arabic)
  val otherName = label(compareTo, arabic)
  LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
    item(key = "selector") {
      RiwayahSelector(base, compareTo, arabic, onCompareToChange)
    }
    when (state) {
      DifferencesState.NoSelection -> item(key = "hint") {
        Text(
          stringResource(R.string.differences_select_ayah),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(24.dp)
        )
      }
      DifferencesState.Loading -> item(key = "loading") {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      is DifferencesState.Loaded -> {
        if (state.words.isEmpty()) {
          item(key = "none") {
            Text(
              stringResource(R.string.differences_none, baseName, otherName),
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth().padding(24.dp)
            )
          }
        }
        itemsIndexed(state.words) { _, difference ->
          DifferenceCard(difference, baseName, otherName, arabicFont)
        }
        item(key = "source") {
          Text(
            stringResource(R.string.differences_source),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun label(riwayah: Riwayah, arabic: Boolean): String {
  val qari = Qiraat.qariOf(riwayah)
  return stringResource(R.string.qiraat_riwayah_label, riwayah.displayName(arabic), qari.displayName(arabic))
}

@Composable
private fun RiwayahSelector(
  base: Riwayah,
  compareTo: Riwayah,
  arabic: Boolean,
  onSelect: (Riwayah) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(
      stringResource(R.string.differences_base, label(base, arabic)),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(R.string.differences_compare_to),
        style = MaterialTheme.typography.titleSmall
      )
      Box {
        TextButton(onClick = { expanded = true }) {
          Text(
            label(compareTo, arabic),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
          )
          Icon(
            QuranIcons.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
          Qiraat.all.filter { it.id != base.id }.forEach { riwayah ->
            DropdownMenuItem(
              text = { Text(label(riwayah, arabic)) },
              onClick = {
                expanded = false
                onSelect(riwayah)
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DifferenceCard(
  difference: WordDifference,
  baseName: String,
  otherName: String,
  arabicFont: FontFamily
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
  ) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
      Text(
        difference.word,
        fontFamily = arabicFont,
        fontSize = 26.sp,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
      Reading(baseName, difference.base, MaterialTheme.colorScheme.onSurfaceVariant, false)
      Reading(otherName, difference.other, MaterialTheme.colorScheme.primary, true)
    }
  }
}

@Composable
private fun Reading(
  name: String,
  description: String,
  color: androidx.compose.ui.graphics.Color,
  emphasized: Boolean
) {
  Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
    Text(
      name,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
      color = color
    )
    // the descriptions are in Arabic
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Text(
        description,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}
