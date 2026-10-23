package com.quran.mobile.feature.qiraat.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.quran.mobile.feature.qiraat.model.Qari
import com.quran.mobile.feature.qiraat.model.Riwayah

@Composable
fun isArabicLocale(): Boolean =
  LocalConfiguration.current.locales[0].language == "ar"

fun Qari.displayName(arabic: Boolean) = if (arabic) nameAr else nameEn
fun Riwayah.displayName(arabic: Boolean) = if (arabic) nameAr else nameEn
