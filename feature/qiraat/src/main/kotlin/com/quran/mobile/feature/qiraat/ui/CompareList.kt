package com.quran.mobile.feature.qiraat.ui

import android.graphics.RectF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.qiraat.R
import com.quran.mobile.feature.qiraat.data.DownloadState
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.data.RiwayahTexts
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.model.Riwayah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One ayah in every riwayah, one under the other, each with a button that recites it in that
 * riwayah: cropped out of the riwayah's mushaf when it is downloaded, else as text.
 *
 * @param canPlay whether the ayah has a recording in the riwayah
 * @param onPlay recites the ayah in the riwayah (downloading its recording if needed)
 * @param onOpenInReader opens the riwayah's mushaf at the ayah
 */
@Composable
fun CompareList(
  sura: Int,
  ayah: Int,
  repository: RiwayahRepository,
  downloadStates: Map<String, DownloadState>,
  canPlay: (Riwayah) -> Boolean,
  onPlay: (Riwayah) -> Unit,
  onOpenInReader: (Riwayah) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val context = LocalContext.current
  val arabic = isArabicLocale()
  var texts by remember(sura, ayah) { mutableStateOf<Map<String, RiwayahTexts.AyahText>>(emptyMap()) }
  LaunchedEffect(sura, ayah) {
    texts = withContext(Dispatchers.IO) {
      try {
        RiwayahTexts.get(context).ayah(sura, ayah)
      } catch (e: Exception) {
        emptyMap()
      }
    }
  }

  LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
    Qiraat.qurra.forEach { qari ->
      items(qari.riwayat, key = { it.id }) { riwayah ->
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp)
          ) {
            Text(
              stringResource(
                R.string.qiraat_riwayah_label,
                riwayah.displayName(arabic),
                qari.displayName(arabic)
              ),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onPlay(riwayah) }, enabled = canPlay(riwayah)) {
              Icon(
                QuranIcons.PlayArrow,
                contentDescription = stringResource(R.string.qiraat_play_ayah)
              )
            }
          }
          Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            if (downloadStates[riwayah.id] == DownloadState.Downloaded) {
              ComparedAyah(
                riwayah = riwayah,
                sura = sura,
                ayah = ayah,
                repository = repository,
                onClick = { onOpenInReader(riwayah) }
              )
            } else {
              texts[riwayah.id]?.takeIf { it.text.isNotEmpty() }?.let { text ->
                AyahText(text, onClick = { onOpenInReader(riwayah) })
              }
            }
          }
          HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
        }
      }
    }
  }
}

/** an ayah as text, in the font of its riwayah */
@Composable
private fun AyahText(text: RiwayahTexts.AyahText, onClick: () -> Unit) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Text(
      text.text,
      fontFamily = riwayahFont(text.font),
      fontSize = 24.sp,
      lineHeight = 48.sp,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 4.dp)
    )
  }
}

@Composable
private fun ComparedAyah(
  riwayah: Riwayah,
  sura: Int,
  ayah: Int,
  repository: RiwayahRepository,
  onClick: () -> Unit
) {
  var rects by remember(riwayah.id, sura, ayah) { mutableStateOf<Map<Int, List<RectF>>?>(null) }
  LaunchedEffect(riwayah.id, sura, ayah) {
    rects = repository.locations(riwayah)?.rectsFor(sura, ayah).orEmpty()
  }
  val renderer = repository.renderer(riwayah) ?: return
  val ayahRects = rects ?: return
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
  ) {
    ayahRects.forEach { (page, pageRects) ->
      AyahCrop(renderer = renderer, page = page, rects = pageRects)
    }
  }
}
