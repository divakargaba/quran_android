package com.quran.mobile.feature.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** whether the app is shown in Arabic */
@Composable
internal fun isArabicLocale(): Boolean =
  LocalConfiguration.current.locales.get(0)?.language == "ar"
