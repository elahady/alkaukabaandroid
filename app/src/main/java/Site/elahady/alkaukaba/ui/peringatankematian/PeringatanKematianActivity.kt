package site.elahady.alkaukaba.ui.peringatankematian

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import site.elahady.alkaukaba.R
import site.elahady.alkaukaba.databinding.ActivityPeringatanKematianBinding
import site.elahady.alkaukaba.databinding.ItemPeringatanKematianBinding
import site.elahady.alkaukaba.utils.HijriDateUtil
import site.elahady.alkaukaba.utils.JavaneseCalendarUtil
import site.elahady.alkaukaba.utils.PeringatanKematianCalculator
import site.elahady.alkaukaba.utils.applyStatusBarIconsForTheme
import site.elahady.alkaukaba.utils.applySystemBarInsetsPadding
import site.elahady.alkaukaba.utils.applyTopSystemBarInsetAsMargin
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Kalkulator peringatan 7, 40, 100, dan 1.000 hari wafat. User memilih tanggal wafat (default
 * hari ini, tidak boleh di masa depan), hasil dihitung ulang otomatis setiap tanggal atau
 * pilihan konvensi hitung berubah. Logika tanggal ada di [PeringatanKematianCalculator]
 * (lihat docs/features/peringatan-kematian.md untuk konvensi hitungnya). */
class PeringatanKematianActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPeringatanKematianBinding

    private val localeId = Locale("id", "ID")
    private val formatHari = SimpleDateFormat("EEEE", localeId)
    private val formatTanggal = SimpleDateFormat("d MMMM yyyy", localeId)
    private val formatRibuan = NumberFormat.getIntegerInstance(localeId)

    private var tanggalWafat: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPeringatanKematianBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        applyStatusBarIconsForTheme()
        binding.includeToolbar.toolbarDefault.applyTopSystemBarInsetAsMargin()
        binding.root.applySystemBarInsetsPadding(applyBottom = true)

        binding.includeToolbar.tvToolbarTitle.text = "Peringatan Hari Wafat"
        binding.includeToolbar.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.tvTanggalWafat.setOnClickListener { tampilkanDatePicker() }
        binding.cbHariWafatKe1.setOnCheckedChangeListener { _, _ -> tampilkanHasil() }

        tampilkanHasil()
    }

    private fun tampilkanDatePicker() {
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                tanggalWafat = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                tampilkanHasil()
            },
            tanggalWafat.get(Calendar.YEAR),
            tanggalWafat.get(Calendar.MONTH),
            tanggalWafat.get(Calendar.DAY_OF_MONTH)
        )
        // Wafat tidak mungkin di masa depan.
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun tampilkanHasil() {
        binding.tvTanggalWafat.text = labelTanggal(tanggalWafat)

        val hariIni = Calendar.getInstance()
        val daftar = PeringatanKematianCalculator.hitung(tanggalWafat, binding.cbHariWafatKe1.isChecked)

        binding.containerPeringatan.removeAllViews()
        daftar.forEachIndexed { index, peringatan ->
            val row = ItemPeringatanKematianBinding.inflate(layoutInflater, binding.containerPeringatan, false)
            row.tvHariKe.text = "Hari ke-${formatRibuan.format(peringatan.hariKe)}"
            row.tvTanggal.text = labelTanggal(peringatan.tanggal)
            bindStatus(row, PeringatanKematianCalculator.selisihHari(hariIni, peringatan.tanggal))
            row.divider.visibility = if (index == daftar.lastIndex) View.GONE else View.VISIBLE
            binding.containerPeringatan.addView(row.root)
        }
        binding.cardHasil.visibility = View.VISIBLE

        tampilkanHaul(hariIni)
    }

    private fun tampilkanHaul(hariIni: Calendar) {
        val daftar = PeringatanKematianCalculator.hitungHaulMendatang(tanggalWafat, hariIni)
        val asal = HijriDateUtil.gregorianToHijri(
            tanggalWafat.get(Calendar.YEAR),
            tanggalWafat.get(Calendar.MONTH) + 1,
            tanggalWafat.get(Calendar.DAY_OF_MONTH)
        )
        binding.tvHaulInfo.text = "Wafat ${HijriDateUtil.hijriLabel(asal)}, jadi haul jatuh tiap " +
            "${asal.day} ${HijriDateUtil.monthNames[asal.month - 1]} menurut kalender Hijriyah. " +
            "${daftar.size} haul berikutnya:"

        binding.containerHaul.removeAllViews()
        daftar.forEachIndexed { index, haul ->
            val row = ItemPeringatanKematianBinding.inflate(layoutInflater, binding.containerHaul, false)
            row.tvHariKe.text = "Haul ke-${formatRibuan.format(haul.haulKe)}"
            row.tvTanggal.text = "${HijriDateUtil.hijriLabel(haul.tanggalHijriyah)}\n${labelTanggal(haul.tanggal)}"
            bindStatus(row, PeringatanKematianCalculator.selisihHari(hariIni, haul.tanggal))
            row.divider.visibility = if (index == daftar.lastIndex) View.GONE else View.VISIBLE
            binding.containerHaul.addView(row.root)
        }
        binding.cardHaul.visibility = View.VISIBLE
    }

    private fun bindStatus(row: ItemPeringatanKematianBinding, selisihHari: Long) {
        val (teks, warna) = when {
            selisihHari > 0 -> "$selisihHari hari lagi" to R.color.icon_emerald
            selisihHari == 0L -> "Hari ini" to R.color.icon_emerald
            else -> "Sudah lewat ${-selisihHari} hari" to R.color.text_secondary
        }
        row.tvStatus.text = teks
        row.tvStatus.setTextColor(ContextCompat.getColor(this, warna))
    }

    /** "Minggu Pahing, 11 Januari 2026" - hari + pasaran Jawa, lalu tanggal Masehi. */
    private fun labelTanggal(tanggal: Calendar): String {
        val hari = formatHari.format(tanggal.time)
        val pasaran = JavaneseCalendarUtil.pasaranFor(tanggal)
        return "$hari $pasaran, ${formatTanggal.format(tanggal.time)}"
    }
}
