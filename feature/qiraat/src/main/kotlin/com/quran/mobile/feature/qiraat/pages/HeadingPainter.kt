package com.quran.mobile.feature.qiraat.pages

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.quran.mobile.feature.qiraat.data.RiwayahLayout
import java.util.Locale

/**
 * Draws the frame of sura titles, and the title or basmalah itself for a mushaf that has none of
 * its own (the riwayat of al-Durrah have no sura titles), in the app's Uthmani font.
 */
internal class HeadingPainter(context: Context) {
  private val suraNames: Array<String> = context.createConfigurationContext(
    Configuration(context.resources.configuration).apply { setLocale(Locale("ar")) }
  ).resources.getStringArray(com.quran.mobile.common.ui.core.R.array.sura_names)

  private val typeface: Typeface = try {
    Typeface.createFromAsset(context.assets, FONT)
  } catch (e: RuntimeException) {
    Typeface.SERIF
  }

  private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    color = INK
  }

  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = INK
    textAlign = Paint.Align.CENTER
    typeface = this@HeadingPainter.typeface
  }

  fun drawFrame(canvas: Canvas, layout: PageLayout, line: PageLayout.PlacedLine) {
    val width = layout.width.toFloat()
    val height = line.bottom - line.top
    val middle = (line.top + line.bottom) / 2
    val margin = width * 0.03f
    val outer = RectF(margin, middle - height * 0.42f, width - margin, middle + height * 0.42f)
    val inset = width * 0.0065f
    framePaint.strokeWidth = width * 0.0024f
    canvas.drawRoundRect(outer, height * 0.2f, height * 0.2f, framePaint)
    framePaint.strokeWidth = width * 0.0008f
    outer.inset(inset, inset)
    canvas.drawRoundRect(outer, height * 0.15f, height * 0.15f, framePaint)
  }

  fun drawText(
    canvas: Canvas,
    layout: PageLayout,
    line: PageLayout.PlacedLine,
    heading: RiwayahLayout.Line.Heading
  ) {
    val text = if (heading.isTitle) {
      "سُورَةُ " + suraNames.getOrElse(heading.sura - 1) { "" }
    } else {
      BASMALAH
    }
    val height = line.bottom - line.top
    textPaint.textSize = height * 0.5f
    val maxWidth = layout.width * 0.7f
    val width = textPaint.measureText(text)
    if (width > maxWidth) textPaint.textSize *= maxWidth / width
    val middle = (line.top + line.bottom) / 2
    val baseline = middle - (textPaint.ascent() + textPaint.descent()) / 2
    canvas.drawText(text, layout.width / 2f, baseline, textPaint)
  }

  companion object {
    private const val FONT = "UthmanTN1Ver10.otf"
    private const val INK = 0xFF2B2B2B.toInt()
    private const val BASMALAH = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"
  }
}
