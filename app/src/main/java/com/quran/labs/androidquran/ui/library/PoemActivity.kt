package com.quran.labs.androidquran.ui.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.audio.PoemPlayer
import com.quran.mobile.feature.library.data.DocumentRepository
import com.quran.mobile.feature.library.data.DocumentState
import com.quran.mobile.feature.library.model.LibraryCatalog
import com.quran.mobile.feature.library.model.PoemText
import com.quran.mobile.feature.library.ui.DocumentRow
import com.quran.mobile.feature.library.ui.PoemPlayerBar
import com.quran.mobile.feature.library.ui.PoemVerses
import com.quran.mobile.feature.library.ui.rowOfChapter
import com.quran.mobile.feature.library.ui.rows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A poem of the qira'at or of tajwid: its text by chapters, its recitation (streamed), and its
 * translations and commentaries (documents, opened in [DocumentActivity]).
 */
class PoemActivity : AppCompatActivity() {
  private var player: PoemPlayer? = null

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportActionBar?.hide()
    val catalog = LibraryCatalog.get(this)
    val poem = catalog.poem(intent.getStringExtra(EXTRA_POEM).orEmpty())
    if (poem == null) {
      finish()
      return
    }
    val repository = DocumentRepository.get(this)
    val arabicFont = FontFamily(TypefaceManager.getTafseerTypeface(this))
    val arabic = resources.configuration.locales.get(0)?.language == "ar"
    val poemPlayer = PoemPlayer(poem.audio?.tracks.orEmpty()).also { player = it }
    val documents = catalog.documentsOf(poem.documents)
    val verseDocument = poem.verseDocument?.let(catalog::document)

    setContentView(
      ComposeView(this).apply {
        setContent {
          QuranTheme {
            var text by remember { mutableStateOf<PoemText?>(null) }
            LaunchedEffect(poem.id) {
              text = withContext(Dispatchers.IO) { LibraryCatalog.poemText(this@PoemActivity, poem.id) }
            }
            val rows = remember(text) { text?.rows().orEmpty() }
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val playerState by poemPlayer.state.collectAsState()
            val states by repository.states.collectAsState()
            var showChapters by remember { mutableStateOf(false) }
            var showDocuments by remember { mutableStateOf(false) }

            Scaffold(
              topBar = {
                TopAppBar(
                  title = { Text(poem.title(arabic), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                  navigationIcon = {
                    IconButton(onClick = ::finish) {
                      Icon(QuranIcons.ArrowBack, contentDescription = null)
                    }
                  },
                  actions = {
                    IconButton(onClick = { showChapters = true }) {
                      Icon(QuranIcons.Notes, contentDescription = stringResource(R.string.library_chapters))
                    }
                    IconButton(onClick = { showDocuments = true }) {
                      Icon(QuranIcons.MenuBook, contentDescription = stringResource(R.string.library_documents))
                    }
                  }
                )
              },
              bottomBar = {
                PoemPlayerBar(
                  poem = poem,
                  text = text,
                  state = playerState,
                  onToggle = { poemPlayer.toggle() },
                  onPrevious = poemPlayer::previous,
                  onNext = poemPlayer::next,
                  onSeek = poemPlayer::seekTo,
                  modifier = Modifier.navigationBarsPadding()
                )
              },
              containerColor = MaterialTheme.colorScheme.surface
            ) { padding ->
              val loaded = text
              if (loaded == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator()
                }
              } else {
                PoemVerses(
                  poem = poem,
                  rows = rows,
                  listState = listState,
                  playingChapters = playerState.track
                    ?.let { poem.audio?.tracks?.getOrNull(it)?.chapters }
                    .orEmpty(),
                  arabicFont = arabicFont,
                  onPlayChapter = { chapter ->
                    poem.audio?.trackOfChapter(chapter)?.let(poemPlayer::play)
                  },
                  onVerse = if (verseDocument != null && poem.versePages.isNotEmpty()) {
                    { number ->
                      val page = poem.versePages.getOrElse(number - 1) { 0 }
                      startActivity(DocumentActivity.intent(this@PoemActivity, verseDocument.id, page))
                    }
                  } else {
                    null
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
                  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                      itemsIndexed(loaded.chapters) { index, chapter ->
                        Text(
                          chapter.title.ifEmpty { poem.titleArabic },
                          fontFamily = arabicFont,
                          fontSize = 18.sp,
                          lineHeight = 32.sp,
                          modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                              showChapters = false
                              // (the first item of the list is the poem's title)
                              scope.launch { listState.scrollToItem(loaded.rowOfChapter(rows, index) + 1) }
                            }
                            .padding(vertical = 6.dp)
                        )
                      }
                    }
                  }
                }
              )
            }
            if (showDocuments) {
              AlertDialog(
                onDismissRequest = { showDocuments = false },
                confirmButton = {
                  TextButton(onClick = { showDocuments = false }) {
                    Text(stringResource(R.string.library_cancel))
                  }
                },
                title = { Text(stringResource(R.string.library_documents)) },
                text = {
                  if (documents.isEmpty()) {
                    Text(stringResource(R.string.library_no_documents))
                  } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                      itemsIndexed(documents) { _, document ->
                        Column {
                          DocumentRow(
                            document = document,
                            state = states[document.id] ?: DocumentState.NotDownloaded,
                            onOpen = {
                              startActivity(DocumentActivity.intent(this@PoemActivity, document.id))
                            },
                            onDownload = { repository.download(document) },
                            onCancel = { repository.cancel(document) },
                            onDelete = { repository.delete(document) }
                          )
                        }
                      }
                    }
                  }
                }
              )
            }
          }
        }
      }
    )
  }

  override fun onDestroy() {
    player?.release()
    player = null
    super.onDestroy()
  }

  companion object {
    private const val EXTRA_POEM = "poem"

    fun intent(context: Context, poemId: String): Intent =
      Intent(context, PoemActivity::class.java).putExtra(EXTRA_POEM, poemId)
  }
}
