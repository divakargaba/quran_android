package com.quran.mobile.feature.qiraat.pages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RiwayahDataSourceTest {

  @Test
  fun pageDataIsConsistent() {
    QiraatPageProvider.all.values.forEach { provider ->
      val data = provider.getDataSource()
      assertThat(data.suraForPageArray).hasLength(604)
      assertThat(data.ayahForPageArray).hasLength(604)
      assertThat(data.pageForSuraArray).hasLength(114)
      assertThat(data.pageForSuraArray.toList()).isInOrder()
      assertThat(data.pageForSuraArray.first()).isEqualTo(1)
      assertThat(data.pageForSuraArray.last()).isEqualTo(604)
      data.pageForSuraArray.forEachIndexed { index, page ->
        // the sura has begun by the end of its page
        assertThat(data.suraForPageArray[page - 1]).isAtMost(index + 1)
      }
    }
  }
}
