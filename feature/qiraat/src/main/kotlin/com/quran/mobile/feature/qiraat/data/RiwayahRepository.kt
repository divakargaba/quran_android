package com.quran.mobile.feature.qiraat.data

import android.content.Context
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.model.Riwayah
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface DownloadState {
  data object NotDownloaded : DownloadState
  data class Downloading(val progress: Float) : DownloadState
  data object Downloaded : DownloadState
  data class Failed(val message: String?) : DownloadState
}

/**
 * Downloads riwayah mushafs on demand (one PDF of 4-15 MB per riwayah, only when the user asks
 * for it), and caches their ayah locations and renderers. Process wide, so downloads survive
 * configuration changes.
 */
class RiwayahRepository private constructor(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val directory = File(appContext.filesDir, "qiraat").apply { mkdirs() }
  private val client by lazy {
    OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .build()
  }

  private val states = MutableStateFlow(
    Qiraat.all.associate { riwayah ->
      riwayah.id to if (fileFor(riwayah).exists()) {
        DownloadState.Downloaded
      } else {
        DownloadState.NotDownloaded
      }
    }
  )
  val downloadStates: StateFlow<Map<String, DownloadState>> = states.asStateFlow()

  private val jobs = HashMap<String, Job>()
  private val locationsLock = Mutex()
  private val locations = HashMap<String, RiwayahLocations>()
  private val renderers = HashMap<String, PdfPageRenderer>()

  fun isDownloaded(riwayah: Riwayah) = states.value[riwayah.id] == DownloadState.Downloaded

  fun download(riwayah: Riwayah) {
    synchronized(jobs) {
      if (jobs[riwayah.id]?.isActive == true || isDownloaded(riwayah)) return
      jobs[riwayah.id] = scope.launch { performDownload(riwayah) }
    }
  }

  fun delete(riwayah: Riwayah) {
    synchronized(jobs) { jobs.remove(riwayah.id)?.cancel() }
    synchronized(renderers) { renderers.remove(riwayah.id)?.close() }
    fileFor(riwayah).delete()
    setState(riwayah, DownloadState.NotDownloaded)
  }

  /** the riwayah's ayah locations, or null if they could not be read */
  suspend fun locations(riwayah: Riwayah): RiwayahLocations? = withContext(Dispatchers.IO) {
    locationsLock.withLock {
      locations[riwayah.id] ?: try {
        RiwayahLocations.load(appContext, riwayah.locationsAsset).also { locations[riwayah.id] = it }
      } catch (e: Exception) {
        null
      }
    }
  }

  private val timings = HashMap<String, RiwayahTimings>()

  /** the riwayah recording's ayah timings, or null if there are none */
  suspend fun timings(riwayah: Riwayah): RiwayahTimings? = withContext(Dispatchers.IO) {
    val asset = riwayah.timingsAsset ?: return@withContext null
    locationsLock.withLock {
      timings[riwayah.id] ?: try {
        RiwayahTimings.load(appContext, asset).also { timings[riwayah.id] = it }
      } catch (e: Exception) {
        null
      }
    }
  }

  /** a renderer for a downloaded mushaf; a mushaf file that cannot be opened is removed */
  fun renderer(riwayah: Riwayah): PdfPageRenderer? {
    if (!isDownloaded(riwayah)) return null
    return synchronized(renderers) {
      renderers[riwayah.id] ?: try {
        PdfPageRenderer(fileFor(riwayah)).also { renderers[riwayah.id] = it }
      } catch (e: Exception) {
        fileFor(riwayah).delete()
        setState(riwayah, DownloadState.Failed(e.message))
        null
      }
    }
  }

  private fun fileFor(riwayah: Riwayah) = File(directory, "${riwayah.id}.pdf")

  private fun setState(riwayah: Riwayah, state: DownloadState) {
    states.update { it + (riwayah.id to state) }
  }

  private suspend fun performDownload(riwayah: Riwayah) {
    val target = fileFor(riwayah)
    val partial = File(directory, "${riwayah.id}.pdf.part")
    setState(riwayah, DownloadState.Downloading(0f))
    try {
      val request = Request.Builder().url(riwayah.pdfUrl).build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        val body = response.body
        val total = body.contentLength().takeIf { it > 0 } ?: riwayah.pdfSizeBytes
        val digest = MessageDigest.getInstance("MD5")
        var lastReported = 0f
        body.byteStream().use { input ->
          partial.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            var read = 0L
            while (true) {
              val count = input.read(buffer)
              if (count < 0) break
              currentCoroutineContext().ensureActive()
              output.write(buffer, 0, count)
              digest.update(buffer, 0, count)
              read += count
              val progress = (read.toFloat() / total).coerceAtMost(1f)
              if (progress - lastReported >= 0.01f) {
                lastReported = progress
                setState(riwayah, DownloadState.Downloading(progress))
              }
            }
          }
        }
        val md5 = digest.digest().joinToString("") { "%02x".format(it) }
        // the ayah locations were computed against this exact file
        if (md5 != riwayah.pdfMd5) error("Downloaded file does not match the expected mushaf")
      }
      if (!partial.renameTo(target)) error("Unable to save file")
      setState(riwayah, DownloadState.Downloaded)
    } catch (e: CancellationException) {
      partial.delete()
      throw e
    } catch (e: Exception) {
      partial.delete()
      setState(riwayah, DownloadState.Failed(e.message))
    } finally {
      synchronized(jobs) { jobs.remove(riwayah.id) }
    }
  }

  companion object {
    @Volatile
    private var instance: RiwayahRepository? = null

    fun get(context: Context): RiwayahRepository =
      instance ?: synchronized(this) {
        instance ?: RiwayahRepository(context).also { instance = it }
      }
  }
}
