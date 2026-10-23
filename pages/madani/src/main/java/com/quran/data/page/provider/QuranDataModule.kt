package com.quran.data.page.provider

import com.quran.common.upgrade.LocalDataUpgrade
import com.quran.common.upgrade.PreferencesUpgrade
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.page.provider.madani.MadaniPageProvider
import com.quran.data.pageinfo.mapper.AyahMapper
import com.quran.data.pageinfo.mapper.IdentityAyahMapper
import com.quran.data.source.PageProvider
import com.quran.mobile.feature.qiraat.pages.QiraatPageProvider
import com.quran.page.common.draw.ImageDrawHelper
import com.quran.page.common.factory.PageViewFactoryProvider
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey


@BindingContainer
object QuranDataModule {

  @Provides
  fun providePageViewFactoryProvider(): PageViewFactoryProvider {
    return PageViewFactoryProvider { null }
  }

  @Named(DependencyInjectionConstants.FALLBACK_PAGE_TYPE)
  @JvmStatic
  @Provides
  fun provideFallbackPageType(): String = "madani"

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("madani")
  fun provideMadaniPageSet(): PageProvider {
    return MadaniPageProvider()
  }

  // the mushafs of the other riwayat of the ten qira'at
  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_qalun")
  fun provideQiraatQalunPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_qalun")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_warsh")
  fun provideQiraatWarshPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_warsh")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_bazzi")
  fun provideQiraatBazziPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_bazzi")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_qunbul")
  fun provideQiraatQunbulPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_qunbul")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_duri_abuamr")
  fun provideQiraatDuriAbuamrPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_duri_abuamr")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_susi")
  fun provideQiraatSusiPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_susi")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_hisham")
  fun provideQiraatHishamPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_hisham")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_ibndhakwan")
  fun provideQiraatIbndhakwanPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_ibndhakwan")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_shubah")
  fun provideQiraatShubahPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_shubah")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_khalaf")
  fun provideQiraatKhalafPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_khalaf")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_khallad")
  fun provideQiraatKhalladPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_khallad")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_abulharith")
  fun provideQiraatAbulharithPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_abulharith")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_duri_kisai")
  fun provideQiraatDuriKisaiPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_duri_kisai")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_ibnwardan")
  fun provideQiraatIbnwardanPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_ibnwardan")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_ibnjammaz")
  fun provideQiraatIbnjammazPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_ibnjammaz")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_ruways")
  fun provideQiraatRuwaysPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_ruways")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_rawh")
  fun provideQiraatRawhPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_rawh")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_ishaq")
  fun provideQiraatIshaqPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_ishaq")

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("qiraat_idris")
  fun provideQiraatIdrisPageSet(): PageProvider = QiraatPageProvider.all.getValue("qiraat_idris")

  @JvmStatic
  @Provides
  @ElementsIntoSet
  fun provideImageDrawHelpers(): Set<ImageDrawHelper> {
    return emptySet()
  }

  @JvmStatic
  @Provides
  fun provideLocalDataUpgrade(): LocalDataUpgrade = object : LocalDataUpgrade {  }

  @JvmStatic
  @Provides
  fun providePreferencesUpgrade(): PreferencesUpgrade = PreferencesUpgrade { _, _, _ -> true }

  @JvmStatic
  @Provides
  fun provideAyahMapper(): AyahMapper = IdentityAyahMapper()
}
