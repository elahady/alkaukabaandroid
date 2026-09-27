package site.elahady.alkaukaba.ui.waktusholat

import site.elahady.alkaukaba.repo.PrayerRepository
import site.elahady.alkaukaba.R
import site.elahady.alkaukaba.api.RetrofitClient
import site.elahady.alkaukaba.databinding.ActivityWaktuSholatBinding
import site.elahady.alkaukaba.viewmodel.waktusholat.PrayerKind
import site.elahady.alkaukaba.viewmodel.waktusholat.PrayerScheduleUiState
import site.elahady.alkaukaba.viewmodel.waktusholat.PrayerTimesViewModel
import site.elahady.alkaukaba.viewmodel.waktusholat.PrayerViewModelFactory
import site.elahady.alkaukaba.databinding.ItemPrayerBreakdownBinding
import site.elahady.alkaukaba.utils.prayerbreakdown.PrayerBreakdownSection
import site.elahady.alkaukaba.utils.SessionManager
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import site.elahady.alkaukaba.databinding.DialogPilihBulanBinding
import site.elahady.alkaukaba.model.MarkazNasional
import site.elahady.alkaukaba.model.PrayerScheduleReportData
import site.elahady.alkaukaba.model.PrayerScheduleReportRow
import site.elahady.alkaukaba.ui.laporanjadwal.LaporanJadwalActivity
import site.elahady.alkaukaba.ui.pilihkota.PilihKotaActivity
import site.elahady.alkaukaba.utils.HisabNasionalCalculator
import site.elahady.alkaukaba.utils.HijriDateUtil
import site.elahady.alkaukaba.utils.applySystemBarInsetsPadding
import site.elahady.alkaukaba.utils.applyTopSystemBarInsetAsMargin
import site.elahady.alkaukaba.utils.applyStatusBarIconsForTheme
import site.elahady.alkaukaba.viewmodel.waktusholat.PrayerMonthlyScheduleViewModel
import site.elahady.alkaukaba.viewmodel.waktusholat.PrayerMonthlyScheduleViewModelFactory
import site.elahady.alkaukaba.viewmodel.shared.PrayerScheduleTableUiState
import androidx.lifecycle.Observer
import java.util.Calendar

class WaktuSholatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWaktuSholatBinding
    private lateinit var viewModel: PrayerTimesViewModel
    private lateinit var sessionManager: SessionManager

    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var lastLocationLabel: String = ""

    private lateinit var fusedLocationClient: FusedLocationProviderClient
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
                // Fallback simpel untuk Android < N
                useDefaultLocation()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup ViewBinding
        binding = ActivityWaktuSholatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        applyStatusBarIconsForTheme()
        binding.includeToolbar.toolbarDefault.applyTopSystemBarInsetAsMargin()
        binding.scrollContent.applySystemBarInsetsPadding(applyBottom = true)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sessionManager = SessionManager(this)

        setupUI()
        setupViewModel()
        observeViewModel()
        updateDateDisplay()
    }

    override fun onResume() {
        super.onResume()
        // Re-check tiap kali resume (bukan cuma onCreate) supaya balik dari PilihKotaActivity
        // sehabis ganti kota langsung reload otomatis, sama seperti HisabNasionalActivity.
        checkLocationPermission()
    }

    private fun setupViewModel() {
        val apiService = RetrofitClient.instance
        val repository = PrayerRepository(apiService, applicationContext)
        val factory = PrayerViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[PrayerTimesViewModel::class.java]
    }

    private fun setupUI() {
        // Set kondisi awal: Tab 'Waktu Aktual' aktif
        updateTabState(isActual = true)

        // Listener Klik Tab Kiri
        binding.btnTabActual.setOnClickListener {
            updateTabState(isActual = true)
        }

        // Listener Klik Tab Kanan
        binding.btnTabDetail.setOnClickListener {
            updateTabState(isActual = false)
        }
        binding.includeToolbar.tvToolbarTitle.text = "Waktu Sholat"
        binding.includeToolbar.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.includeToolbar.btnToolbarAction.apply {
            visibility = View.VISIBLE
            setImageResource(R.drawable.ic_settings)
            setOnClickListener {
                startActivity(
                    Intent(this@WaktuSholatActivity, PilihKotaActivity::class.java).apply {
                        putExtra(PilihKotaActivity.EXTRA_TARGET, PilihKotaActivity.TARGET_WAKTU_SHOLAT)
                        putExtra(PilihKotaActivity.EXTRA_TITLE, "Pilih Kota - Waktu Sholat")
                    }
                )
            }
        }
        binding.btnCetakJadwalBulanan.setOnClickListener { showPilihBulanDialog() }
    }

    private fun updateTabState(isActual: Boolean) {
        val colorActive = ContextCompat.getColor(this, R.color.text_label_gold)
        val colorInactive = ContextCompat.getColor(this, R.color.waktu_sholat_icon_muted)

        val activeTab = if (isActual) binding.btnTabActual else binding.btnTabDetail
        val inactiveTab = if (isActual) binding.btnTabDetail else binding.btnTabActual

        activeTab.setBackgroundResource(R.drawable.bg_tab_underline_active)
        activeTab.setTextColor(colorActive)
        activeTab.setTypeface(null, android.graphics.Typeface.BOLD)

        inactiveTab.setBackgroundResource(R.drawable.bg_tab_underline_inactive)
        inactiveTab.setTextColor(colorInactive)
        inactiveTab.setTypeface(null, android.graphics.Typeface.NORMAL)

        binding.layoutWaktuSholat.visibility = if (isActual) View.VISIBLE else View.GONE
        binding.layoutDetailPerhitungan.visibility = if (isActual) View.GONE else View.VISIBLE
    }

    // 1. Menampilkan Tanggal Masehi & Hijriyah
    @SuppressLint("NewApi") // HijrahDate butuh min API 26 (Android 8.0)
    private fun updateDateDisplay() {
        // A. Tanggal Masehi (Gregorian)
        val masehiFormat = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("id", "ID"))
        val dateNow = java.util.Date()
        val masehiString = masehiFormat.format(dateNow)

        // B. Tanggal Hijriyah
        // Opsi 1: Menggunakan java.time.chrono.HijrahDate (Android 8.0+)
        val hijriString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val hijrahDate = java.time.chrono.HijrahDate.now()
                val hijriFormatter = java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy", java.util.Locale("id", "ID"))
                "${hijrahDate.format(hijriFormatter)}H"
            } catch (e: Exception) {
                "Hijriyah Unavail" // Fallback jika device tidak support
            }
        } else {
            // Untuk Android di bawah 8.0, idealnya ambil dari response API Aladhan (meta.date)
            ""
        }

        // Set ke TextView: tanggal Hijriyah ditebalkan & lebih terang, dipisah bullet dari tanggal Masehi
        // (Format: 11 Rajab 1446H  •  11 Januari 2025)
        binding.tvDate.text = if (hijriString.isNotEmpty()) {
            SpannableStringBuilder().apply {
                append(hijriString)
                setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(ContextCompat.getColor(this@WaktuSholatActivity, android.R.color.white)), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                append("  •  ")
                append(masehiString)
            }
        } else {
            masehiString
        }
    }

    // 2. Bind 8 baris waktu (Tsulutsul Lail Akhir s/d Isya) & tandai periode yang sedang aktif.
    // "Sholat mana yang aktif" sudah dihitung di PrayerTimesViewModel — di sini murni binding ke View.
    @SuppressLint("SetTextI18n")
    private fun updateNextPrayerUI(state: PrayerScheduleUiState) {
        val rows = listOf(
            binding.rowTsulutsulLail, binding.rowImsak, binding.rowSubuh, binding.rowDhuha,
            binding.rowDzuhur, binding.rowAshar, binding.rowMaghrib, binding.rowIsya
        )

        val colorActiveBg = ContextCompat.getColor(this, R.color.waktu_sholat_row_active_bg)
        val colorActiveIconBg = ContextCompat.getColor(this, R.color.gold_accent)
        val colorActiveText = ContextCompat.getColor(this, R.color.text_label_gold)
        val colorIconInactiveBg = ContextCompat.getColor(this, R.color.waktu_sholat_icon_bg_inactive)
        val colorIconMuted = ContextCompat.getColor(this, R.color.waktu_sholat_icon_muted)
        val colorWhite = ContextCompat.getColor(this, android.R.color.white)
        val colorTransparent = ContextCompat.getColor(this, R.color.transparent)
        val colorNameInactive = ContextCompat.getColor(this, R.color.waktu_sholat_name_inactive)

        state.items.forEachIndexed { index, entry ->
            val row = rows[index]
            row.tvPrayerName.text = entry.label
            row.tvTime.text = entry.time
            row.ivIcon.setImageResource(iconFor(entry.kind))

            val isActive = index == state.activeIndex
            row.rowRoot.backgroundTintList = android.content.res.ColorStateList.valueOf(if (isActive) colorActiveBg else colorTransparent)
            row.iconContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(if (isActive) colorActiveIconBg else colorIconInactiveBg)
            row.ivIcon.imageTintList = android.content.res.ColorStateList.valueOf(if (isActive) colorWhite else colorIconMuted)
            row.tvPrayerName.setTypeface(null, if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            row.tvTime.setTypeface(null, if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            row.tvPrayerName.setTextColor(if (isActive) colorActiveText else colorNameInactive)
        }

        binding.tvNextPrayer.text = state.nextPrayerLabel
        binding.tvNextPrayerTime.text = "${state.nextPrayerTime} WIB"
    }

    private fun iconFor(kind: PrayerKind): Int = when (kind) {
        PrayerKind.TSULUTSUL_LAIL -> R.drawable.ic_prayer_tsulutsul_lail
        PrayerKind.IMSAK -> R.drawable.ic_prayer_imsak
        PrayerKind.SUBUH -> R.drawable.ic_prayer_subuh
        PrayerKind.DHUHA -> R.drawable.ic_prayer_dhuha
        PrayerKind.DZUHUR -> R.drawable.ic_prayer_dzuhur
        PrayerKind.ASHAR -> R.drawable.ic_prayer_ashar
        PrayerKind.MAGHRIB -> R.drawable.ic_prayer_maghrib
        PrayerKind.ISYA -> R.drawable.ic_prayer_isya
    }

    private fun observeViewModel() {
        // 1. Observe Jadwal Sholat
        viewModel.prayerSchedule.observe(this) { state ->
            state?.let { updateNextPrayerUI(it) }
        }

        // 2. Observe breakdown perhitungan waktu sholat
        viewModel.calculationBreakdown.observe(this) { sections ->
            renderPrayerBreakdown(sections)
        }

        // 4. Observe Loading/Error
        viewModel.errorMessage.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }


    }

    // Accordion "Detail Perhitungan" - cuma tampil kalau metode aktif punya breakdown
    // (lihat PrayerCalculationBreakdownRegistry). Kalau tidak, tampilkan pesan fallback.
    private fun renderPrayerBreakdown(sections: List<PrayerBreakdownSection>?) {
        binding.layoutPrayerBreakdownContainer.removeAllViews()

        if (sections.isNullOrEmpty()) {
            binding.layoutPrayerBreakdownContainer.visibility = View.GONE
            binding.tvNoPrayerBreakdown.visibility = View.VISIBLE
            return
        }

        binding.layoutPrayerBreakdownContainer.visibility = View.VISIBLE
        binding.tvNoPrayerBreakdown.visibility = View.GONE

        sections.forEach { section ->
            val itemBinding = ItemPrayerBreakdownBinding.inflate(
                layoutInflater, binding.layoutPrayerBreakdownContainer, false
            )
            itemBinding.tvPrayerLabel.text = section.prayerLabel
            itemBinding.tvResultTime.text = "${section.resultTime} WIB"

            section.rows.forEach { row ->
                val rowView = layoutInflater.inflate(R.layout.item_breakdown_row, itemBinding.layoutBody, false)
                rowView.findViewById<TextView>(R.id.tvRowLabel).text = row.label
                rowView.findViewById<TextView>(R.id.tvRowValue).text = row.value
                itemBinding.layoutBody.addView(rowView)
            }

            itemBinding.rowHeader.setOnClickListener {
                val isExpanded = itemBinding.layoutBody.visibility == View.VISIBLE
                itemBinding.layoutBody.visibility = if (isExpanded) View.GONE else View.VISIBLE
                itemBinding.tvChevron.text = if (isExpanded) "⌄" else "⌃"
            }

            binding.layoutPrayerBreakdownContainer.addView(itemBinding.root)
        }
    }

    private fun checkLocationPermission() {
            val markaz = HisabNasionalCalculator.resolveMarkaz(sessionManager.getSelectedMarkazWaktuSholatId())
            if (markaz != null) {
                useMarkazLocation(markaz)
                return
            }

            if (sessionManager.isManualLocationMode()) {
                // Setting lokasi global (lihat KonfigurasiActivity) - lewati GPS/permission sama sekali.
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
                // Jika sudah diizinkan, langsung ambil lokasi
                getLocation()
            }
        }

    @SuppressLint("SetTextI18n")
    private fun getLocation() {
        binding.tvLocationName.text = "Sedang mencari lokasi..."

        if (ActivityCompat.checkSelfPermission(
                this,
                ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val lat = location?.latitude ?: -6.2088
            val long = location?.longitude ?: 106.8456

            updateLocationDisplay(lat, long)

            // PENTING: Panggil ViewModel untuk memproses data
            viewModel.loadData(lat, long)
        }
    }

    private fun useDefaultLocation() {
        Toast.makeText(this, "Izin lokasi ditolak, menggunakan default Jakarta", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("SetTextI18n")
    private fun useManualLocation(lat: Double, lon: Double) {
        updateLocationDisplay(lat, lon)
        viewModel.loadData(lat, lon)
    }

    // Markaz dari daftar HisabNasionalCalculator.allMarkaz (dipilih via PilihKotaActivity) -
    // nama kota+provinsi sudah pasti diketahui, jadi tidak perlu reverse-geocode seperti GPS.
    @SuppressLint("SetTextI18n")
    private fun useMarkazLocation(markaz: MarkazNasional) {
        lastLat = markaz.latitude
        lastLng = markaz.longitude
        lastLocationLabel = "${markaz.nama}, ${markaz.provinsi} (Lat %.4f, Lng %.4f)".format(
            java.util.Locale.US, markaz.latitude, markaz.longitude
        )
        binding.tvDetailCoordinates.text = "Koordinat: Lat ${markaz.latitude}, Long ${markaz.longitude}"
        binding.tvLocationName.text = "${markaz.nama}, ${markaz.provinsi}"
        viewModel.loadData(markaz.latitude, markaz.longitude)
    }

    // Nama lokasi (mis. "Surabaya, Jawa Timur") ditampilkan di hero card - koordinat mentah
    // dipindah ke tab Detail Perhitungan supaya halaman utama tidak terlalu teknis.
    @SuppressLint("SetTextI18n")
    private fun updateLocationDisplay(lat: Double, lon: Double) {
        lastLat = lat
        lastLng = lon
        lastLocationLabel = "Lat %.4f, Lng %.4f".format(java.util.Locale.US, lat, lon)
        binding.tvDetailCoordinates.text = "Koordinat: Lat $lat, Long $lon"
        binding.tvLocationName.text = "Mencari nama lokasi..."

        lifecycleScope.launch(Dispatchers.IO) {
            val placeName = resolvePlaceName(lat, lon)
            withContext(Dispatchers.Main) {
                binding.tvLocationName.text = placeName ?: "Lokasi Anda"
                if (placeName != null) {
                    lastLocationLabel = "$placeName (Lat %.4f, Lng %.4f)".format(java.util.Locale.US, lat, lon)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolvePlaceName(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(this, java.util.Locale("id", "ID"))
            val address = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
            val kota = address.subAdminArea ?: address.locality
            val provinsi = address.adminArea
            listOfNotNull(kota, provinsi).joinToString(", ").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    // Dialog pilih bulan/tahun Masehi untuk cetak PDF jadwal sholat bulanan - bulan bebas
    // (tidak dikunci ke Ramadhan seperti Jadwal Imsakiyah), lihat PrayerMonthlyScheduleViewModel.
    private fun showPilihBulanDialog() {
        val lat = lastLat
        val lng = lastLng
        if (lat == null || lng == null) {
            Toast.makeText(this, "Lokasi belum siap, tunggu sebentar", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogPilihBulanBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        val monthAdapter = ArrayAdapter(this, R.layout.item_spinner_selector, HijriDateUtil.gregorianMonthNames)
        monthAdapter.setDropDownViewResource(R.layout.item_spinner_selector_dropdown)
        dialogBinding.spinnerBulan.adapter = monthAdapter

        val nowCal = Calendar.getInstance()
        val currentMonth = nowCal.get(Calendar.MONTH) + 1
        val currentYear = nowCal.get(Calendar.YEAR)
        val yearRange = (currentYear - 1)..(currentYear + 2)
        val yearAdapter = ArrayAdapter(this, R.layout.item_spinner_selector, yearRange.map { it.toString() })
        yearAdapter.setDropDownViewResource(R.layout.item_spinner_selector_dropdown)
        dialogBinding.spinnerTahun.adapter = yearAdapter

        dialogBinding.spinnerBulan.setSelection(currentMonth - 1)
        dialogBinding.spinnerTahun.setSelection(yearRange.indexOf(currentYear))

        dialogBinding.btnCetakBulanan.setOnClickListener {
            val selectedMonth = dialogBinding.spinnerBulan.selectedItemPosition + 1
            val selectedYear = yearRange.first + dialogBinding.spinnerTahun.selectedItemPosition
            dialog.dismiss()
            cetakJadwalBulanan(lat, lng, selectedMonth, selectedYear)
        }

        dialog.show()
    }

    // Bikin ViewModel baru tiap panggilan (bukan reuse instance ter-scope Activity) supaya
    // observer sekali-pakai di bawah tidak menumpuk kalau tombol cetak ditekan berkali-kali.
    private fun cetakJadwalBulanan(lat: Double, lng: Double, month: Int, year: Int) {
        val repository = PrayerRepository(RetrofitClient.instance, applicationContext)
        val monthlyViewModel = PrayerMonthlyScheduleViewModelFactory(repository)
            .create(PrayerMonthlyScheduleViewModel::class.java)

        monthlyViewModel.errorMessage.observe(this) { message ->
            if (!message.isNullOrEmpty()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        lateinit var stateObserver: Observer<PrayerScheduleTableUiState>
        stateObserver = Observer { state ->
            monthlyViewModel.uiState.removeObserver(stateObserver)
            val reportData = PrayerScheduleReportData(
                reportTitle = "JADWAL WAKTU SHOLAT BULANAN",
                monthLabel = state.monthLabel,
                locationLabel = lastLocationLabel,
                columnLabels = state.columnLabels,
                rows = state.rows.map { row -> PrayerScheduleReportRow(row.gregorianLabel, row.times) },
                fileNamePrefix = "Jadwal_Sholat_Bulanan"
            )
            startActivity(
                Intent(this, LaporanJadwalActivity::class.java)
                    .putExtra(LaporanJadwalActivity.EXTRA_REPORT, reportData)
            )
        }
        monthlyViewModel.uiState.observe(this, stateObserver)
        monthlyViewModel.loadMonth(lat, lng, month, year)
    }
}