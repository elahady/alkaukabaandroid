package site.elahady.alkaukaba.notifikasi

import site.elahady.alkaukaba.R

/** Pemilih rekaman adzan penuh (res/raw), dipakai bersama oleh [AdzanPlaybackService] (saat waktu
 *  sholat tiba) dan pratinjau di Konfigurasi, supaya keduanya pasti memutar file yang sama. */
object AdzanSound {
    val STANDARD = R.raw.adzan_standar
    val SUBUH = R.raw.adzan_subuh

    /** Subuh punya rekaman sendiri karena adzannya memuat "as-shalatu khairun minan-naum". */
    fun forPrayer(prayerName: String): Int =
        if (prayerName == AdzanScheduler.PRAYER_SUBUH) SUBUH else STANDARD
}
