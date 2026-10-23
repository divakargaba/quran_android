package com.quran.mobile.feature.qiraat.pages

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.qiraat.data.RiwayahLocations
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
      val locations = File("src/main/assets/${riwayah.locationsAsset}").inputStream()
        .use { RiwayahLocations.load(it) }
      val bounds = RiwayahPages.textBounds.getValue(riwayah.id).let { RectF(it[0], it[1], it[2], it[3]) }
      val file = folder.newFile("ayahinfo_${riwayah.id}.db").apply { delete() }
      preparer.writeAyahInfo(file, locations, bounds, (1..604).associateWith { 1000 to 1400 })

      SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery(
          "SELECT COUNT(DISTINCT page_number), MIN(min_x), MAX(max_x), MIN(min_y), MAX(max_y), " +
            "COUNT(DISTINCT sura_number || ':' || ayah_number) FROM glyphs", null
        ).use { cursor ->
          cursor.moveToFirst()
          assertThat(cursor.getInt(0)).isEqualTo(604)
          assertThat(cursor.getInt(1)).isAtLeast(0)
          assertThat(cursor.getInt(2)).isAtMost(1000)
          assertThat(cursor.getInt(3)).isAtLeast(0)
          assertThat(cursor.getInt(4)).isAtMost(1400)
          assertThat(cursor.getInt(5)).isEqualTo(6236)
        }
      }
    }
  }

  @Test
  fun timingsDatabase() = runBlocking {
    val provider = QiraatPageProvider.all.getValue("qiraat_warsh")
    val file = folder.newFile("timings.db").apply { delete() }
    preparer.writeTimings(provider, file)

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
      assertThat(provider.getQaris()).hasSize(1)
      assertThat(provider.audioRiwayah.audio).isNotNull()
    }
    assertThat(QiraatPageProvider.all).hasSize(Qiraat.all.size - 1)
  }
}
