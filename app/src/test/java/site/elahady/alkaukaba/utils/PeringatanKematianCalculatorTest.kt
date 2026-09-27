package site.elahady.alkaukaba.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Pure logic test untuk [PeringatanKematianCalculator] (tanpa Android/API/DB) - mengecek
 * hitungan hari ke-7/40/100/1000 untuk dua konvensi (hari wafat = hari ke-1 vs ke-0), lintas
 * bulan/tahun/kabisat, dan selisih hari kalender.
 */
class PeringatanKematianCalculatorTest {

    private fun tanggal(y: Int, m: Int, d: Int, jam: Int = 0, menit: Int = 0) =
        GregorianCalendar(y, m - 1, d, jam, menit)

    private fun Calendar.label() =
        "${get(Calendar.YEAR)}-${get(Calendar.MONTH) + 1}-${get(Calendar.DAY_OF_MONTH)}"

    private fun hitungLabel(wafat: Calendar, hariWafatDihitungKe1: Boolean = true) =
        PeringatanKematianCalculator.hitung(wafat, hariWafatDihitungKe1).associate { it.hariKe to it.tanggal.label() }

    @Test
    fun `daftar peringatan 7, 40, 100, 1000 hari berurutan`() {
        val hasil = PeringatanKematianCalculator.hitung(tanggal(2026, 1, 1))
        assertEquals(listOf(7, 40, 100, 1000), hasil.map { it.hariKe })
    }

    @Test
    fun `hari wafat dihitung hari ke-1 - 7 hari jatuh pada wafat tambah 6 hari`() {
        // Senin 5 Januari 2026 -> hari ke-7 = Ahad 11 Januari 2026
        val hasil = hitungLabel(tanggal(2026, 1, 5))
        assertEquals("2026-1-11", hasil[7])
        // hari ke-40 = wafat + 39 hari = 13 Februari 2026
        assertEquals("2026-2-13", hasil[40])
        // hari ke-100 = wafat + 99 hari = 14 April 2026
        assertEquals("2026-4-14", hasil[100])
        // hari ke-1000 = wafat + 999 hari = 30 September 2028 (2028 kabisat)
        assertEquals("2028-9-30", hasil[1000])
    }

    @Test
    fun `hari wafat dihitung hari ke-0 - 7 hari jatuh pada wafat tambah 7 hari`() {
        val hasil = hitungLabel(tanggal(2026, 1, 5), hariWafatDihitungKe1 = false)
        assertEquals("2026-1-12", hasil[7])
        assertEquals("2026-2-14", hasil[40])
        assertEquals("2026-4-15", hasil[100])
        assertEquals("2028-10-1", hasil[1000])
    }

    @Test
    fun `melewati akhir tahun dan tahun kabisat`() {
        // wafat 28 Februari 2028 (kabisat), hari ke-7 = wafat + 6 = 5 Maret 2028 (ada 29 Feb)
        assertEquals("2028-3-5", hitungLabel(tanggal(2028, 2, 28))[7])
        // wafat 30 Desember 2026, hari ke-7 = 5 Januari 2027
        assertEquals("2027-1-5", hitungLabel(tanggal(2026, 12, 30))[7])
    }

    @Test
    fun `jam pada tanggal wafat diabaikan dan tanggal input tidak berubah`() {
        val wafat = tanggal(2026, 1, 5, jam = 23, menit = 59)
        val hasil = PeringatanKematianCalculator.hitung(wafat)
        assertEquals("2026-1-11", hasil[0].tanggal.label())
        assertEquals(0, hasil[0].tanggal.get(Calendar.HOUR_OF_DAY))
        assertEquals("2026-1-5", wafat.label())
        assertEquals(23, wafat.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `selisih hari - masa depan, hari ini, dan masa lalu`() {
        val hariIni = tanggal(2026, 9, 19, jam = 15)
        assertEquals(3L, PeringatanKematianCalculator.selisihHari(hariIni, tanggal(2026, 9, 22)))
        assertEquals(0L, PeringatanKematianCalculator.selisihHari(hariIni, tanggal(2026, 9, 19, jam = 1)))
        assertEquals(-5L, PeringatanKematianCalculator.selisihHari(hariIni, tanggal(2026, 9, 14)))
    }

    @Test
    fun `selisih hari lintas tahun kabisat`() {
        assertEquals(366L, PeringatanKematianCalculator.selisihHari(tanggal(2028, 1, 1), tanggal(2029, 1, 1)))
    }

    // --- Haul (tahunan, mengikuti tahun Hijriyah) ---

    private fun hijriDari(c: Calendar) =
        HijriDateUtil.gregorianToHijri(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    @Test
    fun `wafat baru - lima haul mendatang mulai dari haul ke-1 berurutan`() {
        val wafat = tanggal(2026, 9, 19)
        val hasil = PeringatanKematianCalculator.hitungHaulMendatang(wafat, hariIni = wafat)
        assertEquals(listOf(1, 2, 3, 4, 5), hasil.map { it.haulKe })
    }

    @Test
    fun `haul jatuh pada tanggal dan bulan Hijriyah wafat dengan tahun bertambah`() {
        val wafat = tanggal(2026, 9, 19)
        val asal = hijriDari(wafat)
        PeringatanKematianCalculator.hitungHaulMendatang(wafat, hariIni = wafat).forEach { haul ->
            assertEquals(HijriDateUtil.DateParts(asal.year + haul.haulKe, asal.month, asal.day), haul.tanggalHijriyah)
            // tanggal Masehinya memang jatuh di tanggal Hijriyah itu
            assertEquals(haul.tanggalHijriyah, hijriDari(haul.tanggal))
        }
    }

    @Test
    fun `tanggal Masehi haul maju sekitar 354 atau 355 hari per tahun`() {
        val wafat = tanggal(2026, 9, 19)
        val hasil = PeringatanKematianCalculator.hitungHaulMendatang(wafat, hariIni = wafat)
        hasil.zipWithNext().forEach { (a, b) ->
            val selisih = PeringatanKematianCalculator.selisihHari(a.tanggal, b.tanggal)
            assertTrue("selisih $selisih", selisih == 354L || selisih == 355L)
        }
    }

    @Test
    fun `wafat sudah lama - haul yang sudah lewat dilewati`() {
        val wafat = tanggal(1990, 3, 14)
        val hariIni = tanggal(2026, 9, 19)
        val hasil = PeringatanKematianCalculator.hitungHaulMendatang(wafat, hariIni)
        assertEquals(PeringatanKematianCalculator.JUMLAH_HAUL_MENDATANG, hasil.size)
        assertTrue(hasil.all { PeringatanKematianCalculator.selisihHari(hariIni, it.tanggal) >= 0 })
        // haul ke-N berurutan tanpa lompat, dan haul tepat sebelum yang pertama sudah lewat
        assertEquals(hasil.first().haulKe + 4, hasil.last().haulKe)
        assertTrue(hasil.first().haulKe > 30)
    }

    @Test
    fun `haul yang jatuh tepat hari ini ikut dihitung, besoknya tidak`() {
        val wafat = tanggal(2026, 9, 19)
        val haul1 = PeringatanKematianCalculator.hitungHaulMendatang(wafat, hariIni = wafat).first().tanggal

        val tepatHariIni = PeringatanKematianCalculator.hitungHaulMendatang(wafat, hariIni = haul1)
        assertEquals(1, tepatHariIni.first().haulKe)

        val besoknya = (haul1.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        assertEquals(2, PeringatanKematianCalculator.hitungHaulMendatang(wafat, hariIni = besoknya).first().haulKe)
    }

    @Test
    fun `tanggal 30 Dzulhijjah kabisat jadi 29 di tahun tujuan yang tidak kabisat`() {
        // 1445 kabisat (Dzulhijjah 30 hari), 1446 bukan (Dzulhijjah 29 hari).
        assertTrue(HijriDateUtil.isHijriLeapYear(1445))
        assertTrue(!HijriDateUtil.isHijriLeapYear(1446))
        val m = HijriDateUtil.hijriToGregorian(1445, 12, 30)
        val wafat = tanggal(m.year, m.month, m.day)
        assertEquals(HijriDateUtil.DateParts(1445, 12, 30), hijriDari(wafat))

        val haul1 = PeringatanKematianCalculator.hitungHaulMendatang(wafat, hariIni = wafat, jumlah = 1).single()
        assertEquals(HijriDateUtil.DateParts(1446, 12, 29), haul1.tanggalHijriyah)
        assertEquals(haul1.tanggalHijriyah, hijriDari(haul1.tanggal))
    }

    @Test
    fun `jam pada tanggal wafat dan hari ini diabaikan untuk haul`() {
        val pagi = PeringatanKematianCalculator.hitungHaulMendatang(tanggal(2026, 9, 19, jam = 1), tanggal(2026, 9, 19, jam = 1))
        val malam = PeringatanKematianCalculator.hitungHaulMendatang(tanggal(2026, 9, 19, jam = 23), tanggal(2026, 9, 19, jam = 23))
        assertEquals(pagi.map { it.tanggal.label() }, malam.map { it.tanggal.label() })
        assertEquals(0, pagi.first().tanggal.get(Calendar.HOUR_OF_DAY))
    }
}
