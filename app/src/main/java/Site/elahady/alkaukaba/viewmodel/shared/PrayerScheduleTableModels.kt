package site.elahady.alkaukaba.viewmodel.shared

// Bentuk tabel jadwal (kolom hari/tanggal + N kolom waktu sholat) yang dipakai bareng oleh
// Jadwal Imsakiyah (1 bulan Hijriyah) dan jadwal bulanan Waktu Sholat (1 bulan Masehi).
// Sebelumnya bernama ImsakiyahRow/ImsakiyahUiState (khusus fitur Imsakiyah), di-generic-kan
// supaya bisa dipakai ulang tanpa duplikasi.
data class PrayerScheduleTableRow(val dayLabel: Int, val gregorianLabel: String, val times: List<String>)

data class PrayerScheduleTableUiState(
    val monthLabel: String,
    val columnLabels: List<String>,
    val rows: List<PrayerScheduleTableRow>
)
