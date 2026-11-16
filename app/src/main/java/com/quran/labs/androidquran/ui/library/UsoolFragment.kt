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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.library.data.DocumentRepository
import com.quran.mobile.feature.library.model.LibraryCatalog
import com.quran.mobile.feature.library.ui.UsoolList

/** the tab of the books on the principles (usul) of the qira'at, on the app's first screen */
class UsoolFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val context = requireContext()
    val catalog = LibraryCatalog.get(context)
    val repository = DocumentRepository.get(context)
    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        QuranTheme {
          Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            val states by repository.states.collectAsState()
            UsoolList(
              catalog = catalog,
              states = states,
              onOpen = { startActivity(DocumentActivity.intent(context, it.id)) },
              onDownload = repository::download,
              onCancel = repository::cancel,
              onDelete = repository::delete,
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
