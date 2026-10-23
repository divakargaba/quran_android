package com.quran.mobile.feature.mutashabihat.data

import android.content.Context
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** an ayah, as "sura:ayah" */
data class VerseKey(val sura: Int, val ayah: Int) : Comparable<VerseKey> {
  override fun compareTo(other: VerseKey) =
    compareValuesBy(this, other, { it.sura }, { it.ayah })

  override fun toString() = "$sura:$ayah"

  companion object {
    fun parse(key: String): VerseKey {
      val (sura, ayah) = key.split(':')
      return VerseKey(sura.toInt(), ayah.toInt())
    }
  }
}

/** an ayah's words, with the words of the shared phrase marked (0-based, inclusive) */
data class Occurrence(val verse: VerseKey, val words: List<String>, val highlighted: List<IntRange>)

/** a phrase shared by several ayat */
data class Phrase(
  val id: String,
  val text: String,
  /** how many times the phrase occurs in the Quran */
  val count: Int,
  val occurrences: List<Occurrence>
)

/**
 * The mutashabihat (similar phrases) data set: for an ayah, the phrases it shares with other ayat.
 */
class Mutashabihat private constructor(private val root: JSONObject) {
  private val phrases = root.getJSONObject("phrases")
  private val verses = root.getJSONObject("verses")
  private val words = root.getJSONObject("words")

  fun phrasesFor(sura: Int, ayah: Int): List<Phrase> {
    val ids = verses.optJSONArray("$sura:$ayah") ?: return emptyList()
    return (0 until ids.length()).mapNotNull { phrase(ids.get(it).toString()) }
  }

  private fun phrase(id: String): Phrase? {
    val json = phrases.optJSONObject(id) ?: return null
    val source = json.getString("s")
    val text = wordsOf(source)
      .slice(range(json.getInt("f"), json.getInt("t"), wordsOf(source).size))
      .joinToString(" ")
    val ayat = json.getJSONObject("a")
    val occurrences = ayat.keys().asSequence().map { key ->
      val verseWords = wordsOf(key)
      val ranges = ayat.getJSONArray(key)
      val highlighted = (0 until ranges.length())
        .map { ranges.getJSONArray(it) }
        .map { range(it.getInt(0), it.getInt(1), verseWords.size) }
        .distinct()
      Occurrence(VerseKey.parse(key), verseWords, highlighted)
    }.sortedBy { it.verse }.toList()
    return Phrase(id, text, json.getInt("c"), occurrences)
  }

  private fun wordsOf(key: String): List<String> {
    val array = words.optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.getString(it) }
  }

  // word positions are 1-based and inclusive; a few ranges run past the end of the ayah
  private fun range(from: Int, to: Int, size: Int): IntRange =
    (from - 1).coerceIn(0, size) until to.coerceIn(0, size)

  companion object {
    private const val ASSET = "mutashabihat.mtj"

    @Volatile
    private var instance: Mutashabihat? = null

    /** loads the data set (blocking; call off the main thread) */
    fun get(context: Context): Mutashabihat =
      instance ?: synchronized(this) {
        instance ?: load(context.assets.open(ASSET)).also { instance = it }
      }

    fun load(stream: InputStream): Mutashabihat =
      GZIPInputStream(stream).bufferedReader().use { Mutashabihat(JSONObject(it.readText())) }
  }
}
