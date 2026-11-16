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
  val audio: PoemAudio?,
  /** ids of its translations and commentaries */
  val documents: List<String>,
  /** a document that has every verse, and the page (from 0) of each: opened from a verse */
  val verseDocument: String?,
  val versePages: List<Int>
) {
  fun title(arabic: Boolean) = if (arabic) titleArabic else titleEnglish
  fun author(arabic: Boolean) = if (arabic) authorArabic else authorEnglish
  fun subject(arabic: Boolean) = if (arabic) subjectArabic else subjectEnglish
  fun source(arabic: Boolean) = if (arabic) sourceArabic else sourceEnglish
}

/** a recording of a poem, in tracks played one after the other */
data class PoemAudio(
  val reciterArabic: String,
  val reciterEnglish: String,
  val tracks: List<AudioTrack>
) {
  fun reciter(arabic: Boolean) = if (arabic) reciterArabic else reciterEnglish

  /** the track in which a chapter is recited, if the recording is divided as the poem is */
  fun trackOfChapter(chapter: Int): Int? = tracks.indexOfFirst { chapter in it.chapters }.takeIf { it >= 0 }
}

/** [title] is null for a track of chapters ([chapters]: their indices), named after the first */
data class AudioTrack(val url: String, val title: String?, val chapters: List<Int>)

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
