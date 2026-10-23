package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.feature.qiraat.QiraatActivity
import com.quran.mobile.feature.qiraat.audio.RiwayahPlayer
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.ui.CompareList
import dev.zacsweers.metro.Inject

/**
 * ayah panel showing the selected ayah in all twenty riwayat of the ten qira'at, each of which can
 * be played on its own
 */
class AyahQiraatFragment : AyahActionFragment() {

  @Inject
  lateinit var quranInfo: QuranInfo

  private val selection = mutableStateOf<SuraAyah?>(null)
  private var player: RiwayahPlayer? = null

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
    val riwayahPlayer = RiwayahPlayer(context).also { player = it }
    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        QuranTheme {
          val downloadStates by repository.downloadStates.collectAsState()
          val modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
          val verse = selection.value
          if (verse == null) {
            Box(modifier.padding(24.dp), contentAlignment = Alignment.TopCenter) {
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
              player = riwayahPlayer,
              onOpenInReader = { riwayah ->
                startActivity(
                  QiraatActivity.ayahIntent(
                    context, riwayah.id, verse.sura, verse.ayah,
                    quranInfo.getPageFromSuraAyah(verse.sura, verse.ayah)
                  )
                )
              },
              contentPadding = WindowInsets.navigationBars.asPaddingValues(),
              modifier = modifier
            )
          }
        }
      }
    }
  }

  override fun onToggleDetailsPanel(isVisible: Boolean) {
    if (!isVisible) player?.stop()
  }

  override fun onDestroyView() {
    player?.release()
    player = null
    super.onDestroyView()
  }

  override fun refreshView() {
    selection.value = start
  }
}
