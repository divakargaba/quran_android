package com.quran.mobile.feature.qiraat.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.quran.mobile.feature.qiraat.data.PdfPageRenderer

/**
 * The text of one ayah on one page of a riwayah's mushaf, cropped out of the page: just the
 * ayah's part of each of its lines. In a dark theme the text is drawn light, as the reader draws
 * its pages at night.
 */
@Composable
fun AyahCrop(
  renderer: PdfPageRenderer,
  page: Int,
  rects: List<RectF>,
  modifier: Modifier = Modifier
) {
  val region = remember(rects) {
    RectF(
      (rects.minOf { it.left } - PADDING_X).coerceAtLeast(0f),
      (rects.minOf { it.top } - PADDING_Y).coerceAtLeast(0f),
      (rects.maxOf { it.right } + PADDING_X).coerceAtMost(1f),
      (rects.maxOf { it.bottom } + PADDING_Y).coerceAtMost(1f)
    )
  }
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val width = constraints.maxWidth
    val bitmap = rememberRendered(renderer, page, region, width)
    if (bitmap == null) {
      val estimatedHeight = with(LocalDensity.current) { (width * 0.12f).toDp() }
      Box(Modifier.fillMaxWidth().height(estimatedHeight), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      val image = remember(bitmap) { bitmap.asImageBitmap() }
      Canvas(Modifier.fillMaxWidth().aspectRatio(bitmap.width / bitmap.height.toFloat())) {
        val path = Path()
        rects.forEach { rect ->
          path.addRect(
            Rect(
              (rect.left - PADDING_X - region.left) / region.width() * size.width,
              (rect.top - PADDING_Y - region.top) / region.height() * size.height,
              (rect.right + PADDING_X - region.left) / region.width() * size.width,
              (rect.bottom + PADDING_Y - region.top) / region.height() * size.height
            )
          )
        }
        clipPath(path) {
          drawImage(
            image,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            colorFilter = if (dark) INVERT else null
          )
        }
      }
    }
  }
}

@Composable
private fun rememberRendered(
  renderer: PdfPageRenderer,
  page: Int,
  region: RectF,
  width: Int
): Bitmap? {
  var bitmap by remember(renderer, page, region, width) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(renderer, page, region, width) {
    bitmap = renderer.render(page, region, width, transparent = true)
  }
  return bitmap
}

private const val PADDING_X = 0.005f
private const val PADDING_Y = 0.012f

private val INVERT = ColorFilter.colorMatrix(
  ColorMatrix(
    floatArrayOf(
      -1f, 0f, 0f, 0f, 255f,
      0f, -1f, 0f, 0f, 255f,
      0f, 0f, -1f, 0f, 255f,
      0f, 0f, 0f, 1f, 0f
    )
  )
)
