package com.quran.mobile.feature.qiraat.ui

import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.qiraat.R
import com.quran.mobile.feature.qiraat.audio.RiwayahPlayer
import com.quran.mobile.feature.qiraat.data.AyahIndex
import com.quran.mobile.feature.qiraat.data.DownloadState
import com.quran.mobile.feature.qiraat.data.RiwayahLocations
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.model.Riwayah

private const val PAGE_COUNT = 604

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiraatScreen(
  repository: RiwayahRepository,
  player: RiwayahPlayer,
  initialRiwayah: Riwayah,
  initialPage: Int,
  initialAyah: Pair<Int, Int>?,
  startInCompare: Boolean,
  onRiwayahChanged: (Riwayah) -> Unit,
  onFinish: () -> Unit
) {
  val arabic = isArabicLocale()
  var riwayahId by rememberSaveable { mutableStateOf(initialRiwayah.id) }
  val riwayah = Qiraat.byId(riwayahId) ?: initialRiwayah
  var selectedSura by rememberSaveable { mutableStateOf(initialAyah?.first ?: 0) }
  var selectedAyah by rememberSaveable { mutableStateOf(initialAyah?.second ?: 0) }
  val selection = if (selectedSura > 0) selectedSura to selectedAyah else null
  var comparing by rememberSaveable { mutableStateOf(startInCompare && initialAyah != null) }
  // when opened straight into compare mode, back leaves the screen instead of showing the reader
  val compareIsRoot = remember { startInCompare && initialAyah != null }

  val downloadStates by repository.downloadStates.collectAsState()
  val playback by player.state.collectAsState()
  val isDownloaded = downloadStates[riwayah.id] == DownloadState.Downloaded

  var locations by remember(riwayah.id) { mutableStateOf<RiwayahLocations?>(null) }
  LaunchedEffect(riwayah.id) { locations = repository.locations(riwayah) }

  val pagerState = rememberPagerState(initialPage = (initialPage - 1).coerceIn(0, PAGE_COUNT - 1)) {
    PAGE_COUNT
  }
  // keep the selected ayah in view when switching riwayat (page breaks can differ slightly)
  LaunchedEffect(locations, selection) {
    val current = locations ?: return@LaunchedEffect
    val (sura, ayah) = selection ?: return@LaunchedEffect
    val pages = current.rectsFor(sura, ayah).keys
    if (pagerState.currentPage + 1 !in pages) pagerState.scrollToPage(current.pageFor(sura, ayah) - 1)
  }

  val currentPage = pagerState.currentPage + 1
  val currentSura = selection?.first ?: locations?.firstAyahOn(currentPage)?.first

  fun select(target: Riwayah) {
    riwayahId = target.id
    onRiwayahChanged(target)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              if (comparing && selection != null) {
                stringResource(R.string.qiraat_compare_title, selection.first, selection.second)
              } else {
                stringResource(R.string.qiraat_title)
              },
              maxLines = 1
            )
            if (!comparing) {
              Text(
                stringResource(
                  R.string.qiraat_riwayah_label,
                  riwayah.displayName(arabic),
                  Qiraat.qariOf(riwayah).displayName(arabic)
                ) + " · " + stringResource(R.string.qiraat_page, currentPage),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = {
            if (comparing && !compareIsRoot) comparing = false else onFinish()
          }) {
            Icon(QuranIcons.ArrowBack, contentDescription = null)
          }
        },
        actions = {
          if (!comparing) {
            TextButton(onClick = { comparing = true }, enabled = selection != null) {
              Text(stringResource(R.string.qiraat_compare))
            }
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }, enabled = isDownloaded) {
              Icon(QuranIcons.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.qiraat_delete)) },
                onClick = {
                  menuOpen = false
                  repository.delete(riwayah)
                }
              )
            }
          } else if (compareIsRoot) {
            TextButton(onClick = { comparing = false }) {
              Text(stringResource(R.string.qiraat_reader))
            }
          }
        }
      )
    },
    bottomBar = {
      if (!comparing) {
        AudioBar(
          riwayah = riwayah,
          sura = currentSura,
          playback = playback,
          onPlay = { sura -> player.play(riwayah, sura) },
          onPause = player::pause,
          onStop = player::stop
        )
      }
    }
  ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
      if (comparing && selection != null) {
        CompareList(
          sura = selection.first,
          ayah = selection.second,
          repository = repository,
          downloadStates = downloadStates,
          onOpenInReader = { target ->
            select(target)
            comparing = false
          }
        )
      } else {
        RiwayahSelector(
          selected = riwayah,
          downloadStates = downloadStates,
          onSelect = ::select,
          modifier = Modifier.padding(vertical = 4.dp)
        )
        if (selection != null) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            AssistChip(
              onClick = { comparing = true },
              label = {
                Text(stringResource(R.string.qiraat_selected_ayah, selection.first, selection.second))
              }
            )
            TextButton(onClick = { selectedSura = 0; selectedAyah = 0 }) {
              Text(stringResource(R.string.qiraat_clear_selection))
            }
          }
        }

        val renderer = if (isDownloaded) repository.renderer(riwayah) else null
        if (renderer == null) {
          Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                stringResource(
                  R.string.qiraat_download_prompt,
                  riwayah.displayName(arabic),
                  Qiraat.qariOf(riwayah).displayName(arabic)
                ),
                textAlign = TextAlign.Center
              )
              DownloadRow(riwayah, downloadStates[riwayah.id]) { repository.download(riwayah) }
              Text(
                stringResource(R.string.qiraat_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
              )
            }
          }
        } else {
          // mushaf pages turn right to left regardless of the ui language
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            HorizontalPager(
              state = pagerState,
              reverseLayout = true,
              beyondViewportPageCount = 1,
              key = { it },
              modifier = Modifier.fillMaxSize()
            ) { index ->
              val page = index + 1
              val highlights: List<RectF> = remember(locations, selection, page) {
                val current = locations
                if (current == null || selection == null) {
                  emptyList()
                } else {
                  current.rectsFor(selection.first, selection.second)[page].orEmpty()
                }
              }
              MushafPage(
                renderer = renderer,
                page = page,
                highlights = highlights,
                onTap = { _, _ ->
                  selectedSura = 0
                  selectedAyah = 0
                },
                onLongPress = { x, y ->
                  locations?.ayahAt(page, x, y)?.let { (sura, ayah) ->
                    selectedSura = sura
                    selectedAyah = ayah
                  }
                },
                modifier = Modifier.fillMaxSize().padding(4.dp)
              )
            }
          }
        }
      }
    }
  }
}

/** the ayah, if it exists in the (Hafs) count */
fun clampAyah(sura: Int, ayah: Int): Pair<Int, Int>? =
  if (sura in 1..114 && ayah in 1..AyahIndex.ayahCount(sura)) sura to ayah else null
