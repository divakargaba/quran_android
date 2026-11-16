package com.quran.labs.androidquran.ui.library

import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.mobile.feature.library.model.LibraryCatalog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class LibraryScreensTest {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  fun everyPoemOpens() {
    LibraryCatalog.get(context).poems.forEach { poem ->
      ActivityScenario.launch<PoemActivity>(PoemActivity.intent(context, poem.id)).use { scenario ->
        shadowOf(Looper.getMainLooper()).idle()
        scenario.onActivity { assertThat(it.isFinishing).isFalse() }
      }
    }
  }

  @Test
  fun aPoemThatIsNotThereCloses() {
    ActivityScenario.launch<PoemActivity>(PoemActivity.intent(context, "none")).use { scenario ->
      assertThat(scenario.state).isEqualTo(androidx.lifecycle.Lifecycle.State.DESTROYED)
    }
  }
}
