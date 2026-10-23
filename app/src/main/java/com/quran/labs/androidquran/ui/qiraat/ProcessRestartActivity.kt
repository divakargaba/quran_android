package com.quran.labs.androidquran.ui.qiraat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.core.content.IntentCompat

/**
 * Runs in its own process: ends the app's main process, then starts the app again with the
 * given intent, so that it starts with a clean slate (after switching to another riwayah).
 */
class ProcessRestartActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Process.killProcess(intent.getIntExtra(EXTRA_PID, -1))
    IntentCompat.getParcelableExtra(intent, EXTRA_INTENT, Intent::class.java)
      ?.let { startActivity(it) }
    finish()
    Runtime.getRuntime().exit(0)
  }

  companion object {
    const val EXTRA_INTENT = "intent"
    const val EXTRA_PID = "pid"
  }
}
