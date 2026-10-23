package com.quran.mobile.feature.qiraat.pages

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.qiraat.data.RiwayahLayout
import com.quran.mobile.feature.qiraat.model.Qiraat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(application = Application::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QiraatPagePreparerTest {
  @get:Rule val folder = TemporaryFolder()

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val preparer = QiraatPagePreparer(context)

  @Test
  fun everyPageHasAyahPositions() {
    QiraatPageProvider.all.values.forEach { provider ->
      val riwayah = provider.riwayah
      val layout = File("src/main/assets/${riwayah.layoutAsset}").inputStream()
        .use { RiwayahLayout.load(it) }
      val file = folder.newFile("ayahinfo_${riwayah.id}.db").apply { delete() }
      preparer.writeAyahInfo(file, PageLayout(layout, 1000, 1618))

      SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery(
          "SELECT COUNT(DISTINCT page_number), MIN(min_x), MAX(max_x), MIN(min_y), MAX(max_y), " +
            "COUNT(DISTINCT sura_number || ':' || ayah_number), MAX(line_number) FROM glyphs", null
        ).use { cursor ->
          cursor.moveToFirst()
          assertThat(cursor.getInt(0)).isEqualTo(604)
          assertThat(cursor.getInt(1)).isAtLeast(0)
          assertThat(cursor.getInt(2)).isAtMost(1000)
          assertThat(cursor.getInt(3)).isAtLeast(0)
          assertThat(cursor.getInt(4)).isAtMost(1618)
          assertThat(cursor.getInt(5)).isEqualTo(6236)
          assertThat(cursor.getInt(6)).isEqualTo(15)
        }
        // the pages hold the ayat of the Madinah mushaf's pages
        db.rawQuery(
          "SELECT sura_number, ayah_number FROM glyphs WHERE page_number = 50 " +
            "ORDER BY line_number, max_x DESC LIMIT 1", null
        ).use { cursor ->
          cursor.moveToFirst()
          assertThat(cursor.getInt(0) to cursor.getInt(1)).isEqualTo(3 to 1)
        }
      }
    }
  }

  @Test
  fun everyPageHasFifteenLines() {
    QiraatPageProvider.all.values.forEach { provider ->
      val layout = File("src/main/assets/${provider.riwayah.layoutAsset}").inputStream()
        .use { RiwayahLayout.load(it) }
      layout.pages.forEachIndexed { index, lines ->
        assertThat(lines.size).isEqualTo(if (index < 2) 8 else 15)
      }
      val pageLayout = PageLayout(layout, 1260, 2038)
      (1..604).forEach { page ->
        pageLayout.place(page).flatMap { it.words }.forEach { word ->
          assertThat(word.target.left).isAtLeast(0f)
          assertThat(word.target.right).isAtMost(1260f)
        }
      }
    }
  }

  @Test
  fun timingsDatabase() = runBlocking {
    val file = folder.newFile("timings.db").apply { delete() }
    preparer.writeTimings(Qiraat.byId("warsh")!!, file)

    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
      db.rawQuery("SELECT ayah, time FROM timings WHERE sura = 2 ORDER BY ayah", null).use {
        val times = generateSequence { if (it.moveToNext()) it.getInt(0) to it.getLong(1) else null }
          .toList()
        // every ayah, then the end of the sura as ayah 999
        assertThat(times.map { time -> time.first }).isEqualTo((1..286).toList() + 999)
        assertThat(times.map { time -> time.second }).isInOrder()
      }
    }
  }

  @Test
  fun everyRiwayahPageTypeHasAReciter() {
    QiraatPageProvider.all.values.forEach { provider ->
      val qaris = provider.getQaris()
      assertThat(qaris).isNotEmpty()
      // the default recording has every sura
      val default = qaris.first { it.id == provider.getDefaultQariId() }
      assertThat(default.isGapless && provider.riwayah.audio?.suras != null).isFalse()
    }
    assertThat(QiraatPageProvider.all.getValue("qiraat_hisham").getQaris()).hasSize(2)
    assertThat(QiraatPageProvider.all).hasSize(Qiraat.all.size - 1)
  }
}
