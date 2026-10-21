package com.quran.mobile.feature.qiraat.data

import android.content.Context
import android.graphics.RectF
import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Where each Hafs ayah's text sits in a riwayah's mushaf.
 *
 * When the riwayah counts ayahs differently (e.g. "الم" is not its own ayah outside the Kufi
 * count), an ayah maps to the whole riwayah ayah that contains it.
 *
 * File format (gzipped, big endian): "QRX1", u16 ayah count, then per ayah in mushaf order:
 * u16 page, u8 rect count, and per rect: u16 page, u16 left, top, right, bottom (scaled by 65535).
 * The files are named .qrx, since the android build unpacks (and renames) assets ending in .gz.
 */
class RiwayahLocations private constructor(
  private val pageForAyah: IntArray,
  private val rectOffsets: IntArray,
  private val rectPages: IntArray,
  private val rectCoords: FloatArray
) {
  private val ayahsByPage: Map<Int, IntArray> by lazy {
    val byPage = HashMap<Int, MutableList<Int>>()
    for (ayah in pageForAyah.indices) {
      for (r in rectOffsets[ayah] until rectOffsets[ayah + 1]) {
        val ayahs = byPage.getOrPut(rectPages[r]) { mutableListOf() }
        if (ayahs.lastOrNull() != ayah) ayahs.add(ayah)
      }
    }
    byPage.mapValues { it.value.toIntArray() }
  }

  /** the page (1-604) on which the ayah ends in this riwayah's mushaf */
  fun pageFor(sura: Int, ayah: Int): Int = pageForAyah[AyahIndex.indexOf(sura, ayah)]

  /** line rectangles (fractions of the page size) of the ayah, grouped by page */
  fun rectsFor(sura: Int, ayah: Int): Map<Int, List<RectF>> {
    val index = AyahIndex.indexOf(sura, ayah)
    val result = LinkedHashMap<Int, MutableList<RectF>>()
    for (r in rectOffsets[index] until rectOffsets[index + 1]) {
      val c = r * 4
      result.getOrPut(rectPages[r]) { mutableListOf() }
        .add(RectF(rectCoords[c], rectCoords[c + 1], rectCoords[c + 2], rectCoords[c + 3]))
    }
    return result
  }

  /** the first ayah (as sura to ayah) with text on the page */
  fun firstAyahOn(page: Int): Pair<Int, Int>? =
    ayahsByPage[page]?.firstOrNull()?.let { AyahIndex.suraAyahOf(it) }

  /** the ayah (as sura to ayah) whose text is at the given fractional point of a page, if any */
  fun ayahAt(page: Int, x: Float, y: Float): Pair<Int, Int>? {
    val candidates = ayahsByPage[page] ?: return null
    for (ayah in candidates) {
      for (r in rectOffsets[ayah] until rectOffsets[ayah + 1]) {
        val c = r * 4
        if (rectPages[r] == page &&
          x >= rectCoords[c] && x <= rectCoords[c + 2] &&
          y >= rectCoords[c + 1] && y <= rectCoords[c + 3]
        ) {
          return AyahIndex.suraAyahOf(ayah)
        }
      }
    }
    return null
  }

  companion object {
    private const val MAGIC = 0x51525831 // "QRX1"

    fun load(context: Context, asset: String): RiwayahLocations = load(context.assets.open(asset))

    fun load(stream: InputStream): RiwayahLocations {
      DataInputStream(GZIPInputStream(stream).buffered()).use { input ->
        require(input.readInt() == MAGIC) { "bad locations file" }
        val count = input.readUnsignedShort()
        val pages = IntArray(count)
        val offsets = IntArray(count + 1)
        val rectPages = ArrayList<Int>(count * 3)
        val coords = ArrayList<Float>(count * 12)
        for (i in 0 until count) {
          pages[i] = input.readUnsignedShort()
          val rects = input.readUnsignedByte()
          repeat(rects) {
            rectPages.add(input.readUnsignedShort())
            repeat(4) { coords.add(input.readUnsignedShort() / 65535f) }
          }
          offsets[i + 1] = offsets[i] + rects
        }
        return RiwayahLocations(pages, offsets, rectPages.toIntArray(), coords.toFloatArray())
      }
    }
  }
}
