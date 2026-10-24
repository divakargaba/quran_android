package com.quran.labs.androidquran.qiraat

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.qiraat.data.DownloadState
import com.quran.mobile.feature.qiraat.data.QiraatDifferences
import com.quran.mobile.feature.qiraat.data.RiwayahLayout
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.pages.QiraatPageProvider
import com.quran.mobile.feature.qiraat.ui.CompareList
import com.quran.mobile.feature.qiraat.ui.DifferencesPanel
import com.quran.mobile.feature.qiraat.ui.DifferencesState
import com.quran.mobile.feature.tafsir.data.TafsirEntry
import com.quran.mobile.feature.tafsir.model.Tafaseer
import com.quran.mobile.feature.tafsir.ui.TafsirPanel
import com.quran.mobile.feature.tafsir.ui.TafsirPanelState
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = Application::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QiraatInAppTest {

  @Test
  fun showsComparison() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    // a mushaf that is "downloaded" but can't be opened must not crash the panel
    java.io.File(context.filesDir, "qiraat").mkdirs()
    java.io.File(context.filesDir, "qiraat/qalun.pdf").writeBytes(ByteArray(16))
    val repository = RiwayahRepository.get(context)
    Robolectric.buildActivity(ComponentActivity::class.java).use { controller ->
      val activity = controller.setup().get()
      activity.setContentView(
        ComposeView(activity).apply {
          setContent {
            QuranTheme {
              CompareList(
                sura = 2,
                ayah = 255,
                repository = repository,
                downloadStates = mapOf("qalun" to DownloadState.Downloaded),
                canPlay = { true },
                onPlay = {},
                onOpenInReader = {}
              )
            }
          }
        }
      )
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(activity.isFinishing).isFalse()
    }
  }

  @Test
  fun showsDifferencesAndTafsirPanels() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val words = QiraatDifferences.get(context).between("hafs", "qalun", 2, 98)
    assertThat(words).isNotEmpty()
    Robolectric.buildActivity(ComponentActivity::class.java).use { controller ->
      val activity = controller.setup().get()
      activity.setContentView(
        ComposeView(activity).apply {
          setContent {
            QuranTheme {
              Column {
                DifferencesPanel(
                  state = DifferencesState.Loaded(words),
                  base = Qiraat.byId("hafs")!!,
                  compareTo = Qiraat.byId("qalun")!!,
                  onCompareToChange = {},
                  arabicFont = FontFamily.Serif
                )
                TafsirPanel(
                  state = TafsirPanelState.Loaded(TafsirEntry(2, 98, 98, "tafsir")),
                  tafaseer = Tafaseer.all.take(2),
                  selected = Tafaseer.all.first(),
                  onSelect = {},
                  onManage = {}
                )
              }
            }
          }
        }
      )
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(activity.isFinishing).isFalse()
    }
  }

  @Test
  fun bundledAyahLocationsLoadForEveryRiwayah() {
    val repository = RiwayahRepository.get(ApplicationProvider.getApplicationContext())
    Qiraat.all.forEach { riwayah ->
      val locations = runBlocking { repository.locations(riwayah) }
      assertThat(locations).isNotNull()
      assertThat(locations!!.pageFor(2, 255)).isIn(41..43)
    }
  }

  @Test
  fun bundledLayoutsLoadForEveryRiwayahPageType() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    QiraatPageProvider.all.values.forEach { provider ->
      val layout = RiwayahLayout.load(context, provider.riwayah.layoutAsset)
      assertThat(layout.pages).hasSize(604)
    }
  }

  @Test
  fun everyAyahCanBeRecitedInEveryRiwayah() {
    Qiraat.all.forEach { riwayah ->
      (1..114).forEach { sura ->
        assertThat(QiraatPageProvider.qariFor(riwayah, sura)).isNotNull()
      }
    }
  }
}
