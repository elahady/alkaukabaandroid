# Al-Kaukaba Android

## Dokumentasi teknis per-fitur

Dokumentasi arsitektur/keputusan desain per-fitur **tidak** ditulis di file ini
(file ini isinya instruksi operasional untuk kerja sehari-hari), tapi di folder
`docs/`:

```
docs/
  README.md              # index + template lengkap untuk nulis doc fitur baru
  features/
    <nama-fitur>.md       # satu file per fitur, kebab-case, mis. waktu-sholat.md
```

Kalau selesai membangun/mengubah sebuah fitur secara signifikan (bukan bug fix
kecil), tambahkan atau update file di `docs/features/` sesuai template di
[`docs/README.md`](docs/README.md) — jangan salin template generik multi-module
Clean Architecture, repo ini single-module (`:app` saja), tanpa DI framework,
tanpa split `data/domain/presentation`, dan navigasi antar-layar pakai `Intent`
biasa (bukan Navigation Component/DeepLink).

## Rilis ke Play Store (proses update sedang berjalan)

Ada proses rilis update Play Store (v1.6 → 1.7) yang belum selesai per
2026-09-08 — status lengkap, lokasi keystore, dan langkah lanjutan ada di
[`docs/rilis-play-store.md`](docs/rilis-play-store.md). **Cek dokumen itu
dulu** sebelum bantu proses build/signing/upload rilis apapun — jangan mulai
dari nol nanya lokasi keystore dsb, itu semua sudah tercatat di sana. Status
per 2026-09-08: upload key reset **sudah approved**, AAB versionCode 8 siap
upload, tapi ke-blok sementara oleh **Data safety declaration** yang belum
selesai di Play Console (butuh halaman "Hapus Akun" publik di
alkaukaba.com yang belum ada — lihat §7 di docs itu untuk detail & TODO
lanjutan).

## Menjalankan & build via CLI (tanpa Android Studio)

Project ini dikerjakan sebagian dengan emulator yang dijalankan langsung dari VSCode, bukan dari Android Studio. Jangan sarankan langkah "buka/jalankan di Android Studio" — gunakan `gradlew` dari command line, dan jalankan/hubungkan emulator via `adb`.

Jika shell tidak punya `JAVA_HOME`/`ANDROID_HOME` (umum di Windows), set dulu sebelum memanggil `gradlew` — path berikut contoh di salah satu mesin dev, sesuaikan dengan instalasi JDK 17 & Android SDK di mesin masing-masing:

```
JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot" ANDROID_HOME="C:/Android/Sdk" ./gradlew.bat compileDebugKotlin --console=plain
```

## Konfigurasi API backend (AuthClient BASE_URL)

`BASE_URL` di [`AuthClient.kt`](app/src/main/java/Site/elahady/alkaukaba/utils/AuthClient.kt) menentukan app nembak ke mana untuk `api.php?action=register|login|google_login`. Backend-nya ada di repo terpisah `alkaukabaweb` (lihat `CLAUDE.md` repo itu untuk detail infra VPS, isu nginx, dsb).

- **Produksi (default sejak 2026-08-30)**: `https://alkaukaba.com/` — API sudah live di VPS, sudah divalidasi jalan (register/login lewat curl & app di emulator).
- **Testing lokal** (kalau lagi develop backend bareng, belum mau pakai server produksi):
  1. Ganti `BASE_URL` ke `"http://127.0.0.1:8000/"`.
  2. Jalankan `php artisan serve` di project `alkaukabaweb`.
  3. `adb reverse tcp:8000 tcp:8000` supaya `127.0.0.1` di emulator diteruskan ke `localhost` mesin host.
  4. `network_security_config.xml` sudah punya exception cleartext (HTTP polos) khusus untuk `127.0.0.1` dan `10.0.2.2` — kalau testing lewat IP LAN lain, exception ini nggak berlaku dan perlu ditambah domain baru atau balik ke HTTPS.
- Jangan lupa balikin ke `https://alkaukaba.com/` lagi sebelum build rilis/testing fitur yang butuh data production (kontak Circle, dsb).
- `google_login` di produksi masih belum tervalidasi end-to-end dari app (baru dites `register`/`login` biasa) — kalau nemu masalah, cek dulu `GOOGLE_CLIENT_ID` di server sama dengan yang dipanggil app di `requestIdToken(...)`.

## Setelah mengerjakan task (fix/fitur)

Setelah mengubah kode, jangan hanya compile check — build & install APK debug ke emulator yang sedang jalan supaya perubahan bisa langsung diverifikasi:

```
"$ANDROID_HOME/platform-tools/adb.exe" devices   # pastikan emulator terdeteksi
JAVA_HOME=... ANDROID_HOME=... ./gradlew.bat installDebug --console=plain
```

## Commit + push setelah selesai edit file/fitur

Setiap kali selesai mengubah/menambah kode untuk sebuah task (fix atau
fitur) dan perubahannya sudah diverifikasi (compile check / build install /
verifikasi manual), langsung `git add` + `git commit` **lalu `git push`**
perubahan tersebut ke remote — jangan menumpuk beberapa task jadi satu
commit besar di akhir sesi, dan jangan menunggu diminta eksplisit tiap
kali. Ini berlaku untuk push biasa ke branch yang sedang dikerjakan;
operasi git yang destruktif/butuh override (force push, reset --hard, dsb)
tetap wajib konfirmasi dulu seperti biasa.

## Batasi percobaan verifikasi UI via adb (jangan spam screenshot)

Kalau `adb shell input tap` di suatu koordinat tidak memberi efek (screenshot
sebelum/sesudah identik) **dua kali berturut-turut**, atau `uiautomator dump`
gagal dengan "could not get idle state" (umum terjadi di layar dengan animasi
terus-menerus, mis. kompas yang di-update sensor listener setiap frame),
**berhenti** — jangan lanjut menembak koordinat lain secara trial-and-error.
Setiap screenshot yang dibaca adalah gambar multimodal, jauh lebih mahal
token-nya dibanding teks; sesi 2026-08-30 menghabiskan puluhan screenshot
berulang untuk satu interaksi tap yang gagal di layar Kiblat, dan itu
kemungkinan besar jadi penyebab utama pemakaian token yang sangat besar untuk
task yang seharusnya kecil.

Kalau menemui ini:
1. Verifikasi lewat cara lain dulu — baca kode, compile check, atau hitung
   manual (mis. cocokkan rumus dengan angka contoh) kalau itu cukup untuk
   meyakinkan logikanya benar.
2. Kalau memang butuh konfirmasi visual/interaktif dan gagal, **laporkan apa
   adanya** ke user bahwa verifikasi otomatis tidak berhasil di lingkungan
   ini (sebutkan dugaan penyebabnya kalau ada), dan minta user coba manual —
   jangan diam-diam terus mencoba sampai berhasil atau sampai token habis.
