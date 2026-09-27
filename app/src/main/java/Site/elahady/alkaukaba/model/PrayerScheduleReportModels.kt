package site.elahady.alkaukaba.model

import java.io.Serializable

// DTO laporan cetak PDF yang dipakai bareng oleh Jadwal Imsakiyah (tabel 1 bulan Ramadhan) dan
// jadwal bulanan Waktu Sholat (tabel 1 bulan Masehi pilihan user) - strukturnya identik (kolom
// tanggal + N kolom waktu), lihat LaporanJadwalActivity.

data class PrayerScheduleReportRow(val dateLabel: String, val times: List<String>) : Serializable

data class PrayerScheduleReportData(
    val reportTitle: String, // "JADWAL IMSAKIYAH" / "JADWAL WAKTU SHOLAT BULANAN"
    val monthLabel: String, // "Ramadhan 1447 H" / "September 2026"
    val locationLabel: String, // "Jakarta, DKI Jakarta (Lat -6.2088, Lng 106.8456)"
    val columnLabels: List<String>,
    val rows: List<PrayerScheduleReportRow>,
    val fileNamePrefix: String // "Jadwal_Imsakiyah" / "Jadwal_Sholat_Bulanan"
) : Serializable // supaya bisa dikirim lewat Intent extra ke LaporanJadwalActivity, pola sama HilalResult
