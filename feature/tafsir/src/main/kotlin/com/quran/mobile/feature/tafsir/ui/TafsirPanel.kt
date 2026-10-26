package com.quran.mobile.feature.tafsir.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.tafsir.R
import com.quran.mobile.feature.tafsir.data.TafsirEntry
import com.quran.mobile.feature.tafsir.model.Tafsir

sealed interface TafsirPanelState {
  data object NoSelection : TafsirPanelState
  data object NothingDownloaded : TafsirPanelState
  data object Loading : TafsirPanelState
  data class Loaded(val entry: TafsirEntry?) : TafsirPanelState
}

@Composable
fun isArabicLocale(): Boolean = LocalConfiguration.current.locales[0].language == "ar"

/**
 * The tafsir of the selected ayah, in the chosen one of the downloaded tafaseer.
 */
@Composable
fun TafsirPanel(
  state: TafsirPanelState,
  tafaseer: List<Tafsir>,
  selected: Tafsir?,
  onSelect: (Tafsir) -> Unit,
  onManage: () -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val arabic = isArabicLocale()
  Column(modifier = modifier.fillMaxSize()) {
    if (state == TafsirPanelState.NothingDownloaded) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(24.dp)
      ) {
        Text(stringResource(R.string.tafsir_none_downloaded), textAlign = TextAlign.Center)
        Button(onClick = onManage, modifier = Modifier.padding(top = 12.dp)) {
          Text(stringResource(R.string.tafsir_get))
        }
      }
      return@Column
    }

    TafsirSelector(tafaseer, selected, arabic, onSelect, onManage)

    when (state) {
      TafsirPanelState.NoSelection -> Text(
        stringResource(R.string.tafsir_select_ayah),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(24.dp)
      )
      TafsirPanelState.Loading -> Box(
        Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center
      ) { CircularProgressIndicator() }
      is TafsirPanelState.Loaded -> TafsirText(state.entry, selected, contentPadding)
      TafsirPanelState.NothingDownloaded -> Unit
    }
  }
}

@Composable
private fun TafsirSelector(
  tafaseer: List<Tafsir>,
  selected: Tafsir?,
  arabic: Boolean,
  onSelect: (Tafsir) -> Unit,
  onManage: () -> Unit
) {
  var expanded by remember { mutableStateOf(false) }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
  ) {
    Box(Modifier.weight(1f)) {
      TextButton(onClick = { expanded = true }) {
        Text(
          selected?.name(arabic).orEmpty(),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary
        )
        Icon(QuranIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        tafaseer.forEach { tafsir ->
          DropdownMenuItem(
            text = { Text(tafsir.name(arabic)) },
            onClick = {
              expanded = false
              onSelect(tafsir)
            }
          )
        }
        DropdownMenuItem(
          text = { Text(stringResource(R.string.tafsir_get)) },
          onClick = {
            expanded = false
            onManage()
          }
        )
      }
    }
  }
}

@Composable
private fun TafsirText(entry: TafsirEntry?, tafsir: Tafsir?, contentPadding: PaddingValues) {
  // each ayah's tafsir starts at its top
  val scroll = remember(entry) { ScrollState(0) }
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scroll)
      .padding(horizontal = 16.dp)
      .padding(contentPadding)
  ) {
    if (entry == null) {
      Text(
        stringResource(R.string.tafsir_no_entry),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp)
      )
      return@Column
    }
    if (entry.firstAyah != entry.lastAyah) {
      Text(
        stringResource(R.string.tafsir_covers, entry.firstAyah, entry.lastAyah),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
      )
    }
    val rtl = tafsir?.isArabic == true
    CompositionLocalProvider(
      LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
      SelectionContainer {
        Text(
          entry.text,
          style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = if (rtl) 19.sp else 16.sp,
            lineHeight = if (rtl) 34.sp else 24.sp,
            textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr
          ),
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}
