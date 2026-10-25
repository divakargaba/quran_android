package com.quran.mobile.feature.qiraat.data

import android.content.Context
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * How the twenty riwayat read each word of an ayah where they differ, from nquran.com (the
 * Shatibiyyah and Durrah path): for each such word, the groups of riwayat that read it alike, a
 * description (in Arabic and English) of how each group reads it, and which of the Hafs words of
 * the ayah it is ([RiwayahTexts] has them in each riwayah). Built by tools/build_differences.py.
 */
class QiraatDifferences private constructor(
  private val riwayat: List<String>,
  private val texts: List<String>,
  private val textsEnglish: List<String>,
  private val ayat: JSONObject
) {

  /** how a riwayah reads a word */
  data class Description(val arabic: String, val english: String)

  /**
   * a word that a riwayah reads differently from [base]: how each of the two reads it, and the
   * Hafs words of the ayah it is ([firstWord] is -1 if not known)
   */
  data class WordDifference(
    val word: String,
    val base: Description,
    val other: Description,
    val firstWord: Int,
    val wordCount: Int
  )

  /** the words of the ayah (Hafs numbering) that [riwayahId] reads differently from [baseId] */
  fun between(baseId: String, riwayahId: String, sura: Int, ayah: Int): List<WordDifference> {
    val base = riwayat.indexOf(baseId)
    val other = riwayat.indexOf(riwayahId)
    if (base < 0 || other < 0 || base == other) return emptyList()
    val words = ayat.optJSONArray("$sura:$ayah") ?: return emptyList()
    val result = ArrayList<WordDifference>()
    for (i in 0 until words.length()) {
      val word = words.getJSONArray(i)
      val groups = word.getJSONArray(1)
      var baseText = -1
      var otherText = -1
      for (g in 0 until groups.length()) {
        val group = groups.getJSONArray(g)
        val bits = group.getLong(0)
        if (bits shr base and 1L == 1L) baseText = group.getInt(1)
        if (bits shr other and 1L == 1L) otherText = group.getInt(1)
      }
      if (baseText >= 0 && otherText >= 0 && baseText != otherText) {
        result.add(
          WordDifference(
            word = word.getString(0),
            base = description(baseText),
            other = description(otherText),
            firstWord = word.optInt(2, -1),
            wordCount = word.optInt(3, 1)
          )
        )
      }
    }
    return result
  }

  private fun description(text: Int) = Description(texts[text], textsEnglish.getOrElse(text) { "" })

  companion object {
    private const val ASSET = "qiraat/differences.qdf"

    @Volatile private var instance: QiraatDifferences? = null

    /** loads the bundled differences (once); call off the main thread */
    fun get(context: Context): QiraatDifferences =
      instance ?: synchronized(this) {
        instance ?: load(context.assets.open(ASSET)).also { instance = it }
      }

    fun load(stream: InputStream): QiraatDifferences {
      val json = GZIPInputStream(stream).bufferedReader().use { it.readText() }
      val root = JSONObject(json)
      val riwayat = root.getJSONArray("riwayat").let { array -> List(array.length()) { array.getString(it) } }
      val texts = root.getJSONArray("texts").let { array -> List(array.length()) { array.getString(it) } }
      val english = root.optJSONArray("texts_en")
        ?.let { array -> List(array.length()) { array.getString(it) } }
        .orEmpty()
      return QiraatDifferences(riwayat, texts, english, root.getJSONObject("ayat"))
    }
  }
}
