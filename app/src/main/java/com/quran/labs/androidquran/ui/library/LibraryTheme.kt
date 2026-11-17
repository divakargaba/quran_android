package com.quran.labs.androidquran.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme

/**
 * The app's theme with the colours of its lists of suras and juz' (the background, the titles
 * and the details under them), for the screens of the poems and of the principles: they are
 * beside those lists, and are read as they are.
 */
@Composable
fun LibraryTheme(content: @Composable () -> Unit) {
  QuranTheme {
    val surface = colorResource(R.color.surface)
    val title = colorResource(R.color.title_color)
    val details = colorResource(R.color.sura_details_color)
    MaterialTheme(
      colorScheme = MaterialTheme.colorScheme.copy(
        surface = surface,
        background = surface,
        onSurface = title,
        onBackground = title,
        onSurfaceVariant = details,
        surfaceContainer = surface,
        surfaceContainerLow = surface,
        surfaceContainerHigh = surface
      ),
      typography = MaterialTheme.typography,
      content = content
    )
  }
}
