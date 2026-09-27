package site.elahady.alkaukaba.ui.laporanjadwal

import site.elahady.alkaukaba.R
import site.elahady.alkaukaba.databinding.ActivityLaporanJadwalBinding
import site.elahady.alkaukaba.databinding.ItemLaporanJadwalRowBinding
import site.elahady.alkaukaba.model.PrayerScheduleReportData
import site.elahady.alkaukaba.utils.HilalPdfService
import site.elahady.alkaukaba.utils.applySystemBarInsetsPadding
import site.elahady.alkaukaba.utils.applyTopSystemBarInsetAsMargin
import site.elahady.alkaukaba.utils.applyStatusBarIconsForTheme
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

/**
 * Halaman "view laporan + unduh PDF" untuk jadwal berkolom-tanggal+8-waktu, dipakai bareng oleh
 * Jadwal Imsakiyah (bulan Ramadhan) dan jadwal bulanan Waktu Sholat (bulan Masehi pilihan user) -
 * strukturnya identik, lihat [PrayerScheduleReportData]. Pola sama persis dengan
 * `LaporanHisabActivity` di fitur Awal Bulan Hijriyah (render -> capture View -> PDF lewat
 * [HilalPdfService]), cuma tabelnya lebar (9 kolom) alih-alih 3 kolom No/Label/Value.
 */
class LaporanJadwalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLaporanJadwalBinding
    private lateinit var result: PrayerScheduleReportData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLaporanJadwalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        applyStatusBarIconsForTheme()
        binding.includeToolbar.toolbarDefault.applyTopSystemBarInsetAsMargin()
        binding.root.applySystemBarInsetsPadding(applyBottom = true)

        @Suppress("DEPRECATION")
        val extraResult = intent.getSerializableExtra(EXTRA_REPORT) as? PrayerScheduleReportData
        if (extraResult == null) {
            finish()
            return
        }
        result = extraResult

        binding.includeToolbar.tvToolbarTitle.text = "Laporan Jadwal"
        binding.includeToolbar.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        renderReport(result)

        binding.btnDownloadPdf.setOnClickListener { downloadPdf() }
    }

    // Di Android 9 (API 28) ke bawah, tulis file ke folder Download publik masih butuh
    // izin WRITE_EXTERNAL_STORAGE eksplisit; API 29+ pakai MediaStore jadi tidak perlu.
    private fun downloadPdf() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_REQUEST_CODE)
            return
        }
        savePdf()
    }

    private fun savePdf() {
        val fileName = "${result.fileNamePrefix}_${System.currentTimeMillis()}.pdf"
        val savedUri = HilalPdfService.exportViewAsPdf(this, binding.reportContent, fileName)
        if (savedUri != null) {
            Toast.makeText(this, "PDF disimpan di folder Download: $fileName", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Gagal menyimpan PDF", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                savePdf()
            } else {
                Toast.makeText(this, "Izin penyimpanan ditolak, PDF tidak bisa disimpan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderReport(result: PrayerScheduleReportData) {
        binding.tvReportTitle.text = result.reportTitle
        binding.tvReportSubtitle.text = "${result.monthLabel}\n${result.locationLabel}"

        binding.layoutReportHeaderRow.removeAllViews()
        binding.layoutReportHeaderRow.addView(buildRow(dateLabel = "Tgl", times = result.columnLabels, isHeader = true))

        binding.layoutReportRows.removeAllViews()
        result.rows.forEachIndexed { index, row ->
            binding.layoutReportRows.addView(buildRow(row.dateLabel, row.times, isHeader = false, rowIndex = index))
        }
    }

    private fun buildRow(dateLabel: String, times: List<String>, isHeader: Boolean, rowIndex: Int = 0): android.view.View {
        val rowBinding = ItemLaporanJadwalRowBinding.inflate(layoutInflater, binding.layoutReportRows, false)
        rowBinding.tvDate.text = dateLabel
        val timeViews = listOf(
            rowBinding.tvTime0, rowBinding.tvTime1, rowBinding.tvTime2, rowBinding.tvTime3,
            rowBinding.tvTime4, rowBinding.tvTime5, rowBinding.tvTime6, rowBinding.tvTime7
        )
        timeViews.forEachIndexed { i, tv -> tv.text = times.getOrElse(i) { "-" } }

        if (isHeader) {
            val colorDeep = ContextCompat.getColor(this, R.color.login_bg_deep)
            rowBinding.tvDate.setTextColor(colorDeep)
            rowBinding.tvDate.setTypeface(null, Typeface.BOLD)
            timeViews.forEach {
                it.setTextColor(colorDeep)
                it.setTypeface(null, Typeface.BOLD)
            }
        } else {
            rowBinding.rowRoot.setBackgroundColor(
                if (rowIndex % 2 == 0) Color.TRANSPARENT else Color.parseColor("#1AFFFFFF")
            )
        }
        return rowBinding.root
    }

    companion object {
        const val EXTRA_REPORT = "extra_prayer_schedule_report"
        private const val STORAGE_PERMISSION_REQUEST_CODE = 200
    }
}
