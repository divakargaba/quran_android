package com.quran.mobile.feature.qiraat.data

import android.content.Context
import android.graphics.Typeface
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * The text of every ayah in each of the twenty riwayat (KFGQPC's Unicode text where it publishes
 * one, else read from the riwayah's mushaf), to show an ayah or a word in any riwayah without its
 * mushaf. Built by tools/build_texts.py, a file for each sura.
 */
class RiwayahTexts private constructor(private val open: (sura: Int) -> InputStream) {

  /**
   * an ayah (by its Hafs number) in a riwayah: the riwayah's words read in the place of each of
   * the Hafs words of the ayah (none, one or more), and the KFGQPC font its text is written for.
   * A word read from a mushaf that may have been misread is marked with [UNSURE] before it.
   */
  class AyahText(val font: String, val slots: List<String>) {
    val text: String get() = words(0, slots.size)

    /** the riwayah's words in the place of [count] Hafs words from [first] */
    fun words(first: Int, count: Int): String =
      range(first, count).filter { it.isNotEmpty() }.joinToString(" ").replace(UNSURE, "")

    /** whether none of the words in the place of [count] Hafs words from [first] may be misread */
    fun isSure(first: Int, count: Int): Boolean = range(first, count).none { UNSURE in it }

    private fun range(first: Int, count: Int) =
      slots.subList(first.coerceIn(0, slots.size), (first + count).coerceIn(0, slots.size))
  }

  private var cachedSura = 0
  private var cached: List<Map<String, AyahText>> = emptyList()

  /** the ayah in every riwayah, by riwayah id; call off the main thread */
  @Synchronized
  fun ayah(sura: Int, ayah: Int): Map<String, AyahText> {
    if (sura != cachedSura) {
      cached = read(open(sura))
      cachedSura = sura
    }
    return cached.getOrElse(ayah - 1) { emptyMap() }
  }

  companion object {
    private const val DIRECTORY = "qiraat/text"
    private const val FONTS = "qiraat/fonts"
    private const val UNSURE = "~"

    @Volatile private var instance: RiwayahTexts? = null
    private val typefaces = HashMap<String, Typeface>()

    fun get(context: Context): RiwayahTexts =
      instance ?: synchronized(this) {
        instance ?: context.applicationContext.assets.let { assets ->
          RiwayahTexts { sura -> assets.open("$DIRECTORY/%03d.qtx".format(sura)) }
        }.also { instance = it }
      }

    fun forTesting(open: (sura: Int) -> InputStream) = RiwayahTexts(open)

    /** the typeface of a KFGQPC font of the riwayat's texts ([AyahText.font]) */
    fun typeface(context: Context, font: String): Typeface = synchronized(typefaces) {
      typefaces.getOrPut(font) {
        Typeface.createFromAsset(context.applicationContext.assets, "$FONTS/$font.ttf")
      }
    }

    /** a sura's file: a line of "riwayah=font" pairs, then each ayah's line for each riwayah */
    internal fun read(stream: InputStream): List<Map<String, AyahText>> {
      val lines = GZIPInputStream(stream).bufferedReader().use { it.readLines() }
      if (lines.isEmpty()) return emptyList()
      val riwayat = lines[0].split(' ').map { it.substringBefore('=') to it.substringAfter('=') }
      return lines.drop(1).chunked(riwayat.size).filter { it.size == riwayat.size }.map { ayah ->
        riwayat.indices.associate { i ->
          riwayat[i].first to AyahText(riwayat[i].second, ayah[i].split('|'))
        }
      }
    }
  }
}
