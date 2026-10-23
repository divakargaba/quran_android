package com.quran.mobile.feature.qiraat.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.SizeF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders pages of a riwayah mushaf PDF. [PdfRenderer] only allows one open page at a time,
 * and pdfium is not thread safe on older Android versions, so all rendering (across every
 * open mushaf) is serialized.
 *
 * @throws java.io.IOException if the file is not a readable PDF
 */
class PdfPageRenderer(private val file: File) {
  private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  private val renderer = try {
    PdfRenderer(descriptor)
  } catch (e: Exception) {
    descriptor.close()
    throw e
  }
  private var closed = false
  private val sizes = java.util.concurrent.ConcurrentHashMap<Int, SizeF>()

  /**
   * renders the [region] of a page (fractions of the page size) at the given width, on a
   * transparent background when [transparent] (otherwise white), or returns null if it cannot be
   * rendered.
   */
  suspend fun render(
    page: Int,
    region: RectF,
    width: Int,
    transparent: Boolean = false
  ): Bitmap? = withContext(Dispatchers.IO) {
    val key = "${file.name}:$page:$region:$width:$transparent"
    cache.get(key)?.let { return@withContext it }
    lock.withLock {
      try {
        renderLocked(page, region, width, transparent)?.also { cache.put(key, it) }
      } catch (e: Exception) {
        null
      } catch (e: OutOfMemoryError) {
        cache.evictAll()
        null
      }
    }
  }

  /** the size of a page in pt, or null if there is no such page */
  suspend fun pageSize(page: Int): SizeF? = withContext(Dispatchers.IO) {
    sizes[page]?.let { return@withContext it }
    lock.withLock {
      if (closed || page < 1 || page > renderer.pageCount) return@withLock null
      renderer.openPage(page - 1).use { SizeF(it.width.toFloat(), it.height.toFloat()) }
        .also { sizes[page] = it }
    }
  }

  /**
   * renders the [region] of a page at the given width without caching it, on a transparent
   * background when [transparent] (otherwise white)
   */
  suspend fun renderUncached(page: Int, region: RectF, width: Int, transparent: Boolean): Bitmap? =
    withContext(Dispatchers.IO) {
      lock.withLock { renderLocked(page, region, width, transparent) }
    }

  private fun renderLocked(
    page: Int,
    region: RectF,
    width: Int,
    transparent: Boolean = false
  ): Bitmap? {
    if (closed || page < 1 || page > renderer.pageCount || width <= 0) return null
    return renderer.openPage(page - 1).use { pdfPage ->
        val pageWidth = pdfPage.width.toFloat()
        val pageHeight = pdfPage.height.toFloat()
        val regionWidth = region.width() * pageWidth
        val regionHeight = region.height() * pageHeight
        val scale = width / regionWidth
        val height = (regionHeight * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (transparent) Color.TRANSPARENT else Color.WHITE)
        val matrix = Matrix().apply {
          postTranslate(-region.left * pageWidth, -region.top * pageHeight)
          postScale(scale, scale)
        }
        pdfPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
      }
  }

  fun close() {
    if (lock.tryLock()) {
      try {
        closeNow()
      } finally {
        lock.unlock()
      }
    } else {
      // a render is in progress; close once it is done
      Thread {
        kotlinx.coroutines.runBlocking { lock.withLock { closeNow() } }
      }.start()
    }
  }

  private fun closeNow() {
    if (!closed) {
      closed = true
      renderer.close()
      descriptor.close()
    }
  }

  companion object {
    private const val CACHE_BYTES = 48 * 1024 * 1024
    private val lock = Mutex()

    // shared, so that the comparison screen (up to 20 open mushafs) stays within one budget
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
      override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
  }
}
