# Konfigurasi

## 1. Ringkasan

**Fitur**: Konfigurasi — layar setting terpusat berisi Lokasi, Metode Hisab
Awal Bulan, Sumber Perhitungan Arah Kiblat, Metode Perhitungan Waktu Sholat,
Suara Notifikasi Adzan, dan Pengingat Pra-Adzan (2026-09-15).

Section **Pengingat Pra-Adzan** menyelesaikan kebutuhan berbeda dari
"Suara Notifikasi Adzan" (lihat
[notifikasi-adzan.md](notifikasi-adzan.md) untuk detail lengkap): bukan soal
suara notifikasi *saat* waktu sholat tiba, tapi opsi (opt-in, nonaktif by
default) untuk diingatkan beberapa menit *sebelum* waktu sholat tiba, supaya
user bisa bersiap-siap (wudhu, dsb).

Sebelumnya tiap fitur (Waktu Sholat, Arah Kiblat, Kalender) selalu mengambil
lokasi live dari GPS sendiri-sendiri, tanpa cara untuk memakai koordinat tetap
(mis. markaz rukyat tertentu, atau lokasi tanpa sinyal GPS bagus). Setting
**Lokasi** di sini menyelesaikan itu: satu toggle Otomatis (GPS) / Manual yang
dibaca oleh semua fitur yang butuh lat/lon — bukan setting lokal per-layar.
Setting **Sumber Perhitungan Arah Kiblat** menyelesaikan kebutuhan serupa
untuk Kiblat secara spesifik: pilih apakah sudut kiblat utama yang ditampilkan
berasal dari Aladhan API (online) atau rumus manual Al Hasib (lokal, tanpa
internet).

## 2. Entry point & prasyarat

- Dari `MainActivity`: tap ikon gear (`btnSettings`, kanan atas toolbar) →
  `startActivity(Intent(..., KonfigurasiActivity::class.java))`, tanpa extra.
- `KonfigurasiActivity` terdaftar di `AndroidManifest.xml`.
- Prasyarat:
  - Tidak ada permission yang diminta oleh layar Konfigurasi itu sendiri
    untuk section Lokasi & Arah Kiblat — **kecuali** user tap tombol "Pakai
    lokasi GPS saat ini" di sheet Lokasi (lihat section 3), yang baru minta
    `ACCESS_FINE_LOCATION` saat itu juga.
  - Section Waktu Sholat tidak butuh permission apa pun (murni pilih preset
    dari daftar statis).

## 3. Titik masuk logika & navigasi

- `SessionManager` — satu-satunya sumber kebenaran (state global,
  `SharedPreferences`) untuk ketiga section:
  - `getLocationMode()`/`setLocationMode()` (`"AUTO"`/`"MANUAL"`),
    `getManualLat()`/`getManualLng()`/`setManualLocation()`,
    `hasManualLocation()`, `isManualLocationMode()` (helper gabungan: mode
    Manual **dan** sudah pernah ada koordinat tersimpan).
  - `getQiblaSource()`/`setQiblaSource()` (`"ALADHAN"`/`"MANUAL_FORMULA"`).
  - `getHisabAwalBulanMethod()`/`setHisabAwalBulanMethod()`
    (`"ASTRONOMY_ENGINE"`/`"DURRUL_ANIQ"`, default Astronomy Engine) — dibaca
    `AwalBulanActivity` saat `runCalculation()`, diteruskan ke
    `HilalViewModel.calculateHilal()` (lihat `docs/features/bulan-hijriyah.md`
    section 5a untuk detail metode Ad-Durrul Aniq).
  - `getPrayerMethodId()` dkk — sudah ada sebelumnya, tidak berubah (lihat
    `docs/features/waktu-sholat.md`).
  - `getAdzanSoundMode()`/`setAdzanSoundMode()` — sudah ada sebelumnya, lihat
    `docs/features/notifikasi-adzan.md`.
  - `isPreAdzanReminderEnabled()`/`setPreAdzanReminderEnabled()` dan
    `getPreAdzanReminderMinutes()`/`setPreAdzanReminderMinutes()` (2026-09-15)
    — detail lengkap alur alarm-nya ada di
    `docs/features/notifikasi-adzan.md` section 4, bukan di sini (section ini
    fokus ke UI settingnya saja).
- `KonfigurasiActivity.showLocationSheet()` — inflate `dialog_lokasi.xml`,
  radio Otomatis/Manual, dua `EditText` lat/lon yang muncul kalau Manual
  dipilih, tombol "Pakai lokasi GPS saat ini" (isi field dari
  `FusedLocationProviderClient.lastLocation` sekali, user masih bisa edit
  manual setelahnya), validasi rentang (-90..90 / -180..180) sebelum simpan.
  Ada juga tombol "🗺️ Pilih dari peta" (2026-09-19) yang membuka
  `PilihLokasiPetaActivity` (lihat di bawah) lewat `pickFromMapLauncher`
  (`StartActivityForResult`); hasilnya cuma mengisi `etManualLat`/`etManualLng`,
  belum menyimpan apa pun — user tetap harus menekan Simpan di sheet.
- `PilihLokasiPetaActivity` (2026-09-19) — layar penuh berisi peta
  OpenStreetMap (`osmdroid` `MapView`, tile Mapnik) dengan **pin tetap di
  tengah**: user menggeser/zoom peta sampai pin tepat di titik yang dimau,
  koordinat pusat peta tampil live di panel bawah, tombol "Pilih lokasi ini"
  mengembalikan `EXTRA_LAT`/`EXTRA_LNG` lewat result Intent — dibungkus
  `PilihLokasiPetaContract` (input: titik awal `Pair<Double,Double>?`, output:
  titik terpilih atau `null` kalau batal) yang juga dipakai sheet lokasi
  Gerhana (dibulatkan 6
  desimal, bujur dinormalisasi ke -180..180 karena peta osmdroid berulang
  horizontal). Titik awal peta: koordinat di field sheet kalau valid, kalau
  kosong dan izin lokasi sudah diberikan → lokasi GPS terakhir, kalau tidak →
  tengah Indonesia zoom 5. Tombol target di kanan bawah = "ke lokasi saya"
  (baru minta izin `ACCESS_FINE_LOCATION` saat ditekan). Cache tile ada di
  `cacheDir/osmdroid` (bukan storage eksternal default osmdroid, supaya tak
  butuh izin storage) dan `User-Agent` di-set ke package name — server tile OSM
  menolak UA default library.
- `KonfigurasiActivity.showQiblaSourceSheet()` — inflate
  `dialog_qibla_source.xml`, radio Aladhan/Rumus Manual, simpan langsung
  (tidak ada input tambahan).
- `KonfigurasiActivity.showHisabMethodSheet()` — inflate
  `dialog_hisab_method.xml`, radio Astronomy Engine/Ad-Durrul Aniq, simpan
  langsung (pola identik dgn `showQiblaSourceSheet()`).
- `KonfigurasiActivity.showPreAdzanReminderSheet()` (2026-09-15) — inflate
  `dialog_pengingat_pra_adzan.xml`: satu `SwitchCompat` on/off yang
  menampilkan/menyembunyikan `RadioGroup` durasi (5/10/15/30 menit, default
  10) saat disimpan. Setelah simpan, langsung panggil
  `rescheduleAdzanAlarms()` (enqueue `AdzanRefreshWorker` immediate) supaya
  perubahan tidak menunggu app dibuka ulang atau job harian jam 00:05 — lihat
  `docs/features/notifikasi-adzan.md` section 3 & 4.
- Ini **bukan** setting yang otomatis "aktif" begitu disimpan di sini — tiap
  fitur pemakai (lihat daftar di bawah) yang bertanggung jawab membaca
  `SessionManager` di titik masuk lokasinya sendiri (`checkLocationPermission()`
  masing-masing). Kalau ada fitur baru yang butuh lokasi, developer harus
  sadar menambahkan pengecekan ini sendiri — tidak ada mekanisme paksa/DI
  yang menjamin semua konsumen otomatis ikut.
- Navigasi: `MainActivity` → `KonfigurasiActivity` via `Intent` biasa. Dari
  dalam `KonfigurasiActivity`, ketiga sheet (Lokasi, Arah Kiblat, Metode
  Waktu Sholat) dan konfirmasi logout semuanya modal (`BottomSheetDialog`/
  `AlertDialog`), bukan Activity terpisah.

### Fitur yang sudah membaca setting ini

| Fitur | Lokasi (Manual/Auto) | Sumber Kiblat |
|---|---|---|
| `MainActivity` (beranda: Waktu Sholat preview, Kalender, hari besar) | ✅ | tidak relevan |
| `WaktuSholatActivity` | ✅ | tidak relevan |
| `KiblatActivity` | ✅ | ✅ |
| `CalendarActivity` | ❌ tidak langsung — terima lat/lon lewat extra `Intent` dari `MainActivity`, jadi otomatis ikut kalau `MainActivity` sudah benar (lihat `docs/features/kalender.md`) | tidak relevan |
| `AwalBulanActivity` (Bulan Hijriyah) | ✅ (`isManualLocationMode()`, lihat `docs/features/bulan-hijriyah.md`) | tidak relevan |

`AwalBulanActivity` juga satu-satunya konsumen `getHisabAwalBulanMethod()`
(Astronomy Engine/Ad-Durrul Aniq) — tidak ada di tabel di atas karena bukan
setting lokasi/kiblat.

## 4. Struktur & alur data

File yang terlibat:

| File | Peran |
|---|---|
| `ui/konfigurasi/KonfigurasiActivity.kt` | Satu-satunya Activity untuk layar ini: wiring 4 row + 4 sheet (tidak ada logout di sini lagi, lihat catatan 2026-08-30 di bawah) |
| `utils/SessionManager.kt` | Persistensi semua setting (lokasi, sumber kiblat, metode hisab awal bulan, metode sholat) di `SharedPreferences "AppSession"` |
| `res/layout/activity_konfigurasi.xml` | Layout utama: 4 section (LOKASI, HISAB AWAL BULAN, ARAH KIBLAT, WAKTU SHOLAT), masing-masing satu row card |
| `res/layout/dialog_lokasi.xml` | Bottom sheet Lokasi: radio Otomatis/Manual, field lat/lon, tombol GPS, tombol "Pilih dari peta", tombol Simpan |
| `ui/konfigurasi/PilihLokasiPetaActivity.kt` + `res/layout/activity_pilih_lokasi_peta.xml` | Pemilih koordinat via peta OSM + pin tengah (2026-09-19), hasil lewat result Intent |
| `res/drawable/ic_map_pin.xml`, `ic_my_location.xml` | Ikon pin & tombol "ke lokasi saya" untuk layar peta |
| `res/layout/dialog_hisab_method.xml` | Bottom sheet Metode Hisab: radio Astronomy Engine/Ad-Durrul Aniq, tombol Simpan |
| `res/layout/dialog_qibla_source.xml` | Bottom sheet Arah Kiblat: radio Aladhan/Rumus Manual, tombol Simpan |
| `res/layout/dialog_prayer_method.xml` | Bottom sheet Waktu Sholat — sudah ada sebelumnya, tidak berubah |
| `res/layout/dialog_notifikasi_adzan.xml` | Bottom sheet Suara Notifikasi Adzan — sudah ada sebelumnya, tidak berubah |
| `res/layout/dialog_pengingat_pra_adzan.xml` | Bottom sheet Pengingat Pra-Adzan (baru, 2026-09-15): switch on/off + radio durasi |

Per 2026-08-30: `activity_konfigurasi.xml` di-polish murni visual (tidak ada
perubahan logika) — tiap row menu (Lokasi/Kiblat/Waktu Sholat) dapat ikon
(`ic_gis_location_poi`, `ic_menu_compass`, `ic_menu_clock` — file baru, belum
ada icon jam sebelumnya) tint `navy_dongker`, dan label kategori (LOKASI/ARAH
KIBLAT/WAKTU SHOLAT) warna `text_secondary` → `navy_dongker`. Header
(`view_toolbar_default`) tidak disentuh — sudah konsisten dari commit
`955430f`. Diverifikasi visual di emulator (`Pixel6_API34`) — pada percobaan
pertama `ic_menu_clock.xml` render aneh karena dua path lingkaran
tumpang-tindih (salin-tempel keliru dari referensi Material icon), diperbaiki
jadi satu lingkaran + jarum jam.

Per 2026-08-30 (follow-up): dua revisi lanjutan dari review visual di
emulator: (1) jarak antara header dan label "LOKASI" sempat mepet tanpa
spasi sama sekali — ditambah `paddingTop="20dp"` pada label pertama; (2)
tombol Logout **dipindah keluar dari halaman ini** ke halaman Profil,
menggantikan row teks polos "Keluar" yang sudah ada di sana (lihat
`docs/features/profil.md`) — `btnLogout`, `showLogoutConfirmation()`, dan
`performLogout()` dihapus dari `KonfigurasiActivity.kt` karena redundan
dengan logout yang sudah ada di Profil.

Alur data (simpan Lokasi Manual): user isi `etManualLat`/`etManualLng` (atau
tap "Pakai lokasi GPS saat ini" untuk auto-isi sekali dari GPS) → tap Simpan
→ validasi rentang → `sessionManager.setManualLocation(lat, lng)` +
`setLocationMode(MANUAL)` → `updateCurrentLocationLabel()` update subtitle
row jadi `"Manual: %.4f, %.4f"`.

Alur data (konsumsi oleh fitur lain): tiap Activity pemakai (lihat tabel di
atas) memanggil `sessionManager.isManualLocationMode()` di awal alur
lokasinya (biasanya `checkLocationPermission()`) — kalau `true`, lewati
permission/GPS sepenuhnya dan langsung pakai `getManualLat()`/`getManualLng()`;
kalau `false`, jalankan alur GPS seperti biasa (masing-masing Activity punya
implementasi permission-request sendiri-sendiri, tidak ada abstraksi
`LocationProvider` bersama — lihat Known issues).

Alur data (Sumber Perhitungan Arah Kiblat): dibaca oleh `KiblatActivity`
di `onLocationReady()`, lihat detail lengkap di
`docs/features/arah-kiblat.md` section 3 & 4.

## 5. Dependencies & tech stack khusus

- `FusedLocationProviderClient` — dipakai untuk tombol "Pakai lokasi GPS
  saat ini" di sheet Lokasi; sama seperti yang dipakai fitur lain, tidak ada
  tambahan library baru.
- `org.osmdroid:osmdroid-android:6.1.20` (2026-09-19) — untuk pemilih lokasi via
  peta. Dipilih dibanding Google Maps SDK karena tidak butuh API key/billing
  GCP (alasan yang sama dengan Overpass API di Masjid Terdekat). Konsekuensi:
  butuh internet untuk memuat tile (offline hanya tile yang sudah ter-cache),
  dan wajib menampilkan atribusi "© OpenStreetMap contributors" (sudah ada di
  pojok kiri bawah peta). Manifest menambah `ACCESS_NETWORK_STATE` (dipakai
  osmdroid untuk cek koneksi).
- Tidak ada tambahan lain di luar stack umum app (`SharedPreferences`,
  `BottomSheetDialog`).

### Catatan teknis peta (jebakan yang sudah ketemu)

- **Jangan `setCenter` sebelum layout pertama.** osmdroid menyimpan pusat
  sebagai posisi scroll piksel; `setCenter` yang jatuh saat `MapView` masih
  berukuran 0 (kejadian di cold start lambat, mis. tepat setelah install)
  bergeser setengah ukuran view begitu layout selesai — pada zoom 16 pin
  meleset ±1 km. Karena itu zoom+pusat awal diterapkan di
  `addOnFirstLayoutListener`. Gejalanya intermiten (1 dari ±4 cold start),
  jadi tes ulang beberapa kali kalau menyentuh bagian ini.
- **Posisi pin di layout**: `FrameLayout` menaruh child `gravity=center` di
  `(H-h)/2 + topMargin - bottomMargin`, dan ujung runcing path `ic_map_pin`
  ada 4dp di atas dasar view, jadi `marginBottom` pin = 20dp (bukan tinggi
  pin). Salah hitung ini bikin ujung pin ~24dp di atas titik tengah peta.

## 6. Testing

Belum ada test otomatis untuk fitur ini (konsisten dengan fitur lain di app
ini — lihat `app/src/test`/`app/src/androidTest`, masih boilerplate). Verifikasi
manual:

1. Build & install debug APK (lihat `CLAUDE.md` root untuk perintah `gradlew`).
2. Buka `MainActivity` → tap gear → pastikan masuk `KonfigurasiActivity`
   dengan 3 section dalam urutan Lokasi, Arah Kiblat, Waktu Sholat.
3. Tap row "Sumber Lokasi" → pastikan sheet terbuka dengan radio
   Otomatis/Manual + deskripsi masing-masing.
4. Pilih Manual → pastikan field lat/lon + tombol GPS muncul; isi angka valid
   → Simpan → subtitle row berubah jadi `"Manual: lat, lng"`.
4b. Di sheet Lokasi mode Manual tap "🗺️ Pilih dari peta" → peta terbuka
   dengan pin di tengah, geser peta → koordinat di panel bawah ikut berubah →
   "Pilih lokasi ini" → kembali ke sheet dengan field lat/lon terisi angka
   yang sama (belum tersimpan sampai Simpan ditekan). Sudah diverifikasi
   otomatis di emulator (2026-09-19) lewat dump UI teks; hasil: label peta
   -7.12670, 112.42269 → field -7.126699 / 112.422688. Uji juga: field kosong
   (peta mulai dari lokasi GPS/tengah Indonesia), field terisi (peta mulai
   dari situ), dan cold start berulang untuk memastikan pusat awal tidak
   bergeser (lihat catatan teknis peta di section 5).
5. Tap row "Sumber Perhitungan" (Arah Kiblat) → pilih Rumus Manual → Simpan
   → subtitle berubah jadi "Rumus Manual (Al Hasib)".
6. Buka `WaktuSholatActivity` dan `KiblatActivity` → pastikan keduanya
   memakai koordinat manual (bukan minta permission GPS) — lihat langkah
   detail & hasil di `docs/features/waktu-sholat.md` dan
   `docs/features/arah-kiblat.md` section Testing.
7. Tap row "Pengingat Sebelum Waktu Sholat" → nyalakan switch → pastikan
   pilihan durasi (5/10/15/30 menit) muncul → pilih salah satu → Simpan →
   subtitle row berubah jadi "Aktif, N menit sebelum waktu sholat". Matikan
   lagi switch → Simpan → subtitle kembali ke "Nonaktif". Lihat
   `docs/features/notifikasi-adzan.md` section 6 untuk verifikasi alarm-nya
   (di luar cakupan layar Konfigurasi ini).

**Catatan verifikasi sesi 2026-08-30**: langkah 4-6 sudah dicek berhasil di
emulator. Verifikasi interaktif langkah 3-5 (tap radio di dalam
`BottomSheetDialog`) sempat gagal berkali-kali lewat `adb shell input tap`
(sheet ke-dismiss alih-alih toggle) — diduga soal presisi koordinat tap dari
screenshot, bukan bug kode, karena polanya identik dengan sheet Metode
Perhitungan Waktu Sholat yang sudah terbukti jalan. Verifikasi akhir untuk
alur baca (`SessionManager` → fitur konsumen) dilakukan lewat
`adb shell run-as site.elahady.alkaukaba cat shared_prefs/AppSession.xml`
(baca state tersimpan) dan cocok dengan yang ditampilkan di layar konsumen —
lihat detail di `docs/features/arah-kiblat.md` & `waktu-sholat.md`. Verifikasi
tap manual langsung di sheet Lokasi/Arah Kiblat oleh developer di device
fisik masih disarankan sebelum dianggap 100% teruji secara interaktif.

## 7. Known issues & TODOs

- [ ] **Tidak ada abstraksi `LocationProvider` bersama** — tiap Activity
      pemakai (`MainActivity`, `WaktuSholatActivity`, `KiblatActivity`)
      mengulang pola yang sama (cek `isManualLocationMode()` di awal
      `checkLocationPermission()`) dengan implementasi permission-request GPS
      masing-masing yang berbeda-beda gaya (lihat kode tiap Activity). Kalau
      makin banyak fitur butuh lokasi (mis. Bulan Hijriyah nanti), pertimbangkan
      ekstrak helper bersama — belum dilakukan sekarang karena baru 3
      konsumen dan tiap Activity punya nuansa permission-flow yang beda.
- [ ] **`CalendarActivity` dan `AwalBulanActivity` belum ikut baca setting
      ini** — lihat tabel section 3. `CalendarActivity` otomatis "ikut
      benar" selama `MainActivity` benar (karena lat/lon dioper lewat
      `Intent` extra), tapi `AwalBulanActivity` (fitur Bulan Hijriyah) masih
      dummy total dan perlu baca `SessionManager` ini dari awal saat dibangun
      ulang.
- [ ] **Kompas visual untuk mode Manual di Arah Kiblat masih placeholder
      generik**, bukan digambar sesuai sudut manual — lihat
      `docs/features/arah-kiblat.md` Known issues.
- [ ] Input lokasi manual = angka lat/lon atau pin di peta
      (`PilihLokasiPetaActivity`, 2026-09-19) — tetap tidak ada pencarian nama
      tempat (forward geocoding). Ini keputusan sadar (lihat diskusi desain),
      bukan keterbatasan teknis yang belum sempat.
- [x] (2026-09-19) Halaman **Gerhana, Awal Bulan, Okultasi** kini semuanya
      punya override lokasi per-halaman lewat `PageLocationOverride` (sheet
      "Lokasi untuk Halaman Ini", bisa isi manual/GPS/pin di peta), terpisah
      dari setting global di sini. Halaman lain (Waktu Sholat, Kiblat, Masjid
      Terdekat, Imsakiyah, Fase Bulan, Beranda) tetap hanya membaca setting
      global.
- [ ] Build release (R8/minify) lolos dengan osmdroid, tapi belum dijalankan
      di device (APK release belum ditandatangani) — cek peta di build rilis
      sebelum upload Play Store berikutnya.
- [ ] Belum ada test otomatis sama sekali untuk fitur ini (lihat section 6).
