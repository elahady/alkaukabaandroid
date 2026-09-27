# Waktu Sholat — Metode Perhitungan

## 1. Ringkasan

**Fitur**: Waktu Sholat — konfigurasi metode perhitungan.

User bisa memilih metode perhitungan waktu sholat sendiri lewat halaman
Konfigurasi, alih-alih dikunci ke satu metode hardcode seperti sebelumnya.
Ini menyelesaikan kebutuhan bahwa metode hisab waktu sholat itu banyak
macamnya (Kemenag RI, MWL, ISNA, dll) dan preferensinya beda-beda per user.

Per 2026-09-28, layar ini juga dapat **pilihan kota** (daftar sama dengan
Hisab Nasional) dan **cetak PDF jadwal bulanan** (bulan Masehi bebas
dipilih) — lihat subsection "Pilihan kota + cetak PDF jadwal bulanan" di
bawah untuk detailnya.

## 2. Entry point & prasyarat

- Dari `MainActivity`: tap ikon gear (kanan atas) → langsung membuka
  `KonfigurasiActivity` (tidak lewat dialog perantara).
- Di `KonfigurasiActivity`, tap baris **"Metode Perhitungan"** → membuka
  bottom sheet `dialog_prayer_method.xml` berisi daftar preset.
- Prasyarat: tidak ada permission/API key tambahan — semua preset memakai
  base URL Aladhan API yang sudah dikonfigurasi di `RetrofitClient`.
- Lokasi (lat/lon) yang dikirim ke Aladhan: dari GPS secara default, atau dari
  koordinat manual kalau setting **Konfigurasi → Lokasi** di-set ke Manual
  (lihat `docs/features/konfigurasi.md`) — `WaktuSholatActivity
  .checkLocationPermission()` cek `SessionManager.isManualLocationMode()`
  duluan sebelum minta permission GPS, persis pola yang sama dengan
  `MainActivity` dan `KiblatActivity`.

## 3. Titik masuk logika & navigasi

- `PrayerCalculationMethods.PRESETS` — daftar preset yang ditampilkan di UI.
  Tambah/ubah/hapus preset dilakukan di sini.
- `SessionManager.setPrayerMethodId()` / `getPrayerMethodId()` — baca/tulis
  pilihan user.
- `PrayerRepository.getPrayerTimes/getIslamicHolidays/getTimingPrayers` —
  satu-satunya jalur yang benar-benar memanggil Aladhan API; semua sudah
  otomatis memakai method yang tersimpan di `SessionManager`, jadi pemanggil
  (ViewModel) tidak perlu tahu soal method sama sekali.
- `PrayerTimesViewModel.buildSchedule()` — business logic "sholat mana yang
  aktif sekarang" (dari `TimingPrayers` mentah jadi `PrayerScheduleUiState`
  siap-render). Sengaja di ViewModel, bukan `WaktuSholatActivity`, supaya
  Activity cuma bind data ke View (lihat catatan clean-code di bawah).
- `PrayerCalculationBreakdownRegistry.providerFor(methodId)` — titik ekstensi
  untuk breakdown detail perhitungan ("kenapa hasilnya segini") per metode.
  Untuk menambah breakdown ke metode lain: implement interface
  `PrayerCalculationBreakdownProvider`, daftarkan di map registry ini —
  ViewModel/Activity tidak perlu diubah.
- Navigasi: `MainActivity` → `KonfigurasiActivity` via `Intent` biasa (tidak
  ada extra yang dibawa). Dari dalam `KonfigurasiActivity`, bottom sheet metode
  dan konfirmasi logout keduanya modal (`BottomSheetDialog` / `AlertDialog`),
  bukan Activity terpisah.

## 4. Struktur & alur data

File yang terlibat:

| File | Peran |
|---|---|
| `utils/PrayerCalculationMethods.kt` | Data class + daftar preset (id, nama, subtitle) |
| `utils/SessionManager.kt` | Persistensi pilihan user di `SharedPreferences` — metode perhitungan sholat **dan** setting lokasi global (lihat `docs/features/konfigurasi.md`) |
| `repo/PrayerRepository.kt` | Jembatan ke Aladhan API, resolve method yang benar dikirim |
| `api/PrayersApiService.kt` (`AladhanApi`) | Interface Retrofit ke `api.aladhan.com`, terima param `method` & `methodSettings` |
| `viewmodel/waktusholat/PrayerTimesViewModel.kt` | State untuk layar `WaktuSholatActivity`, panggil `PrayerRepository` |
| `viewmodel/MainViewModel.kt` | State untuk widget waktu sholat di home, juga lewat `PrayerRepository` |
| `ui/konfigurasi/KonfigurasiActivity.kt` + `activity_konfigurasi.xml`, `dialog_prayer_method.xml` | UI pemilihan metode & logout |
| `ui/waktusholat/WaktuSholatActivity.kt` | Murni bind `PrayerScheduleUiState` ke View (icon, warna, teks) — tidak ada logika "mana yang aktif" di sini |
| `utils/prayerbreakdown/PrayerCalculationBreakdownRegistry.kt` | Map `methodId -> PrayerCalculationBreakdownProvider`; hari ini isinya cuma Ephemeris |
| `utils/prayerbreakdown/PrayerBreakdownModels.kt` | `PrayerBreakdownSection`/`PrayerBreakdownRow` (data) + interface `PrayerCalculationBreakdownProvider` |
| `utils/prayerbreakdown/EphemerisPrayerCalculator.kt` | Implementasi breakdown untuk Ephemeris — deklinasi matahari & Equation of Time diturunkan dari posisi matahari riil (Astronomy Engine), rumus gabungan per waktu sholat (Kwd, h°, t, ikhtiyat) mengikuti prosedur hisab klasik, lihat `docs/features/rumus-hisab-ephemeris.md` |
| `res/layout/item_prayer_breakdown.xml` | Card accordion per waktu sholat (header klik untuk expand/collapse + body berisi baris rumus) |
| `res/layout/item_breakdown_row.xml` | Satu baris rumus di dalam card (`tvRowLabel`/`tvRowValue`) |
| `ui/pilihkota/PilihKotaActivity.kt` + `adapter/MarkazRadioAdapter.kt` | Picker kota single-select (baru, dipakai bareng Jadwal Imsakiyah) — lihat subsection "Pilihan kota + cetak PDF jadwal bulanan" |
| `viewmodel/waktusholat/PrayerMonthlyScheduleViewModel.kt` + Factory | Jadwal 1 bulan Masehi bebas pilihan, untuk cetak PDF (baru) |
| `utils/PrayerScheduleFormatter.kt` | Format waktu per-hari (8 kolom), dipakai bareng Jadwal Imsakiyah (baru, dipindah dari `JadwalImsakiyahViewModel`) |
| `ui/laporanjadwal/LaporanJadwalActivity.kt` | Render laporan + unduh PDF, dipakai bareng Jadwal Imsakiyah (baru) |

Alur data (pilih metode): `KonfigurasiActivity` (radio pilih preset) →
`SessionManager` (simpan id) → `PrayerRepository` (baca id saat request
berikutnya) → `AladhanApi` (kirim `method`/`methodSettings`) → Aladhan API.

Alur data (render jadwal di `WaktuSholatActivity`): `PrayerRepository`
(`TimingPrayers` mentah) → `PrayerTimesViewModel.buildSchedule()` (hitung
`activeIndex`, susun `PrayerScheduleItem` per baris) → `PrayerScheduleUiState`
lewat LiveData `prayerSchedule` → Activity `updateNextPrayerUI()` cuma
mem-bind ke `binding.row*`, tanpa logika bisnis apa pun.

Alur data (breakdown perhitungan): `PrayerTimesViewModel.loadData()` panggil
`calculatePrayerBreakdown()` → `PrayerCalculationBreakdownRegistry.providerFor(
repository.getSelectedMethodId())` (pakai id method yang **dipilih user**,
bukan hasil fallback Aladhan) → kalau ada provider, `EphemerisPrayerCalculator
.breakdown()` dipanggil dan hasilnya (`List<PrayerBreakdownSection>?`) di-set
ke LiveData `calculationBreakdown`. Null/kosong kalau metode aktif tidak punya
breakdown terdaftar (semua metode Aladhan selain Ephemeris) → Activity
`renderPrayerBreakdown()` sembunyikan `layoutPrayerBreakdownContainer` dan
tampilkan pesan fallback `tvNoPrayerBreakdown` untuk kasus ini.

UI-nya sendiri: `WaktuSholatActivity` punya dua tab, **"Waktu Aktual"**
(`btnTabActual`, jadwal biasa — default aktif) dan **"Detail Perhitungan"**
(`btnTabDetail`, breakdown ini), diswitch lewat `updateTabState()` yang
toggle visibility `layoutWaktuSholat` vs `layoutDetailPerhitungan`. Tab ini
murni tentang waktu sholat — sebelumnya sempat tercampur dengan detail rumus
Arah Kiblat (`tvCalculationResult`/`tvResultDegree`/`QiblaCalculator`) di
layout yang sama (`layoutDetailKiblat`, nama lama), yang keliru karena Arah
Kiblat itu fitur terpisah (`KiblatActivity`) — sudah dibersihkan, lihat bagian
"Kenapa Arah Kiblat dikeluarkan" di bawah. Setiap section
breakdown (per waktu sholat) dirender sebagai card accordion
(`item_prayer_breakdown.xml`, klik `rowHeader` expand/collapse `layoutBody`,
`tvChevron` berubah ⌄/⌃) berisi baris-baris rumus (`item_breakdown_row.xml`,
`tvRowLabel`/`tvRowValue`) dari `PrayerBreakdownSection.rows`.

### Polish UI hero card, tab, & lokasi (2026-08-30)

Perombakan visual atas masukan review UX, semuanya di `WaktuSholatActivity`
dan `activity_waktu_sholat.xml`, tanpa mengubah logika `PrayerTimesViewModel`:

- **Nama lokasi human-readable**: `tvLocationName` di hero card sekarang
  menampilkan hasil reverse-geocode (`"Kota, Provinsi"`, mis. "Surabaya, Jawa
  Timur") lewat `resolvePlaceName()` (pakai `android.location.Geocoder`,
  pola yang sama dengan `MainViewModel.fetchAddressName` dan
  `KiblatActivity.getAddressFromLatLong`), bukan lagi string mentah
  `"Lat: x, Long: y"`. Koordinat mentah dipindah ke `tvDetailCoordinates`
  (baru) di tab Detail Perhitungan — dipanggil dari `updateLocationDisplay()`
  yang jadi titik tunggal setiap kali lokasi berubah (GPS via `getLocation()`
  maupun manual via `useManualLocation()`). Geocoding jalan di
  `lifecycleScope.launch(Dispatchers.IO)` supaya tidak blok main thread;
  kalau gagal/alamat tidak ketemu (umum untuk titik lokasi manual/markaz),
  fallback teksnya `"Lokasi Anda"`.
- **Kontras ikon pin lokasi**: `ic_gis_location_poi` aslinya solid merah
  gelap (`#850000`) — kontras jelek di atas pill gelap hero card. Tidak
  diubah warna aslinya di file vector (drawable ini dipakai juga di
  `activity_awal_bulan.xml`), melainkan di-tint per-pemakaian lewat
  `app:drawableTint="@color/waktu_sholat_pill_text"` di `tvLocationName`.
- **Tab navigasi**: diganti dari toggle abu-abu (`bg_tab_container` +
  `bg_tab_active`/`bg_tab_inactive`, kotak solid) jadi gaya tab Material —
  teks polos berjajar, tab aktif ditandai underline 3dp
  (`bg_tab_underline_active.xml`, warna `waktu_sholat_dark_bg`) di
  `updateTabState()`. Drawable lama (`bg_tab_container`/`bg_tab_active`/
  `bg_tab_inactive`) dibiarkan ada (belum dipakai di tempat lain, belum
  dihapus — lihat Known Issues) demi tidak menyentuh file yang mungkin
  sedang dipakai/di-refactor sesi lain.
- **Highlight baris sholat aktif**: warna `waktu_sholat_row_active_bg`
  diubah dari abu nyaris putih (`#F8FAFC`) ke biru pastel yang kelihatan
  (`#E8F0FE`) supaya baris Maghrib (mis.) yang sedang aktif langsung
  kebaca tanpa harus mencari teks yang di-bold. `rowRoot` di
  `item_prayer_row.xml` sudah full-width sejak awal, jadi tidak perlu ubah
  struktur layout.
- **Tanggal Hijriyah/Masehi**: `updateDateDisplay()` sekarang membangun
  `tvDate` pakai `SpannableStringBuilder` — bagian Hijriyah dibuat bold +
  putih (lebih terang dari teks Masehi yang tetap `waktu_sholat_date_muted`),
  dipisah bullet `"  •  "` (sebelumnya `" | "` polos, sama-sama abu tanpa
  penekanan).

### Pilihan kota + cetak PDF jadwal bulanan (2026-09-28)

Dua kemampuan baru, di luar konfigurasi metode yang jadi topik utama file
ini, ditambahkan supaya `WaktuSholatActivity` sejajar dengan Jadwal
Imsakiyah (lihat `docs/features/jadwal-imsakiyah.md`) dan Hisab Nasional:

- **Menu pilih kota** (ikon ⚙️ toolbar, `btnToolbarAction`) → buka
  `PilihKotaActivity` (`ui/pilihkota/`, single-select) dengan
  `EXTRA_TARGET = TARGET_WAKTU_SHOLAT`. Daftar kota persis
  `HisabNasionalCalculator.allMarkaz` (38 ibu kota provinsi, sama dengan
  Hisab Nasional/Jadwal Imsakiyah), pilihan tersimpan terpisah
  (`SessionManager.getSelectedMarkazWaktuSholatId()` — key sendiri, TIDAK
  dibagi dengan pilihan kota Imsakiyah, sesuai keputusan produk saat
  fitur ini diminta). `checkLocationPermission()` cek markaz tersimpan ini
  duluan, sebelum cabang manual-global/GPS/fallback yang sudah ada —
  kalau ada, `useMarkazLocation()` langsung pakai lat/lng markaz tanpa GPS.
  Trigger pengecekan lokasi dipindah dari `onCreate()` ke `onResume()`
  supaya balik dari `PilihKotaActivity` langsung reload otomatis (pola
  sama `HisabNasionalActivity`).
- **Cetak jadwal sholat bulanan (PDF)** — tombol baru
  `btnCetakJadwalBulanan` di bawah tab, buka dialog (`dialog_pilih_bulan.xml`,
  2 `Spinner` bulan Masehi + tahun, reuse `HijriDateUtil.gregorianMonthNames`
  dan layout spinner yang sama dipakai `AwalBulanActivity`). Submit dialog
  → `PrayerMonthlyScheduleViewModel.loadMonth(lat, lng, month, year)` (file
  baru, terpisah dari `PrayerTimesViewModel` yang cuma hari ini) memanggil
  `PrayerRepository.getIslamicHolidays()` (endpoint yang sama dipakai Jadwal
  Imsakiyah — 1 panggilan API sudah dapat 1 bulan Masehi penuh) → hasil
  dirender via `LaporanJadwalActivity` (`ui/laporanjadwal/`, dipakai bareng
  Jadwal Imsakiyah, lihat dokumen itu) → tombol "Unduh PDF" di sana pakai
  `HilalPdfService` yang sama dengan fitur Awal Bulan Hijriyah.
  Bulan **bebas dipilih** (tidak dikunci ke Ramadhan seperti Imsakiyah) —
  itu keputusan produk eksplisit: Imsakiyah cuma perlu Ramadhan, Waktu
  Sholat perlu semua bulan.
- Format waktu per-hari (kolom Sepertiga Malam/Imsak/Subuh/Dhuha/Dzuhur/
  Ashar/Maghrib/Isya, termasuk Dhuha = Sunrise+15 menit) di-share lewat
  `utils/PrayerScheduleFormatter.kt` (dipindah dari `JadwalImsakiyahViewModel`
  supaya tidak duplikat) — perubahan pada file itu otomatis memengaruhi
  kedua fitur.

Diverifikasi manual di emulator: pilih kota "Jambi" di Waktu Sholat sambil
Jadwal Imsakiyah tetap di kota "Padang" (independen, tidak saling
menimpa); dialog bulan default ke bulan berjalan; cetak PDF bulan September
2026 tersimpan ke folder Download tanpa crash.

### Rumus hisab Ephemeris jadi presisi & sesuai prosedur klasik (2026-08-30)

`EphemerisPrayerCalculator.kt` sebelumnya pakai pendekatan sinusoidal
sederhana untuk deklinasi matahari & Equation of Time (rumus perkiraan
berbasis hari-ke-berapa dalam setahun, bukan data matahari riil), dan rumus
gabungan tiap waktu sholat tidak konsisten (mis. Imsak dihitung sebagai
"Subuh - 10 menit" alih-alih sudut tersendiri, tanda `+`/`-` untuk `t`/`i`
tidak seragam per waktu). Diganti total mengikuti handout **"Perhitungan
Waktu Sholat"** (M. Khoirul Anam) yang didokumentasikan lengkap di
[`rumus-hisab-ephemeris.md`](rumus-hisab-ephemeris.md):

- **δ & e riil, bukan aproksimasi**: diturunkan dari posisi matahari
  sebenarnya lewat `searchHourAngle(Sun, 0°)` (fungsi Astronomy Engine yang
  sudah dipakai `EphemerisCalculator.kt` untuk Awal Bulan) untuk mencari
  waktu transit/istiwa' matahari hari itu, lalu `equator(Sun, ...)` untuk
  deklinasi pada saat itu. Equation of time diturunkan dari selisih transit
  riil terhadap tengah hari rata-rata (`12 - λ/15`).
- **Rumus gabungan sesuai buku**: Dzuhur `12-e+Kwd+i`; Ashar/Maghrib/Isya
  `12-e+t+Kwd+i`; Subuh/Imsak/Dhuha `12-e-t+Kwd+i`; khusus Terbit/Syuruq `i`
  dikurangkan (`12-e-t+Kwd-i`). Tinggi matahari (h°) per waktu & rumus
  `cotan h° = tan|φ-δ|+1` untuk Ashar mengikuti tabel di dokumen rujukan.
- **2 section breakdown baru**: Terbit/Syuruq dan Dhuha ditambahkan (dulu
  cuma Dzuhur/Ashar/Maghrib/Isya/Subuh/Imsak) — urutan section juga
  dibikin kronologis (Imsak → Subuh → Terbit → Dhuha → Dzuhur → Ashar →
  Maghrib → Isya), tidak perlu ubah `WaktuSholatActivity` karena rendering
  breakdown sudah dinamis per-list (`renderPrayerBreakdown()`).
- Baris breakdown baru per section: `Koreksi Waktu Daerah (Kwd)`,
  `Tinggi Matahari (h°)`, `Sudut Waktu Matahari (t)`, `Ikhtiyat (i)`, dan
  `Rumus` (formula yang dipakai) — lebih dekat ke istilah buku aslinya
  dibanding versi sebelumnya.

Catatan: ini cuma mengubah **breakdown "Detail Perhitungan"**, bukan jadwal
di tab "Waktu Aktual" — jadwal utama tetap dari Aladhan API (fallback
`method=20`/Kemenag RI), lihat "Kenapa Ephemeris fallback ke Kemenag RI" di
bawah. Menyambungkan hasil hitung lokal ini ke jadwal utama masih TODO
terpisah (lihat Known Issues).

### Preset yang tersedia

| Preset | id (dikirim ke Aladhan sbg `method`) | Catatan |
|---|---|---|
| Ephemeris (Al Hasib - Alkaukaba Team) | `1000` (id internal, lihat bagian di bawah) | Belum ada mesin hisab manual |
| Muslim World League | `3` | |
| Islamic Society of North America (ISNA) | `2` | |
| Umm al-Qura, Makkah | `4` | |
| Egyptian General Authority | `5` | |
| University of Islamic Sciences, Karachi | `1` | |
| Custom | `99` | pakai `methodSettings`, lihat `SessionManager.getMethodSettingsQuery()` |

Preferensi tersimpan di `SharedPreferences` (`"AppSession"`):
- `PRAYER_METHOD_ID` — default: `PrayerCalculationMethods.EPHEMERIS_ID`
  (fresh install langsung memakai Ephemeris sebagai metode yang ditampilkan).
- `PRAYER_CUSTOM_FAJR` / `PRAYER_CUSTOM_ISHA` — sudut custom, dipakai saat
  method = Custom (`99`).

### Kenapa "Ephemeris" fallback ke Kemenag RI

Al-Kaukaba ingin identitas metode sendiri di app ("Ephemeris — Al Hasib"),
tapi mesin hitungnya (posisi bulan/matahari, semacam algoritma Jean
Meeus/Swiss Ephemeris) belum diimplementasikan di app ini.
`utils/EphemerisCalculator.kt` yang sudah ada sekarang berisi data simulasi
untuk fitur Awal Bulan Hijriyah, bukan untuk Waktu Sholat, dan tidak dipakai
di sini.

Sampai mesin hisab manual itu siap, memilih **"Ephemeris" di UI tetap secara
teknis mengirim `method=20` (Kemenag RI) ke Aladhan API** di belakang layar —
lihat konstanta `EPHEMERIS_FALLBACK_ALADHAN_METHOD_ID` di
`PrayerRepository.kt`.

Ini keputusan sadar, bukan bug:
- Label **"Ephemeris (Al Hasib - Alkaukaba Team)"** di UI tidak boleh berubah
  jadi "Kemenag RI" di manapun (termasuk subtitle) — dikonfirmasi eksplisit.
- Tapi secara teknis harus tetap mengirim id `method` yang valid ke Aladhan,
  karena `1000` bukan id yang dikenali Aladhan dan akan gagal kalau dikirim
  langsung.

### Kenapa Arah Kiblat dikeluarkan dari layar ini

Sebelumnya, tab "Detail Perhitungan" di `WaktuSholatActivity` menampilkan DUA
hal sekaligus: "1. PERHITUNGAN ARAH KIBLAT" (pakai `QiblaCalculator`, hasil
`detailFormulaSteps` + derajat) dan "2. DETAIL PERHITUNGAN WAKTU SHOLAT"
(breakdown Ephemeris). Ini keliru secara scope — Arah Kiblat adalah fitur
sendiri dengan layarnya sendiri (`ui/arahkiblat/KiblatActivity`), tidak ada
hubungannya dengan Waktu Sholat selain kebetulan sama-sama butuh lat/long.

Sudah dihapus dari sini: `QiblaCalculator` import, `calculateQibla()`,
LiveData `qiblaDetailText`/`qiblaDegreeUI` di `PrayerTimesViewModel`, dan
View terkait (`etCoordinates`, `tvCalculationResult`, `tvResultDegree`,
"Koordinat Pengguna") di `activity_waktu_sholat.xml`. `layoutDetailKiblat`
di-rename jadi `layoutDetailPerhitungan` supaya nama file/id tidak lagi
menyesatkan (isinya sekarang murni waktu sholat).

**Konsekuensi**: `utils/QiblaCalculator.kt` sekarang jadi kelas yatim (tidak
dipakai di manapun) — `KiblatActivity` punya mekanisme sendiri
(`KiblatViewModel.fetchQiblaAngle`) yang tidak memakai kelas ini sama sekali.
File belum dihapus (masih berisi breakdown rumus arah kiblat yang mungkin
berguna), tapi perlu keputusan: hapus, atau sambungkan ke `KiblatActivity`
kalau breakdown rumus semacam ini juga diinginkan di sana. Lihat Known Issues.

## 5. Dependencies & tech stack khusus

Tidak ada tambahan khusus di luar stack umum app — Retrofit + Gson (untuk
panggil Aladhan API) dan `SharedPreferences` bawaan Android, keduanya sudah
dipakai di bagian lain app ini juga.

## 6. Testing

Belum ada test otomatis untuk fitur ini (project baru punya boilerplate
`ExampleUnitTest.kt`/`ExampleInstrumentedTest.kt`, belum ada test nyata sama
sekali). Verifikasi saat ini manual:

1. Build & install debug APK (lihat `CLAUDE.md` root untuk perintah `gradlew`).
2. Buka `MainActivity` → tap gear → pastikan langsung ke `KonfigurasiActivity`
   (bukan dialog perantara).
3. Tap "Metode Perhitungan" → pastikan semua preset tampil dengan label yang
   benar, radio Custom memunculkan input sudut Fajr/Isya.
4. Pilih preset lain → Simpan → pastikan label di baris "Metode Perhitungan"
   berubah sesuai pilihan.
5. (Opsional, kalau mengubah `PrayerRepository`) Cek lewat `adb logcat` atau
   proxy bahwa request ke Aladhan benar-benar mengirim `method` yang sesuai.
6. Buka `WaktuSholatActivity` → tab "Detail Perhitungan" → pastikan isinya
   **cuma** breakdown waktu sholat (tidak ada lagi "PERHITUNGAN ARAH KIBLAT").
7. Ganti **Konfigurasi → Lokasi → Manual** dengan koordinat tertentu → buka
   `WaktuSholatActivity` → pastikan **tidak ada** dialog permission GPS, dan
   `tvDetailCoordinates` di tab Detail Perhitungan (format `"Koordinat: Lat x,
   Long y"`) menunjukkan koordinat manual persis (sudah diverifikasi sesi
   2026-08-30 lewat `adb shell run-as ... cat shared_prefs/AppSession.xml` +
   screenshot, koordinat yang tampil identik dengan yang tersimpan).
8. Cek `tvLocationName` di hero card menampilkan nama tempat ("Kota,
   Provinsi"), bukan koordinat mentah — untuk titik lokasi manual yang tidak
   ke-resolve Geocoder (mis. markaz tanpa alamat jalan), pastikan fallback
   `"Lokasi Anda"` yang tampil, bukan crash/teks kosong.
9. Tap tab "Detail Perhitungan" → pastikan tab aktif ditandai underline biru
   dongker di bawah teksnya (bukan lagi tombol abu-abu solid), dan baris
   sholat yang sedang aktif di tab "Waktu Aktual" (mis. Maghrib) punya
   background biru pastel dari ujung kiri ke kanan.
10. Tab "Detail Perhitungan" dengan metode Ephemeris aktif → pastikan ada
    8 card (Imsak, Subuh, Terbit/Syuruq, Dhuha, Dzuhur, Ashar, Maghrib, Isya)
    urut kronologis, tiap card di-expand menampilkan baris Lintang/Bujur/
    Deklinasi/Equation of Time/Kwd/h°/t/Ikhtiyat/Rumus, dan jam hasil naik
    monoton dari Imsak ke Isya (tidak ada waktu yang lebih awal dari waktu
    sebelumnya).

## 7. Known issues & TODOs

- [ ] Mesin hisab manual Ephemeris (Al Hasib) — belum diimplementasikan.
      Kemungkinan menyusul setelah fitur Awal Bulan Hijriyah (yang juga masih
      simulasi di `EphemerisCalculator.kt`) beres duluan. Kalau sudah siap:
      1. Ganti isi `currentMethod()` di `PrayerRepository.kt` — untuk
         `PrayerCalculationMethods.EPHEMERIS_ID`, panggil mesin hisab lokal
         alih-alih fallback ke Aladhan.
      2. Kemungkinan perlu ubah tipe return `getPrayerTimes`/`getTimingPrayers`
         di repository supaya bisa datang dari dua sumber (API vs hasil hitung
         lokal) — saat ini keduanya diasumsikan selalu berasal dari Aladhan
         (`Response<PrayerResponse>` / `Response<PrayerTimeResponse>`).
- [ ] Belum ada test otomatis (lihat bagian Testing di atas).
- [ ] Tab "Detail Perhitungan" cuma berguna untuk metode **Ephemeris** — untuk
      semua metode Aladhan lain, tab ini hanya menampilkan pesan fallback
      (`tvNoPrayerBreakdown`) karena memang tidak ada breakdown terdaftar di
      `PrayerCalculationBreakdownRegistry` untuk method selain Ephemeris.
      Kalau mau breakdown juga tersedia untuk metode lain, perlu provider
      baru per metode (lihat titik ekstensi di section 3).
- [ ] `utils/QiblaCalculator.kt` sekarang yatim piatu (tidak dipakai di
      manapun) setelah dikeluarkan dari layar ini — lihat "Kenapa Arah Kiblat
      dikeluarkan". Perlu keputusan: hapus, atau sambungkan ke `KiblatActivity`
      kalau breakdown rumus arah kiblat memang diinginkan di sana.
- [ ] `res/drawable/bg_tab_container.xml`, `bg_tab_active.xml`,
      `bg_tab_inactive.xml` jadi tidak terpakai lagi setelah tab diganti ke
      gaya underline (`bg_tab_underline_active.xml`/`bg_tab_underline_inactive.xml`,
      lihat "Polish UI hero card, tab, & lokasi" di atas). Belum dihapus —
      cek dulu tidak dipakai layar lain sebelum dibuang.
- [ ] Markaz kota (`HisabNasionalCalculator.allMarkaz`, dipakai picker kota
      baru) belum punya field timezone/elevasi — jam yang ditampilkan
      mengikuti timezone perangkat, bukan WIB/WITA/WIT per kota (batasan
      yang sama sudah dicatat di `docs/features/hisab-nasional.md` §7).
- [ ] PDF cetak (Jadwal Imsakiyah maupun jadwal bulanan Waktu Sholat) masih
      "capture View jadi 1 halaman panjang" (`HilalPdfService`), bukan
      pagination A4 sungguhan — untuk tabel 28-31 baris hasilnya jadi satu
      halaman PDF yang sangat tinggi, bukan beberapa halaman A4 standar.
      Cukup untuk kebutuhan cetak saat ini, tapi kalau nanti perlu print
      fisik multi-halaman rapi, perlu ditulis ulang pakai `Canvas`/`drawText`
      manual per halaman.
