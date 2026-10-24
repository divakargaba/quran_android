package com.quran.labs.androidquran.ui.fragment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.R

/**
 * The frame of the compose ayah panels: the theme's surface (so that text and icons take the
 * theme's colours, light in the dark theme), and the translation panel's buttons to move to the
 * next and previous ayah at the bottom. [content] gets the padding that keeps it clear of them.
 */
@Composable
fun AyahPanelScaffold(
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  content: @Composable (PaddingValues) -> Unit
) {
  val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()) {
      content(PaddingValues(bottom = navigationBar + CONTROLS_HEIGHT))
      // as the translation panel: next on the left, previous on the right, in any language
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(bottom = navigationBar)
        ) {
          IconButton(onClick = onNext) {
            Icon(
              painterResource(R.drawable.ic_translation_next),
              contentDescription = stringResource(R.string.next),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Spacer(Modifier.weight(1f))
          IconButton(onClick = onPrevious) {
            Icon(
              painterResource(R.drawable.ic_translation_previous),
              contentDescription = stringResource(R.string.previous),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

private val CONTROLS_HEIGHT = 56.dp
