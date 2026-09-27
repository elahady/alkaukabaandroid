package site.elahady.alkaukaba.utils

import site.elahady.alkaukaba.model.HilalInput
import site.elahady.alkaukaba.model.MarkazHisabResult
import site.elahady.alkaukaba.model.MarkazNasional

// Hisab awal bulan Hijriyah di titik-titik markaz yang bisa dipilih user (checklist di
// PilihMarkazActivity, tersimpan via SessionManager). `allMarkaz` = 38 ibu kota provinsi
// se-Indonesia, diurutkan barat -> timur (longitude). `defaultMarkazIds` = subset kecil yang
// dipilih supaya celah bujur antar titik merata (bukan cuma "kota besar terkenal") —
// dipakai kalau user belum pernah mengubah setting, supaya hisab pertama kali dibuka tetap
// cepat & representatif tanpa harus menghitung 38 titik sekaligus.
object HisabNasionalCalculator {

    val allMarkaz = listOf(
        MarkazNasional("aceh", "Banda Aceh", "Aceh", 5.5483, 95.3238),
        MarkazNasional("sumut", "Medan", "Sumatera Utara", 3.5952, 98.6722),
        MarkazNasional("sumbar", "Padang", "Sumatera Barat", -0.9471, 100.4172),
        MarkazNasional("riau", "Pekanbaru", "Riau", 0.5071, 101.4478),
        MarkazNasional("kepri", "Tanjung Pinang", "Kepulauan Riau", 0.9186, 104.4453),
        MarkazNasional("jambi", "Jambi", "Jambi", -1.6101, 103.6131),
        MarkazNasional("babel", "Pangkal Pinang", "Bangka Belitung", -2.1316, 106.1169),
        MarkazNasional("sumsel", "Palembang", "Sumatera Selatan", -2.9761, 104.7754),
        MarkazNasional("bengkulu", "Bengkulu", "Bengkulu", -3.7928, 102.2608),
        MarkazNasional("lampung", "Bandar Lampung", "Lampung", -5.4292, 105.2610),
        MarkazNasional("kalbar", "Pontianak", "Kalimantan Barat", -0.0263, 109.3425),
        MarkazNasional("banten", "Serang", "Banten", -6.1200, 106.1500),
        MarkazNasional("dki", "Jakarta", "DKI Jakarta", -6.2088, 106.8456),
        MarkazNasional("jabar", "Bandung", "Jawa Barat", -6.9175, 107.6191),
        MarkazNasional("jateng", "Semarang", "Jawa Tengah", -6.9932, 110.4203),
        MarkazNasional("diy", "Yogyakarta", "DI Yogyakarta", -7.7956, 110.3695),
        MarkazNasional("kalteng", "Palangka Raya", "Kalimantan Tengah", -2.2096, 113.9213),
        MarkazNasional("jatim", "Surabaya", "Jawa Timur", -7.2575, 112.7521),
        MarkazNasional("kalsel", "Banjarmasin", "Kalimantan Selatan", -3.3186, 114.5944),
        MarkazNasional("bali", "Denpasar", "Bali", -8.6500, 115.2167),
        MarkazNasional("ntb", "Mataram", "Nusa Tenggara Barat", -8.5833, 116.1167),
        MarkazNasional("kaltim", "Samarinda", "Kalimantan Timur", -0.5022, 117.1536),
        MarkazNasional("kalut", "Tanjung Selor", "Kalimantan Utara", 2.8386, 117.3667),
        MarkazNasional("sulbar", "Mamuju", "Sulawesi Barat", -2.6785, 118.8879),
        MarkazNasional("sulteng", "Palu", "Sulawesi Tengah", -0.8917, 119.8707),
        MarkazNasional("sulsel", "Makassar", "Sulawesi Selatan", -5.1477, 119.4327),
        MarkazNasional("ntt", "Kupang", "Nusa Tenggara Timur", -10.1772, 123.6070),
        MarkazNasional("gorontalo", "Gorontalo", "Gorontalo", 0.5435, 123.0568),
        MarkazNasional("sultra", "Kendari", "Sulawesi Tenggara", -3.9450, 122.4989),
        MarkazNasional("sulut", "Manado", "Sulawesi Utara", 1.4748, 124.8421),
        MarkazNasional("malut", "Sofifi", "Maluku Utara", 0.7397, 127.5665),
        MarkazNasional("maluku", "Ambon", "Maluku", -3.6954, 128.1814),
        MarkazNasional("pabar", "Manokwari", "Papua Barat", -0.8615, 134.0620),
        MarkazNasional("pbd", "Sorong", "Papua Barat Daya", -0.8833, 131.2500),
        MarkazNasional("pteng", "Nabire", "Papua Tengah", -3.3667, 135.4833),
        MarkazNasional("ppeg", "Wamena", "Papua Pegunungan", -4.0847, 138.9440),
        MarkazNasional("papua", "Jayapura", "Papua", -2.5337, 140.7181),
        MarkazNasional("psel", "Merauke", "Papua Selatan", -8.4667, 140.4000)
    ).sortedBy { it.longitude }

    val defaultMarkazIds: Set<String> = setOf(
        "aceh", "sumbar", "dki", "diy", "jatim", "ntb", "sulsel", "sulut", "maluku", "pabar", "papua"
    )

    /** Cari [MarkazNasional] dari id tersimpan (mis. hasil `PilihKotaActivity`/SessionManager).
     *  `null` id atau id yang tidak ditemukan (mis. daftar markaz berubah) -> null, caller jatuh
     *  ke alur lokasi GPS/manual lama. */
    fun resolveMarkaz(id: String?): MarkazNasional? = id?.let { mid -> allMarkaz.find { it.id == mid } }

    // heightMeters disamakan 0.0 (markaz permukaan laut) untuk semua titik — penyederhanaan
    // yang sama dipakai fitur Okultasi utk lokasi default.
    fun calculate(monthOffset: Int = 0, selectedIds: Set<String> = defaultMarkazIds): List<MarkazHisabResult> {
        val ids = selectedIds.ifEmpty { defaultMarkazIds }
        return allMarkaz
            .filter { it.id in ids }
            .map { markaz ->
                val hasil = EphemerisCalculator.calculate(
                    HilalInput(markaz.latitude, markaz.longitude, 0.0, monthOffset)
                )
                MarkazHisabResult(markaz, hasil)
            }
    }
}
