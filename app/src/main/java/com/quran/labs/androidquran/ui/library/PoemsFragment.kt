package com.quran.labs.androidquran.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.fragment.app.Fragment
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.mobile.feature.library.model.LibraryCatalog
import com.quran.mobile.feature.library.ui.PoemsList

/** the tab of the poems (mutun) of the qira'at and of tajwid, on the app's first screen */
class PoemsFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val context = requireContext()
    val catalog = LibraryCatalog.get(context)
    val arabicFont = FontFamily(TypefaceManager.getTafseerTypeface(context))
    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        QuranTheme {
          Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            PoemsList(
              poems = catalog.poems,
              arabicFont = arabicFont,
              onOpen = { poem -> startActivity(PoemActivity.intent(context, poem.id)) },
              contentPadding = WindowInsets.navigationBars.asPaddingValues()
            )
          }
        }
      }
    }
  }

  companion object {
    fun newInstance() = PoemsFragment()
  }
}
