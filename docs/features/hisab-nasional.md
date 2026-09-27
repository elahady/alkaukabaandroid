# Hisab Awal Bulan (Hijriyah) Nasional

## 1. Ringkasan

**Fitur**: hisab awal bulan Hijriyah di titik-titik markaz (ibu kota
provinsi) yang bisa dipilih user, ditampilkan sebagai daftar per-markaz +
satu kesimpulan level nasional (berapa dari titik yang dihitung memenuhi
kriteria Neo-MABIMS).

Per 2026-09-16 (revisi kedua): awalnya cuma 8 titik hardcode, tapi didiskusikan
ulang dengan user soal risiko kalau semua 38 ibu kota provinsi dimasukkan
sekaligus (waktu hitung makin lama + banyak titik berdekatan bujur jadi
redundan, lihat section 5). Solusi yang disepakati: **default kecil yang
mengisi celah bujur** (11 titik) + **checklist di `PilihMarkazActivity`**
supaya user yang mau lebih lengkap (sampai 38 ibu kota provinsi) tinggal
centang sendiri, tanpa menaikkan beban default.

Ini pelengkap [`bulan-hijriyah.md`](bulan-hijriyah.md) ("Awal Bulan"), bukan
pengganti — "Awal Bulan" menghitung untuk **satu markaz** (lokasi user,
GPS/manual), fitur ini menghitung **banyak markaz sekaligus** untuk memberi
gambaran apakah kriteria imkan rukyat cenderung terpenuhi merata di seluruh
Indonesia atau hanya di sebagian wilayah — mendekati logika yang jadi dasar
Sidang Isbat Kemenag (yang mempertimbangkan hasil rukyat/hisab dari banyak
titik pemantauan, bukan cuma satu tempat).

Dibuat 2026-09-16 sebagai isi awal untuk ticket Notion "Hisab Awal Bulan
(Hijriyah) Nasional" — user belum punya spesifikasi rinci saat ticket dibuat,
jadi cakupan/desain menu & layar ini adalah interpretasi pertama yang masuk
akal, bukan hasil requirement gathering. Lihat section 7 untuk hal-hal yang
kemungkinan perlu didiskusikan ulang dengan user.

## 2. Entry point & navigasi

- Layar: `HisabNasionalActivity` (layout `activity_hisab_nasional.xml`,
  judul UI "Hisab Awal Bulan Nasional").
- Terdaftar di `AndroidManifest.xml` (`exported="false"`, sama seperti
  activity fitur lain).
- Dibuka dari grid "Semua Menu" (`SemuaMenuActivity`), item "Hisab Awal
  Bulan Nasional" — sengaja diletakkan tepat setelah "Okultasi" sesuai
  permintaan user saat ticket dibuat, warna baru `bg_cyan_light`/`icon_cyan`
  (ditambahkan khusus fitur ini karena semua 11 pasangan warna kartu menu
  yang ada sudah terpakai masing-masing tepat sekali). Icon custom
  `ic_menu_hisab_nasional.xml` (awalnya reuse `ic_menu_document`, diganti
  setelah user komplain tidak merepresentasikan fitur) — gabungan hilal/
  bulan sabit (evenOdd dua lingkaran, pola sama dengan `ic_menu_hilal`) di
  atas + garis horizontal dengan 3 titik di bawahnya, mewakili "perhitungan
  + akumulasi dari beberapa markaz" sesuai permintaan user.
- **Juga ditambahkan ke quick-access home** (`activity_main.xml`/
  `MainActivity.kt`, `bt_hisab_nasional`) — awalnya cuma ditaruh di "Semua
  Menu", tapi user eksplisit minta muncul juga di home tepat setelah
  Okultasi. Grid home dikunci tetap 2 baris x 4 kolom (bukan direnggangkan
  jadi 3 baris atau diubah ke 3 kolom) sesuai instruksi user — supaya
  8 slot tetap pas, **"Kalkulator" (`bt_kalkulator`) dihapus dari home**
  atas permintaan user (tetap ada di "Semua Menu", cuma tidak lagi jadi
  shortcut cepat di home). Urutan home sekarang: Kiblat, Waktu Sholat,
  Awal Bulan, Gerhana / Okultasi, **Hisab Nasional**, Al-Qur'an, Doa &
  Dzikir.
- Tidak butuh permission lokasi sama sekali — semua titik markaz sudah
  fixed by-design (koordinat ibu kota provinsi, bukan GPS user), beda dari
  "Awal Bulan"/Okultasi.
- Tombol ⚙️ di toolbar (`btnToolbarAction`, icon `ic_settings`) -> buka
  `PilihMarkazActivity` (layout `activity_pilih_markaz.xml`) — checklist 38
  ibu kota provinsi buat pilih markaz mana yang mau dihitung.

## 3. Alur

**HisabNasionalActivity:**
1. `onCreate` cuma setup UI/RecyclerView, TIDAK langsung hitung.
2. `onResume` -> baca `SessionManager.getSelectedMarkazNasionalIds()` (null
   kalau user belum pernah atur -> fallback `HisabNasionalCalculator.
   defaultMarkazIds`) -> `HisabNasionalViewModel.calculateNasional(selectedIds)`.
   Sengaja di `onResume` (bukan `onCreate`) supaya begitu user balik dari
   `PilihMarkazActivity` sehabis ubah pilihan & simpan, hasil langsung
   ke-refresh otomatis tanpa perlu tombol refresh manual.
3. Hisab dijalankan sekali per markaz terpilih (`EphemerisCalculator.
   calculate()`) di `Dispatchers.Default` supaya UI tidak nge-freeze, hasil
   dikirim sebagai satu list lewat LiveData.
4. Kartu navy di atas menampilkan label bulan Hijriyah yang dicek + kalimat
   kesimpulan ("X dari Y titik markaz memenuhi kriteria...") + badge status.
5. Di bawahnya, `RecyclerView` menampilkan kartu per-markaz: nama+provinsi,
   ghurub, badge Memenuhi/Belum Memenuhi, lalu 2 grup data — "Matahari saat
   Ghurub" (tinggi/irtifa' + azimuth) dan "Hilal (Bulan) saat Ghurub"
   (tinggi/irtifa' + azimuth + elongasi). Semua field ini sudah dihitung
   `EphemerisCalculator` sejak awal (`HilalResult.tinggiMatahari` /
   `azimuthMatahari` / `azimuthHilal`) — per 2026-09-16 baru ditampilkan di
   kartu markaz (sebelumnya cuma tinggi hilal & elongasi), atas permintaan
   user yang mau lihat data matahari & azimuth kedua benda langit juga.
6. `btnBack` toolbar -> `finish()`.

**PilihMarkazActivity:**
1. Load pilihan tersimpan (atau default kalau belum pernah diatur) ke
   `selectedIds: MutableSet<String>`, dipegang di Activity (bukan di
   `MarkazCheckboxAdapter`) supaya tombol "Pilih Semua"/"Pakai Default" bisa
   ubah banyak item sekaligus lalu tinggal `notifyDataSetChanged()`.
2. Tap baris manapun toggle checkbox-nya (row seluruhnya clickable, bukan
   cuma kotak centangnya — `CheckBox` di `item_markaz_checkbox.xml` sengaja
   `clickable="false"` biar tidak rebutan touch target dengan root row).
3. "Simpan" -> tolak (Toast) kalau 0 dicentang, kalau tidak ->
   `SessionManager.setSelectedMarkazNasionalIds(selectedIds)` -> `finish()`
   -> `HisabNasionalActivity.onResume()` otomatis hitung ulang dengan
   pilihan baru.

## 4. Struktur & alur data

| File | Peran |
|---|---|
| `ui/hisabnasional/HisabNasionalActivity.kt` + `activity_hisab_nasional.xml` | UI: kartu kesimpulan nasional + daftar per-markaz |
| `ui/hisabnasional/PilihMarkazActivity.kt` + `activity_pilih_markaz.xml` | UI: checklist 38 markaz + tombol Pilih Semua/Pakai Default/Simpan |
| `viewmodel/hisabnasional/HisabNasionalViewModel.kt` | `LiveData<List<MarkazHisabResult>>`, jembatan ke `HisabNasionalCalculator`; `calculateNasional(selectedIds, monthOffset)` menerima set ID markaz aktif |
| `utils/HisabNasionalCalculator.kt` | `allMarkaz` — 38 `MarkazNasional` (ibu kota tiap provinsi, diurutkan `longitude`) + `defaultMarkazIds` (11 ID, subset yang mengisi celah bujur — lihat section 5) + `calculate(monthOffset, selectedIds)` yang filter `allMarkaz` lalu panggil `EphemerisCalculator.calculate()` (mesin yang sama dgn "Awal Bulan", lihat [`bulan-hijriyah.md` section 5](bulan-hijriyah.md#5-mesin-hisab-ephemeriscalculator)) per titik terpilih |
| `utils/SessionManager.kt` | `getSelectedMarkazNasionalIds()`/`setSelectedMarkazNasionalIds()` — persist pilihan user (`Set<String>` di SharedPreferences, key `HISAB_NASIONAL_MARKAZ_IDS`), `null` = belum pernah diatur |
| `model/HisabNasionalModels.kt` | `MarkazNasional` (`id` slug provinsi + nama, provinsi, lat, lng) dan `MarkazHisabResult` (markaz + `HilalResult`) |
| `adapter/MarkazHisabAdapter.kt` + `item_markaz_hisab.xml` | Render satu kartu hasil per markaz di `RecyclerView` (`HisabNasionalActivity`) |
| `adapter/MarkazCheckboxAdapter.kt` + `item_markaz_checkbox.xml` | Render satu baris checklist per markaz di `RecyclerView` (`PilihMarkazActivity`) |

Alur data: `onResume` -> baca `SessionManager` -> `calculateNasional(selectedIds)`
-> filter+loop markaz terpilih -> `EphemerisCalculator.calculate()` per markaz
-> `List<MarkazHisabResult>` -> LiveData -> Activity render kartu kesimpulan
(hitung `count { hilalMemenuhiKriteria }`) + `MarkazHisabAdapter.setData()`.

## 5. Pemilihan titik markaz & kriteria

`allMarkaz` mencakup ibu kota ke-38 provinsi (per pemekaran Papua 2022),
supaya user yang mau lengkap punya opsi. `id` tiap markaz pakai slug
provinsi (bukan nama kota) sebagai key persist — stabil biarpun label kota
di UI berubah nanti.

`defaultMarkazIds` (11 titik: Aceh, Sumbar, DKI Jakarta, DIY, Jatim, NTB,
Sulsel, Sulut, Maluku, Papua Barat, Papua) dipilih murni untuk mengisi celah
bujur (bukan "kota besar terkenal") — didiskusikan dengan user soal risiko
kalau default-nya all-38: (a) waktu hitung ~38x pemanggilan
`EphemerisCalculator.calculate()` sekaligus di setiap buka layar, (b) banyak
ibu kota berdekatan bujur (mis. semua provinsi di Jawa/Sumatera) memberi
hasil nyaris identik karena visibilitas hilal di Indonesia dominan
dipengaruhi bujur, bukan lintang — jadi menambah titik di situ tidak
menambah informasi, cuma menambah waktu tunggu. Kesepakatannya: default
kecil & merata, opsi lengkap tersedia via checklist (section 3) buat user
yang mau eksplisit.

**Bukan** daftar titik rukyat resmi Kemenag (yang jumlahnya ratusan dan
tersebar per kabupaten/kota, bukan cuma ibu kota provinsi). Kriteria
kelulusan tiap titik pakai Neo-MABIMS yang sama dengan "Awal Bulan" (tinggi
hilal ≥3°, elongasi ≥6.4°, dari `HilalResult.hilalMemenuhiKriteria` — tidak
ada logika kriteria baru yang ditulis khusus di sini).

Kesimpulan nasional di layar cuma penjumlahan sederhana
(`count/total` markaz yang lulus) dengan 3 label:
"Kriteria Terpenuhi Secara Nasional" (semua lulus), "Kriteria Terpenuhi
Sebagian Wilayah" (sebagian), "Kriteria Belum Terpenuhi di Semua Markaz"
(tidak ada yang lulus) — **bukan** simulasi proses Sidang Isbat sungguhan
(yang mempertimbangkan laporan rukyat lapangan, bukan cuma hisab).

## 6. Testing

Belum ada test otomatis (`HisabNasionalCalculator` murni komposisi berulang
dari `EphemerisCalculator` yang sudah ada, tidak ada rumus baru yang perlu
divalidasi terpisah). Verifikasi manual (emulator, 2026-09-16, dicek via
`uiautomator dump`, bukan screenshot — lihat `CLAUDE.md` soal batasi
screenshot):

- Home -> "Hisab Nasional" -> default 11 markaz terhitung benar ("11 dari 11
  titik markaz memenuhi kriteria...", badge "Kriteria Terpenuhi Secara
  Nasional") tanpa crash.
- Toolbar ⚙️ -> `PilihMarkazActivity` terbuka, list terurut benar mulai dari
  markaz paling barat (Banda Aceh), tombol Pilih Semua/Pakai Default/Simpan
  semua ada & bisa di-tap.
- Tap "Pilih Semua" -> "Simpan" -> kembali ke `HisabNasionalActivity` tanpa
  crash (dikonfirmasi lewat `dumpsys activity activities` — resume ke
  activity yang benar, task ID sama, bukan restart).
- Verifikasi lanjutan (hasil 38/38 setelah "Pilih Semua") **tidak sempat
  dikonfirmasi lewat dump** — sesi emulator ini dipakai bersamaan oleh sesi
  Claude lain (`alkaukaba-00`, terlihat dari `ListAgents` + perubahan file
  tak terduga seperti fitur "Jadwal Imsakiyah" muncul di tengah kerja) yang
  ikut mengirim perintah `adb`/rebuild ke emulator yang sama, sempat
  menyebabkan activity balik ke `MainActivity` di tengah pengecekan. Logcat
  penuh dicek ulang (`FATAL EXCEPTION`) dan bersih — tidak ada crash dari
  app, jadi kemungkinan besar cuma race navigasi antar-sesi, bukan bug. Perlu
  dicek ulang manual sekali lagi saat emulator tidak dipakai bersamaan.

## 7. Known limitations

- [ ] Home quick-access sekarang cuma menyisakan 8 fitur (Kalkulator
      dikeluarkan supaya "Hisab Nasional" muat tanpa nambah baris) — kalau
      nanti ada fitur lain yang mau ditambahkan ke home juga, slotnya sudah
      penuh lagi, perlu keputusan serupa (ganti salah satu, atau baru boleh
      nambah baris/kolom).

- [ ] **Scope/desain layar ini adalah interpretasi pertama, belum divalidasi
      user** — ticket Notion dibuat kosong ("saya belum punya ide untuk
      isinya"), jadi kemungkinan besar perlu direvisi setelah user lihat
      hasilnya (mis. mungkin maunya bukan daftar 8 kota tapi sesuatu yang
      lain sama sekali).
- [ ] Jam ghurub ditampilkan mengikuti zona waktu **perangkat**
      (`EphemerisCalculator.formatLocalTime` pakai `Locale`/`TimeZone`
      default device), BUKAN zona waktu lokal tiap markaz (WIB/WITA/WIT) —
      sudah diberi disclaimer di UI, tapi kalau nanti mau akurat per-zona,
      `formatLocalTime` perlu parameter `TimeZone` eksplisit per markaz
      (perubahan ke `EphemerisCalculator` yang dipakai bareng "Awal Bulan",
      jadi perlu hati-hati tidak mengubah perilaku fitur itu).
- [ ] Tidak ada selector bulan/tahun seperti "Awal Bulan" (section 3 di
      `bulan-hijriyah.md`) — selalu bulan Hijriyah terdekat ke depan
      (`monthOffset=0`). `HisabNasionalViewModel.calculateNasional()` sudah
      menerima parameter `monthOffset` supaya gampang disambung ke UI
      selector kalau nanti dibutuhkan.
- [ ] 38 titik markaz (ibu kota provinsi) hardcode di kode
      (`HisabNasionalCalculator.allMarkaz`) — kalau user mau titik yang
      berbeda/mengikuti daftar resmi Kemenag (ratusan titik per
      kabupaten/kota), tinggal ubah list ini, tidak ada dependensi lain yang
      perlu diubah (UI checklist otomatis ikut menyesuaikan). Per 2026-09-28,
      list yang sama juga direuse oleh `PilihKotaActivity` (picker kota
      single-select untuk Jadwal Imsakiyah & Waktu Sholat, lihat
      `docs/features/jadwal-imsakiyah.md`/`waktu-sholat.md`) lewat
      `HisabNasionalCalculator.resolveMarkaz(id)` — perubahan pada
      `allMarkaz` (tambah/hapus/ubah kota) otomatis ikut memengaruhi kedua
      picker itu juga.
- [ ] Elevasi (`heightMeters`) semua markaz disamakan `0.0` — penyederhanaan,
      belum pakai elevasi asli tiap kota.
- [ ] `PilihMarkazActivity` belum ada search/filter — scroll manual di 38
      item masih wajar, tapi kalau daftar markaz diperbesar lagi (mis. ke
      level kabupaten/kota) perlu ditambah search box.
- [ ] Verifikasi manual belum tuntas untuk alur "Pilih Semua" -> hasil 38/38
      benar-benar tampil (lihat section 6) — terganggu sesi emulator
      bersamaan, perlu dicek ulang.
