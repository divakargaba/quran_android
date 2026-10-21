package com.quran.mobile.feature.qiraat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.qiraat.audio.RiwayahPlayer
import com.quran.mobile.feature.qiraat.data.RiwayahRepository
import com.quran.mobile.feature.qiraat.model.Qiraat
import com.quran.mobile.feature.qiraat.ui.QiraatScreen
import com.quran.mobile.feature.qiraat.ui.clampAyah

/**
 * Reads the mushaf in any of the twenty riwayat of the ten qira'at, and compares an ayah across
 * all of them.
 */
class QiraatActivity : ComponentActivity() {
  private lateinit var player: RiwayahPlayer

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val repository = RiwayahRepository.get(this)
    player = RiwayahPlayer(this)

    val preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    val riwayah = Qiraat.byId(intent.getStringExtra(EXTRA_RIWAYAH))
      ?: Qiraat.byId(preferences.getString(PREF_LAST_RIWAYAH, null))
      ?: Qiraat.all.first()
    val ayah = clampAyah(intent.getIntExtra(EXTRA_SURA, 0), intent.getIntExtra(EXTRA_AYAH, 0))

    setContent {
      QuranTheme {
        QiraatScreen(
          repository = repository,
          player = player,
          initialRiwayah = riwayah,
          initialPage = intent.getIntExtra(EXTRA_PAGE, 1),
          initialAyah = ayah,
          startInCompare = intent.getBooleanExtra(EXTRA_COMPARE, false),
          onRiwayahChanged = { preferences.edit { putString(PREF_LAST_RIWAYAH, it.id) } },
          onFinish = ::finish
        )
      }
    }
  }

  override fun onDestroy() {
    player.release()
    super.onDestroy()
  }

  companion object {
    private const val EXTRA_PAGE = "page"
    private const val EXTRA_SURA = "sura"
    private const val EXTRA_AYAH = "ayah"
    private const val EXTRA_COMPARE = "compare"
    private const val EXTRA_RIWAYAH = "riwayah"
    private const val PREFERENCES = "qiraat"
    private const val PREF_LAST_RIWAYAH = "last_riwayah"

    /** opens the qira'at mushaf on a (Madinah mushaf) page */
    fun readerIntent(context: Context, page: Int): Intent =
      Intent(context, QiraatActivity::class.java).putExtra(EXTRA_PAGE, page)

    /** shows an ayah (Hafs numbering) in all the riwayat */
    fun compareIntent(context: Context, sura: Int, ayah: Int, page: Int): Intent =
      Intent(context, QiraatActivity::class.java)
        .putExtra(EXTRA_PAGE, page)
        .putExtra(EXTRA_SURA, sura)
        .putExtra(EXTRA_AYAH, ayah)
        .putExtra(EXTRA_COMPARE, true)
  }
}
