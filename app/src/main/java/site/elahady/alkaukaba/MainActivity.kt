package site.elahady.alkaukaba

import site.elahady.alkaukaba.repo.DailyQuoteRepository
import site.elahady.alkaukaba.repo.PrayerRepository
import site.elahady.alkaukaba.adapter.CalendarAdapter
import site.elahady.alkaukaba.adapter.HolidayAdapter
import site.elahady.alkaukaba.api.RetrofitClient
import site.elahady.alkaukaba.notifikasi.AdzanRefreshWorker
import site.elahady.alkaukaba.ui.arahkiblat.KiblatActivity
import site.elahady.alkaukaba.ui.awalbulan.AwalBulanActivity
import site.elahady.alkaukaba.ui.calendar.CalendarActivity
import site.elahady.alkaukaba.ui.fasebulan.FaseBulanActivity
import site.elahady.alkaukaba.ui.gerhana.GerhanaActivity
import site.elahady.alkaukaba.ui.hisabnasional.HisabNasionalActivity
import site.elahady.alkaukaba.ui.jadwalimsakiyah.JadwalImsakiyahActivity
import site.elahady.alkaukaba.ui.menu.SemuaMenuActivity
import site.elahady.alkaukaba.ui.okultasi.OkultasiActivity
import site.elahady.alkaukaba.ui.petavisibilitas.PetaVisibilitasActivity
import site.elahady.alkaukaba.ui.waktusholat.WaktuSholatActivity
import site.elahady.alkaukaba.utils.Resource
import site.elahady.alkaukaba.utils.SessionManager
import site.elahady.alkaukaba.utils.applySystemBarInsetsPadding
import site.elahady.alkaukaba.utils.applyStatusBarIconsForTheme
import site.elahady.alkaukaba.utils.HijriCalendarEngine
import site.elahady.alkaukaba.utils.HijriDateUtil
import site.elahady.alkaukaba.utils.ImageUtils
import site.elahady.alkaukaba.utils.MoonPhaseLabel
import site.elahady.alkaukaba.utils.MoonTilt
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import site.elahady.alkaukaba.viewmodel.MainViewModel
import site.elahady.alkaukaba.viewmodel.MainViewModelFactory
import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import site.elahady.alkaukaba.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var holidayAdapter: HolidayAdapter
    private lateinit var calendarAdapter: CalendarAdapter

    var latitude  = 0.0
    var longitude  = 0.0

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
            ) {
                getUserLocation()
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        applyStatusBarIconsForTheme()
        binding.mainContent.applySystemBarInsetsPadding(applyBottom = true)

        setupViewModel()
        setupHeaderDate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkLocationPermission() // Aman dipanggil di sini
        setupObservers()
        setupNavigation()
        setupHolidayPreview()
        setupMonthlyCalendar()
        setupCalendarNavigation()
        setupMoonPhaseCard()
        setupDailyQuote()

        // Setup Swipe Refresh
        binding.swipeRefresh.setOnRefreshListener {
            refreshData()
        }
    }

    private fun refreshData() {
        setupHeaderDate()
        checkLocationPermission() // Sekarang AMAN dipanggil berulang kali
    }

    private fun setupViewModel() {
        val apiService = RetrofitClient.instance
        val repository = PrayerRepository(apiService, applicationContext)
        val factory = MainViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
    }

    private fun setupObservers() {
        // 1. Observe Data Sholat
        viewModel.prayerState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.tvStatus.text = "Memuat data..."
                }
                is Resource.Success -> {
                    binding.swipeRefresh.isRefreshing = false // Stop loading
                    val data = resource.data
                    if (data != null) {
                        binding.tvPrayerName.text = data.prayerName
                        binding.tvLabelUpcoming.text = data.topLabel
                        binding.tvStatus.text = data.statusText

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val color = if (data.isPassed) getColor(android.R.color.holo_red_dark)
                            else getColor(android.R.color.holo_green_dark)
                            binding.tvStatus.setTextColor(color)
                        }
                    }
                }
                is Resource.Error -> {
                    binding.swipeRefresh.isRefreshing = false // Stop loading
                    binding.tvStatus.text = "Error: ${resource.message}"
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. Observe Lokasi
        viewModel.locationName.observe(this) { locationText ->
            binding.textLoc.text = locationText
        }

        // 3. Observe Hari Besar
        viewModel.holidayAlert.observe(this) { holidayName ->
            if (holidayName != null) {
                Toast.makeText(this, "Hari Besar: $holidayName", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupHeaderDate() {
        val dateFormatFull = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        binding.tvDateNow.text = dateFormatFull.format(Date())
        binding.tvDateHijri.text = HijriDateUtil.fullDateLabel(Calendar.getInstance())
    }

    /**
     * Kartu "Ayat/Hadits Hari Ini" di bawah kalender - fitur prioritas rendah,
     * jadi kalau gagal diambil (offline/API eksternal down), sembunyikan
     * kartunya saja daripada tampilkan pesan error di halaman utama.
     */
    private fun setupDailyQuote() {
        lifecycleScope.launch {
            when (val resource = DailyQuoteRepository.getToday()) {
                is Resource.Success -> {
                    binding.progressDailyQuote.visibility = View.GONE
                    val quote = resource.data ?: return@launch

                    binding.tvDailyQuoteArabic.text = quote.arabic
                    binding.tvDailyQuoteArabic.visibility = View.VISIBLE

                    if (!quote.latin.isNullOrBlank()) {
                        binding.tvDailyQuoteLatin.text = quote.latin
                        binding.tvDailyQuoteLatin.visibility = View.VISIBLE
                    }

                    binding.tvDailyQuoteTranslation.text = quote.translation
                    binding.tvDailyQuoteTranslation.visibility = View.VISIBLE

                    binding.tvDailyQuoteSource.text = quote.source
                    binding.tvDailyQuoteSource.visibility = View.VISIBLE
                }
                is Resource.Error -> {
                    binding.cardDailyQuote.visibility = View.GONE
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun checkLocationPermission() {
        val sessionManager = SessionManager(this)
        if (sessionManager.isManualLocationMode()) {
            // Setting lokasi global (lihat KonfigurasiActivity) - lewati GPS/permission sama sekali.
            useManualLocation(sessionManager.getManualLat(), sessionManager.getManualLng())
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            // Jika sudah diizinkan, langsung ambil lokasi
            getUserLocation()
        }
    }

    private fun useManualLocation(lat: Double, lon: Double) {
        fetchDataByCoordinate(lat, lon)
        val geocoder = Geocoder(this, Locale("id", "ID"))
        viewModel.fetchAddressName(geocoder, lat, lon)
        binding.swipeRefresh.isRefreshing = false
    }

    private fun useDefaultLocation() {
        Toast.makeText(this, "Izin lokasi ditolak, menggunakan default Jakarta", Toast.LENGTH_SHORT).show()
        fetchDataByCoordinate(-6.2088, 106.8456)
        binding.swipeRefresh.isRefreshing = false
    }

    @SuppressLint("MissingPermission")
    private fun getUserLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                fetchDataByCoordinate(location.latitude, location.longitude)

                val geocoder = Geocoder(this, Locale("id", "ID"))
                viewModel.fetchAddressName(geocoder, location.latitude, location.longitude)
            } else {
                Toast.makeText(this, "Lokasi tidak ditemukan, coba refresh", Toast.LENGTH_SHORT).show()
                binding.swipeRefresh.isRefreshing = false
            }
        }.addOnFailureListener {
            binding.swipeRefresh.isRefreshing = false
            Toast.makeText(this, "Gagal mendapatkan lokasi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchDataByCoordinate(lat: Double, lon: Double) {
        latitude = lat
        longitude = lon
        // Adzan mengacu ke lokasi yang sama dengan jadwal sholat di Beranda.
        AdzanRefreshWorker.onHomeLocationResolved(this, lat, lon)
        viewModel.fetchPrayerData(lat, lon)
        viewModel.fetchUpcomingEvents(lat, lon)
        viewModel.initCalendar(lat, lon)
        updateMoonPhaseCardTilt(lat, lon)
        updateHeaderHijriDate(lat, lon)
    }

    /** Setelah lokasi tersedia, koreksi label Hijriyah header pakai mesin hisab yang sama
     * dengan widget Kalender & fitur Awal Bulan ([HijriCalendarEngine]), bukan tabular offline
     * murni - supaya "hari ini" konsisten di seluruh layar Beranda. */
    private fun updateHeaderHijriDate(lat: Double, lon: Double) {
        CoroutineScope(Dispatchers.Default).launch {
            val label = try {
                val observer = Observer(lat, lon, 0.0)
                HijriCalendarEngine.fullDateLabelFor(observer, Calendar.getInstance())
            } catch (e: Exception) {
                HijriDateUtil.fullDateLabel(Calendar.getInstance())
            }
            withContext(Dispatchers.Main) {
                binding.tvDateHijri.text = label
            }
        }
    }

    /**
     * Begitu lokasi tersedia, perbarui kartu Fase Bulan dari [setupMoonPhaseCard]
     * (kiri/kanan generik tanpa rotasi) ke kemiringan limb terang sungguhan
     * seperti tampak di langit pengamat - pola sama dengan
     * `FaseBulanActivity.onLocationReady()`. Perhitungan single-point
     * (bukan pencarian akar iteratif seperti rise/set), jadi aman sinkron di
     * main thread tanpa coroutine.
     */
    private fun updateMoonPhaseCardTilt(lat: Double, lon: Double) {
        val observer = Observer(lat, lon, 0.0)
        val now = Time.fromMillisecondsSince1970(System.currentTimeMillis())
        val moonEq = equator(Body.Moon, now, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val moonHor = horizon(now, observer, moonEq.ra, moonEq.dec, Refraction.Normal)
        val sunEq = equator(Body.Sun, now, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val sunHor = horizon(now, observer, sunEq.ra, sunEq.dec, Refraction.Normal)
        val tiltDegrees = MoonTilt.brightLimbAngleDegrees(
            moonAzimuthDeg = moonHor.azimuth,
            moonAltitudeDeg = moonHor.altitude,
            sunAzimuthDeg = sunHor.azimuth,
            sunAltitudeDeg = sunHor.altitude
        )
        binding.moonPhaseView.setPhaseWithTrueTilt(illumination(Body.Moon, now).phaseFraction, tiltDegrees)
    }

    private fun setupNavigation() {
        binding.btSholat.setOnClickListener {
            val intentWaktuSholat = Intent(this@MainActivity, WaktuSholatActivity::class.java)
            startActivity(intentWaktuSholat)
        }
        binding.btKiblat.setOnClickListener {
            val intentKiblat = Intent(this@MainActivity, KiblatActivity::class.java)
            startActivity(intentKiblat)
        }
        binding.btSholat.setOnClickListener {
            val intentSholat = Intent(this@MainActivity, WaktuSholatActivity::class.java)
            startActivity(intentSholat)
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, site.elahady.alkaukaba.ui.konfigurasi.KonfigurasiActivity::class.java))
        }
        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, site.elahady.alkaukaba.ui.profile.ProfileActivity::class.java))
        }
        binding.btAwalbulan.setOnClickListener {
            startActivity(Intent(this, AwalBulanActivity::class.java))
        }
        binding.btGerhana.setOnClickListener {
            startActivity(Intent(this, GerhanaActivity::class.java))
        }
        binding.btOkultasi.setOnClickListener {
            startActivity(Intent(this, OkultasiActivity::class.java))
        }
        binding.btHisabNasional.setOnClickListener {
            startActivity(Intent(this, HisabNasionalActivity::class.java))
        }
        binding.btPetaVisibilitas.setOnClickListener {
            startActivity(Intent(this, PetaVisibilitasActivity::class.java))
        }
        binding.btJadwalImsakiyah.setOnClickListener {
            startActivity(Intent(this, JadwalImsakiyahActivity::class.java))
        }
        binding.btnSeeAllMenu.setOnClickListener {
            startActivity(Intent(this, SemuaMenuActivity::class.java))
        }
        binding.tvLabelCalendar.setOnClickListener { openCalendarPage() }
        binding.tvLabelDetailCalendar.setOnClickListener { openCalendarPage() }
    }

    private fun setupMoonPhaseCard() {
        val now = Time.fromMillisecondsSince1970(System.currentTimeMillis())
        val synodicAngle = moonPhase(now)
        val illum = illumination(Body.Moon, now)
        binding.moonPhaseView.setPhase(synodicAngle, illum.phaseFraction)
        binding.tvMoonPhaseName.text = MoonPhaseLabel.forAngle(synodicAngle)
        binding.tvMoonIllumination.text = "%.0f%% tersinari".format(illum.phaseFraction * 100)
        binding.btnMoonPhase.setOnClickListener {
            startActivity(Intent(this, FaseBulanActivity::class.java))
        }

        // Kartu ini pakai background hitam solid, jadi sisi malam Bulan
        // ikut hitam juga supaya tetap menyatu (opak + warna sama).
        binding.moonPhaseView.setNightBaseColor(Color.BLACK)
    }

    private fun openCalendarPage() {
        val intent = Intent(this, CalendarActivity::class.java)
        intent.putExtra("LATITUDE", latitude)
        intent.putExtra("LONGITUDE", longitude)
        startActivity(intent)
    }

    private fun setupHolidayPreview() {
        holidayAdapter = HolidayAdapter()
        binding.rvHolidayPreview.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = holidayAdapter
            isNestedScrollingEnabled = false
        }
        viewModel.holidayPreview.observe(this) { resource ->
            when (resource) {
                is Resource.Success -> {
                    resource.data?.let { items -> holidayAdapter.setData(items) }
                }
                is Resource.Error -> println("error :: " + resource.message)
                is Resource.Loading -> {}
            }
        }

        // Lewat openCalendarPage() supaya lat/lng ikut terbawa — Hari Tanpa Bayangan
        // dihitung per koordinat, tanpa extra ini jatuh ke default Jakarta.
        binding.btnSeeAllHolidays.setOnClickListener { openCalendarPage() }
    }

    private fun setupMonthlyCalendar() {
        calendarAdapter = CalendarAdapter { date ->
            viewModel.selectDate(Calendar.getInstance().apply { time = date })
        }
        binding.rvWeeklyCalendar.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 7)
            adapter = calendarAdapter
            isNestedScrollingEnabled = false
        }

        viewModel.hijriTitle.observe(this) { hijriText ->
            binding.tvHijriMonthYear.text = hijriText
        }

        viewModel.calendarData.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressCalendar.visibility = View.VISIBLE
                    binding.rvWeeklyCalendar.visibility = View.INVISIBLE
                }
                is Resource.Success -> {
                    binding.progressCalendar.visibility = View.GONE
                    binding.rvWeeklyCalendar.visibility = View.VISIBLE
                    resource.data?.let { calendarAdapter.setData(it) }
                }
                is Resource.Error -> {
                    binding.progressCalendar.visibility = View.GONE
                    binding.rvWeeklyCalendar.visibility = View.VISIBLE
                }
            }
        }

        viewModel.monthYearTitle.observe(this) { title ->
            val parts = title.split(" ")
            if (parts.size >= 2) {
                binding.tvMonth.text = parts[0]
                binding.tvYear.text = parts[1]
            } else {
                binding.tvMonth.text = title
            }
        }
    }

    private fun setupCalendarNavigation() {
        binding.btnPrevMonth.setOnClickListener { viewModel.changeMonth(-1) }
        binding.btnNextMonth.setOnClickListener { viewModel.changeMonth(1) }
        binding.layoutMonthTitle.setOnClickListener { showMonthTitleDatePicker() }
    }

    /** Tap judul bulan Masehi/Hijriyah -> lompat langsung ke tanggal mana pun (tidak terbatas
     * geser bulan satu-satu lewat panah prev/next). Judul & grid otomatis menyesuaikan tanggal terpilih. */
    private fun showMonthTitleDatePicker() {
        val seed = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.selectDate(picked)
            },
            seed.get(Calendar.YEAR),
            seed.get(Calendar.MONTH),
            seed.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /** Ikon profil di header ikut foto profil user - direfresh di onResume juga karena
     * MainActivity tidak di-recreate saat balik dari ProfileActivity via back. */
    private fun renderProfileAvatar() {
        ImageUtils.loadAvatarInto(this, binding.btnProfile, SessionManager(this).getAvatarUrl(), 8)
    }

    override fun onResume() {
        super.onResume()
        renderProfileAvatar()
    }

}