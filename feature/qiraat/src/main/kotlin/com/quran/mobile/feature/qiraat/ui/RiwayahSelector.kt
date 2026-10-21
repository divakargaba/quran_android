package com.quran.mobile.feature.qiraat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.qiraat.data.DownloadState
import com.quran.mobile.feature.qiraat.model.Qari
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.model.Riwayah

@Composable
fun isArabicLocale(): Boolean =
  LocalConfiguration.current.locales[0].language == "ar"

fun Qari.displayName(arabic: Boolean) = if (arabic) nameAr else nameEn
fun Riwayah.displayName(arabic: Boolean) = if (arabic) nameAr else nameEn

/**
 * Two rows: the ten qurra, then the two rawis of the selected qari. Switching keeps the page.
 */
@Composable
fun RiwayahSelector(
  selected: Riwayah,
  downloadStates: Map<String, DownloadState>,
  onSelect: (Riwayah) -> Unit,
  modifier: Modifier = Modifier
) {
  val arabic = isArabicLocale()
  val selectedQari = Qiraat.qariOf(selected)
  val listState = rememberLazyListState()
  LaunchedEffect(selectedQari.id) {
    listState.animateScrollToItem(Qiraat.qurra.indexOf(selectedQari))
  }

  Column(modifier = modifier.fillMaxWidth()) {
    LazyRow(
      state = listState,
      contentPadding = PaddingValues(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(Qiraat.qurra, key = { it.id }) { qari ->
        FilterChip(
          selected = qari == selectedQari,
          onClick = {
            if (qari != selectedQari) {
              // prefer a rawi whose mushaf is already downloaded
              val target = qari.riwayat.firstOrNull {
                downloadStates[it.id] == DownloadState.Downloaded
              } ?: qari.riwayat.first()
              onSelect(target)
            }
          },
          label = { Text(qari.displayName(arabic)) }
        )
      }
    }
    Row(
      modifier = Modifier.padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      selectedQari.riwayat.forEach { riwayah ->
        FilterChip(
          selected = riwayah == selected,
          onClick = { onSelect(riwayah) },
          label = { Text(riwayah.displayName(arabic)) },
          leadingIcon = if (downloadStates[riwayah.id] == DownloadState.Downloaded) {
            { Icon(QuranIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
          } else {
            null
          }
        )
      }
    }
  }
}
