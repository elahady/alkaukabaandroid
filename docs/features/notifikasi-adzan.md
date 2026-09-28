# Notifikasi Adzan

> **Riwayat**: fitur ini sempat dihapus dari kode pada 2026-09-26 (commit
> `86c2623`, masuk backlog) lalu **dihidupkan kembali pada 2026-09-28** dengan
> me-revert commit tersebut, plus tiga perubahan (lihat "Perubahan 2026-09-28"
> di bawah). Isi dokumen ini sudah mengikuti kondisi kode terbaru.
>
> **Perubahan 2026-09-28**
> - **Audio diganti** dengan 2 rekaman yang diberikan pemilik project:
>   `res/raw/adzan_standar.mp3` (Dzuhur/Ashar/Maghrib/Isya) dan
>   `res/raw/adzan_subuh.mp3` (Subuh). File Marrakesh & rekaman Mekkah lama
>   dibuang dari repo. Pemetaan ada di `AdzanSound` (satu-satunya tempat).
> - **Acuan wilayah = lokasi yang tampil di Beranda** (`MainActivity`):
>   `AdzanRefreshWorker.resolveLocation()` memakai urutan lokasi manual
>   (Konfigurasi) → GPS `lastLocation` → koordinat terakhir yang dipakai
>   Beranda (`SessionManager.get/setLastHomeLocation`) → Jakarta. Beranda
>   memanggil `AdzanRefreshWorker.onHomeLocationResolved()` tiap lokasi
>   ketemu; kalau lokasinya bergeser >0,05° (~5 km) atau baru pertama, alarm
>   langsung dijadwalkan ulang. Cadangan dari Beranda diperlukan karena
>   Android biasanya tidak memberi `lastLocation` ke worker di background
>   (tanpa izin lokasi latar belakang). Pilihan kota per-fitur di Waktu
>   Sholat/Imsakiyah **tidak** dipakai adzan.
> - **Penjadwalan kemunculan berikutnya**: `AdzanScheduler.scheduleFromTimings()`
>   menerima jadwal besok (`tomorrowTimings`, diambil worker) dan memasang tiap
>   waktu sholat pada kemunculan berikutnya — hari ini kalau belum lewat, besok
>   kalau sudah. `AdzanAlarmReceiver` juga men-enqueue refresh tiap kali adzan
>   berbunyi, jadi Subuh besok terpasang begitu Isya selesai tanpa menunggu job
>   00:05 (yang bisa tertunda Doze). Versi lama hanya memasang sisa waktu hari
>   ini.

### 1. Ringkasan (Overview)
- **Nama fitur**: Notifikasi Adzan + Personalisasi Suara + Pengingat Pra-Adzan
- **Deskripsi singkat**: Mengirim notifikasi otomatis (dengan opsi suara) tepat
  saat masuk waktu Subuh/Dzuhur/Ashar/Maghrib/Isya, tanpa perlu app dibuka.
  User bisa memilih apakah notifikasinya berupa adzan penuh, beep pelan (mis.
  untuk situasi di kantor), atau senyap (visual saja). Sebelum fitur ini
  dibangun (2026-09-05), app **tidak punya mekanisme notifikasi apa pun** —
  ini fondasi pertamanya, bukan sekadar penambahan opsi ke sistem yang sudah
  ada.
- **Pengingat Pra-Adzan** (ditambahkan 2026-09-15, ide awal dari Notion "🚀
  Pengembangan Al-Kaukaba" → "Pengingat Pra-Waktu Sholat (Pre-Adzan
  Reminder)"): opsi tambahan, terpisah dari & independen terhadap notifikasi
  adzan di atas — sekali diaktifkan, user dapat notifikasi biasa (getar, tanpa
  suara adzan) beberapa menit (5/10/15/30, pilihan user) sebelum tiap dari 5
  waktu sholat wajib tiba, supaya bisa bersiap-siap lebih awal. Nonaktif by
  default (opt-in), dan satu toggle berlaku untuk semua 5 waktu sekaligus
  (belum ada opsi per-waktu-sholat, sama seperti keterbatasan notifikasi
  adzan utama — lihat section 7).

### 2. Entry point & prasyarat
- **Trigger notifikasi**: bukan dari UI, tapi dari `AlarmManager` yang
  dijadwalkan `AdzanScheduler` — dipicu ulang tiap hari oleh `AdzanRefreshWorker`
  (WorkManager periodic, jam 00:05) dan sekali lagi tiap app baru dibuka
  (`AlKaukabaApplication.onCreate()`).
- **Setting user**: row "Suara Notifikasi Adzan" di `KonfigurasiActivity`
  (`app/src/main/java/Site/elahady/alkaukaba/ui/konfigurasi/KonfigurasiActivity.kt`,
  fungsi `showAdzanSoundSheet()`) — lihat juga
  [konfigurasi.md](konfigurasi.md) untuk pola BottomSheetDialog yang dipakai
  ulang di sini. Row "Pengingat Sebelum Waktu Sholat" (fungsi
  `showPreAdzanReminderSheet()`) memakai pola yang sama, ditambah satu
  `SwitchCompat` on/off yang menampilkan/menyembunyikan pilihan durasi.
- **Putar Suara Adzan (pratinjau)**, ditambahkan 2026-09-19: row "Putar Suara Adzan"
  di `KonfigurasiActivity` (`showAdzanPreviewSheet()`, layout
  `dialog_putar_adzan.xml`) — bottom sheet dengan dua tombol play/pause: adzan
  biasa (Dzuhur/Ashar/Maghrib/Isya) dan adzan Subuh. Pratinjau memutar file yang
  sama dengan adzan asli lewat `AdzanSound`, tapi lewat `USAGE_MEDIA` (volume
  media), bukan `USAGE_NOTIFICATION_RINGTONE` (volume dering) seperti
  `AdzanPlaybackService` — sengaja, supaya tetap terdengar walau HP mode dering
  senyap; teks di sheet menjelaskan perbedaannya. Suara otomatis berhenti saat
  sheet ditutup atau app ke background (`onStop`).
- **Prasyarat runtime**:
  - `POST_NOTIFICATIONS` (Android 13+) — diminta lewat
    `ensureNotificationPrerequisites()` saat user membuka section ini.
  - Izin "Alarm & pengingat" / `SCHEDULE_EXACT_ALARM` (Android 12+) — dicek via
    `AlarmManager.canScheduleExactAlarms()`, kalau belum diarahkan ke
    `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Kalau user menolak,
    `AdzanScheduler` fallback ke `setAndAllowWhileIdle` (tidak-exact — notifikasi
    tetap muncul tapi bisa mundur beberapa menit).
  - Koneksi internet saat `AdzanRefreshWorker` jalan (jadwal sholat diambil dari
    Aladhan API lewat `PrayerRepository`, tidak ada cache lokal — lihat section
    7 untuk risikonya).

### 3. Titik masuk logika & navigasi
- `AdzanScheduler.scheduleFromTimings(context, timings: TimingPrayers)` — titik
  masuk utama kalau developer lain mau memicu ulang penjadwalan alarm secara
  manual (mis. setelah user ganti lokasi di Konfigurasi). Fungsi ini sekaligus
  yang menjadwalkan alarm reminder pra-adzan (baca `SessionManager` di awal
  panggilan untuk tahu enabled/durasi, bukan di titik lain).
- `KonfigurasiActivity.rescheduleAdzanAlarms()` — dipanggil setelah user
  menyimpan setting pengingat pra-adzan, supaya perubahan langsung kepakai
  (enqueue `AdzanRefreshWorker` sekali secara immediate, pola sama dengan yang
  dipakai `BootReceiver`) — tidak perlu menunggu app dibuka ulang atau job
  harian jam 00:05.
- `NotificationHelper.createChannels(context)` — daftar `NotificationChannel`
  yang ada (`adzan_playback`, `adzan_beep`, `adzan_silent`,
  `pre_adzan_reminder`); tambah channel baru di sini kalau suatu saat ada mode
  suara baru.
- Tidak ada navigasi antar-Activity di fitur ini — semuanya background
  (Receiver/Service/Worker) sampai user tap notifikasi, yang membuka
  `MainActivity` (lihat `contentIntent`/`buildNotification` di
  `NotificationHelper.kt` dan `AdzanPlaybackService.kt`).

### 4. Struktur & alur data
Semua file baru di `app/src/main/java/Site/elahady/alkaukaba/notifikasi/`
kecuali `AlKaukabaApplication.kt` (root package):

| File | Peran |
|---|---|
| `AlKaukabaApplication.kt` | Application class custom — init channel + jadwalkan WorkManager (immediate + periodic 00:05) |
| `AdzanRefreshWorker.kt` | `CoroutineWorker` — fetch jadwal hari ini via `PrayerRepository`, resolve lokasi (manual → GPS → koordinat terakhir Beranda → Jakarta), ambil jadwal hari ini + besok, lalu panggil `AdzanScheduler` |
| `AdzanScheduler.kt` | Pasang `AlarmManager.setExactAndAllowWhileIdle` per waktu sholat, `PendingIntent` ke `AdzanAlarmReceiver` |
| `AdzanAlarmReceiver.kt` | Diterima tepat saat alarm bunyi — baca `SessionManager.getAdzanSoundMode()` lalu branch ke Service/NotificationHelper |
| `AdzanPlaybackService.kt` | Foreground service (`mediaPlayback`) — `MediaPlayer` play `res/raw/adzan_subuh.mp3` untuk Subuh, `res/raw/adzan_standar.mp3` untuk 4 waktu lain (dipilih dari `prayerName == AdzanScheduler.PRAYER_SUBUH`) di mode Adzan Penuh, ada tombol Stop di notifikasi |
| `AdzanSound.kt` | Satu-satunya tempat yang memetakan waktu sholat → file `res/raw` (Subuh vs lainnya), dipakai `AdzanPlaybackService` dan pratinjau di Konfigurasi |
| `NotificationHelper.kt` | Definisi `NotificationChannel` + post notifikasi untuk mode Beep/Senyap/Pengingat Pra-Adzan |
| `PreAdzanReminderReceiver.kt` | Diterima `reminderMinutes` sebelum waktu sholat — cek `SessionManager.isPreAdzanReminderEnabled()` lalu post notifikasi via `NotificationHelper.postPreAdzanReminderNotification()` |
| `BootReceiver.kt` | `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` — jadwalkan ulang alarm (hilang saat reboot), termasuk alarm reminder |

Alur data (adzan): `AlKaukabaApplication` (jadwal awal) atau `BootReceiver`
(reboot) → `WorkManager` → `AdzanRefreshWorker` → `PrayerRepository` (Aladhan
API, **reuse langsung**, tidak ada layer baru) → `AdzanScheduler` →
`AlarmManager` → `AdzanAlarmReceiver` → `AdzanPlaybackService` /
`NotificationHelper`.

Alur data (pengingat pra-adzan): sama seperti di atas sampai `AdzanScheduler`,
lalu untuk tiap waktu sholat — kalau `SessionManager.isPreAdzanReminderEnabled()`
true — dijadwalkan alarm kedua di `(waktu sholat - getPreAdzanReminderMinutes())`
menuju `PreAdzanReminderReceiver` (bukan `AdzanAlarmReceiver`), dengan
`requestCode` PendingIntent terpisah (5101-5105, lihat
`AdzanScheduler.PRE_ADZAN_REMINDER_REQUEST_CODES`) supaya tidak menimpa alarm
adzan yang sudah ada (4101-4105). `PreAdzanReminderReceiver` membaca ulang
status enabled saat alarm bunyi (pola sama seperti `AdzanAlarmReceiver` baca
mode suara saat bunyi) — kalau user sempat menonaktifkan fitur ini di antara
waktu penjadwalan dan waktu alarm bunyi, notifikasi tidak jadi muncul.

Setting user: `KonfigurasiActivity` ↔ `SessionManager` (key
`ADZAN_SOUND_MODE` untuk suara adzan; `PRE_ADZAN_REMINDER_ENABLED` &
`PRE_ADZAN_REMINDER_MINUTES` untuk pengingat pra-adzan — sama seperti key lain
di kelas itu, SharedPreferences biasa, bukan DataStore).

### 5. Dependencies & tech stack khusus
- `androidx.work:work-runtime-ktx:2.8.1` (baru ditambahkan). **Bukan 2.9.0**:
  versi itu mensyaratkan `compileSdk 34+`, sedangkan project ini masih
  `compileSdk 33` — jangan naikkan versi WorkManager tanpa menaikkan
  `compileSdk` (dan cek dampak AGP 7.2.2 yang dipakai project ini, lihat
  catatan di `app/build.gradle`).
- Tidak ada library alarm/notifikasi tambahan lain — pakai `AlarmManager`,
  `NotificationCompat`, dan `MediaPlayer` bawaan Android.

### 6. Testing
- **Belum ada test otomatis** untuk fitur ini (gap, bukan sengaja dilewati).
- Verifikasi manual yang sudah dilakukan: `gradlew compileDebugKotlin` dan
  `gradlew assembleDebug` — BUILD SUCCESSFUL, APK debug ~13MB.
- **Diverifikasi 2026-09-28 di emulator Pixel6_API34** (Android 14, lokasi manual
  Jakarta): worker jalan, 5 alarm adzan (+5 pra-adzan) terpasang ke Subuh 04:22,
  Dzuhur 11:43, Ashar 14:51, Maghrib 17:48, Isya 18:57 WIB besok (karena
  sekarang sudah lewat Isya — cek `adb shell dumpsys alarm | grep -B1
  AdzanAlarmReceiver`, kolom `origWhen` adalah epoch UTC). Simulasi alarm:
  service foreground aktif dan `MediaPlayer` `started` (`dumpsys audio`) dengan
  sample rate 44100 Hz untuk Subuh (= `adzan_subuh.mp3`) dan 22050 Hz untuk
  Dzuhur (= `adzan_standar.mp3`); tombol Stop menghentikan pemutaran.
  **Belum diverifikasi**: bunyi sungguhan lewat alarm asli di device fisik,
  layar Konfigurasi (row/bottom sheet adzan) secara visual, dan perilaku di
  Android 14+ saat izin "Alarm & pengingat" belum diberikan (lihat section 7).
- **Cara memicu alarm secara manual** (resep lama di dokumen ini keliru untuk
  device non-root): receiver `exported=false`, jadi `adb shell am broadcast` dari
  shell biasa ditolak sistem *diam-diam* ("skipped by policy… not exported" hanya
  muncul di `dumpsys activity broadcasts`). Pakai emulator (Google APIs) dengan
  `adb root` dulu:
  ```
  adb root
  adb shell am broadcast --es prayer_name "Subuh" \
    -n site.elahady.alkaukaba/.notifikasi.AdzanAlarmReceiver
  ```
  (intent yang dikirim `AdzanScheduler` tidak diberi `action`, jadi tidak perlu
  `-a`; hentikan suara dengan `adb shell am startservice -a
  site.elahady.alkaukaba.ACTION_STOP_ADZAN -n
  site.elahady.alkaukaba/.notifikasi.AdzanPlaybackService`)
  — ganti pilihan suara di Konfigurasi lalu ulangi, pastikan mode yang aktif
  yang kepakai (bukan yang di-cache saat scheduling). Test juga reboot
  (`adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n
  site.elahady.alkaukaba/.notifikasi.BootReceiver` atau reboot device
  sungguhan) untuk pastikan `BootReceiver` jalan.
- Menu "Putar Suara Adzan" (2026-09-19): `compileDebugKotlin` BUILD SUCCESSFUL;
  APK release (ditandatangani kunci debug) terpasang & terbuka di emulator tanpa
  crash, dan baris menu + bottom sheet dengan kedua tombol tampil (dicek lewat
  `uiautomator dump`). **Pemutaran audionya belum terkonfirmasi** — pengecekan
  `dumpsys audio` di emulator tidak menunjukkan player MediaPlayer aktif, tapi
  hasil itu tidak konklusif (bisa karena emulator/ketukan meleset), jadi anggap
  belum diverifikasi. Perlu dicek manual di HP: kedua tombol memutar file yang
  benar, tombol yang sama menghentikan, memencet tombol lain berpindah rekaman,
  ikon kembali ke "play" saat selesai, suara berhenti saat sheet ditutup / app ke
  background.
- Pengingat pra-adzan (2026-09-15): sama seperti di atas, belum ada test
  otomatis. Verifikasi manual yang sudah dilakukan: `compileDebugKotlin` dan
  `assembleDebug` — BUILD SUCCESSFUL. **Belum dilakukan** (perlu sebelum
  rilis): test broadcast manual ke `PreAdzanReminderReceiver`, mis.
  ```
  adb shell am broadcast \
    --es prayer_name "Dzuhur" \
    -n site.elahady.alkaukaba/.notifikasi.PreAdzanReminderReceiver
  ```
  — aktifkan dulu fitur ini di Konfigurasi (kalau nonaktif, receiver akan
  early-return tanpa notifikasi apa pun — perilaku yang benar, bukan bug), lalu
  cek label subtitle row berubah jadi "Aktif, N menit sebelum waktu sholat";
  cek juga alur end-to-end (bukan broadcast manual) dengan ganti durasi lalu
  pastikan `rescheduleAdzanAlarms()` benar-benar memasang ulang alarm di waktu
  yang baru (lihat lewat `adb shell dumpsys alarm | grep alkaukaba`).

### 7. Known issues & TODOs
- Hanya **1 pilihan "Adzan Penuh"**, bukan multi-muadzin seperti rencana awal di
  Notion. Sejak 2026-09-28 sumbernya adalah **2 rekaman yang diberikan pemilik
  project** (file WhatsApp `AUD-20260928-WA0112` → `adzan_standar.mp3`, mp3
  22,05 kHz stereo 40 kbps ~3:20, untuk Dzuhur/Ashar/Maghrib/Isya; dan
  `AUD-20260928-WA0111` → `adzan_subuh.mp3`, mp3 44,1 kHz stereo 128 kbps
  ~3:07, untuk Subuh karena memuat "as-shalatu khairun minan-naum"). Total ~4 MB
  di APK. Asal-usul/lisensi rekaman & identitas muadzin **belum tercatat** —
  konfirmasi ke pemilik project sebelum dirilis publik, dan jangan menyebut nama
  muadzin di app kalau tidak yakin. Nama qari terkenal (Mishary Alafasy dll.)
  yang beredar di GitHub/YouTube tidak punya lisensi jelas. (Rekaman Mekkah &
  Marrakesh yang dipakai sebelumnya sudah dihapus dari `res/raw`; ada di
  history git sebelum 2026-09-28.)
- **Kejelasan suara belum diukur** — `adzan_standar.mp3` berbitrate rendah (40
  kbps, 22 kHz), mungkin terdengar kurang jernih di speaker HP; belum diproses
  (denoise/EQ/normalisasi) dan belum didengar di device.
- **Waktu sholat = jam lokal koordinat, bukan jam device**: Aladhan mengembalikan
  "HH:mm" di zona waktu lokasi, sedangkan `AdzanScheduler` menafsirkannya di zona
  waktu device. Tidak masalah selama lokasi & device di zona yang sama (kasus
  normal di Indonesia), tapi kalau user memilih lokasi manual di zona lain
  (mis. Mekkah) alarm akan salah jam. Belum ditangani (Beranda pun menampilkan
  jam mentah yang sama).
- **Android 14+ menolak `SCHEDULE_EXACT_ALARM` secara default** untuk install
  baru; tanpa izin itu alarm jatuh ke `setAndAllowWhileIdle` (tidak exact) dan
  belum diuji apakah `startForegroundService` dari receiver alarm tersebut selalu
  diizinkan. Kalau adzan tidak bunyi di device tertentu, cek ini dulu.
- **Battery optimization OEM** (Xiaomi/Oppo/Vivo dkk.) belum ditangani — alarm
  exact bisa saja tetap di-kill di background pada device tertentu meski app
  sudah pakai `setExactAndAllowWhileIdle`. Perlu diarahkan ke pengaturan
  whitelist battery optimizer per-OEM kalau ada laporan notifikasi tidak
  konsisten.
- **Tidak ada fallback jadwal offline** — kalau `AdzanRefreshWorker` gagal fetch
  (tidak ada internet saat itu), `Result.retry()` dipanggil tapi tidak ada
  jadwal cadangan dari hari sebelumnya. WorkManager akan retry dengan backoff
  default, tapi kalau tetap gagal sampai alarm terakhir yang terpasang lewat,
  tidak ada alarm sama sekali sampai fetch berhasil. (Sejak 2026-09-28 jadwal
  besok ikut dipasang begitu tersedia, jadi jendela risikonya lebih sempit.)
- Belum ada UI untuk menonaktifkan notifikasi per-waktu-sholat (mis. matikan
  cuma untuk Dzuhur) — saat ini semua-atau-tidak-sama-sekali per mode suara.
  Pengingat pra-adzan (di bawah) punya keterbatasan yang sama secara sengaja
  (lihat keputusan desain di Notion, task selesai 2026-09-15).
- **Pengingat pra-adzan tidak punya toggle per-waktu-sholat** — satu switch
  on/off berlaku untuk semua 5 waktu sekaligus (keputusan desain sadar, sesuai
  diskusi task Notion "Pengingat Pra-Waktu Sholat", bukan keterbatasan teknis
  yang belum sempat dikerjakan). Kalau nanti dibutuhkan per-waktu, tambahkan
  key baru per prayer di `SessionManager` dan baca di
  `AdzanScheduler.scheduleFromTimings()` saat memutuskan jadwal reminder mana
  yang dipasang.
- **Belum ada opsi memilih menit custom** di luar 4 pilihan (5/10/15/30) —
  cukup untuk kebutuhan awal, tapi kalau ada permintaan angka lain
  pertimbangkan ganti jadi `NumberPicker`/`EditText` daripada terus menambah
  `RadioButton` di `dialog_pengingat_pra_adzan.xml`.
- Reminder pra-adzan **ikut kena keterbatasan yang sama dengan notifikasi
  adzan utama** di atas: tidak ada fallback offline (kalau `AdzanRefreshWorker`
  gagal fetch, reminder hari itu juga tidak terpasang) dan rentan battery
  optimization OEM.
