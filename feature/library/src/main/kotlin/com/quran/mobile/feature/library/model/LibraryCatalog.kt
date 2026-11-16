package com.quran.mobile.feature.library.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * The poems, the books on the principles of the qira'at, and the documents of both (assets/
 * library/catalog.json; the poems' texts are in assets/library/poems, made by tools/
 * build_poems.py).
 */
class LibraryCatalog(
  val poems: List<Poem>,
  val usool: List<UsoolGroup>,
  private val documentsById: Map<String, Document>
) {
  val documents: Collection<Document> get() = documentsById.values

  fun poem(id: String): Poem? = poems.firstOrNull { it.id == id }
  fun document(id: String): Document? = documentsById[id]
  fun documentsOf(ids: List<String>): List<Document> = ids.mapNotNull(documentsById::get)

  companion object {
    private const val CATALOG = "library/catalog.json"
    private const val POEMS = "library/poems"
    private const val TIMINGS = "library/timings"
    private const val INLINE = "library/inline"

    @Volatile private var instance: LibraryCatalog? = null

    fun get(context: Context): LibraryCatalog =
      instance ?: synchronized(this) {
        instance ?: context.applicationContext.assets.open(CATALOG).use(::parse).also { instance = it }
      }

    /** the text of a poem; call off the main thread */
    fun poemText(context: Context, id: String): PoemText =
      context.applicationContext.assets.open("$POEMS/$id.json").use(::parsePoemText)

    /** where a recording recites each verse; call off the main thread */
    fun timings(context: Context, recitation: Recitation): VerseTimings =
      context.applicationContext.assets.open("$TIMINGS/${recitation.id}.json").use(::parseTimings)

    /** where a book says what of each verse of its poem; call off the main thread */
    fun inlineIndex(context: Context, documentId: String): InlineIndex =
      context.applicationContext.assets.open("$INLINE/$documentId.json").use(::parseInlineIndex)

    fun parseTimings(stream: InputStream): VerseTimings {
      val verses = JSONObject(stream.bufferedReader().readText()).getJSONArray("v")
      val tracks = IntArray(verses.length()) { -1 }
      val starts = IntArray(verses.length())
      for (i in 0 until verses.length()) {
        val verse = verses.optJSONArray(i) ?: continue
        tracks[i] = verse.getInt(0)
        starts[i] = verse.getInt(1)
      }
      return VerseTimings(tracks, starts)
    }

    fun parseInlineIndex(stream: InputStream): InlineIndex {
      val root = JSONObject(stream.bufferedReader().readText())
      fun regions(array: JSONArray) = List(array.length()) { i ->
        array.getJSONArray(i).let { InlineIndex.Region(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3)) }
      }
      val entries = root.getJSONArray("e")
      return InlineIndex(
        width = root.getInt("w"),
        entries = List(entries.length()) { i ->
          entries.getJSONArray(i).let {
            InlineIndex.Entry(it.getInt(0), it.getInt(1), regions(it.getJSONArray(2)), regions(it.getJSONArray(3)))
          }
        }
      )
    }

    fun parse(stream: InputStream): LibraryCatalog {
      val root = JSONObject(stream.bufferedReader().readText())
      val documents = root.getJSONObject("documents").let { all ->
        all.keys().asSequence().associateWith { id ->
          val d = all.getJSONObject(id)
          Document(
            id = id,
            titleArabic = d.optString("title_ar"),
            titleEnglish = d.optString("title_en"),
            authorArabic = d.optString("author_ar"),
            authorEnglish = d.optString("author_en"),
            language = d.optString("lang", "ar"),
            sizeBytes = d.optLong("size"),
            pages = d.optInt("pages"),
            url = d.optString("url").takeIf { it.isNotEmpty() },
            asset = d.optString("asset").takeIf { it.isNotEmpty() }
          )
        }
      }
      val poems = root.getJSONArray("poems").objects().map { p ->
        val recitations = p.optJSONArray("recitations")?.objects().orEmpty().map { r ->
          val base = r.optString("base")
          Recitation(
            id = r.getString("id"),
            reciterArabic = r.getString("reciter_ar"),
            reciterEnglish = r.getString("reciter_en"),
            tracks = r.getJSONArray("tracks").strings().map { name ->
              if (base.isEmpty()) AudioTrack(url = null, asset = name) else AudioTrack(url = base + name, asset = null)
            }
          )
        }
        Poem(
          id = p.getString("id"),
          titleArabic = p.getString("title_ar"),
          titleEnglish = p.getString("title_en"),
          authorArabic = p.getString("author_ar"),
          authorEnglish = p.getString("author_en"),
          subjectArabic = p.getString("subject_ar"),
          subjectEnglish = p.getString("subject_en"),
          verseCount = p.getInt("verses"),
          sourceArabic = p.optString("source_ar"),
          sourceEnglish = p.optString("source_en"),
          recitations = recitations,
          documents = p.optJSONArray("documents")?.strings().orEmpty(),
          inlineDocuments = p.optJSONArray("inline")?.strings().orEmpty()
        )
      }
      val usool = root.getJSONArray("usool").objects().map { g ->
        UsoolGroup(g.getString("title_ar"), g.getString("title_en"), g.getJSONArray("documents").strings())
      }
      return LibraryCatalog(poems, usool, documents)
    }

    fun parsePoemText(stream: InputStream): PoemText {
      val root = JSONObject(stream.bufferedReader().readText())
      var first = 1
      val chapters = root.getJSONArray("chapters").objects().map { c ->
        val verses = c.getJSONArray("v").let { v ->
          List(v.length()) { i -> v.getJSONArray(i).let { PoemText.Verse(it.getString(0), it.getString(1)) } }
        }
        PoemText.Chapter(c.getString("t"), first, verses).also { first += verses.size }
      }
      return PoemText(chapters)
    }

    private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
    private fun JSONArray.strings() = List(length()) { getString(it) }
  }
}
