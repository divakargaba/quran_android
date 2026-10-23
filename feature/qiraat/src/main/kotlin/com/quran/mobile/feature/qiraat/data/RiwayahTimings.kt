package com.quran.mobile.feature.qiraat.data

import android.content.Context
import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Where each (Hafs numbered) ayah starts and ends in a riwayah recording's sura file.
 *
 * Built by tools/build_timing.py and tools/silence_align.py. When the riwayah counts ayat
 * differently, ayat that fall in the same riwayah ayah share its times.
 *
 * File format (gzipped, big endian): "QRT1", u16 ayah count, then per ayah: u32 start ms,
 * u32 end ms (both 0xFFFFFFFF when the recording does not have that sura).
 */
class RiwayahTimings private constructor(private val times: LongArray) {

  /** start and end of the ayah in its sura's recording, in milliseconds, if known */
  fun timesFor(sura: Int, ayah: Int): LongRange? {
    val index = AyahIndex.indexOf(sura, ayah) * 2
    val start = times[index]
    if (start == MISSING) return null
    return start..times[index + 1]
  }

  companion object {
    private const val MAGIC = 0x51525431 // "QRT1"
    private const val MISSING = 0xFFFFFFFFL

    fun load(context: Context, asset: String): RiwayahTimings = load(context.assets.open(asset))

    fun load(stream: InputStream): RiwayahTimings {
      DataInputStream(GZIPInputStream(stream).buffered()).use { input ->
        require(input.readInt() == MAGIC) { "bad timings file" }
        val count = input.readUnsignedShort()
        val times = LongArray(count * 2) { input.readInt().toLong() and 0xFFFFFFFFL }
        return RiwayahTimings(times)
      }
    }
  }
}
