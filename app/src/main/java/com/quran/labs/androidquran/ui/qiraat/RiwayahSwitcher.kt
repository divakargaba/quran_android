package com.quran.labs.androidquran.ui.qiraat

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.feature.qiraat.model.Riwayah
import com.quran.mobile.feature.qiraat.pages.QiraatPageProvider

/**
 * Switches the reader between the Madinah (Hafs) mushaf and the mushafs of the other riwayat,
 * which are page types like any other.
 */
object RiwayahSwitcher {
  private const val HAFS_PAGE_TYPE = "madani"

  /** the page type of a riwayah's mushaf (Hafs is the Madinah mushaf) */
  fun pageTypeOf(riwayah: Riwayah) =
    if (riwayah.id == "hafs") HAFS_PAGE_TYPE else QiraatPageProvider.PAGE_TYPE_PREFIX + riwayah.id

  fun isQiraatPageType(pageType: String?) =
    pageType?.startsWith(QiraatPageProvider.PAGE_TYPE_PREFIX) == true

  /** asks which riwayah to read, then reopens the reader at the same ayah in it */
  fun showPicker(activity: Activity, sura: Int, ayah: Int, page: Int) {
    val settings = QuranSettings.getInstance(activity)
    val current = settings.pageType ?: HAFS_PAGE_TYPE
    val types = listOf(HAFS_PAGE_TYPE) + QiraatPageProvider.all.keys
    val names = listOf(activity.getString(R.string.riwayah_hafs_madinah)) +
      QiraatPageProvider.all.values.map { activity.getString(it.getPreviewTitle()) }
    val selected = types.indexOf(current).coerceAtLeast(0)
    AlertDialog.Builder(activity)
      .setTitle(R.string.menu_riwayah)
      .setSingleChoiceItems(names.toTypedArray(), selected) { dialog, which ->
        dialog.dismiss()
        if (which != selected) switchTo(activity, types[which], sura, ayah, page)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  /**
   * switches to [pageType] and opens it at the ayah. The process is restarted, since the reciters
   * and other per page type data are held for the life of the process.
   */
  fun switchTo(activity: Activity, pageType: String, sura: Int, ayah: Int, page: Int) {
    QuranSettings.getInstance(activity).pageType = pageType
    val intent = QuranDataActivity.openPageIntent(activity, page, sura, ayah)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    restart(activity, intent)
  }

  /** starts [intent] in a fresh process */
  fun restart(context: Context, intent: Intent) {
    context.startActivity(
      Intent(context, ProcessRestartActivity::class.java)
        .putExtra(ProcessRestartActivity.EXTRA_INTENT, intent)
        .putExtra(ProcessRestartActivity.EXTRA_PID, android.os.Process.myPid())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    (context as? Activity)?.finishAffinity()
  }
}
