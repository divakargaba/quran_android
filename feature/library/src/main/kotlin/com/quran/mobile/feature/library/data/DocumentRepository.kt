package com.quran.mobile.feature.library.data

import android.content.Context
import android.util.Log
import com.quran.mobile.feature.library.model.Document
import com.quran.mobile.feature.library.model.LibraryCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed interface DocumentState {
  data object NotDownloaded : DocumentState
  data class Downloading(val progress: Float?) : DocumentState
  data object Downloaded : DocumentState
  data class Failed(val message: String?) : DocumentState
}

/**
 * The documents of the library as files: the bundled ones are copied out of the app's assets
 * when first opened (a pdf is read from a file), the others are downloaded from where they are
 * published, when the reader asks for them.
 */
class DocumentRepository private constructor(context: Context) {
  private val appContext = context.applicationContext
  private val directory = File(appContext.filesDir, "library").apply { mkdirs() }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val jobs = HashMap<String, Job>()
  private val client by lazy {
    OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
  }

  private val _states = MutableStateFlow(
    LibraryCatalog.get(appContext).documents.associate { document ->
      document.id to if (document.isBundled || file(document).exists()) {
        DocumentState.Downloaded
      } else {
        DocumentState.NotDownloaded
      }
    }
  )
  val states: StateFlow<Map<String, DocumentState>> = _states.asStateFlow()

  private fun file(document: Document) = File(directory, "${document.id}.pdf")

  /** the document's file, if it is here; call off the main thread (a bundled one is copied) */
  fun open(document: Document): File? {
    val file = file(document)
    if (file.exists()) return file
    val asset = document.asset ?: return null
    val partial = File(directory, file.name + ".part")
    appContext.assets.open(asset).use { input -> partial.outputStream().use { input.copyTo(it) } }
    return if (partial.renameTo(file)) file else null
  }

  fun download(document: Document) {
    val url = document.url ?: return
    synchronized(jobs) {
      if (jobs[document.id]?.isActive == true) return
      set(document, DocumentState.Downloading(null))
      jobs[document.id] = scope.launch {
        try {
          fetch(url, file(document)) { set(document, DocumentState.Downloading(it)) }
          set(document, DocumentState.Downloaded)
        } catch (e: CancellationException) {
          set(document, DocumentState.NotDownloaded)
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "could not download ${document.id}", e)
          set(document, DocumentState.Failed(e.message))
        }
      }
    }
  }

  fun cancel(document: Document) {
    synchronized(jobs) { jobs.remove(document.id)?.cancel() }
  }

  /** removes a downloaded document (a bundled one stays) */
  fun delete(document: Document) {
    cancel(document)
    if (document.isBundled) return
    file(document).delete()
    set(document, DocumentState.NotDownloaded)
  }

  private fun set(document: Document, state: DocumentState) {
    _states.update { it + (document.id to state) }
  }

  private suspend fun fetch(url: String, destination: File, onProgress: (Float?) -> Unit) =
    withContext(Dispatchers.IO) {
      val partial = File(destination.parentFile, destination.name + ".part")
      var last: Exception? = null
      repeat(ATTEMPTS) { attempt ->
        try {
          client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("${response.code}")
            val body = response.body ?: throw IOException("no body")
            val total = body.contentLength()
            body.byteStream().use { input ->
              partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                  val read = input.read(buffer)
                  if (read < 0) break
                  output.write(buffer, 0, read)
                  done += read
                  onProgress(if (total > 0) done.toFloat() / total else null)
                  coroutineContext.ensureActive()
                }
              }
            }
          }
          // a pdf, not an error page
          val header = ByteArray(5)
          partial.inputStream().use { it.read(header) }
          if (!header.contentEquals(PDF_HEADER)) throw IOException("not a pdf")
          if (!partial.renameTo(destination)) throw IOException("could not save")
          return@withContext
        } catch (e: CancellationException) {
          partial.delete()
          throw e
        } catch (e: Exception) {
          partial.delete()
          last = e
          delay(RETRY_DELAY_MS * (attempt + 1))
        }
      }
      throw last ?: IOException("download failed")
    }

  companion object {
    private const val TAG = "DocumentRepository"
    private const val ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 1500L
    private val PDF_HEADER = "%PDF-".toByteArray()

    @Volatile private var instance: DocumentRepository? = null

    fun get(context: Context): DocumentRepository =
      instance ?: synchronized(this) {
        instance ?: DocumentRepository(context).also { instance = it }
      }
  }
}
