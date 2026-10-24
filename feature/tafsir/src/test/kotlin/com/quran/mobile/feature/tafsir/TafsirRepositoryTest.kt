package com.quran.mobile.feature.tafsir

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.tafsir.data.TafsirRepository
import com.quran.mobile.feature.tafsir.model.Tafaseer
import org.json.JSONArray
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = Application::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class TafsirRepositoryTest {
  @get:Rule val folder = TemporaryFolder()

  @Test
  fun sharedCommentaryIsStoredOnce() {
    val json = JSONArray(
      """[{"ayah":1,"surah":2,"text":"one"},{"ayah":2,"surah":2,"text":"two<br>lines"},
         {"ayah":3,"surah":2,"text":"two<br>lines"},{"ayah":4,"surah":2,"text":""}]"""
    )
    val file = folder.newFile("t.db").apply { delete() }
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
      db.execSQL("CREATE TABLE texts(id INTEGER PRIMARY KEY, text TEXT NOT NULL)")
      db.execSQL(
        "CREATE TABLE ayat(sura INTEGER NOT NULL, ayah INTEGER NOT NULL, " +
          "text_id INTEGER NOT NULL, PRIMARY KEY(sura, ayah))"
      )
      TafsirRepository.write(db, 2, json)
      db.rawQuery("SELECT COUNT(*) FROM texts", null).use {
        it.moveToFirst(); assertThat(it.getInt(0)).isEqualTo(2)
      }
      db.rawQuery("SELECT COUNT(*) FROM ayat", null).use {
        it.moveToFirst(); assertThat(it.getInt(0)).isEqualTo(3)
      }
    }
    assertThat(TafsirRepository.clean("a<br/>b <b>c</b>&amp;d")).isEqualTo("a\nb c&d")
  }

  @Test
  fun tafaseerInBothLanguages() {
    assertThat(Tafaseer.all.map { it.id }).containsAtLeast(
      "ar-tafsir-ibn-kathir", "ar-tafseer-al-saddi", "en-tafisr-ibn-kathir"
    )
    assertThat(Tafaseer.all.map { it.id }.toSet()).hasSize(Tafaseer.all.size)
  }
}
