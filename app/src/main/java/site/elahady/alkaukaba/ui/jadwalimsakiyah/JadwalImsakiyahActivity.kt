package site.elahady.alkaukaba.ui.jadwalimsakiyah

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import site.elahady.alkaukaba.R
import site.elahady.alkaukaba.api.RetrofitClient
import site.elahady.alkaukaba.databinding.ActivityJadwalImsakiyahBinding
import site.elahady.alkaukaba.databinding.ItemImsakiyahDayCellBinding
import site.elahady.alkaukaba.databinding.ItemImsakiyahRowBinding
import site.elahady.alkaukaba.model.MarkazNasional
import site.elahady.alkaukaba.model.PrayerScheduleReportData
import site.elahady.alkaukaba.model.PrayerScheduleReportRow
import site.elahady.alkaukaba.repo.PrayerRepository
import site.elahady.alkaukaba.ui.laporanjadwal.LaporanJadwalActivity
import site.elahady.alkaukaba.ui.pilihkota.PilihKotaActivity
import site.elahady.alkaukaba.utils.HisabNasionalCalculator
import site.elahady.alkaukaba.utils.SessionManager
import site.elahady.alkaukaba.utils.applyStatusBarIconsForTheme
import site.elahady.alkaukaba.utils.applySystemBarInsetsPadding
import site.elahady.alkaukaba.utils.applyTopSystemBarInsetAsMargin
import site.elahady.alkaukaba.viewmodel.jadwalimsakiyah.JadwalImsakiyahViewModel
import site.elahady.alkaukaba.viewmodel.jadwalimsakiyah.JadwalImsakiyahViewModelFactory
import site.elahady.alkaukaba.viewmodel.shared.PrayerScheduleTableRow
import site.elahady.alkaukaba.viewmodel.shared.PrayerScheduleTableUiState
import java.util.Locale

// Tabel jadwal imsakiyah bulan Ramadhan: hari + semua waktu sholat yang ada di menu Waktu
// Sholat. Pola lokasi (GPS/manual/fallback/kota pilihan) & wiring ViewModel dicopy dari
// WaktuSholatActivity supaya konsisten dengan fitur sejenis di app ini.
class JadwalImsakiyahActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJadwalImsakiyahBinding
    private lateinit var viewModel: JadwalImsakiyahViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var lastLocationLabel: String = ""
    private var lastUiState: PrayerScheduleTableUiState? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false)
            ) {
                getLocation()
            } else {
                useDefaultLocation()
            }
        } else {
            useDefaultLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityJadwalImsakiyahBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        applyStatusBarIconsForTheme()
        binding.includeToolbar.toolbarDefault.applyTopSystemBarInsetAsMargin()
        binding.root.applySystemBarInsetsPadding(applyBottom = true)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sessionManager = SessionManager(this)

        setupUI()
        setupViewModel()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Re-check tiap kali resume (bukan cuma onCreate) supaya balik dari PilihKotaActivity
        // sehabis ganti kota langsung reload otomatis, sama seperti HisabNasionalActivity.
        checkLocationPermission()
    }

    private fun setupUI() {
        binding.includeToolbar.tvToolbarTitle.text = "Jadwal Imsakiyah"
        binding.includeToolbar.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.includeToolbar.btnToolbarAction.apply {
            visibility = View.VISIBLE
            setImageResource(R.drawable.ic_settings)
            setOnClickListener {
                startActivity(
                    Intent(this@JadwalImsakiyahActivity, PilihKotaActivity::class.java).apply {
                        putExtra(PilihKotaActivity.EXTRA_TARGET, PilihKotaActivity.TARGET_IMSAKIYAH)
                        putExtra(PilihKotaActivity.EXTRA_TITLE, "Pilih Kota - Jadwal Imsakiyah")
                    }
                )
            }
        }
        binding.btnCetakPdf.setOnClickListener { cetakPdf() }
    }

    private fun setupViewModel() {
        val apiService = RetrofitClient.instance
        val repository = PrayerRepository(apiService, applicationContext)
        val factory = JadwalImsakiyahViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[JadwalImsakiyahViewModel::class.java]
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            lastUiState = state
            renderTable(state)
        }
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.errorMessage.observe(this) { message ->
            if (message.isNullOrEmpty()) {
                binding.tvError.visibility = View.GONE
            } else {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = message
            }
        }
    }

    private fun renderTable(state: PrayerScheduleTableUiState) {
        binding.tvMonthLabel.text = state.monthLabel
        binding.layoutImsakiyahDayColumn.removeAllViews()
        binding.layoutImsakiyahTable.removeAllViews()

        binding.layoutImsakiyahDayColumn.addView(buildDayCell("Tgl", isHeader = true))
        binding.layoutImsakiyahTable.addView(buildHeaderRow(state.columnLabels))
        state.rows.forEachIndexed { index, row ->
            binding.layoutImsakiyahDayColumn.addView(
                buildDayCell("${row.dayLabel}\n${row.gregorianLabel}", isHeader = false, rowIndex = index)
            )
            binding.layoutImsakiyahTable.addView(buildDataRow(row, index))
        }
    }

    // Kolom tanggal (freeze) - di luar HorizontalScrollView, dibangun terpisah dari kolom waktu
    // supaya hanya kolom 2 s/d terakhir yang ikut geser horizontal.
    private fun buildDayCell(text: String, isHeader: Boolean, rowIndex: Int = 0): View {
        val cellBinding = ItemImsakiyahDayCellBinding.inflate(layoutInflater, binding.layoutImsakiyahDayColumn, false)
        cellBinding.root.text = text
        if (isHeader) {
            cellBinding.root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_amber_light))
            cellBinding.root.setTextColor(ContextCompat.getColor(this, R.color.icon_amber))
            cellBinding.root.setTypeface(null, Typeface.BOLD)
        } else {
            val colorEven = ContextCompat.getColor(this, R.color.white)
            val colorOdd = ContextCompat.getColor(this, R.color.card_gold_tint)
            cellBinding.root.setBackgroundColor(if (rowIndex % 2 == 0) colorEven else colorOdd)
        }
        return cellBinding.root
    }

    private fun buildHeaderRow(columnLabels: List<String>): View {
        val rowBinding = ItemImsakiyahRowBinding.inflate(layoutInflater, binding.layoutImsakiyahTable, false)
        val colorAmberBg = ContextCompat.getColor(this, R.color.bg_amber_light)
        val colorAmberText = ContextCompat.getColor(this, R.color.icon_amber)

        rowBinding.rowRoot.setBackgroundColor(colorAmberBg)
        val timeViews = listOf(
            rowBinding.tvTime0, rowBinding.tvTime1, rowBinding.tvTime2, rowBinding.tvTime3,
            rowBinding.tvTime4, rowBinding.tvTime5, rowBinding.tvTime6, rowBinding.tvTime7
        )
        timeViews.forEachIndexed { index, tv -> tv.text = columnLabels[index] }

        timeViews.forEach { tv ->
            tv.setTextColor(colorAmberText)
            tv.setTypeface(null, Typeface.BOLD)
        }
        return rowBinding.root
    }

    private fun buildDataRow(row: PrayerScheduleTableRow, index: Int): View {
        val rowBinding = ItemImsakiyahRowBinding.inflate(layoutInflater, binding.layoutImsakiyahTable, false)
        val colorEven = ContextCompat.getColor(this, R.color.white)
        val colorOdd = ContextCompat.getColor(this, R.color.card_gold_tint)

        rowBinding.rowRoot.setBackgroundColor(if (index % 2 == 0) colorEven else colorOdd)
        val timeViews = listOf(
            rowBinding.tvTime0, rowBinding.tvTime1, rowBinding.tvTime2, rowBinding.tvTime3,
            rowBinding.tvTime4, rowBinding.tvTime5, rowBinding.tvTime6, rowBinding.tvTime7
        )
        timeViews.forEachIndexed { i, tv -> tv.text = row.times.getOrElse(i) { "-" } }
        return rowBinding.root
    }

    private fun checkLocationPermission() {
        val markaz = HisabNasionalCalculator.resolveMarkaz(sessionManager.getSelectedMarkazImsakiyahId())
        if (markaz != null) {
            useMarkazLocation(markaz)
            return
        }

        if (sessionManager.isManualLocationMode()) {
            useManualLocation(sessionManager.getManualLat(), sessionManager.getManualLng())
            return
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionRequest.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            getLocation()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val lat = location?.latitude ?: -6.2088
            val long = location?.longitude ?: 106.8456
            setLocation(lat, long, "Lokasi GPS Anda (Lat %.4f, Lng %.4f)".format(Locale.US, lat, long))
        }
    }

    private fun useDefaultLocation() {
        Toast.makeText(this, "Izin lokasi ditolak, menggunakan default Jakarta", Toast.LENGTH_SHORT).show()
        setLocation(-6.2088, 106.8456, "Lokasi GPS Anda (Lat -6.2088, Lng 106.8456)")
    }

    private fun useManualLocation(lat: Double, lon: Double) {
        setLocation(lat, lon, "Lokasi Manual (Lat %.4f, Lng %.4f)".format(Locale.US, lat, lon))
    }

    private fun useMarkazLocation(markaz: MarkazNasional) {
        setLocation(
            markaz.latitude, markaz.longitude,
            "Markaz: %s, %s (Lat %.4f, Lng %.4f)".format(
                Locale.US, markaz.nama, markaz.provinsi, markaz.latitude, markaz.longitude
            )
        )
    }

    @SuppressLint("SetTextI18n")
    private fun setLocation(lat: Double, lng: Double, label: String) {
        lastLat = lat
        lastLng = lng
        lastLocationLabel = label
        binding.tvLocationInfo.text = label
        viewModel.loadRamadhan(lat, lng)
    }

    private fun cetakPdf() {
        val state = lastUiState
        if (state == null) {
            Toast.makeText(this, "Jadwal belum siap, tunggu sebentar", Toast.LENGTH_SHORT).show()
            return
        }
        val reportData = PrayerScheduleReportData(
            reportTitle = "JADWAL IMSAKIYAH",
            monthLabel = state.monthLabel,
            locationLabel = lastLocationLabel,
            columnLabels = state.columnLabels,
            rows = state.rows.map { row ->
                PrayerScheduleReportRow("${row.dayLabel}\n${row.gregorianLabel}", row.times)
            },
            fileNamePrefix = "Jadwal_Imsakiyah"
        )
        startActivity(
            Intent(this, LaporanJadwalActivity::class.java)
                .putExtra(LaporanJadwalActivity.EXTRA_REPORT, reportData)
        )
    }
}
