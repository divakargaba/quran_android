package com.quran.mobile.feature.qiraat.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.quran.mobile.feature.qiraat.data.PdfPageRenderer

/** the part of a mushaf page that is shown, trimming the outer margins of the pdf page */
private val PAGE_REGION = RectF(0.08f, 0.04f, 0.92f, 0.96f)

/** a whole mushaf page with the given ayah highlighted */
@Composable
fun MushafPage(
  renderer: PdfPageRenderer,
  page: Int,
  highlights: List<RectF>,
  onTap: (x: Float, y: Float) -> Unit,
  onLongPress: (x: Float, y: Float) -> Unit,
  modifier: Modifier = Modifier
) {
  BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
    val width = constraints.maxWidth
    val bitmap = rememberRendered(renderer, page, PAGE_REGION, width)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    if (bitmap == null) {
      CircularProgressIndicator()
    } else {
      val region = PAGE_REGION
      Box(
        modifier = Modifier
          .aspectRatio(bitmap.width / bitmap.height.toFloat())
          .pointerInput(page, region) {
            fun toPage(offset: Offset) = Pair(
              region.left + offset.x / size.width * region.width(),
              region.top + offset.y / size.height * region.height()
            )
            detectTapGestures(
              onTap = { offset -> toPage(offset).let { currentOnTap(it.first, it.second) } },
              onLongPress = { offset ->
                toPage(offset).let { currentOnLongPress(it.first, it.second) }
              }
            )
          }
      ) {
        RenderedRegion(bitmap, region, highlights)
      }
    }
  }
}

/**
 * The lines of one ayah on one page, cropped out of the page, with the ayah highlighted.
 */
@Composable
fun AyahCrop(
  renderer: PdfPageRenderer,
  page: Int,
  rects: List<RectF>,
  modifier: Modifier = Modifier
) {
  val region = remember(rects) {
    val top = rects.minOf { it.top }
    val bottom = rects.maxOf { it.bottom }
    val padding = 0.012f
    RectF(0.1f, (top - padding).coerceAtLeast(0f), 0.9f, (bottom + padding).coerceAtMost(1f))
  }
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val width = constraints.maxWidth
    val bitmap = rememberRendered(renderer, page, region, width)
    if (bitmap == null) {
      val estimatedHeight = with(LocalDensity.current) { (width * 0.12f).toDp() }
      Box(Modifier.fillMaxWidth().height(estimatedHeight), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width / bitmap.height.toFloat())) {
        RenderedRegion(bitmap, region, rects)
      }
    }
  }
}

@Composable
private fun RenderedRegion(bitmap: Bitmap, region: RectF, highlights: List<RectF>) {
  val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
  Image(
    bitmap = imageBitmap,
    contentDescription = null,
    contentScale = ContentScale.FillBounds,
    modifier = Modifier.fillMaxSize()
  )
  if (highlights.isNotEmpty()) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    Canvas(modifier = Modifier.fillMaxSize()) {
      highlights.forEach { rect -> drawHighlight(rect, region, color) }
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHighlight(
  rect: RectF,
  region: RectF,
  color: Color
) {
  val left = (rect.left - region.left) / region.width() * size.width
  val top = (rect.top - region.top) / region.height() * size.height
  val right = (rect.right - region.left) / region.width() * size.width
  val bottom = (rect.bottom - region.top) / region.height() * size.height
  drawRect(color, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
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
    bitmap = renderer.render(page, region, width)
  }
  return bitmap
}
