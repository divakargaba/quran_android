package com.quran.mobile.feature.library.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

/** the pages of a pdf file as images (one page is drawn at a time) */
class PdfPages(file: File) : Closeable {
  private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  private val renderer = PdfRenderer(descriptor)
  private val mutex = Mutex()
  private var closed = false

  val count: Int = renderer.pageCount

  /** height / width of each page */
  val ratios: List<Float> = List(count) { index ->
    renderer.openPage(index).use { it.height.toFloat() / it.width }
  }

  /** the page (from 0) drawn [width] pixels wide, on white */
  suspend fun render(index: Int, width: Int): Bitmap? = withContext(Dispatchers.IO) {
    mutex.withLock {
      if (closed || index !in 0 until count) return@withLock null
      renderer.openPage(index).use { page ->
        val height = (width * page.height.toFloat() / page.width).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
      }
    }
  }

  /**
   * a part of a page (from 0), [left] to [right] and [top] to [bottom] in points of the page,
   * drawn [width] pixels wide, on white
   */
  suspend fun renderRegion(index: Int, left: Int, top: Int, right: Int, bottom: Int, width: Int): Bitmap? =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        if (closed || index !in 0 until count || right <= left || bottom <= top || width <= 0) return@withLock null
        renderer.openPage(index).use { page ->
          val scale = width.toFloat() / (right - left)
          val height = ((bottom - top) * scale).roundToInt().coerceAtLeast(1)
          val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
          bitmap.eraseColor(Color.WHITE)
          val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(-left * scale, -top * scale)
          }
          page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
          bitmap
        }
      }
    }

  override fun close() {
    // (after the page being drawn, if any)
    kotlinx.coroutines.runBlocking {
      mutex.withLock {
        if (!closed) {
          closed = true
          renderer.close()
          descriptor.close()
        }
      }
    }
  }
}

/**
 * The pages of a pdf one under the other, as wide as the screen times [zoom] (scrolled sideways
 * when wider).
 */
@Composable
fun PdfPagesColumn(
  pages: PdfPages,
  listState: LazyListState,
  zoom: Float,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
    val density = LocalDensity.current
    val pageWidth = maxWidth * zoom
    val widthPx = with(density) { pageWidth.toPx() }.roundToInt().coerceIn(1, MAX_WIDTH_PX)
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().horizontalScroll(scroll, enabled = zoom > 1f)) {
      LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.width(pageWidth)
      ) {
        items(pages.count, key = { it }) { index ->
          var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
          LaunchedEffect(index, widthPx) { bitmap = pages.render(index, widthPx) }
          Box(
            Modifier
              .padding(bottom = 6.dp)
              .width(pageWidth)
              .aspectRatio(1f / pages.ratios[index])
              .background(androidx.compose.ui.graphics.Color.White)
          ) {
            bitmap?.let {
              Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize()
              )
            }
          }
        }
      }
    }
  }
}

private const val MAX_WIDTH_PX = 2400
