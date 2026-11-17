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
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.mobile.feature.library.model.LibraryCatalog
import com.quran.mobile.feature.library.ui.UsoolList

/**
 * the tab of the principles (usul) of the qira'at, on the app's first screen: each qira'ah opens
 * to its rules and the books on them ([UsoolActivity])
 */
class UsoolFragment : Fragment() {

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
        LibraryTheme {
          Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            UsoolList(
              groups = catalog.usool,
              arabicFont = arabicFont,
              onOpen = { startActivity(UsoolActivity.intent(context, it)) },
              contentPadding = WindowInsets.navigationBars.asPaddingValues()
            )
          }
        }
      }
    }
  }

  companion object {
    fun newInstance() = UsoolFragment()
  }
}
