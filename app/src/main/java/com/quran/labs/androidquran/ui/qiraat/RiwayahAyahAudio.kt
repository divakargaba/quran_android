package com.quran.labs.androidquran.ui.qiraat

import android.content.Context
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.mobile.feature.qiraat.audio.NquranAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * The recording files the reader needs to recite some ayat of a sura in a riwayah: the sura's
 * file for a gapless recording, or the ayat's (and the basmalah's, when they open a sura) for an
 * ayah by ayah one, named as the reader's download service names them.
 */
class RiwayahAyahAudio(
  private val context: Context,
  private val client: OkHttpClient,
  private val qari: QariItem,
  private val directory: String,
  private val urlFormat: String,
  private val extension: String
) {

  private class Needed(val url: String, val file: File)

  /** whether every file the ayat need is already downloaded */
  fun isDownloaded(start: SuraAyah, end: SuraAyah): Boolean = needed(start, end).all { it.file.exists() }

  /**
   * downloads the files the ayat (of one sura) still need, reporting the fraction done (or null
   * while its size is unknown); throws an [IOException] when one cannot be downloaded
   */
  suspend fun download(start: SuraAyah, end: SuraAyah, onProgress: (Float?) -> Unit) = withContext(Dispatchers.IO) {
    val missing = needed(start, end).filterNot { it.file.exists() }
    missing.forEachIndexed { index, needed ->
      needed.file.parentFile?.mkdirs()
      if (NquranAudio.handles(needed.url)) {
        onProgress(null)
        if (!NquranAudio.download(context, client, needed.url, needed.file)) {
          throw IOException("could not download ${needed.url}")
        }
      } else {
        fetch(needed) { fraction ->
          onProgress(fraction?.let { (index + it) / missing.size })
        }
      }
      coroutineContext.ensureActive()
    }
  }

  private fun needed(start: SuraAyah, end: SuraAyah): List<Needed> {
    val sura = start.sura
    return if (qari.isGapless) {
      listOf(Needed(urlFormat.format(Locale.US, sura), File(directory, "%03d.$extension".format(Locale.US, sura))))
    } else {
      val ayat = (start.ayah..end.ayah).map { ayah ->
        Needed(
          urlFormat.format(Locale.US, sura, ayah),
          File(directory, "$sura${File.separator}$ayah.$extension")
        )
      }
      if (start.ayah == 1 && sura != 1 && sura != 9) {
        listOf(Needed(urlFormat.format(Locale.US, 1, 1), File(directory, "1${File.separator}1.$extension"))) + ayat
      } else {
        ayat
      }
    }
  }

  private suspend fun fetch(needed: Needed, onProgress: (Float?) -> Unit) {
    val partial = File(needed.file.parentFile, needed.file.name + ".part")
    try {
      client.newCall(Request.Builder().url(needed.url).build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("${response.code} for ${needed.url}")
        val body = response.body ?: throw IOException("no body for ${needed.url}")
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
      if (!partial.renameTo(needed.file)) throw IOException("could not save ${needed.file}")
    } finally {
      partial.delete()
    }
  }
}
