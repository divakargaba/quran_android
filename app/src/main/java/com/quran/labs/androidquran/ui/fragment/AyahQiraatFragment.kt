package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.qiraat.RiwayahSwitcher
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.model.Riwayah
import com.quran.mobile.feature.qiraat.pages.QiraatPageProvider
import com.quran.mobile.feature.qiraat.ui.CompareList
import dev.zacsweers.metro.Inject

/**
 * ayah panel showing the selected ayah in all twenty riwayat of the ten qira'at, each of which can
 * be recited on its own (with the reader's audio, so from the downloaded recordings when there are)
 */
class AyahQiraatFragment : AyahActionFragment() {

  @Inject
  lateinit var quranInfo: QuranInfo

  private val selection = mutableStateOf<SuraAyah?>(null)

  object Provider : AyahActionFragmentProvider {
    override val order = 3
    override val iconResId = com.quran.labs.androidquran.common.toolbar.R.drawable.ic_qiraat
    override fun newAyahActionFragment() = AyahQiraatFragment()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (activity as? PagerActivity)?.pagerActivityComponent?.inject(this)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val context = requireContext()
    val repository = RiwayahRepository.get(context)
    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        QuranTheme {
          AyahPanelScaffold(
            onPrevious = { readingEventPresenter.selectPreviousAyah() },
            onNext = { readingEventPresenter.selectNextAyah() }
          ) { padding ->
            val downloadStates by repository.downloadStates.collectAsState()
            val verse = selection.value
            if (verse == null) {
              Box(Modifier.padding(24.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                  stringResource(com.quran.mobile.feature.qiraat.R.string.qiraat_long_press_hint),
                  textAlign = TextAlign.Center
                )
              }
            } else {
              CompareList(
                sura = verse.sura,
                ayah = verse.ayah,
                repository = repository,
                downloadStates = downloadStates,
                canPlay = { QiraatPageProvider.qariFor(it, verse.sura) != null },
                onPlay = { riwayah ->
                  (activity as? PagerActivity)?.playRiwayahAyah(riwayah, verse.sura, verse.ayah)
                },
                onOpenInReader = { riwayah -> openInReader(riwayah, verse) },
                contentPadding = padding,
                modifier = Modifier.fillMaxSize()
              )
            }
          }
        }
      }
    }
  }

  /** the reader, switched to the riwayah's mushaf, at the ayah */
  private fun openInReader(riwayah: Riwayah, verse: SuraAyah) {
    val activity = activity ?: return
    val page = quranInfo.getPageFromSuraAyah(verse.sura, verse.ayah)
    RiwayahSwitcher.switchTo(activity, RiwayahSwitcher.pageTypeOf(riwayah), verse.sura, verse.ayah, page)
  }

  override fun refreshView() {
    selection.value = start
  }
}
