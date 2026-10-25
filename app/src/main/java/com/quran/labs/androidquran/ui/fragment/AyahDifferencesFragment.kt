package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.edit
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.feature.qiraat.data.QiraatDifferences
import com.quran.mobile.feature.qiraat.data.RiwayahTexts
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.model.Riwayah
import com.quran.mobile.feature.qiraat.pages.QiraatPageProvider
import com.quran.mobile.feature.qiraat.ui.DifferencesPanel
import com.quran.mobile.feature.qiraat.ui.DifferencesState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * ayah panel showing the words of the selected ayah that a chosen riwayah reads differently from
 * the riwayah of the mushaf being read, and how
 */
class AyahDifferencesFragment : AyahActionFragment() {

  private val selection = mutableStateOf<SuraAyah?>(null)

  object Provider : AyahActionFragmentProvider {
    override val order = 4
    override val iconResId = com.quran.labs.androidquran.common.toolbar.R.drawable.ic_differences
    override fun newAyahActionFragment() = AyahDifferencesFragment()
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
    val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    val arabicFont = FontFamily(TypefaceManager.getUthmaniTypeface(context))
    // the mushaf being read: a riwayah's page type, or the Madinah (Hafs) mushaf
    val pageType = QuranSettings.getInstance(context).pageType.orEmpty()
    val base = Qiraat.byId(pageType.removePrefix(QiraatPageProvider.PAGE_TYPE_PREFIX))
      ?.takeIf { pageType.startsWith(QiraatPageProvider.PAGE_TYPE_PREFIX) }
      ?: Qiraat.byId(HAFS)!!
    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        QuranTheme {
          AyahPanelScaffold(
            onPrevious = { readingEventPresenter.selectPreviousAyah() },
            onNext = { readingEventPresenter.selectNextAyah() }
          ) { padding ->
            var compareTo by remember {
              mutableStateOf(
                Qiraat.byId(preferences.getString(PREF_COMPARE_TO, null))
                  ?.takeIf { it.id != base.id }
                  ?: Qiraat.all.first { it.id != base.id }
              )
            }
            val verse = selection.value
            var state by remember { mutableStateOf<DifferencesState>(DifferencesState.NoSelection) }
            LaunchedEffect(verse, compareTo) {
              state = if (verse == null) {
                DifferencesState.NoSelection
              } else {
                state = DifferencesState.Loading
                withContext(Dispatchers.IO) {
                  val words = try {
                    QiraatDifferences.get(context.applicationContext)
                      .between(base.id, compareTo.id, verse.sura, verse.ayah)
                  } catch (e: Exception) {
                    Timber.e(e)
                    emptyList()
                  }
                  val texts = try {
                    RiwayahTexts.get(context).ayah(verse.sura, verse.ayah)
                  } catch (e: Exception) {
                    Timber.e(e)
                    emptyMap()
                  }
                  DifferencesState.Loaded(words, texts)
                }
              }
            }
            DifferencesPanel(
              state = state,
              base = base,
              compareTo = compareTo,
              onCompareToChange = { riwayah: Riwayah ->
                compareTo = riwayah
                preferences.edit { putString(PREF_COMPARE_TO, riwayah.id) }
              },
              arabicFont = arabicFont,
              contentPadding = padding,
              modifier = Modifier.fillMaxSize()
            )
          }
        }
      }
    }
  }

  override fun refreshView() {
    selection.value = start
  }

  companion object {
    private const val HAFS = "hafs"
    private const val PREFERENCES = "qiraat"
    private const val PREF_COMPARE_TO = "differences_compare_to"
  }
}
