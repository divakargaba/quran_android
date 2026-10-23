package com.quran.mobile.feature.qiraat.data

import android.content.Context
import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * A riwayah's text laid out on the 604 pages of the 15 line Madinah mushaf: for each line of a
 * page, the words of the riwayah's mushaf (its pdf) that go on it, so that a page holds the same
 * ayat, line for line, as the Hafs mushaf of the reader.
 *
 * File format (gzipped, big endian): "QRL2", u16 typical line height, u16 line pitch, u16 typical
 * line width, u32 pdf page width, u32 pdf page height (all in 1/100 pt), then per page: u8 line
 * count, and per line: u8 kind (0 empty, 1 text, 2 sura title, 3 basmalah); for a title or
 * basmalah: u8 sura, u16 pdf page (0 if the mushaf has none), u16 left, right, top, bottom; for
 * text: u8 strip count, and per strip (words from one line of the pdf): u16 pdf page, u16 top,
 * u16 bottom, u16 ayah index of its first word, u8 word count, and per word, right to left:
 * u16 left, u16 right, u8 flags (bit 0: an ayah marker, bits 1-7: the change of ayah index from
 * the previous word). Positions are fractions of the pdf page scaled by 65535.
 */
class RiwayahLayout private constructor(
  /** in pt: the height of the text of a line, the distance between lines, a typical line width */
  val lineHeight: Float,
  val linePitch: Float,
  val lineWidth: Float,
  val pdfPageWidth: Float,
  val pdfPageHeight: Float,
  val pages: List<List<Line>>
) {

  sealed interface Line {
    data object Empty : Line

    /** a sura title or basmalah: its line in the pdf, or [pdfPage] 0 when the pdf has none */
    data class Heading(
      val isTitle: Boolean,
      val sura: Int,
      val pdfPage: Int,
      val left: Float,
      val right: Float,
      val top: Float,
      val bottom: Float
    ) : Line

    data class Text(val strips: List<Strip>) : Line
  }

  /** words of a line that are on the same line of the pdf, right to left */
  class Strip(val pdfPage: Int, val top: Float, val bottom: Float, val words: List<Word>)

  /** [ayahIndex]: the (Hafs) ayah of the word, an index for [AyahIndex.suraAyahOf] */
  class Word(val left: Float, val right: Float, val isMarker: Boolean, val ayahIndex: Int)

  companion object {
    private const val MAGIC = 0x51524c32 // "QRL2"
    private const val PAGES = 604

    fun load(context: Context, asset: String): RiwayahLayout = load(context.assets.open(asset))

    fun load(stream: InputStream): RiwayahLayout {
      DataInputStream(GZIPInputStream(stream).buffered()).use { input ->
        require(input.readInt() == MAGIC) { "bad layout file" }
        fun u16() = input.readUnsignedShort()
        fun u8() = input.readUnsignedByte()
        fun fraction() = u16() / 65535f
        val lineHeight = u16() / 100f
        val linePitch = u16() / 100f
        val lineWidth = u16() / 100f
        val pageWidth = input.readInt() / 100f
        val pageHeight = input.readInt() / 100f
        val pages = List(PAGES) {
          List(u8()) {
            when (val kind = u8()) {
              0 -> Line.Empty
              1 -> Line.Text(
                List(u8()) {
                  val pdfPage = u16()
                  val top = fraction()
                  val bottom = fraction()
                  var ayah = u16()
                  val words = List(u8()) {
                    val left = fraction()
                    val right = fraction()
                    val flags = u8()
                    ayah += flags shr 1
                    Word(left, right, flags and 1 == 1, ayah)
                  }
                  Strip(pdfPage, top, bottom, words)
                }
              )
              2, 3 -> Line.Heading(
                isTitle = kind == 2,
                sura = u8(),
                pdfPage = u16(),
                left = fraction(),
                right = fraction(),
                top = fraction(),
                bottom = fraction()
              )
              else -> error("bad line kind $kind")
            }
          }
        }
        return RiwayahLayout(lineHeight, linePitch, lineWidth, pageWidth, pageHeight, pages)
      }
    }
  }
}
