package com.quran.labs.androidquran.ui.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.DocumentRepository
import com.quran.mobile.feature.library.model.LibraryCatalog
import com.quran.mobile.feature.library.ui.PdfPages
import com.quran.mobile.feature.library.ui.PdfPagesColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** reads a document of the library (a pdf), page under page, from a given page */
class DocumentActivity : AppCompatActivity() {

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // (the screen stays on while a book is read, as while the mushaf is)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    supportActionBar?.hide()
    val document = LibraryCatalog.get(this).document(intent.getStringExtra(EXTRA_DOCUMENT).orEmpty())
    if (document == null) {
      finish()
      return
    }
    val firstPage = intent.getIntExtra(EXTRA_PAGE, 0)
    val repository = DocumentRepository.get(this)
    val arabic = resources.configuration.locales.get(0)?.language == "ar"
    setContentView(
      ComposeView(this).apply {
        setContent {
          QuranTheme {
            // null while it is being opened; a failure has no pages
            var pages by remember { mutableStateOf<Result<PdfPages>?>(null) }
            LaunchedEffect(document.id) {
              pages = withContext(Dispatchers.IO) {
                runCatching { PdfPages(repository.open(document) ?: error("not downloaded")) }
                  .onFailure { Timber.e(it) }
              }
            }
            DisposableEffect(Unit) { onDispose { pages?.getOrNull()?.close() } }
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstPage)
            var zoom by rememberSaveable { mutableFloatStateOf(1f) }
            val page by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
            Scaffold(
              topBar = {
                TopAppBar(
                  title = {
                    Text(document.title(arabic), maxLines = 1, overflow = TextOverflow.Ellipsis)
                  },
                  navigationIcon = {
                    IconButton(onClick = ::finish) {
                      Icon(QuranIcons.ArrowBack, contentDescription = null)
                    }
                  },
                  actions = {
                    pages?.getOrNull()?.let {
                      Text(
                        stringResource(R.string.library_page_of, page.coerceAtMost(it.count), it.count),
                        style = MaterialTheme.typography.labelLarge
                      )
                    }
                    TextButton(onClick = { zoom = (zoom - ZOOM_STEP).coerceAtLeast(1f) }, enabled = zoom > 1f) {
                      Text("−", style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onClick = { zoom = (zoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM) }, enabled = zoom < MAX_ZOOM) {
                      Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                  }
                )
              },
              containerColor = MaterialTheme.colorScheme.surface
            ) { padding ->
              val modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
              val opened = pages
              when {
                opened == null -> Box(modifier, contentAlignment = Alignment.Center) {
                  CircularProgressIndicator()
                }
                opened.isFailure -> Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                  Text(stringResource(R.string.library_document_failed), textAlign = TextAlign.Center)
                }
                else -> PdfPagesColumn(
                  pages = opened.getOrThrow(),
                  listState = listState,
                  zoom = zoom,
                  contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                  modifier = modifier
                )
              }
            }
          }
        }
      }
    )
  }

  companion object {
    private const val EXTRA_DOCUMENT = "document"
    private const val EXTRA_PAGE = "page"
    private const val ZOOM_STEP = 0.5f
    private const val MAX_ZOOM = 3f

    /** [page] from 0 */
    fun intent(context: Context, documentId: String, page: Int = 0): Intent =
      Intent(context, DocumentActivity::class.java)
        .putExtra(EXTRA_DOCUMENT, documentId)
        .putExtra(EXTRA_PAGE, page)
  }
}
