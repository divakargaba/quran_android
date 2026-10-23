package com.quran.mobile.feature.qiraat.pages

import android.graphics.RectF
import com.quran.mobile.feature.qiraat.data.RiwayahLayout
import kotlin.math.max
import kotlin.math.min

/**
 * Places the lines of a riwayah's page ([RiwayahLayout]) on a page image of the given size: the
 * 15 lines of the Madinah mushaf one under the other (the 8 of the first two pages in the
 * middle), each text line justified across the page. tools/preview_layout.py does the same.
 */
class PageLayout(private val layout: RiwayahLayout, val width: Int, val height: Int) {

  /** a word of a text line: the [source] region of the pdf page (in pt) drawn at [target] */
  class PlacedWord(
    val pdfPage: Int,
    val source: RectF,
    val target: RectF,
    val word: RiwayahLayout.Word
  )

  class PlacedLine(
    /** 1 based */
    val number: Int,
    val top: Float,
    val bottom: Float,
    val line: RiwayahLayout.Line,
    val words: List<PlacedWord>
  )

  private val marginX = width * MARGIN_X
  private val available = width - 2 * marginX
  private val pitch = height * (1 - 2 * MARGIN_Y) / LINES

  /** the pixels per pt that pdf text is drawn at: a line fills the line height or the width */
  val scale = min(pitch / layout.linePitch, available / layout.lineWidth)

  fun place(page: Int): List<PlacedLine> {
    val lines = layout.pages[page - 1]
    val top = if (lines.size == LINES) height * MARGIN_Y else (height - lines.size * pitch) / 2
    return lines.mapIndexed { i, line ->
      val lineTop = top + i * pitch
      val words = if (line is RiwayahLayout.Line.Text) {
        placeText(line, lineTop + pitch / 2, centered = page <= 2)
      } else {
        emptyList()
      }
      PlacedLine(i + 1, lineTop, lineTop + pitch, line, words)
    }
  }

  /** where a sura title or basmalah from the pdf goes: centered, and narrower than the page */
  fun placeHeading(heading: RiwayahLayout.Line.Heading, line: PlacedLine): Pair<RectF, RectF> {
    val source = sourceRect(heading.left, heading.right, (heading.top + heading.bottom) / 2)
    val s = min(scale, available * HEADING_WIDTH / source.width())
    val middle = (line.top + line.bottom) / 2
    val w = source.width() * s
    val h = source.height() * s
    return source to RectF(width / 2f - w / 2, middle - h / 2, width / 2f + w / 2, middle + h / 2)
  }

  /** the region of the pdf (in pt) for a part of a line: its height is the distance of lines */
  private fun sourceRect(left: Float, right: Float, middle: Float): RectF {
    val y = middle * layout.pdfPageHeight
    val half = layout.linePitch / 2
    return RectF(left * layout.pdfPageWidth, y - half, right * layout.pdfPageWidth, y + half)
  }

  private fun placeText(
    line: RiwayahLayout.Line.Text,
    middle: Float,
    centered: Boolean
  ): List<PlacedWord> {
    class Piece(val strip: RiwayahLayout.Strip, val word: RiwayahLayout.Word, val gap: Float)

    val pieces = ArrayList<Piece>()
    line.strips.forEachIndexed { si, strip ->
      strip.words.forEachIndexed { wi, word ->
        val gap = when {
          wi > 0 -> (strip.words[wi - 1].left - word.right) * layout.pdfPageWidth
          si > 0 -> STRIP_GAP
          else -> 0f
        }
        pieces.add(Piece(strip, word, max(0f, gap)))
      }
    }
    if (pieces.isEmpty()) return emptyList()

    var s = scale
    var stretch = 1f
    var offset = 0f
    var ink = pieces.sumOf { (it.word.right - it.word.left).toDouble() }.toFloat() *
      layout.pdfPageWidth * s
    val gaps = FloatArray(pieces.size) { pieces[it].gap * s }
    val natural = ink + gaps.sum()
    when {
      centered -> offset = (available - natural) / 2
      natural > available -> {
        // tighten the word spacing (to half), then shrink the line
        val need = natural - available
        val total = gaps.sum()
        val cut = min(need, total * 0.5f)
        if (total > 0) for (i in gaps.indices) gaps[i] -= cut * gaps[i] / total
        if (need > cut) {
          val f = available / (ink + gaps.sum())
          s *= f
          ink *= f
          for (i in gaps.indices) gaps[i] *= f
        }
      }
      else -> {
        // widen the words a little, then the spaces between them
        var extra = available - natural
        stretch = if (ink > 0) min(1 + MAX_STRETCH, 1 + extra / ink) else 1f
        extra -= ink * (stretch - 1)
        val widened = (1 until gaps.size).filter { gaps[it] >= WORD_GAP * s }
        if (widened.isEmpty()) {
          offset = extra / 2
        } else {
          val used = min(extra, widened.size * layout.lineHeight * s * 2)
          widened.forEach { gaps[it] += used / widened.size }
          offset = (extra - used) / 2
        }
      }
    }

    val result = ArrayList<PlacedWord>(pieces.size)
    var x = marginX + available - offset
    pieces.forEachIndexed { i, piece ->
      x -= gaps[i]
      val source = sourceRect(
        piece.word.left, piece.word.right, (piece.strip.top + piece.strip.bottom) / 2
      )
      val w = source.width() * s * stretch
      val h = source.height() * s
      result.add(
        PlacedWord(
          piece.strip.pdfPage,
          source,
          RectF(x - w, middle - h / 2, x, middle + h / 2),
          piece.word
        )
      )
      x -= w
    }
    return result
  }

  companion object {
    const val LINES = 15

    /** the height of a page image for its width, as the Madinah mushaf images */
    const val PAGE_RATIO = 1.6175f

    private const val MARGIN_X = 0.03f
    private const val MARGIN_Y = 0.012f

    /** pt between words of a line that come from two lines of the riwayah's mushaf */
    private const val STRIP_GAP = 5.5f

    /** pt: narrower gaps are within a word, and are not widened */
    private const val WORD_GAP = 3.2f

    /** how much wider than in the riwayah's mushaf the words of a line can be drawn */
    private const val MAX_STRETCH = 0.05f

    /** the most of the page width a sura title or basmalah takes */
    private const val HEADING_WIDTH = 0.8f
  }
}
