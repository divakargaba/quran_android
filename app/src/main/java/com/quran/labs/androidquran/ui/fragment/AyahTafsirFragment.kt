package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.TafsirManagerActivity
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.feature.tafsir.data.TafsirRepository
import com.quran.mobile.feature.tafsir.data.TafsirState
import com.quran.mobile.feature.tafsir.model.Tafaseer
import com.quran.mobile.feature.tafsir.ui.TafsirPanel
import com.quran.mobile.feature.tafsir.ui.TafsirPanelState

/**
 * ayah panel showing the tafsir of the selected ayah, in the chosen one of the downloaded tafaseer
 */
class AyahTafsirFragment : AyahActionFragment() {

  private val selection = mutableStateOf<SuraAyah?>(null)

  object Provider : AyahActionFragmentProvider {
    override val order = 6
    override val iconResId = com.quran.labs.androidquran.common.toolbar.R.drawable.ic_tafsir
    override fun newAyahActionFragment() = AyahTafsirFragment()
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
    val repository = TafsirRepository.get(context)
    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        QuranTheme {
          AyahPanelScaffold(
            onPrevious = { readingEventPresenter.selectPreviousAyah() },
            onNext = { readingEventPresenter.selectNextAyah() }
          ) { padding ->
            val states by repository.states.collectAsState()
            val selectedId by repository.selected.collectAsState()
            val downloaded = Tafaseer.all.filter { states[it.id] == TafsirState.Downloaded }
            val tafsir = downloaded.firstOrNull { it.id == selectedId } ?: downloaded.firstOrNull()
            val verse = selection.value
            var state by remember { mutableStateOf<TafsirPanelState>(TafsirPanelState.Loading) }
            LaunchedEffect(tafsir, verse) {
              state = when {
                tafsir == null -> TafsirPanelState.NothingDownloaded
                verse == null -> TafsirPanelState.NoSelection
                else -> {
                  state = TafsirPanelState.Loading
                  TafsirPanelState.Loaded(repository.entry(tafsir, verse.sura, verse.ayah))
                }
              }
            }
            TafsirPanel(
              state = state,
              tafaseer = downloaded,
              selected = tafsir,
              onSelect = repository::select,
              onManage = { startActivity(Intent(context, TafsirManagerActivity::class.java)) },
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
}
