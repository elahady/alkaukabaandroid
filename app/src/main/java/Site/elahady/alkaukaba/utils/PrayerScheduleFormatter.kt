package site.elahady.alkaukaba.utils

import site.elahady.alkaukaba.api.Timings
import site.elahady.alkaukaba.viewmodel.waktusholat.PrayerKind
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Format 1 baris jadwal (8 kolom waktu) dari [Timings] Aladhan — dipakai bareng oleh Jadwal
 * Imsakiyah (1 bulan Hijriyah) dan jadwal bulanan Waktu Sholat (1 bulan Masehi), supaya kedua
 * fitur menampilkan waktu dengan aturan yang identik (termasuk Dhuha = Sunrise + 15 menit).
 * Dipindah dari `JadwalImsakiyahViewModel` (awalnya cuma dipakai fitur itu sendiri).
 */
object PrayerScheduleFormatter {

    private const val DHUHA_OFFSET_MINUTES = 15

    val COLUMN_ORDER = listOf(
        PrayerKind.TSULUTSUL_LAIL, PrayerKind.IMSAK, PrayerKind.SUBUH, PrayerKind.DHUHA,
        PrayerKind.DZUHUR, PrayerKind.ASHAR, PrayerKind.MAGHRIB, PrayerKind.ISYA
    )
    val COLUMN_LABELS = listOf(
        "Sepertiga\nMalam", "Imsak", "Subuh", "Dhuha", "Dzuhur", "Ashar", "Maghrib", "Isya"
    )

    fun buildTimes(timings: Timings?): List<String> {
        if (timings == null) return COLUMN_ORDER.map { "-" }
        val dhuha = timings.sunrise?.take(5)?.let { addMinutes(it, DHUHA_OFFSET_MINUTES) } ?: "-"
        return COLUMN_ORDER.map { kind ->
            when (kind) {
                PrayerKind.TSULUTSUL_LAIL -> timings.lastThird?.take(5) ?: "-"
                PrayerKind.IMSAK -> timings.imsak?.take(5) ?: "-"
                PrayerKind.SUBUH -> timings.Fajr.take(5)
                PrayerKind.DHUHA -> dhuha
                PrayerKind.DZUHUR -> timings.Dhuhr.take(5)
                PrayerKind.ASHAR -> timings.Asr.take(5)
                PrayerKind.MAGHRIB -> timings.Maghrib.take(5)
                PrayerKind.ISYA -> timings.Isha.take(5)
            }
        }
    }

    private fun addMinutes(time: String, minutesToAdd: Int): String {
        return try {
            val parts = time.split(":")
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            cal.set(Calendar.MINUTE, parts[1].toInt())
            cal.set(Calendar.SECOND, 0)
            cal.add(Calendar.MINUTE, minutesToAdd)
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
        } catch (e: Exception) {
            time
        }
    }
}
