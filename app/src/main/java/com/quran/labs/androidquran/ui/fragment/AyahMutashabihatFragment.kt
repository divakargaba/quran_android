package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.lifecycleScope
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.feature.mutashabihat.data.Mutashabihat
import com.quran.mobile.feature.mutashabihat.data.VerseKey
import com.quran.mobile.feature.mutashabihat.ui.MutashabihatPanel
import com.quran.mobile.feature.mutashabihat.ui.MutashabihatState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ayah panel showing the ayat that share phrases (mutashabihat) with the selected ayah */
class AyahMutashabihatFragment : AyahActionFragment() {

  @Inject
  lateinit var quranInfo: QuranInfo

  private val state = mutableStateOf<MutashabihatState>(MutashabihatState.NoSelection)

  object Provider : AyahActionFragmentProvider {
    override val order = 5
    override val iconResId = com.quran.labs.androidquran.common.toolbar.R.drawable.ic_mutashabihat
    override fun newAyahActionFragment() = AyahMutashabihatFragment()
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
    val arabicFont = FontFamily(TypefaceManager.getUthmaniTypeface(context))
    val suraNames = resources.getStringArray(com.quran.mobile.common.ui.core.R.array.sura_names)
    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        QuranTheme {
          AyahPanelScaffold(
            onPrevious = { readingEventPresenter.selectPreviousAyah() },
            onNext = { readingEventPresenter.selectNextAyah() }
          ) { padding ->
            MutashabihatPanel(
              state = state.value,
              arabicFont = arabicFont,
              suraName = { suraNames[it - 1] },
              onVerseClick = ::openVerse,
              contentPadding = padding,
              modifier = Modifier.fillMaxSize()
            )
          }
        }
      }
    }
  }

  override fun refreshView() {
    val verse = start ?: return
    state.value = MutashabihatState.Loading
    val context = context?.applicationContext ?: return
    lifecycleScope.launch {
      val phrases = withContext(Dispatchers.IO) {
        Mutashabihat.get(context).phrasesFor(verse.sura, verse.ayah)
      }
      // a newer selection may have arrived meanwhile
      if (start == verse) {
        state.value = MutashabihatState.Loaded(VerseKey(verse.sura, verse.ayah), phrases)
      }
    }
  }

  private fun openVerse(verse: VerseKey) {
    val page = quranInfo.getPageFromSuraAyah(verse.sura, verse.ayah)
    (activity as? PagerActivity)?.jumpToAndHighlight(page, verse.sura, verse.ayah)
  }
}
