package com.quran.mobile.feature.qiraat.pages

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import com.quran.data.model.audio.Qari
import com.quran.mobile.feature.qiraat.data.AyahIndex
import com.quran.mobile.feature.qiraat.data.DownloadState
import com.quran.mobile.feature.qiraat.data.PdfPageRenderer
import com.quran.mobile.feature.qiraat.data.RiwayahLayout
import com.quran.mobile.feature.qiraat.data.RiwayahLocations
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.data.RiwayahTimings
import com.quran.mobile.feature.qiraat.model.Riwayah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Prepares a riwayah mushaf for the main reader on the device: downloads its PDF (once), draws
 * the riwayah's text on the 15 line pages of the Madinah mushaf ([PageLayout]) as the image files
 * the reader loads, and writes the ayah position database (for highlighting and tapping ayat) and
 * the audio timing database (for playing from an ayah and following the recitation).
 */
class QiraatPagePreparer(private val context: Context) {
  private val repository = RiwayahRepository.get(context)

  sealed interface Progress {
    data class Downloading(val fraction: Float) : Progress
    data class Rendering(val page: Int, val pages: Int) : Progress
  }

  /**
   * @param widthParams the reader's image width parameters (such as "_1260") to render for
   * @param imagesDirectory where the page images for a width parameter go
   * @param ayahInfoFile the ayah position database for a width parameter
   * @param timingDatabase the gapless timing database of a recording (a qari of the page type)
   */
  suspend fun prepare(
    provider: QiraatPageProvider,
    widthParams: List<String>,
    imagesDirectory: (String) -> File,
    ayahInfoFile: (String) -> File,
    timingDatabase: (Qari) -> File,
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
    val layout = RiwayahLayout.load(context, riwayah.layoutAsset)
    val headings = HeadingPainter(context)

    val widths = widthParams.distinct()
    val totalPages = PAGES * widths.size
    widths.forEachIndexed { widthIndex, widthParam ->
      val width = widthParam.filter { it.isDigit() }.toInt()
      val pageLayout = PageLayout(layout, width, (width * PageLayout.PAGE_RATIO).roundToInt())
      val directory = imagesDirectory(widthParam).apply { mkdirs() }
      removeEarlierVersion(directory, riwayah.id)
      removeEarlierVersion(ayahInfoFile(widthParam).parentFile, riwayah.id)
      File(directory, ".nomedia").createNewFile()
      for (page in 1..PAGES) {
        coroutineContext.ensureActive()
        onProgress(Progress.Rendering(widthIndex * PAGES + page, totalPages))
        val file = File(directory, pageFileName(page))
        if (file.exists()) {
          val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeFile(file.absolutePath, options)
          if (options.outWidth > 0) continue
        }
        val bitmap = drawPage(renderer, pageLayout, page, headings)
        writeImage(bitmap, file)
        bitmap.recycle()
      }
      writeAyahInfo(ayahInfoFile(widthParam), pageLayout)
    }
    provider.getQaris().filter { it.isGapless }.forEach { qari ->
      writeTimings(riwayah, timingDatabase(qari))
    }
  }

  /** writes the timing database of the riwayah's recording ([QiraatPageProvider.recordingQari]) */
  suspend fun prepareTimings(riwayah: Riwayah, file: File) = withContext(Dispatchers.IO) {
    if (!file.exists()) writeTimings(riwayah, file)
  }

  /** pages and ayah positions of the riwayah's earlier layouts, if any */
  private fun removeEarlierVersion(directory: File?, riwayahId: String) {
    // ".../qiraat/<id>_15v2/width_1260" (or ".../qiraat/<id>_15v2") replaced ".../qiraat/<id>_15/..."
    // and ".../qiraat/<id>/..."
    val current = riwayahId + QiraatPageProvider.DIRECTORY_SUFFIX
    generateSequence(directory) { it.parentFile }
      .firstOrNull { it.name == current }
      ?.parentFile
      ?.let { parent -> EARLIER_SUFFIXES.map { File(parent, riwayahId + it) } }
      ?.filter { it.isDirectory }
      ?.forEach { it.deleteRecursively() }
  }

  private suspend fun drawPage(
    renderer: PdfPageRenderer,
    layout: PageLayout,
    page: Int,
    headings: HeadingPainter
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val lines = layout.place(page)

    // the words of each line, from bands of the pdf pages rendered once at the page's scale
    val words = lines.flatMap { it.words }
    words.groupBy { it.pdfPage }.forEach { (pdfPage, onPage) ->
      for (band in bands(onPage, layout.pdfLinePitch)) {
        val region = RectF(
          band.minOf { it.source.left }, band.minOf { it.source.top },
          band.maxOf { it.source.right }, band.maxOf { it.source.bottom }
        )
        val image = renderRegion(renderer, pdfPage, region, layout.scale * (1 + 0.05f)) ?: continue
        val s = image.width / region.width()
        band.forEach { word ->
          val src = Rect(
            ((word.source.left - region.left) * s).roundToInt(),
            ((word.source.top - region.top) * s).roundToInt(),
            ((word.source.right - region.left) * s).roundToInt(),
            ((word.source.bottom - region.top) * s).roundToInt()
          )
          canvas.drawBitmap(image, src, word.target, paint)
        }
        image.recycle()
      }
    }

    lines.forEach { line ->
      val heading = line.line as? RiwayahLayout.Line.Heading ?: return@forEach
      if (heading.isTitle) headings.drawFrame(canvas, layout, line)
      if (heading.pdfPage > 0) {
        val (source, target) = layout.placeHeading(heading, line)
        val image = renderRegion(renderer, heading.pdfPage, source, target.width() / source.width())
        if (image != null) {
          canvas.drawBitmap(image, null, target, paint)
          image.recycle()
        }
      } else {
        headings.drawText(canvas, layout, line, heading)
      }
    }
    return bitmap
  }

  /** the words of a pdf page in groups of nearby lines, each rendered as one image */
  private fun bands(words: List<PageLayout.PlacedWord>, pitch: Float): List<List<PageLayout.PlacedWord>> {
    val result = ArrayList<MutableList<PageLayout.PlacedWord>>()
    var top = 0f
    words.sortedBy { it.source.top }.forEach { word ->
      val band = result.lastOrNull()
      if (band != null && word.source.bottom - top <= pitch * BAND_LINES) {
        band.add(word)
      } else {
        result.add(mutableListOf(word))
        top = word.source.top
      }
    }
    return result
  }

  /** the [region] (in pt) of a pdf page, at [scale] pixels per pt */
  private suspend fun renderRegion(
    renderer: PdfPageRenderer,
    page: Int,
    region: RectF,
    scale: Float
  ): Bitmap? {
    val size = renderer.pageSize(page) ?: return null
    val fractions = RectF(
      region.left / size.width, region.top / size.height,
      region.right / size.width, region.bottom / size.height
    )
    val width = ceil(region.width() * scale).toInt().coerceAtLeast(1)
    return renderer.renderUncached(page, fractions, width, transparent = true)
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

  /**
   * the ayah positions of the page images: a "glyph" for the words of an ayah on each line, as
   * the reader's own ayah position databases have for the lines of an ayah
   */
  internal fun writeAyahInfo(file: File, layout: PageLayout) {
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
        val values = ContentValues()
        val positions = HashMap<Int, Int>()
        // the first line of each ayah: (page, line number, left, right, top, bottom)
        val firstRuns = HashMap<Int, IntArray>()
        fun insert(ayahIndex: Int, page: Int, line: Int, bounds: IntArray) {
          val (sura, ayah) = AyahIndex.suraAyahOf(ayahIndex)
          val position = (positions[ayahIndex] ?: 0) + 1
          positions[ayahIndex] = position
          values.clear()
          values.put("page_number", page)
          values.put("line_number", line)
          values.put("sura_number", sura)
          values.put("ayah_number", ayah)
          values.put("position", position)
          values.put("min_x", bounds[0])
          values.put("max_x", bounds[1])
          values.put("min_y", bounds[2])
          values.put("max_y", bounds[3])
          db.insert("glyphs", null, values)
        }
        for (page in 1..PAGES) {
          layout.place(page).forEach { line ->
            // runs of words of the same ayah
            var start = 0
            while (start < line.words.size) {
              val ayahIndex = line.words[start].word.ayahIndex
              var end = start
              while (end + 1 < line.words.size && line.words[end + 1].word.ayahIndex == ayahIndex) {
                end++
              }
              val run = line.words.subList(start, end + 1)
              val bounds = intArrayOf(
                run.minOf { it.target.left }.roundToInt().coerceIn(0, layout.width),
                run.maxOf { it.target.right }.roundToInt().coerceIn(0, layout.width),
                line.top.roundToInt().coerceIn(0, layout.height),
                line.bottom.roundToInt().coerceIn(0, layout.height)
              )
              firstRuns.getOrPut(ayahIndex) { intArrayOf(page, line.number) + bounds }
              insert(ayahIndex, page, line.number, bounds)
              start = end + 1
            }
          }
        }
        // an ayah the riwayah writes as one word with the next (as "حم عسق") shares its place
        for (ayahIndex in 0 until AyahIndex.AYAH_COUNT) {
          if (ayahIndex in firstRuns) continue
          val near = firstRuns[ayahIndex + 1] ?: firstRuns[ayahIndex - 1] ?: continue
          insert(ayahIndex, near[0], near[1], near.copyOfRange(2, 6))
        }
        db.setTransactionSuccessful()
      } finally {
        db.endTransaction()
      }
    }
    if (!partial.renameTo(file)) throw IOException("Unable to save ${file.name}")
  }

  /**
   * The gapless timing database of the recording: the start of each ayah in its sura file, and
   * (as ayah 999) the end of the last. Ayat that the riwayah reads as one share its time, which
   * is split between them by the length of their text so that each is highlighted in turn.
   */
  internal suspend fun writeTimings(riwayah: Riwayah, file: File) {
    val timings: RiwayahTimings? = repository.timings(riwayah)
    val locations = repository.locations(riwayah)
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

    /** of the directories of earlier layouts: the mushaf pages as they are, then 15 lines */
    private val EARLIER_SUFFIXES = listOf("", "_15")

    /** the most lines of a pdf page rendered as one image */
    private const val BAND_LINES = 6

    fun pageFileName(page: Int) = "page" + page.toString().padStart(3, '0') + ".png"
  }
}
