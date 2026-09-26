# Dokumentasi Teknis — Al-Kaukaba Android

Folder ini berisi dokumentasi teknis per-fitur: keputusan desain, alasan di balik
implementasi yang tidak jelas hanya dari membaca kode, dan referensi ke file-file
kunci. Ini melengkapi `CLAUDE.md` di root repo (instruksi operasional cara
build/jalankan project), bukan menggantikannya.

## Konvensi struktur folder

- Satu file Markdown per fitur, di `docs/features/<nama-fitur>.md`.
- Nama file pakai kebab-case sesuai nama fitur di UI, mis. `waktu-sholat.md`
  untuk fitur "Waktu Sholat".
- Update dokumen begitu keputusan desain berubah — dokumen yang basi lebih
  berbahaya daripada tidak ada dokumen.

## Template isi dokumen fitur

> Catatan penting sebelum pakai template ini: repo ini **single Gradle module**
> (cuma `:app`), tanpa DI framework (Hilt/Koin/Dagger), tanpa split
> `data/domain/presentation`, dan navigasi antar-layar pakai `Intent` biasa
> (bukan Navigation Component/DeepLink). Jangan isi section di bawah dengan
> hal yang tidak ada di repo ini (mis. `implementation project(':features:x')`)
> — kalau suatu section memang tidak relevan untuk fitur yang didokumentasikan,
> tulis singkat "tidak relevan" beserta alasannya, jangan dikosongkan diam-diam.

### 1. Ringkasan (Overview)
- **Nama fitur**
- **Deskripsi singkat** — 1-2 kalimat, apa fungsi fitur ini dan masalah apa
  yang diselesaikannya.

### 2. Entry point & prasyarat
- Dari layar/tombol mana fitur ini dipicu (activity + elemen UI-nya).
- Prasyarat: permission, API key/config khusus, dsb yang dibutuhkan fitur ini
  supaya jalan (kalau ada).

### 3. Titik masuk logika & navigasi
- Kelas/fungsi kunci yang jadi tempat "colok" kalau developer lain mau
  extend/pakai ulang logika fitur ini (mis. object berisi daftar preset,
  method public di ViewModel/Repository).
- Navigasi antar-layar di dalam fitur ini (Activity mana ke Activity mana,
  lewat `Intent` apa, extra apa saja yang dibawa).

### 4. Struktur & alur data
- Daftar file nyata yang terlibat beserta perannya masing-masing (bukan
  struktur folder generik `data/domain/presentation` — tulis file yang benar
  ada di repo).
- Alur data singkat, mis. `View -> ViewModel -> Repository -> Retrofit`.

### 5. Dependencies & tech stack khusus
- Library pihak ketiga yang krusial/spesifik dipakai fitur ini saja (bukan
  yang sudah dipakai app-wide, seperti Retrofit/Gson). Kalau tidak ada, tulis
  "tidak ada tambahan khusus di luar stack umum app".

### 6. Testing
- Cakupan test otomatis untuk fitur ini saat ini (jujur — kalau belum ada,
  tulis itu sebagai gap, jangan dilewati).
- Cara manual verifikasi kalau belum ada test otomatis (mis. langkah build +
  install debug APK + skenario yang perlu dicoba di emulator).

### 7. Known issues & TODOs
- Technical debt atau bagian yang sengaja belum selesai/placeholder.
- Bug yang diketahui tapi belum jadi prioritas.

---

**Tips level-kode:** pakai KDoc pada kelas/fungsi public yang jadi titik masuk
fitur (bagian "3. Titik masuk logika" di atas) — nanti bisa di-generate jadi
HTML/Markdown pakai Dokka kalau suatu saat dibutuhkan.

## Dokumen lain

- [roadmap-clean-code.md](roadmap-clean-code.md) — bukan per-fitur, tapi
  catatan keputusan/rencana lintas-fitur soal kualitas kode (hygiene fixes,
  kapan domain layer/DI framework baru relevan, dsb).
- [rilis-play-store.md](rilis-play-store.md) — catatan proses rilis update
  Play Store yang sedang berjalan (status request upload key reset, lokasi
  keystore, draft release notes) — bukan per-fitur, dan isinya berubah
  seiring proses rilis (bukan dokumen final/statis).
- [referensi-ilmu-falak.md](referensi-ilmu-falak.md) — referensi keilmuan
  Ilmu Falak (fase Bulan, manazil, parameter visibilitas hilal, sejarah
  hisab Nusantara, Wujudul Hilal vs Imkanur Rukyat Neo-MABIMS, KHGT) yang
  jadi latar belakang konseptual fitur Bulan Hijriyah & Fase Bulan — bukan
  dokumen arsitektur/rumus teknis.
- [strategi-unit-test.md](strategi-unit-test.md) — bukan per-fitur, metodologi
  lintas-fitur: kapan pakai golden/reference test vs mock test, teknik
  `mockkObject` untuk dependency berupa Kotlin `object`, alat tambahan
  (`InstantTaskExecutorRule`/`coroutines-test`/Turbine) dan kapan masing-masing
  dibutuhkan, serta catatan kompatibilitas versi mockk vs Kotlin plugin 1.7.10.

## Status dokumentasi

| Fitur | Dokumen | Status |
|---|---|---|
| Waktu Sholat (metode perhitungan) | [features/waktu-sholat.md](features/waktu-sholat.md) | Terdokumentasi |
| Waktu Sholat — rumus hisab Ephemeris (referensi rumus, bukan arsitektur) | [features/rumus-hisab-ephemeris.md](features/rumus-hisab-ephemeris.md) | Terdokumentasi |
| Arah Kiblat | [features/arah-kiblat.md](features/arah-kiblat.md) | Terdokumentasi |
| Bulan Hijriyah / Awal Bulan | [features/bulan-hijriyah.md](features/bulan-hijriyah.md) | Terdokumentasi |
| Gerhana | [features/gerhana.md](features/gerhana.md) | Terdokumentasi |
| Fase Bulan (kartu home + detail) | [features/fase-bulan.md](features/fase-bulan.md) | Terdokumentasi |
| Kalender | [features/kalender.md](features/kalender.md) | Terdokumentasi |
| Autentikasi (login/register) | [features/autentikasi.md](features/autentikasi.md) | Terdokumentasi |
| Konfigurasi (Lokasi, Sumber Kiblat, Metode Sholat) | [features/konfigurasi.md](features/konfigurasi.md) | Terdokumentasi |
| Profil (akun, bantuan, keamanan) | [features/profil.md](features/profil.md) | Terdokumentasi |
| Notifikasi Adzan + Personalisasi Suara + Pengingat Pra-Adzan | [features/notifikasi-adzan.md](features/notifikasi-adzan.md) | **Dihapus dari kode 2026-09-26** (backlog; dokumen dipertahankan sebagai spesifikasi, kode terakhir ada di commit `b609ae0`) |
| Al-Qur'an Digital & Audio | [features/al-quran.md](features/al-quran.md) | Terdokumentasi |
| Kumpulan Doa & Dzikir (Al-Mathurat/Hisnul Muslim) | [features/doa-dzikir.md](features/doa-dzikir.md) | Terdokumentasi (backend belum di-deploy produksi) |
| Kalkulator Scientific | [features/kalkulator-scientific.md](features/kalkulator-scientific.md) | Terdokumentasi |
| Ayat/Hadits Harian (Kutipan Harian) | [features/kutipan-harian.md](features/kutipan-harian.md) | Terdokumentasi (belum diverifikasi visual di emulator) |
| Kalkulator Zakat (Fitrah/Mal/Profesi) | [features/zakat.md](features/zakat.md) | Terdokumentasi (tab Profesi & hasil hitung Mal belum diverifikasi visual, lihat §7) |
| Masjid Terdekat | [features/masjid-terdekat.md](features/masjid-terdekat.md) | Terdokumentasi (perlu re-verifikasi end-to-end di emulator, lihat Known issues) |
| Tasbih Digital | [features/tasbih-digital.md](features/tasbih-digital.md) | Terdokumentasi (belum diverifikasi visual di emulator) |
| Jadwal Imsakiyah | [features/jadwal-imsakiyah.md](features/jadwal-imsakiyah.md) | Terdokumentasi |
| Peringatan Hari Wafat (7/40/100/1.000 hari) | [features/peringatan-kematian.md](features/peringatan-kematian.md) | Terdokumentasi (DatePicker & mode gelap belum diverifikasi visual, lihat §7) |
| Konversi Hijriyah - Masehi | [features/konversi-hijriyah-masehi.md](features/konversi-hijriyah-masehi.md) | Terdokumentasi (pesan validasi & date picker belum dicek visual) |
