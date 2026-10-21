package com.quran.mobile.feature.qiraat

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = Application::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QiraatActivityTest {

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
}
