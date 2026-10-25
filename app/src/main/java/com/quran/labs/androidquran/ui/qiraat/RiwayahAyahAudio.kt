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
 * The recording files the reader needs to recite one ayah in a riwayah: the sura's file for a
 * gapless recording, or the ayah's (and the basmalah's, when the ayah opens a sura) for an ayah
 * by ayah one, named as the reader's download service names them.
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

  /** whether every file the ayah needs is already downloaded */
  fun isDownloaded(verse: SuraAyah): Boolean = needed(verse).all { it.file.exists() }

  /**
   * downloads the files the ayah still needs, reporting the fraction done (or null while its
   * size is unknown); throws an [IOException] when one cannot be downloaded
   */
  suspend fun download(verse: SuraAyah, onProgress: (Float?) -> Unit) = withContext(Dispatchers.IO) {
    val missing = needed(verse).filterNot { it.file.exists() }
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

  private fun needed(verse: SuraAyah): List<Needed> {
    return if (qari.isGapless) {
      listOf(Needed(urlFormat.format(Locale.US, verse.sura), File(directory, "%03d.$extension".format(Locale.US, verse.sura))))
    } else {
      val ayah = Needed(
        urlFormat.format(Locale.US, verse.sura, verse.ayah),
        File(directory, "${verse.sura}${File.separator}${verse.ayah}.$extension")
      )
      if (verse.ayah == 1 && verse.sura != 1 && verse.sura != 9) {
        listOf(Needed(urlFormat.format(Locale.US, 1, 1), File(directory, "1${File.separator}1.$extension")), ayah)
      } else {
        listOf(ayah)
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
