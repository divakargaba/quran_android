package com.quran.labs.androidquran.qiraat

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.qiraat.QiraatActivity
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.model.Qiraat
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
  fun opensReader() {
    val intent = QiraatActivity.readerIntent(ApplicationProvider.getApplicationContext(), 50)
    Robolectric.buildActivity(QiraatActivity::class.java, intent).use { controller ->
      val activity = controller.setup().get()
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(activity.isFinishing).isFalse()
    }
  }

  @Test
  fun opensComparison() {
    val intent =
      QiraatActivity.compareIntent(ApplicationProvider.getApplicationContext(), 2, 255, 42)
    Robolectric.buildActivity(QiraatActivity::class.java, intent).use { controller ->
      val activity = controller.setup().get()
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(activity.isFinishing).isFalse()
    }
  }

  @Test
  fun opensDownloadedMushaf() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    java.io.File(context.filesDir, "qiraat").mkdirs()
    java.io.File(context.filesDir, "qiraat/qalun.pdf").writeBytes(ByteArray(16))
    val intent = QiraatActivity.compareIntent(context, 2, 255, 42)
    Robolectric.buildActivity(QiraatActivity::class.java, intent).use { controller ->
      val activity = controller.setup().get()
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
}
