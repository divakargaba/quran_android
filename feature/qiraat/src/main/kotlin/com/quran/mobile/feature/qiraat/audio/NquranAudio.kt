package com.quran.mobile.feature.qiraat.audio

import android.content.Context
import android.util.Base64
import com.quran.mobile.feature.qiraat.data.AyahIndex
import com.quran.mobile.feature.qiraat.data.RiwayahLocations
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.model.Riwayah
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Ayah by ayah recordings from nquran.com, for the riwayat that have no complete recording
 * elsewhere. nquran numbers a riwayah's ayat by its own count, while the reader asks for Hafs
 * ayat; a Hafs ayah is given the riwayah ayat that end in it (one, several joined, or, when the
 * riwayah reads it as part of the next one, a moment of silence).
 *
 * The reader downloads these like any ayah by ayah recording, from urls of the form
 * "qiraat-nquran://<riwayah>/<sura><ayah>.mp3" ([baseUrl] and the sura and ayah as 3 digits
 * each), which its download service hands to [download].
 */
object NquranAudio {
  private const val SCHEME = "qiraat-nquran://"
  private const val AUDIO_URL = "https://www.nquran.com/ar/quranplayer/readaudio.php?mp3="
  private const val SILENCE_ASSET = "qiraat/silence.mp3"

  private val FOLDERS = mapOf(
    "hisham" to "hesham_amer/msaltani",
    "khallad" to "khallad/msaltani",
    "ibnwardan" to "ibnwartan/msaltani",
    "ibnjammaz" to "ibnjammaz/msaltani",
    "ishaq" to "eshaq_khalaf/msaltani",
    "idris" to "edris_khalaf/msaltani"
  )

  /** suras where nquran counts the riwayah's ayat differently from its mushaf here */
  private val COUNTS = mapOf(
    "hisham" to mapOf(83 to 36, 114 to 6),
    "ibnwardan" to mapOf(32 to 31, 101 to 10, 102 to 8, 106 to 5, 107 to 6),
    "ibnjammaz" to mapOf(4 to 176, 32 to 31, 101 to 10, 102 to 8, 106 to 5, 107 to 6)
  )

  fun has(riwayah: Riwayah) = riwayah.id in FOLDERS

  fun baseUrl(riwayah: Riwayah) = "$SCHEME${riwayah.id}/"

  fun handles(url: String) = url.startsWith(SCHEME)

  /**
   * downloads the recording of the Hafs ayah that [url] names to [destination]; false if it
   * could not be downloaded
   */
  fun download(context: Context, client: OkHttpClient, url: String, destination: File): Boolean {
    val match = URL_PATTERN.matchEntire(url) ?: return false
    val riwayah = Qiraat.byId(match.groupValues[1]) ?: return false
    val folder = FOLDERS[riwayah.id] ?: return false
    val sura = match.groupValues[2].toInt()
    val ayah = match.groupValues[3].toInt()
    if (sura !in 1..114 || ayah !in 1..AyahIndex.ayahCount(sura)) return false
    val locations = runBlocking { RiwayahRepository.get(context).locations(riwayah) } ?: return false

    val partial = File(destination.parentFile, destination.name + ".part")
    return try {
      partial.outputStream().buffered().use { out ->
        val files = filesFor(riwayah, locations, sura, ayah)
        if (files.isEmpty()) {
          context.assets.open(SILENCE_ASSET).use { it.copyTo(out) }
        } else {
          files.forEach { number -> copyAudio(client, folder, sura, number, out) }
        }
      }
      partial.renameTo(destination)
    } catch (e: IOException) {
      partial.delete()
      false
    }
  }

  /** the riwayah's ayat (as nquran numbers them) to play for a Hafs ayah */
  internal fun filesFor(riwayah: Riwayah, locations: RiwayahLocations, sura: Int, ayah: Int): List<Int> {
    val number = locations.riwayahAyahNumber(sura, ayah)
    val previous = if (ayah > 1) locations.riwayahAyahNumber(sura, ayah - 1) else 0
    if (sura == 1 && ayah <= 2 && locations.riwayahAyahNumber(1, 1) == locations.riwayahAyahNumber(1, 2)) {
      // outside the Kufi count, the basmalah of al-Fatiha is not an ayah: nquran's ayah 0
      return if (ayah == 1) listOf(0) else (1..number).toList()
    }
    // where nquran counts the sura's ayat differently, its numbers are taken in proportion
    val ours = locations.riwayahAyahNumber(sura, AyahIndex.ayahCount(sura))
    val theirs = COUNTS[riwayah.id]?.get(sura) ?: ours
    fun theirNumber(n: Int) = if (theirs == ours) n else (n * theirs / ours.toFloat()).roundToInt()
    return ((theirNumber(previous) + 1)..theirNumber(number)).toList()
  }

  private fun copyAudio(client: OkHttpClient, folder: String, sura: Int, number: Int, out: OutputStream) {
    val path = "moshaf/$folder/%03d/%03d.mp3".format(sura, number)
    val key = "s:${path.length}:\"$path\";|_*7H_"
    val request = Request.Builder()
      .url(AUDIO_URL + Base64.encodeToString(key.toByteArray(), Base64.NO_WRAP))
      .header("User-Agent", "Mozilla/5.0")
      .build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("nquran: ${response.code} for $path")
      val bytes = response.body?.bytes() ?: throw IOException("nquran: no audio for $path")
      if (bytes.size < 512) throw IOException("nquran: no audio for $path")
      val (start, end) = audioFrames(bytes)
      out.write(bytes, start, end - start)
    }
  }

  /** the part of an mp3 file between its ID3 tags, so that files can be joined */
  internal fun audioFrames(bytes: ByteArray): Pair<Int, Int> {
    var start = 0
    if (bytes.size > 10 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() &&
      bytes[2] == '3'.code.toByte()
    ) {
      val size = (bytes[6].toInt() and 0x7f shl 21) or (bytes[7].toInt() and 0x7f shl 14) or
        (bytes[8].toInt() and 0x7f shl 7) or (bytes[9].toInt() and 0x7f)
      val footer = if (bytes[5].toInt() and 0x10 != 0) 10 else 0
      start = (10 + size + footer).coerceAtMost(bytes.size)
    }
    var end = bytes.size
    if (end - 128 >= start && bytes[end - 128] == 'T'.code.toByte() &&
      bytes[end - 127] == 'A'.code.toByte() && bytes[end - 126] == 'G'.code.toByte()
    ) {
      end -= 128
    }
    return start to end
  }

  private val URL_PATTERN = Regex("""qiraat-nquran://([a-z_]+)/(\d{3})(\d{3})\.mp3""")
}
