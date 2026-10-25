package com.quran.mobile.feature.tafsir.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.edit
import com.quran.mobile.feature.tafsir.model.Tafaseer
import com.quran.mobile.feature.tafsir.model.Tafsir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed interface TafsirState {
  data object NotDownloaded : TafsirState
  data class Downloading(val progress: Float) : TafsirState
  data object Downloaded : TafsirState
  data class Failed(val message: String?) : TafsirState
}

/** the tafsir of an ayah: the commentary on [firstAyah] to [lastAyah], which it is part of */
data class TafsirEntry(val sura: Int, val firstAyah: Int, val lastAyah: Int, val text: String)

/**
 * The downloaded tafaseer: each is kept as a small database (one row per ayah, pointing to the
 * commentary that covers it) in the app's files.
 */
class TafsirRepository private constructor(context: Context) {
  private val appContext = context.applicationContext
  private val directory = File(appContext.filesDir, "tafsir").apply { mkdirs() }
  private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val jobs = HashMap<String, Job>()
  private val client by lazy {
    OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
  }
  private val databases = HashMap<String, SQLiteDatabase>()

  private val _states = MutableStateFlow(
    Tafaseer.all.associate { tafsir ->
      tafsir.id to (if (file(tafsir).exists()) TafsirState.Downloaded else TafsirState.NotDownloaded)
    }
  )
  val states: StateFlow<Map<String, TafsirState>> = _states.asStateFlow()

  private val _selected = MutableStateFlow(preferences.getString(PREF_SELECTED, null))

  /** the tafsir last chosen in the tafsir panel */
  val selected: StateFlow<String?> = _selected.asStateFlow()

  fun select(tafsir: Tafsir) {
    preferences.edit { putString(PREF_SELECTED, tafsir.id) }
    _selected.value = tafsir.id
  }

  fun downloaded(): List<Tafsir> =
    Tafaseer.all.filter { _states.value[it.id] == TafsirState.Downloaded }

  private fun file(tafsir: Tafsir) = File(directory, tafsir.id + ".db")

  fun download(tafsir: Tafsir) {
    if (jobs[tafsir.id]?.isActive == true) return
    setState(tafsir, TafsirState.Downloading(0f))
    jobs[tafsir.id] = scope.launch {
      // kept between attempts: a failed or interrupted download continues where it stopped
      val partial = File(directory, tafsir.id + ".db.part")
      try {
        SQLiteDatabase.openOrCreateDatabase(partial, null).use { db ->
          db.execSQL("CREATE TABLE IF NOT EXISTS texts(id INTEGER PRIMARY KEY, text TEXT NOT NULL)")
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS ayat(sura INTEGER NOT NULL, ayah INTEGER NOT NULL, " +
              "text_id INTEGER NOT NULL, PRIMARY KEY(sura, ayah))"
          )
          db.execSQL("CREATE TABLE IF NOT EXISTS done(sura INTEGER PRIMARY KEY)")
          val done = HashSet<Int>()
          db.rawQuery("SELECT sura FROM done", null).use { while (it.moveToNext()) done.add(it.getInt(0)) }
          for (sura in 1..SURAS) {
            if (sura in done) continue
            coroutineContext.ensureActive()
            val json = fetch(tafsir.suraUrls(sura))
            write(db, sura, JSONArray(json))
            db.execSQL("INSERT INTO done VALUES ($sura)")
            setState(tafsir, TafsirState.Downloading(sura / SURAS.toFloat()))
          }
          db.execSQL("DROP TABLE done")
        }
        File(partial.path + "-journal").delete()
        if (!partial.renameTo(file(tafsir))) throw IOException("Unable to save the tafsir")
        setState(tafsir, TafsirState.Downloaded)
        if (_selected.value == null) select(tafsir)
      } catch (e: CancellationException) {
        setState(tafsir, TafsirState.NotDownloaded)
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "unable to download ${tafsir.id}", e)
        setState(tafsir, TafsirState.Failed(e.message))
      }
    }
  }

  fun cancel(tafsir: Tafsir) {
    jobs.remove(tafsir.id)?.cancel()
  }

  fun delete(tafsir: Tafsir) {
    cancel(tafsir)
    synchronized(databases) { databases.remove(tafsir.id)?.close() }
    file(tafsir).delete()
    File(directory, tafsir.id + ".db.part").delete()
    setState(tafsir, TafsirState.NotDownloaded)
  }

  /** the tafsir of an ayah, or null if the tafsir isn't downloaded or has none for it */
  suspend fun entry(tafsir: Tafsir, sura: Int, ayah: Int): TafsirEntry? = withContext(Dispatchers.IO) {
    val db = database(tafsir) ?: return@withContext null
    try {
      db.rawQuery(
        "SELECT a.text_id, t.text, (SELECT MIN(ayah) FROM ayat WHERE sura = a.sura AND " +
          "text_id = a.text_id), (SELECT MAX(ayah) FROM ayat WHERE sura = a.sura AND " +
          "text_id = a.text_id) FROM ayat a JOIN texts t ON t.id = a.text_id " +
          "WHERE a.sura = ? AND a.ayah = ?",
        arrayOf(sura.toString(), ayah.toString())
      ).use { cursor ->
        if (!cursor.moveToFirst()) return@withContext null
        TafsirEntry(sura, cursor.getInt(2), cursor.getInt(3), cursor.getString(1))
      }
    } catch (e: Exception) {
      null
    }
  }

  private fun database(tafsir: Tafsir): SQLiteDatabase? = synchronized(databases) {
    databases[tafsir.id]?.let { return it }
    val file = file(tafsir)
    if (!file.exists()) return null
    return try {
      SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        .also { databases[tafsir.id] = it }
    } catch (e: Exception) {
      null
    }
  }

  /** the first of [urls] that can be downloaded (each is tried a few times) */
  private suspend fun fetch(urls: List<String>): String {
    var failure: Exception? = null
    repeat(ATTEMPTS) { attempt ->
      for (url in urls) {
        coroutineContext.ensureActive()
        try {
          client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body?.string() ?: throw IOException("empty response")
            // make sure it is the json we expect, not an error page
            JSONArray(body)
            return body
          }
        } catch (e: IOException) {
          failure = e
        } catch (e: org.json.JSONException) {
          failure = e
        }
      }
      delay(RETRY_DELAY_MS * (attempt + 1))
    }
    throw IOException("unable to download", failure)
  }

  private fun setState(tafsir: Tafsir, state: TafsirState) {
    _states.update { it + (tafsir.id to state) }
  }

  companion object {
    private const val SURAS = 114
    private const val ATTEMPTS = 4
    private const val RETRY_DELAY_MS = 2000L
    private const val TAG = "TafsirRepository"
    private const val PREFERENCES = "tafsir"
    private const val PREF_SELECTED = "selected"

    @Volatile private var instance: TafsirRepository? = null

    fun get(context: Context): TafsirRepository =
      instance ?: synchronized(this) {
        instance ?: TafsirRepository(context).also { instance = it }
      }

    /**
     * writes one sura's tafsir: ayat that share their commentary (the same text in a row) point
     * to one copy of it
     */
    internal fun write(db: SQLiteDatabase, sura: Int, ayat: JSONArray) {
      db.beginTransaction()
      try {
        var lastText: String? = null
        var lastId = -1L
        val values = ContentValues()
        for (i in 0 until ayat.length()) {
          val item = ayat.getJSONObject(i)
          val text = clean(item.optString("text"))
          if (text.isBlank()) continue
          if (text != lastText) {
            values.clear()
            values.put("text", text)
            lastId = db.insert("texts", null, values)
            lastText = text
          }
          values.clear()
          values.put("sura", sura)
          values.put("ayah", item.getInt("ayah"))
          values.put("text_id", lastId)
          db.insertWithOnConflict("ayat", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        db.setTransactionSuccessful()
      } finally {
        db.endTransaction()
      }
    }

    /** plain text: some texts carry html line breaks and tags */
    internal fun clean(text: String): String =
      text.replace(Regex("(?i)<br\\s*/?>|</p>|</h\\d>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
  }
}
