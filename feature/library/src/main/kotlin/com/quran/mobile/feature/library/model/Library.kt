package com.quran.mobile.feature.library.model

/** a didactic poem (matn) of the qira'at or of tajwid */
data class Poem(
  val id: String,
  val titleArabic: String,
  val titleEnglish: String,
  val authorArabic: String,
  val authorEnglish: String,
  val subjectArabic: String,
  val subjectEnglish: String,
  val verseCount: Int,
  /** where its text is from */
  val sourceArabic: String,
  val sourceEnglish: String,
  /** its recordings: the first is played unless another is chosen */
  val recitations: List<Recitation>,
  /** ids of its translations and commentaries (books) */
  val documents: List<String>,
  /** ids of those of them that can be shown under its verses, verse by verse */
  val inlineDocuments: List<String>
) {
  fun title(arabic: Boolean) = if (arabic) titleArabic else titleEnglish
  fun author(arabic: Boolean) = if (arabic) authorArabic else authorEnglish
  fun subject(arabic: Boolean) = if (arabic) subjectArabic else subjectEnglish
  fun source(arabic: Boolean) = if (arabic) sourceArabic else sourceEnglish
}

/** a recording of a poem, in tracks played one after the other */
data class Recitation(
  val id: String,
  val reciterArabic: String,
  val reciterEnglish: String,
  val tracks: List<AudioTrack>
) {
  fun reciter(arabic: Boolean) = if (arabic) reciterArabic else reciterEnglish
}

/** a track is streamed from where it is published ([url]) or is bundled with the app ([asset]) */
data class AudioTrack(val url: String?, val asset: String?)

/**
 * Where each verse of a poem (numbered from 1) is recited in a recording: its track, and the
 * time in it at which it starts. A verse the recording does not have has no track (-1).
 */
class VerseTimings(private val tracks: IntArray, private val starts: IntArray) {
  val verseCount: Int get() = tracks.size

  fun has(verse: Int): Boolean = verse in 1..tracks.size && tracks[verse - 1] >= 0
  fun trackOf(verse: Int): Int = tracks[verse - 1]
  fun startOf(verse: Int): Int = starts[verse - 1]

  /** the first verse the recording has from [verse] on */
  fun from(verse: Int): Int? = (verse.coerceAtLeast(1)..tracks.size).firstOrNull(::has)

  /** the last verse the recording has up to [verse] */
  fun upTo(verse: Int): Int? = (verse.coerceAtMost(tracks.size) downTo 1).firstOrNull(::has)

  fun firstVerseOf(track: Int): Int? = (1..tracks.size).firstOrNull { tracks[it - 1] == track }

  /** the verse being recited at a time of a track: the last that has started by then */
  fun verseAt(track: Int, positionMs: Int): Int? {
    var found: Int? = null
    for (verse in 1..tracks.size) {
      if (tracks[verse - 1] != track) {
        if (found != null) break else continue
      }
      if (starts[verse - 1] <= positionMs) found = verse else break
    }
    return found
  }
}

/**
 * Where a book (a translation or commentary of a poem, kept as a pdf) says what of each verse:
 * the parts of its pages that are shown under the verse. [width] is that of the book's text.
 * Lengths are in points of the pdf's pages.
 */
class InlineIndex(val width: Int, val entries: List<Entry>) {
  data class Region(val page: Int, val left: Int, val top: Int, val bottom: Int)

  /** of the verses [first] to [last]: their translation, and what else is said of them */
  data class Entry(
    val first: Int,
    val last: Int,
    val translation: List<Region>,
    val explanation: List<Region>
  )
}

/** the text of a poem: [first] is the number of a chapter's first verse */
data class PoemText(val chapters: List<Chapter>) {
  data class Chapter(val title: String, val first: Int, val verses: List<Verse>)
  data class Verse(val firstHalf: String, val secondHalf: String)
}

/** a book kept as a pdf: bundled with the app ([asset]) or downloaded from [url] */
data class Document(
  val id: String,
  val titleArabic: String,
  val titleEnglish: String,
  val authorArabic: String,
  val authorEnglish: String,
  /** "ar" or "en" */
  val language: String,
  val sizeBytes: Long,
  val pages: Int,
  val url: String?,
  val asset: String?
) {
  val isBundled: Boolean get() = asset != null
  fun title(arabic: Boolean) = if (arabic || titleEnglish.isEmpty()) titleArabic else titleEnglish
  fun author(arabic: Boolean) = if (arabic || authorEnglish.isEmpty()) authorArabic else authorEnglish
}

/** books on the principles (usul) of a qira'ah, or of them all */
data class UsoolGroup(val titleArabic: String, val titleEnglish: String, val documents: List<String>) {
  fun title(arabic: Boolean) = if (arabic) titleArabic else titleEnglish
}
