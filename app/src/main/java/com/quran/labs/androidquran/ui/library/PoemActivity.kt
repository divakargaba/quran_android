package com.quran.labs.androidquran.ui.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.quran.mobile.feature.library.model.Recitation
import com.quran.mobile.feature.library.ui.DocumentRow
import com.quran.mobile.feature.library.ui.InlineBook
import com.quran.mobile.feature.library.ui.PdfPages
import com.quran.mobile.feature.library.ui.PoemPlayerBar
import com.quran.mobile.feature.library.ui.PoemRow
import com.quran.mobile.feature.library.ui.PoemVerses
import com.quran.mobile.feature.library.ui.rowOfChapter
import com.quran.mobile.feature.library.ui.rowOfVerse
import com.quran.mobile.feature.library.ui.rows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A poem of the qira'at or of tajwid: its text by chapters, with the translations and
 * explanations chosen under its verses; its recitation, which the text follows and which is
 * played from any verse; and its commentaries (documents, opened in [DocumentActivity]).
 */
class PoemActivity : AppCompatActivity() {
  private var player: PoemPlayer? = null
  private var closeBooks: () -> Unit = {}

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
    val preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    val arabicFont = FontFamily(TypefaceManager.getTafseerTypeface(this))
    val arabic = resources.configuration.locales.get(0)?.language == "ar"
    val poemPlayer = PoemPlayer(this).also { player = it }
    val documents = catalog.documentsOf(poem.documents)
    val recitationKey = KEY_RECITATION + poem.id
    val inlineKey = KEY_INLINE + poem.id
    val inlineDocuments = catalog.documentsOf(poem.inlineDocuments)
    // shown under the verses unless chosen otherwise: the first in the app's language that is here
    val inlineDefault = inlineDocuments
      .firstOrNull { (it.language == "ar") == arabic && repository.states.value[it.id] is DocumentState.Downloaded }
      ?.let { setOf(it.id) }
      .orEmpty()
    // the books open to be shown under the verses
    val openBooks = HashMap<String, InlineBook>()
    fun book(id: String): InlineBook? = synchronized(openBooks) {
      openBooks[id] ?: try {
        val document = catalog.document(id) ?: return null
        val file = repository.open(document) ?: return null
        // (only the very book whose pages were indexed)
        if (document.sizeBytes > 0 && file.length() != document.sizeBytes) return null
        InlineBook(document, PdfPages(file), LibraryCatalog.inlineIndex(this, id)).also { openBooks[id] = it }
      } catch (e: Exception) {
        null
      } catch (e: LinkageError) {
        null
      }
    }
    closeBooks = {
      synchronized(openBooks) {
        openBooks.values.forEach { it.pages.close() }
        openBooks.clear()
      }
    }

    setContentView(
      ComposeView(this).apply {
        setContent {
          QuranTheme {
            var text by remember { mutableStateOf<PoemText?>(null) }
            LaunchedEffect(poem.id) {
              text = withContext(Dispatchers.IO) { LibraryCatalog.poemText(this@PoemActivity, poem.id) }
            }
            val states by repository.states.collectAsState()
            var inlineIds by remember {
              mutableStateOf(preferences.getStringSet(inlineKey, null)?.toSet() ?: inlineDefault)
            }
            // those chosen that are here (a book chosen is shown once it is downloaded)
            val wanted = inlineDocuments.map { it.id }
              .filter { it in inlineIds && states[it] is DocumentState.Downloaded }
            val books by produceState(emptyList<InlineBook>(), wanted) {
              value = withContext(Dispatchers.IO) {
                synchronized(openBooks) {
                  (openBooks.keys - wanted.toSet()).forEach { openBooks.remove(it)?.pages?.close() }
                }
                wanted.mapNotNull(::book)
              }
            }
            val rows = remember(text, books) { text?.rows(books).orEmpty() }
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val playerState by poemPlayer.state.collectAsState()
            var showChapters by remember { mutableStateOf(false) }
            var showDocuments by remember { mutableStateOf(false) }
            var showReciters by remember { mutableStateOf(false) }

            var recitation by remember {
              val saved = preferences.getString(recitationKey, null)
              mutableStateOf(poem.recitations.firstOrNull { it.id == saved } ?: poem.recitations.firstOrNull())
            }
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(recitation) {
              ready = false
              val chosen = recitation ?: return@LaunchedEffect
              val timings = withContext(Dispatchers.IO) { LibraryCatalog.timings(this@PoemActivity, chosen) }
              poemPlayer.setRecitation(chosen, timings)
              ready = true
            }

            // the text follows the recitation: the verse being recited is kept in view
            val currentRows by rememberUpdatedState(rows)
            LaunchedEffect(Unit) {
              snapshotFlow { playerState.verse.takeIf { playerState.isPlaying } }.collect { verse ->
                val row = verse?.let { rowOfVerse(currentRows, it) } ?: -1
                if (row >= 0) {
                  // (the first item of the list is the poem's title)
                  val index = row + 1
                  val layout = listState.layoutInfo
                  val height = layout.viewportEndOffset - layout.viewportStartOffset
                  val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
                  val inView = item != null && item.offset >= 0 && item.offset + item.size <= height * 2 / 3
                  if (!inView) listState.animateScrollToItem(index, -height / 5)
                }
              }
            }

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
                recitation?.let { chosen ->
                  PoemPlayerBar(
                    recitation = chosen,
                    canChooseReciter = poem.recitations.size > 1,
                    state = playerState,
                    onChooseReciter = { showReciters = true },
                    onToggle = {
                      // from the first verse on screen, when nothing is being recited
                      val first = rows.getOrNull(listState.firstVisibleItemIndex - 1)
                      val verse = when (first) {
                        is PoemRow.Verse -> first.number
                        is PoemRow.Heading -> first.first
                        is PoemRow.Inline -> first.entry.last + 1
                        null -> 1
                      }
                      if (ready) poemPlayer.toggle(verse)
                    },
                    onPrevious = poemPlayer::previousVerse,
                    onNext = poemPlayer::nextVerse,
                    onStop = poemPlayer::stop,
                    modifier = Modifier.navigationBarsPadding()
                  )
                }
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
                  recitedVerse = playerState.verse,
                  arabicFont = arabicFont,
                  onPlayVerse = if (recitation != null) {
                    { verse -> if (ready) poemPlayer.playVerse(verse) }
                  } else {
                    null
                  },
                  onOpenBook = { document, page ->
                    startActivity(DocumentActivity.intent(this@PoemActivity, document.id, page))
                  },
                  onChooseInline = if (inlineDocuments.isNotEmpty() && inlineIds.none { id -> inlineDocuments.any { it.id == id } }) {
                    { showDocuments = true }
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
            if (showReciters) {
              AlertDialog(
                onDismissRequest = { showReciters = false },
                confirmButton = {
                  TextButton(onClick = { showReciters = false }) {
                    Text(stringResource(R.string.library_close))
                  }
                },
                title = { Text(stringResource(R.string.library_reciters)) },
                text = {
                  LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    items(poem.recitations) { item ->
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                          .fillMaxWidth()
                          .clickable {
                            showReciters = false
                            if (item.id != recitation?.id) {
                              preferences.edit().putString(recitationKey, item.id).apply()
                              recitation = item
                            }
                          }
                      ) {
                        RadioButton(selected = item.id == recitation?.id, onClick = null)
                        Text(item.reciter(arabic), modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
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
                    Text(stringResource(R.string.library_close))
                  }
                },
                title = { Text(stringResource(R.string.library_documents)) },
                text = {
                  if (documents.isEmpty()) {
                    Text(stringResource(R.string.library_no_documents))
                  } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                      if (inlineDocuments.isNotEmpty()) {
                        item {
                          Text(
                            stringResource(R.string.library_inline),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                          )
                        }
                        items(inlineDocuments) { item ->
                          val state = states[item.id] ?: DocumentState.NotDownloaded
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                              .fillMaxWidth()
                              .clickable {
                                val chosen = item.id !in inlineIds
                                inlineIds = if (chosen) inlineIds + item.id else inlineIds - item.id
                                preferences.edit().putStringSet(inlineKey, inlineIds).apply()
                                if (chosen && (state is DocumentState.NotDownloaded || state is DocumentState.Failed)) {
                                  repository.download(item)
                                }
                              }
                              .padding(vertical = 6.dp)
                          ) {
                            Checkbox(checked = item.id in inlineIds, onCheckedChange = null)
                            Column(Modifier.padding(start = 12.dp)) {
                              Text(item.title(arabic), style = MaterialTheme.typography.bodyLarge)
                              Text(
                                item.author(arabic),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                              )
                              val status = when (state) {
                                is DocumentState.Downloaded -> null
                                is DocumentState.Downloading -> stringResource(R.string.library_downloading) +
                                  (state.progress?.let { " ${(it * 100).toInt()}%" } ?: "")
                                is DocumentState.Failed -> stringResource(R.string.library_download_failed)
                                DocumentState.NotDownloaded -> stringResource(R.string.library_inline_hint)
                              }
                              if (status != null) {
                                Text(
                                  status,
                                  style = MaterialTheme.typography.labelSmall,
                                  color = if (state is DocumentState.Failed) {
                                    MaterialTheme.colorScheme.error
                                  } else {
                                    MaterialTheme.colorScheme.primary
                                  }
                                )
                              }
                            }
                          }
                        }
                      }
                      if (documents.isNotEmpty()) {
                        item {
                          Text(
                            stringResource(R.string.library_books),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                          )
                        }
                        items(documents) { document ->
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
    closeBooks()
    super.onDestroy()
  }

  companion object {
    private const val EXTRA_POEM = "poem"
    private const val PREFERENCES = "library"
    private const val KEY_RECITATION = "recitation_"
    private const val KEY_INLINE = "inline_"

    fun intent(context: Context, poemId: String): Intent =
      Intent(context, PoemActivity::class.java).putExtra(EXTRA_POEM, poemId)
  }
}
