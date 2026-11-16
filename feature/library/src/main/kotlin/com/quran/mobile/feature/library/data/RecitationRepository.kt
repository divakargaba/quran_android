package com.quran.mobile.feature.library.data

import android.content.Context
import android.util.Log
import com.quran.mobile.feature.library.model.LibraryCatalog
import com.quran.mobile.feature.library.model.Recitation
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** whether a recording is kept on the device */
sealed interface RecitationState {
  data object NotDownloaded : RecitationState
  data class Downloading(val progress: Float) : RecitationState
  data object Downloaded : RecitationState
  data object Failed : RecitationState
}

/**
 * The poems' recordings kept on the device, to be played without a connection: compact copies of
 * their tracks, downloaded when the reader asks. (A recording that is in the app is always here.)
 */
class RecitationRepository private constructor(context: Context) {
  private val appContext = context.applicationContext
  private val directory = File(appContext.filesDir, "library/audio")
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val jobs = HashMap<String, Job>()
  private val client by lazy {
    OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
  }

  private val _states = MutableStateFlow(
    LibraryCatalog.get(appContext).poems.flatMap { it.recitations }.associate { recitation ->
      recitation.id to if (isHere(recitation)) RecitationState.Downloaded else RecitationState.NotDownloaded
    }
  )
  val states: StateFlow<Map<String, RecitationState>> = _states.asStateFlow()

  /** the copy of a track kept here, if it is (whole) */
  fun file(recitation: Recitation, track: Int): File? {
    val expected = recitation.tracks.getOrNull(track)?.offlineBytes ?: return null
    val file = trackFile(recitation, track)
    return file.takeIf { expected > 0 && it.length() == expected }
  }

  fun download(recitation: Recitation) {
    if (!recitation.canDownload) return
    synchronized(jobs) {
      if (jobs[recitation.id]?.isActive == true) return
      set(recitation, RecitationState.Downloading(0f))
      jobs[recitation.id] = scope.launch {
        try {
          val total = recitation.offlineBytes.coerceAtLeast(1)
          var done = 0L
          recitation.tracks.forEachIndexed { index, track ->
            // (the tracks already here, of a download that was stopped, are kept)
            if (file(recitation, index) == null) {
              val url = track.offlineUrl ?: throw IOException("no copy of track $index")
              fetch(url, trackFile(recitation, index), track.offlineBytes) { read ->
                set(recitation, RecitationState.Downloading((done + read).toFloat() / total))
              }
            }
            done += track.offlineBytes
            set(recitation, RecitationState.Downloading(done.toFloat() / total))
          }
          set(recitation, RecitationState.Downloaded)
        } catch (e: CancellationException) {
          set(recitation, RecitationState.NotDownloaded)
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "could not download ${recitation.id}", e)
          set(recitation, RecitationState.Failed)
        }
      }
    }
  }

  fun cancel(recitation: Recitation) {
    synchronized(jobs) { jobs.remove(recitation.id)?.cancel() }
  }

  /** removes a downloaded recording (one in the app stays) */
  fun delete(recitation: Recitation) {
    cancel(recitation)
    if (recitation.isBundled) return
    File(directory, recitation.id).deleteRecursively()
    set(recitation, RecitationState.NotDownloaded)
  }

  private fun isHere(recitation: Recitation): Boolean =
    recitation.isBundled || recitation.canDownload && recitation.tracks.indices.all { file(recitation, it) != null }

  private fun trackFile(recitation: Recitation, track: Int) =
    File(File(directory, recitation.id), LibraryCatalog.offlineName(track))

  private fun set(recitation: Recitation, state: RecitationState) {
    _states.update { it + (recitation.id to state) }
  }

  private suspend fun fetch(url: String, destination: File, expected: Long, onProgress: (Long) -> Unit) {
    destination.parentFile?.mkdirs()
    val partial = File(destination.parentFile, destination.name + ".part")
    var last: Exception? = null
    repeat(ATTEMPTS) { attempt ->
      try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
          if (!response.isSuccessful) throw IOException("${response.code}")
          val body = response.body ?: throw IOException("no body")
          body.byteStream().use { input ->
            partial.outputStream().use { output ->
              val buffer = ByteArray(64 * 1024)
              var done = 0L
              while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                done += read
                onProgress(done)
                coroutineContext.ensureActive()
              }
            }
          }
        }
        // the very copy the verses' times are of
        if (partial.length() != expected) throw IOException("${partial.length()} bytes of $expected")
        if (!partial.renameTo(destination)) throw IOException("could not save")
        return
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
    private const val TAG = "RecitationRepository"
    private const val ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 1500L

    @Volatile private var instance: RecitationRepository? = null

    fun get(context: Context): RecitationRepository =
      instance ?: synchronized(this) {
        instance ?: RecitationRepository(context).also { instance = it }
      }
  }
}
