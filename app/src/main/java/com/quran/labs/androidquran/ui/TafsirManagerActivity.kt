package com.quran.labs.androidquran.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.tafsir.R
import com.quran.mobile.feature.tafsir.data.TafsirRepository
import com.quran.mobile.feature.tafsir.ui.TafsirManagerScreen

/** downloads and deletes tafaseer, as the translation manager does translations */
class TafsirManagerActivity : AppCompatActivity() {

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportActionBar?.hide()
    val repository = TafsirRepository.get(this)
    setContentView(
      ComposeView(this).apply {
        setContent {
          QuranTheme {
            Scaffold(
              topBar = {
                TopAppBar(
                  title = { Text(stringResource(R.string.tafsir_manager)) },
                  navigationIcon = {
                    IconButton(onClick = ::finish) {
                      Icon(QuranIcons.ArrowBack, contentDescription = null)
                    }
                  }
                )
              },
              containerColor = MaterialTheme.colorScheme.surface
            ) { padding ->
              val states by repository.states.collectAsState()
              TafsirManagerScreen(
                states = states,
                onDownload = repository::download,
                onCancel = repository::cancel,
                onDelete = repository::delete,
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
              )
            }
          }
        }
      }
    )
  }
}
