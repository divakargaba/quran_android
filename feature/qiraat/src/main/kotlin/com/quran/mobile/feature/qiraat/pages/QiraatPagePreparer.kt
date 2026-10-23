package com.quran.mobile.feature.qiraat.pages

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import com.quran.mobile.feature.qiraat.data.AyahIndex
import com.quran.mobile.feature.qiraat.data.DownloadState
import com.quran.mobile.feature.qiraat.data.RiwayahLocations
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.data.RiwayahTimings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Prepares a riwayah mushaf for the main reader on the device: downloads its PDF (once), renders
 * the text block of each page to the image files the reader loads, and writes the ayah position
 * database (for highlighting and tapping ayat) and the audio timing database (for playing from an
 * ayah and following the recitation).
 */
class QiraatPagePreparer(context: Context) {
  private val repository = RiwayahRepository.get(context)

  sealed interface Progress {
    data class Downloading(val fraction: Float) : Progress
    data class Rendering(val page: Int, val pages: Int) : Progress
  }

  /**
   * @param widthParams the reader's image width parameters (such as "_1260") to render for
   * @param imagesDirectory where the page images for a width parameter go
   * @param ayahInfoFile the ayah position database for a width parameter
   * @param timingDatabase the gapless timing database of the page type's recording
   */
  suspend fun prepare(
    provider: QiraatPageProvider,
    widthParams: List<String>,
    imagesDirectory: (String) -> File,
    ayahInfoFile: (String) -> File,
    timingDatabase: File,
    onProgress: (Progress) -> Unit
  ) = withContext(Dispatchers.IO) {
    val riwayah = provider.riwayah
    if (!repository.isDownloaded(riwayah)) {
      repository.download(riwayah)
      val result = repository.downloadStates.first { states ->
        when (val state = states[riwayah.id]) {
          is DownloadState.Downloading -> {
            onProgress(Progress.Downloading(state.progress)); false
          }
          DownloadState.Downloaded, is DownloadState.Failed -> true
          else -> false
        }
      }[riwayah.id]
      if (result is DownloadState.Failed) throw IOException(result.message)
    }
    val renderer = repository.renderer(riwayah) ?: throw IOException("Unable to open the mushaf")
    val locations = repository.locations(riwayah) ?: throw IOException("Missing ayah locations")
    val bounds = RiwayahPages.textBounds.getValue(riwayah.id)
      .let { RectF(it[0], it[1], it[2], it[3]) }

    val widths = widthParams.distinct()
    val totalPages = PAGES * widths.size
    widths.forEachIndexed { widthIndex, widthParam ->
      val width = widthParam.filter { it.isDigit() }.toInt()
      val directory = imagesDirectory(widthParam).apply { mkdirs() }
      File(directory, ".nomedia").createNewFile()
      val sizes = HashMap<Int, Pair<Int, Int>>()
      for (page in 1..PAGES) {
        coroutineContext.ensureActive()
        onProgress(Progress.Rendering(widthIndex * PAGES + page, totalPages))
        val file = File(directory, pageFileName(page))
        if (file.exists()) {
          val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeFile(file.absolutePath, options)
          if (options.outWidth > 0) {
            sizes[page] = options.outWidth to options.outHeight
            continue
          }
        }
        val bitmap = renderer.renderUncached(page, bounds, width, transparent = true)
          ?: throw IOException("Unable to render page $page")
        sizes[page] = bitmap.width to bitmap.height
        writeImage(bitmap, file)
        bitmap.recycle()
      }
      writeAyahInfo(ayahInfoFile(widthParam), locations, bounds, sizes)
    }
    writeTimings(provider, timingDatabase)
  }

  private fun writeImage(bitmap: Bitmap, file: File) {
    // the reader decodes any image format; the name stays page###.png
    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Bitmap.CompressFormat.WEBP_LOSSLESS
    } else {
      Bitmap.CompressFormat.PNG
    }
    val partial = File(file.parentFile, file.name + ".part")
    partial.outputStream().use { bitmap.compress(format, 100, it) }
    if (!partial.renameTo(file)) throw IOException("Unable to save ${file.name}")
  }

  /** one "glyph" per line of each ayah, in the coordinates of its page image */
  internal fun writeAyahInfo(
    file: File,
    locations: RiwayahLocations,
    bounds: RectF,
    sizes: Map<Int, Pair<Int, Int>>
  ) {
    file.parentFile?.mkdirs()
    val partial = File(file.parentFile, file.name + ".part").apply { delete() }
    SQLiteDatabase.openOrCreateDatabase(partial, null).use { db ->
      db.execSQL(
        "CREATE TABLE glyphs(glyph_id INTEGER PRIMARY KEY, page_number INTEGER NOT NULL, " +
          "line_number INTEGER NOT NULL, sura_number INTEGER NOT NULL, " +
          "ayah_number INTEGER NOT NULL, position INTEGER NOT NULL, min_x INTEGER NOT NULL, " +
          "max_x INTEGER NOT NULL, min_y INTEGER NOT NULL, max_y INTEGER NOT NULL)"
      )
      db.execSQL("CREATE INDEX glyphs_page ON glyphs(page_number)")
      db.beginTransaction()
      try {
        // line numbers: the distinct line positions on each page, top to bottom
        val lineTops = HashMap<Int, MutableList<Float>>()
        forEachRect(locations) { _, _, page, rect, _ ->
          val lines = lineTops.getOrPut(page) { mutableListOf() }
          if (lines.none { kotlin.math.abs(it - rect.centerY()) < LINE_TOLERANCE }) {
            lines.add(rect.centerY())
          }
        }
        lineTops.values.forEach { it.sort() }
        val values = ContentValues()
        forEachRect(locations) { sura, ayah, page, rect, position ->
          val (width, height) = sizes[page] ?: return@forEachRect
          val line = lineTops.getValue(page)
            .indexOfFirst { kotlin.math.abs(it - rect.centerY()) < LINE_TOLERANCE } + 1
          fun x(value: Float) = ((value - bounds.left) / bounds.width() * width).roundToInt()
            .coerceIn(0, width)
          fun y(value: Float) = ((value - bounds.top) / bounds.height() * height).roundToInt()
            .coerceIn(0, height)
          values.clear()
          values.put("page_number", page)
          values.put("line_number", line)
          values.put("sura_number", sura)
          values.put("ayah_number", ayah)
          values.put("position", position)
          values.put("min_x", x(rect.left))
          values.put("max_x", x(rect.right))
          values.put("min_y", y(rect.top))
          values.put("max_y", y(rect.bottom))
          db.insert("glyphs", null, values)
        }
        db.setTransactionSuccessful()
      } finally {
        db.endTransaction()
      }
    }
    if (!partial.renameTo(file)) throw IOException("Unable to save ${file.name}")
  }

  private inline fun forEachRect(
    locations: RiwayahLocations,
    block: (sura: Int, ayah: Int, page: Int, rect: RectF, position: Int) -> Unit
  ) {
    for (index in 0 until AyahIndex.AYAH_COUNT) {
      val (sura, ayah) = AyahIndex.suraAyahOf(index)
      var position = 0
      locations.rectsFor(sura, ayah).forEach { (page, rects) ->
        rects.forEach { rect -> block(sura, ayah, page, rect, ++position) }
      }
    }
  }

  /**
   * The gapless timing database of the recording: the start of each ayah in its sura file, and
   * (as ayah 999) the end of the last. Ayat that the riwayah reads as one share its time, which
   * is split between them by the length of their text so that each is highlighted in turn.
   */
  internal suspend fun writeTimings(provider: QiraatPageProvider, file: File) {
    val audioRiwayah = provider.audioRiwayah
    val timings: RiwayahTimings? = repository.timings(audioRiwayah)
    val locations = repository.locations(audioRiwayah)
    file.parentFile?.mkdirs()
    val partial = File(file.parentFile, file.name + ".part").apply { delete() }
    SQLiteDatabase.openOrCreateDatabase(partial, null).use { db ->
      db.execSQL("CREATE TABLE timings(sura INTEGER NOT NULL, ayah INTEGER NOT NULL, time INTEGER NOT NULL)")
      db.execSQL("CREATE TABLE properties(property TEXT NOT NULL, value TEXT NOT NULL)")
      db.execSQL("INSERT INTO properties VALUES ('version', '1')")
      db.beginTransaction()
      try {
        val values = ContentValues()
        fun insert(sura: Int, ayah: Int, time: Long) {
          values.clear()
          values.put("sura", sura)
          values.put("ayah", ayah)
          values.put("time", time)
          db.insert("timings", null, values)
        }
        for (sura in 1..114) {
          val ayat = (1..AyahIndex.ayahCount(sura)).map { ayah -> timings?.timesFor(sura, ayah) }
          if (ayat.any { it == null }) {
            // no timings: the sura can still be played from its start
            insert(sura, 1, 0)
            continue
          }
          var ayah = 1
          while (ayah <= ayat.size) {
            val times = ayat[ayah - 1]!!
            // the run of ayat read as one riwayah ayah
            var last = ayah
            while (last < ayat.size && ayat[last] == times) last++
            val weights = (ayah..last).map { textWidth(locations, sura, it) }
            val total = weights.sum().takeIf { it > 0f } ?: weights.size.toFloat()
            var start = times.first.toFloat()
            val duration = times.last - times.first
            (ayah..last).forEachIndexed { i, current ->
              insert(sura, current, start.toLong())
              start += duration * (if (total > 0f) weights[i] / total else 1f / weights.size)
            }
            ayah = last + 1
          }
          insert(sura, 999, ayat.last()!!.last)
        }
        db.setTransactionSuccessful()
      } finally {
        db.endTransaction()
      }
    }
    if (!partial.renameTo(file)) throw IOException("Unable to save ${file.name}")
  }

  private fun textWidth(locations: RiwayahLocations?, sura: Int, ayah: Int): Float =
    locations?.rectsFor(sura, ayah)?.values?.flatten()?.sumOf { it.width().toDouble() }?.toFloat()
      ?: 1f

  companion object {
    private const val PAGES = 604
    private const val LINE_TOLERANCE = 0.012f

    fun pageFileName(page: Int) = "page" + page.toString().padStart(3, '0') + ".png"
  }
}
