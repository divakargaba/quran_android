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

    @Volatile private var instance: LibraryCatalog? = null

    fun get(context: Context): LibraryCatalog =
      instance ?: synchronized(this) {
        instance ?: context.applicationContext.assets.open(CATALOG).use(::parse).also { instance = it }
      }

    /** the text of a poem; call off the main thread */
    fun poemText(context: Context, id: String): PoemText =
      context.applicationContext.assets.open("$POEMS/$id.json").use(::parsePoemText)

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
        val audio = p.optJSONObject("audio")?.let { a ->
          val base = a.getString("base")
          PoemAudio(
            reciterArabic = a.getString("reciter_ar"),
            reciterEnglish = a.getString("reciter_en"),
            tracks = a.getJSONArray("tracks").objects().map { t ->
              AudioTrack(
                url = base + t.getString("f"),
                title = t.optString("t").takeIf { it.isNotEmpty() },
                chapters = t.optJSONArray("c")?.ints().orEmpty()
              )
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
          audio = audio,
          documents = p.optJSONArray("documents")?.strings().orEmpty(),
          verseDocument = p.optString("verse_document").takeIf { it.isNotEmpty() },
          versePages = p.optJSONArray("verse_pages")?.ints().orEmpty()
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
    private fun JSONArray.ints() = List(length()) { getInt(it) }
  }
}
