# Jadwal Imsakiyah

## 1. Ringkasan

**Fitur**: Jadwal Imsakiyah — tabel semua waktu sholat (Sepertiga Malam
Akhir, Imsak, Subuh, Dhuha, Dzuhur, Ashar, Maghrib, Isya) untuk 1 bulan
Ramadhan penuh, satu baris per hari.

Kolom tanggal (kolom pertama) **freeze** — tetap diam di kiri layar saat
kolom-kolom waktu sholat digeser horizontal (lihat section 4).

**Revisi 2026-09-28** (per permintaan user, lihat section 3/4 untuk detail):
- **Dikunci ke Ramadhan saja** — navigasi ◀/▶ bebas ke bulan Hijriyah
  manapun (versi awal) dihapus. Layar ini sekarang selalu mencari &
  menampilkan Ramadhan terdekat ke depan (`JadwalImsakiyahViewModel
  .loadRamadhan()`). Kebutuhan "cek beberapa bulan Hijriyah berdekatan"
  dianggap tidak relevan untuk fitur ini — kalau user butuh jadwal sholat
  bulan lain (bulan Masehi bebas), itu dipindah ke fitur Waktu Sholat
  (lihat `docs/features/waktu-sholat.md`).
- **Koordinat/kota dasar perhitungan ditampilkan** (`tvLocationInfo`, di
  bawah label bulan) dan **bisa dipilih dari daftar kota** — konsepnya
  disamakan dengan Hisab Awal Bulan Nasional: daftar 38 ibu kota provinsi
  yang sama (`HisabNasionalCalculator.allMarkaz`), dibuka lewat ikon ⚙️ di
  toolbar (`PilihKotaActivity`, single-select, baru — beda dari
  `PilihMarkazActivity` yang checklist multi-select untuk Hisab Nasional).
  Pilihan kota Imsakiyah **tidak dibagi** dengan pilihan kota Waktu Sholat
  (setting tersimpan terpisah, keputusan produk eksplisit).
- **Cetak PDF** — tombol "CETAK PDF" baru, render laporan lewat
  `LaporanJadwalActivity` (dipakai bareng fitur Waktu Sholat) dan unduh
  lewat `HilalPdfService` yang sama dipakai fitur Awal Bulan Hijriyah.

## 2. Entry point & prasyarat

- Layar: `JadwalImsakiyahActivity` (layout `activity_jadwal_imsakiyah.xml`).
- Dibuka dari tombol "Jadwal Imsakiyah" di home (`bt_jadwal_imsakiyah`,
  posisi setelah "Hisab Nasional") dan dari "Semua Menu" (posisi sama,
  setelah "Hisab Awal Bulan Nasional").
- Per 2026-09-16: menu "Doa & Dzikir" dihapus dari grid home (2x4, sudah
  penuh — lihat `docs/features/hisab-nasional.md` §2/§7) untuk kasih tempat
  menu ini; "Doa & Dzikir" **masih ada** di "Semua Menu", tidak dihapus dari
  aplikasi.
- Prasyarat: sama seperti Waktu Sholat — lokasi (kota pilihan tersimpan
  lewat ⚙️, atau GPS + permission, atau lokasi manual dari Konfigurasi,
  fallback Jakarta) dan koneksi network (data diambil dari Aladhan API).

## 3. Titik masuk logika & navigasi

- `HijriCalendarEngine.monthRangeForOffset(observer, referenceDate,
  monthOffset): HijriMonthRange` (`utils/HijriCalendarEngine.kt`) — titik
  masuk untuk "kasih tanggal 1 Masehi + jumlah hari 1 bulan Hijriyah yang
  digeser N bulan dari bulan yang memuat `referenceDate`". Reuse mesin
  hisab yang sama dengan kalender home/Awal Bulan (ijtima' + ghurub +
  kriteria Neo-MABIMS + istikmal), bukan `HijriDateUtil` tabular.
- `JadwalImsakiyahViewModel.loadRamadhan(lat, lng)` — entry point utama
  fitur (ganti nama dari `loadMonth(lat, lng, offset)` versi lama). Loop
  `monthRangeForOffset` dengan offset 0..12 sampai ketemu `monthName ==
  "Ramadhan"` (batas aman 12 iterasi = 1 tahun Hijriyah), lalu load jadwal
  1 bulan itu — **tidak ada lagi** `nextMonth()`/`prevMonth()`/`monthOffset`
  tersimpan, fitur ini sekarang murni "tampilkan Ramadhan terdekat".
- `HisabNasionalCalculator.resolveMarkaz(id)` — lookup `MarkazNasional` dari
  id kota tersimpan (`SessionManager.getSelectedMarkazImsakiyahId()`). Kalau
  ada, `JadwalImsakiyahActivity.checkLocationPermission()` pakai koordinat
  markaz itu langsung, melewati cabang GPS/manual/fallback yang sudah ada.
- Navigasi ke layar ini: `Intent(context, JadwalImsakiyahActivity::class.java)`
  dari `MainActivity`/`SemuaMenuActivity`. Dari dalam layar ini, ikon ⚙️ →
  `PilihKotaActivity` (ganti kota), tombol "CETAK PDF" →
  `LaporanJadwalActivity` (lihat `docs/features/waktu-sholat.md` untuk
  detail activity yang dipakai bareng ini). Tombol back toolbar → keluar.

## 4. Struktur & alur data

| File | Peran |
|---|---|
| `ui/jadwalimsakiyah/JadwalImsakiyahActivity.kt` + `activity_jadwal_imsakiyah.xml` | UI: toolbar (+ ikon ⚙️ pilih kota), label bulan + info lokasi (`tvLocationInfo`), tabel (di-build programatic ke `layoutImsakiyahTable`), tombol "CETAK PDF". Resolusi lokasi (kota tersimpan → manual global → GPS → fallback) dicopy polanya dari `WaktuSholatActivity`. |
| `viewmodel/jadwalimsakiyah/JadwalImsakiyahViewModel.kt` + `JadwalImsakiyahViewModelFactory.kt` | `LiveData<PrayerScheduleTableUiState>` (monthLabel, columnLabels, rows), `isLoading`, `errorMessage`. Entry point `loadRamadhan(lat, lng)`. |
| `viewmodel/shared/PrayerScheduleTableModels.kt` | `PrayerScheduleTableRow`/`PrayerScheduleTableUiState` (baru, generic — rename dari `ImsakiyahRow`/`ImsakiyahUiState` supaya bisa dipakai ulang oleh jadwal bulanan Waktu Sholat). |
| `utils/PrayerScheduleFormatter.kt` | `COLUMN_ORDER`/`COLUMN_LABELS`/`buildTimes()` (baru, dipindah dari `JadwalImsakiyahViewModel` supaya dipakai bareng jadwal bulanan Waktu Sholat — lihat `docs/features/waktu-sholat.md`). |
| `ui/pilihkota/PilihKotaActivity.kt` + `adapter/MarkazRadioAdapter.kt` | Picker kota single-select (baru, dipakai bareng Waktu Sholat) — daftar dari `HisabNasionalCalculator.allMarkaz`, tulis ke `SessionManager.setSelectedMarkazImsakiyahId()`/`setSelectedMarkazWaktuSholatId()` sesuai `EXTRA_TARGET`, lalu `finish()` (caller re-read di `onResume()`, pola sama `HisabNasionalActivity`). |
| `ui/laporanjadwal/LaporanJadwalActivity.kt` + `model/PrayerScheduleReportModels.kt` | Render laporan (tabel tanggal + 8 kolom waktu) + unduh PDF lewat `HilalPdfService` (dipakai bareng jadwal bulanan Waktu Sholat) — pola sama `LaporanHisabActivity` di fitur Awal Bulan. |
| `res/layout/item_imsakiyah_day_cell.xml` | 1 sel kolom tanggal **freeze** (`TextView` tunggal, 56dp x 52dp), diinflate berulang ke `layoutImsakiyahDayColumn` — di luar `HorizontalScrollView` supaya tidak ikut geser. |
| `res/layout/item_imsakiyah_row.xml` | 1 baris 8 kolom waktu (tanpa kolom tanggal lagi sejak freeze-column), diinflate berulang ke `layoutImsakiyahTable` di dalam `HorizontalScrollView`, untuk header (bold + tint amber) dan tiap hari (background selang-seling). Row height di-fixed 52dp di kedua layout (`item_imsakiyah_day_cell.xml` & `item_imsakiyah_row.xml`) supaya baris tanggal & baris waktu tetap sejajar saat scroll vertikal. |
| `utils/HijriCalendarEngine.kt` | `HijriMonthRange` + `monthRangeForOffset()` + `previousSegment()` — logic inti tetap yang lama (`findSegmentContaining`/`nextSegment`/dst), tidak diubah. |
| `api/PrayersApiService.kt` | `Timings` (dipakai response `/v1/calendar`) ditambah field opsional `imsak`/`sunrise`/`lastThird` (additive, field lama tidak berubah) — sebelumnya cuma `Fajr/Dhuhr/Asr/Maghrib/Isha`. |
| `repo/PrayerRepository.kt` | Tidak berubah — reuse `getIslamicHolidays(lat, lng, month, year)` yang sudah ada. |
| `viewmodel/waktusholat/PrayerKind` (di `PrayerTimesViewModel.kt`) | Reuse enum urutan kolom — tidak diubah. |

Alur data: `JadwalImsakiyahActivity` resolve lokasi -> `viewModel.loadMonth(lat,
lng, 0)` -> `HijriCalendarEngine.monthRangeForOffset()` (dapat tanggal 1 +
jumlah hari) -> kumpulkan pasangan (bulan, tahun) Masehi unik yang dilewati
rentang itu -> `PrayerRepository.getIslamicHolidays()` per pasangan (1-2
panggilan network, BUKAN loop per-hari 29/30x) -> gabung hasil, cocokkan per
tanggal (pola sama `MainViewModel.fetchMonthlyCalendar()`: parse
`PrayerData.date.readable` format `dd MMM yyyy` EN, banding string
`yyyy-MM-dd`) -> `ImsakiyahUiState` -> Activity flatten ke dua container
terpisah (`renderTable()`), bukan satu (lihat revisi "kolom tanggal freeze"
di bawah).

**Struktur layout tabel (`activity_jadwal_imsakiyah.xml`)** — kolom tanggal
freeze, sisanya scroll:

```
ScrollView (vertikal, weight=1)
  LinearLayout horizontal, background=bg_card_rounded, clipToOutline=true  <- "kartu"
    LinearLayout id=layoutImsakiyahDayColumn (vertikal)   <- FREEZE, di luar HorizontalScrollView
    View (divider 1dp)
    HorizontalScrollView
      LinearLayout id=layoutImsakiyahTable (vertikal)     <- 8 kolom waktu, ikut geser
```

Karena kolom tanggal & tabel waktu adalah dua `LinearLayout` vertikal
terpisah yang jadi children horizontal dari kartu yang sama, keduanya tetap
scroll vertikal bersamaan (satu `ScrollView` membungkus semuanya) — tapi
cuma `layoutImsakiyahTable` yang dibungkus `HorizontalScrollView`, jadi
geser horizontal hanya menggerakkan kolom 2 dst. `JadwalImsakiyahActivity.
buildDayCell()`/`buildHeaderRow()`/`buildDataRow()` mengisi kedua container
ini secara paralel per baris (index yang sama -> warna selang-seling yang
sama), bukan lagi satu `buildHeaderRow()`/`buildDataRow()` yang mengisi
kolom tanggal+waktu sekaligus seperti versi awal.

Dhuha dihitung dari `Sunrise + 15 menit` (`DHUHA_OFFSET_MINUTES`), sama
persis seperti `PrayerTimesViewModel`. Kalau hari tertentu tidak ada data
match (network gagal sebagian/field null), sel ditampilkan "-", bukan crash.

## 5. Dependencies & tech stack khusus

Tidak ada tambahan khusus di luar stack umum app (Retrofit/Gson untuk
Aladhan API, `io.github.cosinekitty.astronomy` untuk `HijriCalendarEngine`
— keduanya sudah dipakai fitur lain).

## 6. Testing

Belum ada test otomatis (`HijriCalendarEngine.monthRangeForOffset()` maupun
`JadwalImsakiyahViewModel` belum ada unit test — konsisten dengan
`HijriCalendarEngine`/`EphemerisCalculator` lain yang juga belum ada test
JVM, lihat `docs/features/bulan-hijriyah.md` §7).

**Verifikasi manual (emulator Pixel_4_XL_API_36, 2026-09-16)**: install APK
debug, home -> tombol "Jadwal Imsakiyah" (setelah "Hisab Nasional", slot
"Doa & Dzikir" sudah tidak ada) -> tabel tampil (header "Tgl" + 8 kolom
waktu, tint amber), scroll vertical (30 baris) & horizontal (kolom) jalan
lancar tanpa lag. "Semua Menu" dicek juga: "Jadwal Imsakiyah" muncul tepat
setelah "Hisab Awal Bulan Nasional", "Doa & Dzikir" masih ada & masih bisa
dibuka.

**Verifikasi manual revisi Ramadhan-only + kota + PDF (emulator Pixel6_API34,
2026-09-28)**, via `uiautomator dump` (bukan screenshot, lihat `CLAUDE.md`):
tabel selalu "Ramadhan 1448 H" tanpa tombol ◀/▶; `tvLocationInfo` tampil
("Lokasi Manual (Lat -6.2088, Lng 106.8456)" default); ikon ⚙️ -> pilih
"Padang" -> kembali otomatis (`onResume()`), `tvLocationInfo` update jadi
"Markaz: Padang, Sumatera Barat (Lat -0.9471, Lng 100.4172)" dan tabel
reload dengan waktu sesuai lokasi baru; tombol "CETAK PDF" ->
`LaporanJadwalActivity` tampil tabel identik (judul "JADWAL IMSAKIYAH",
subtitle bulan+lokasi) -> "UNDUH PDF" tersimpan ke `Download/
Jadwal_Imsakiyah_<timestamp>.pdf` tanpa crash (dicek `adb logcat -s
AndroidRuntime:E` bersih di tiap langkah).

**Revisi setelah verifikasi visual pertama** (2026-09-16, sama hari):
1. User minta kolom tanggal di-freeze (awalnya seluruh tabel termasuk
   kolom tanggal ikut geser horizontal jadi satu blok) — direstrukturisasi
   jadi 2 container terpisah (lihat section 4). Diverifikasi ulang: kolom
   "Tgl" diam saat swipe horizontal, baris tetap sejajar kiri-kanan saat
   swipe vertikal.
2. User laporkan "background tanggalnya overlapping layout" — kolom
   tanggal freeze (background flat per-sel, tanpa rounded corner) menonjol
   melewati sudut rounded kartu (`bg_card_rounded`, radius 24dp) di
   pojok kiri-atas, kelihatan seperti notch putih memotong header amber.
   Fix: `android:clipToOutline="true"` di kartu wrapper supaya semua
   children (termasuk kolom freeze) ke-clip ke outline rounded-nya. Sudah
   diverifikasi ulang via screenshot — sudut bersih, tidak ada notch.

## 7. Known issues & TODOs

- [ ] Tabel 9 kolom (Tgl + 8 waktu) cukup lebar — di layar sempit user harus
  scroll horizontal untuk lihat kolom Dzuhur-Isya, tidak ada indikator visual
  "geser ke kanan" selain scrollbar tipis bawaan `HorizontalScrollView`.
- [ ] Field `Imsak`/`Sunrise`/`Lastthird` di `Timings` baru pertama kali
  dipakai lewat endpoint `/v1/calendar` di fitur ini (sebelumnya endpoint itu
  cuma dipakai untuk deteksi hari libur di kalender home, jadi field-field
  itu tidak pernah diparse). Sudah diverifikasi manual di emulator hasilnya
  masuk akal (lihat section 6), tapi belum ada assertion otomatis kalau
  Aladhan suatu saat ubah shape response endpoint ini.
- [ ] State tabel cuma di memori ViewModel, hilang kalau Activity
  di-recreate/rotate — sama seperti `AwalBulanActivity`, dianggap cukup
  untuk kasus pakai utamanya.
- [ ] Markaz kota (`HisabNasionalCalculator.allMarkaz`) belum punya field
  timezone/elevasi (batasan yang sama sudah dicatat di
  `docs/features/hisab-nasional.md` §7) — jam tetap dihitung dari data
  Aladhan API per lat/lng, jadi tidak terlalu terdampak, tapi perlu diingat
  kalau nanti ada fitur lain yang butuh timezone eksplisit per markaz.
- [ ] PDF cetak masih "capture View jadi 1 halaman panjang"
  (`HilalPdfService`), bukan pagination A4 sungguhan — untuk 30 baris hasil
  jadi 1 halaman PDF yang sangat tinggi. Cukup untuk kebutuhan cetak saat
  ini (lihat juga `docs/features/waktu-sholat.md` §7).
