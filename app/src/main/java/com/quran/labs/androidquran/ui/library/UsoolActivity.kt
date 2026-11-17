package com.quran.labs.androidquran.ui.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.data.DocumentRepository
import com.quran.mobile.feature.library.data.DocumentState
import com.quran.mobile.feature.library.model.LibraryCatalog
import com.quran.mobile.feature.library.model.UsoolText
import com.quran.mobile.feature.library.ui.DocumentRow
import com.quran.mobile.feature.library.ui.UsoolChapters
import com.quran.mobile.feature.library.ui.rowOfUsoolChapter
import com.quran.mobile.feature.library.ui.rows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The principles (usul) of a qira'ah: its rules as text, chapter by chapter (with the chapters
 * to go to), and the books on them (documents, opened in [DocumentActivity]). For the qira'at
 * together, the books alone.
 */
class UsoolActivity : AppCompatActivity() {

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportActionBar?.hide()
    // (the screen stays on while this is read, as while the mushaf is)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    val catalog = LibraryCatalog.get(this)
    val group = catalog.usool.getOrNull(intent.getIntExtra(EXTRA_GROUP, -1))
    if (group == null) {
      finish()
      return
    }
    val repository = DocumentRepository.get(this)
    val arabicFont = FontFamily(TypefaceManager.getTafseerTypeface(this))
    val arabic = resources.configuration.locales.get(0)?.language == "ar"
    val documents = catalog.documentsOf(group.documents)
    val textId = group.id

    setContentView(
      ComposeView(this).apply {
        setContent {
          LibraryTheme {
            var text by remember { mutableStateOf<UsoolText?>(null) }
            LaunchedEffect(textId) {
              if (textId != null) {
                text = withContext(Dispatchers.IO) { LibraryCatalog.usoolText(this@UsoolActivity, textId) }
              }
            }
            val rows = remember(text) { text?.rows(catalog::document).orEmpty() }
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val states by repository.states.collectAsState()
            var showChapters by remember { mutableStateOf(false) }
            var showBooks by remember { mutableStateOf(false) }

            @androidx.compose.runtime.Composable
            fun Books(modifier: Modifier = Modifier) {
              LazyColumn(modifier) {
                items(documents) { document ->
                  DocumentRow(
                    document = document,
                    state = states[document.id] ?: DocumentState.NotDownloaded,
                    onOpen = { startActivity(DocumentActivity.intent(this@UsoolActivity, document.id)) },
                    onDownload = { repository.download(document) },
                    onCancel = { repository.cancel(document) },
                    onDelete = { repository.delete(document) }
                  )
                }
              }
            }

            Scaffold(
              topBar = {
                TopAppBar(
                  title = { Text(group.title(arabic), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                  navigationIcon = {
                    IconButton(onClick = ::finish) {
                      Icon(QuranIcons.ArrowBack, contentDescription = null)
                    }
                  },
                  actions = {
                    if (textId != null) {
                      IconButton(onClick = { showChapters = true }) {
                        Icon(QuranIcons.Notes, contentDescription = stringResource(R.string.library_chapters))
                      }
                      IconButton(onClick = { showBooks = true }) {
                        Icon(QuranIcons.MenuBook, contentDescription = stringResource(R.string.library_books))
                      }
                    }
                  }
                )
              },
              containerColor = MaterialTheme.colorScheme.surface
            ) { padding ->
              val loaded = text
              when {
                textId == null -> Books(Modifier.fillMaxSize().padding(padding))
                loaded == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator()
                }
                else -> UsoolChapters(
                  rows = rows,
                  listState = listState,
                  arabicFont = arabicFont,
                  onOpenBook = { document, page ->
                    if (states[document.id] is DocumentState.Downloaded) {
                      startActivity(DocumentActivity.intent(this@UsoolActivity, document.id, page))
                    } else {
                      showBooks = true
                    }
                  },
                  modifier = Modifier.padding(padding)
                )
              }
            }

            val loaded = text
            if (showChapters && loaded != null) {
              AlertDialog(
                onDismissRequest = { showChapters = false },
                confirmButton = {
                  TextButton(onClick = { showChapters = false }) {
                    Text(stringResource(R.string.library_cancel))
                  }
                },
                title = { Text(stringResource(R.string.library_chapters)) },
                text = {
                  LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    itemsIndexed(loaded.chapters) { index, chapter ->
                      Text(
                        chapter.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                          .fillMaxWidth()
                          .clickable {
                            showChapters = false
                            scope.launch { listState.scrollToItem(rowOfUsoolChapter(rows, index)) }
                          }
                          .padding(vertical = 10.dp)
                      )
                    }
                  }
                }
              )
            }
            if (showBooks) {
              AlertDialog(
                onDismissRequest = { showBooks = false },
                confirmButton = {
                  TextButton(onClick = { showBooks = false }) {
                    Text(stringResource(R.string.library_close))
                  }
                },
                title = { Text(stringResource(R.string.library_books)) },
                text = { Books(Modifier.fillMaxWidth().heightIn(max = 460.dp)) }
              )
            }
          }
        }
      }
    )
  }

  companion object {
    private const val EXTRA_GROUP = "group"

    fun intent(context: Context, group: Int): Intent =
      Intent(context, UsoolActivity::class.java).putExtra(EXTRA_GROUP, group)
  }
}
