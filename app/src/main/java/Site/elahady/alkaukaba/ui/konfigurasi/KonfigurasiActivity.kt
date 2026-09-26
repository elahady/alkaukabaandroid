package site.elahady.alkaukaba.ui.konfigurasi

import site.elahady.alkaukaba.R
import site.elahady.alkaukaba.databinding.ActivityKonfigurasiBinding
import site.elahady.alkaukaba.utils.PrayerCalculationMethods
import site.elahady.alkaukaba.utils.SessionManager
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
import site.elahady.alkaukaba.utils.applySystemBarInsetsPadding
import site.elahady.alkaukaba.utils.applyTopSystemBarInsetAsMargin
import site.elahady.alkaukaba.utils.applyStatusBarIconsForTheme

class KonfigurasiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKonfigurasiBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Referensi field lat/lon aktif selagi dialog_lokasi terbuka, dipakai callback GPS/permission.
    private var etManualLatRef: EditText? = null
    private var etManualLngRef: EditText? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchGpsIntoManualFields()
        } else {
            Toast.makeText(this, "Izin lokasi diperlukan untuk mengambil GPS", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickFromMapLauncher = registerForActivityResult(PilihLokasiPetaContract()) { picked ->
        picked?.let { (lat, lng) ->
            etManualLatRef?.setText(lat.toString())
            etManualLngRef?.setText(lng.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityKonfigurasiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        applyStatusBarIconsForTheme()
        binding.includeToolbar.toolbarDefault.applyTopSystemBarInsetAsMargin()
        binding.root.applySystemBarInsetsPadding(applyBottom = true)

        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.includeToolbar.tvToolbarTitle.text = "Konfigurasi"
        binding.includeToolbar.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.rowLocation.setOnClickListener { showLocationSheet() }
        binding.rowQiblaSource.setOnClickListener { showQiblaSourceSheet() }
        binding.rowPrayerMethod.setOnClickListener { showPrayerMethodSheet() }
        binding.rowHisabMethod.setOnClickListener { showHisabMethodSheet() }

        updateCurrentLocationLabel()
        updateCurrentQiblaSourceLabel()
        updateCurrentMethodLabel()
        updateCurrentHisabMethodLabel()
    }

    // --- Lokasi ---

    private fun updateCurrentLocationLabel() {
        binding.tvCurrentLocation.text = if (sessionManager.isManualLocationMode()) {
            "Manual: %.4f, %.4f".format(sessionManager.getManualLat(), sessionManager.getManualLng())
        } else {
            "Otomatis (GPS)"
        }
    }

    private fun showLocationSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_lokasi, null)
        bottomSheetDialog.setContentView(view)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupLocationMode)
        val radioAuto = view.findViewById<RadioButton>(R.id.radioLocationAuto)
        val radioManual = view.findViewById<RadioButton>(R.id.radioLocationManual)
        val layoutManual = view.findViewById<View>(R.id.layoutManualLocation)
        val etLat = view.findViewById<EditText>(R.id.etManualLat)
        val etLng = view.findViewById<EditText>(R.id.etManualLng)
        val btnUseGps = view.findViewById<AppCompatButton>(R.id.btnUseCurrentGps)
        val btnPickFromMap = view.findViewById<AppCompatButton>(R.id.btnPickFromMap)
        val btnSave = view.findViewById<AppCompatButton>(R.id.btnSaveLocation)

        val isManual = sessionManager.getLocationMode() == SessionManager.LOCATION_MODE_MANUAL
        radioManual.isChecked = isManual
        radioAuto.isChecked = !isManual
        layoutManual.visibility = if (isManual) View.VISIBLE else View.GONE
        if (sessionManager.hasManualLocation()) {
            etLat.setText(sessionManager.getManualLat().toString())
            etLng.setText(sessionManager.getManualLng().toString())
        }

        etManualLatRef = etLat
        etManualLngRef = etLng

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            layoutManual.visibility = if (checkedId == R.id.radioLocationManual) View.VISIBLE else View.GONE
        }

        btnUseGps.setOnClickListener { fetchGpsIntoManualFields() }

        // Titik awal peta = angka yang sedang tertulis di field (kalau valid), supaya user bisa
        // menggeser pin dari koordinat sebelumnya alih-alih mulai dari nol.
        btnPickFromMap.setOnClickListener {
            val lat = etLat.text.toString().toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
            val lng = etLng.text.toString().toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
            pickFromMapLauncher.launch(if (lat != null && lng != null) lat to lng else null)
        }

        btnSave.setOnClickListener {
            if (radioGroup.checkedRadioButtonId == R.id.radioLocationManual) {
                val lat = etLat.text.toString().toDoubleOrNull()
                val lng = etLng.text.toString().toDoubleOrNull()
                if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                    Toast.makeText(this, "Isi lintang/bujur dengan angka yang valid", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                sessionManager.setManualLocation(lat, lng)
                sessionManager.setLocationMode(SessionManager.LOCATION_MODE_MANUAL)
            } else {
                sessionManager.setLocationMode(SessionManager.LOCATION_MODE_AUTO)
            }
            updateCurrentLocationLabel()
            Toast.makeText(this, "Lokasi disimpan", Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setOnDismissListener {
            etManualLatRef = null
            etManualLngRef = null
        }

        bottomSheetDialog.show()
    }

    @SuppressLint("MissingPermission")
    private fun fetchGpsIntoManualFields() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                etManualLatRef?.setText(location.latitude.toString())
                etManualLngRef?.setText(location.longitude.toString())
            } else {
                Toast.makeText(this, "Lokasi GPS tidak ditemukan, coba lagi", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Gagal mengambil lokasi GPS", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Hisab Awal Bulan ---

    private fun updateCurrentHisabMethodLabel() {
        binding.tvCurrentHisabMethod.text =
            if (sessionManager.getHisabAwalBulanMethod() == SessionManager.HISAB_AWAL_BULAN_DURRUL_ANIQ) {
                "Ad-Durrul Aniq"
            } else {
                "Astronomy Engine"
            }
    }

    private fun showHisabMethodSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_hisab_method, null)
        bottomSheetDialog.setContentView(view)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupHisabMethod)
        val radioAstronomyEngine = view.findViewById<RadioButton>(R.id.radioHisabAstronomyEngine)
        val radioDurrulAniq = view.findViewById<RadioButton>(R.id.radioHisabDurrulAniq)
        val btnSave = view.findViewById<AppCompatButton>(R.id.btnSaveHisabMethod)

        val isDurrulAniq = sessionManager.getHisabAwalBulanMethod() == SessionManager.HISAB_AWAL_BULAN_DURRUL_ANIQ
        radioDurrulAniq.isChecked = isDurrulAniq
        radioAstronomyEngine.isChecked = !isDurrulAniq

        btnSave.setOnClickListener {
            val method = if (radioGroup.checkedRadioButtonId == R.id.radioHisabDurrulAniq) {
                SessionManager.HISAB_AWAL_BULAN_DURRUL_ANIQ
            } else {
                SessionManager.HISAB_AWAL_BULAN_ASTRONOMY_ENGINE
            }
            sessionManager.setHisabAwalBulanMethod(method)
            updateCurrentHisabMethodLabel()
            Toast.makeText(this, "Metode hisab awal bulan disimpan", Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    // --- Arah Kiblat ---

    private fun updateCurrentQiblaSourceLabel() {
        binding.tvCurrentQiblaSource.text = if (sessionManager.getQiblaSource() == SessionManager.QIBLA_SOURCE_MANUAL) {
            "Rumus Manual (Al Hasib)"
        } else {
            "Aladhan (Kompas API)"
        }
    }

    private fun showQiblaSourceSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_qibla_source, null)
        bottomSheetDialog.setContentView(view)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupQiblaSource)
        val radioAladhan = view.findViewById<RadioButton>(R.id.radioQiblaAladhan)
        val radioManual = view.findViewById<RadioButton>(R.id.radioQiblaManual)
        val btnSave = view.findViewById<AppCompatButton>(R.id.btnSaveQiblaSource)

        val isManual = sessionManager.getQiblaSource() == SessionManager.QIBLA_SOURCE_MANUAL
        radioManual.isChecked = isManual
        radioAladhan.isChecked = !isManual

        btnSave.setOnClickListener {
            val source = if (radioGroup.checkedRadioButtonId == R.id.radioQiblaManual) {
                SessionManager.QIBLA_SOURCE_MANUAL
            } else {
                SessionManager.QIBLA_SOURCE_ALADHAN
            }
            sessionManager.setQiblaSource(source)
            updateCurrentQiblaSourceLabel()
            Toast.makeText(this, "Sumber perhitungan kiblat disimpan", Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    // --- Waktu Sholat ---

    private fun updateCurrentMethodLabel() {
        val method = PrayerCalculationMethods.findById(sessionManager.getPrayerMethodId())
        binding.tvCurrentMethod.text = method.displayName
    }

    private fun showPrayerMethodSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_prayer_method, null)
        bottomSheetDialog.setContentView(view)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupMethods)
        val layoutCustomAngle = view.findViewById<View>(R.id.layoutCustomAngle)
        val etCustomFajr = view.findViewById<EditText>(R.id.etCustomFajr)
        val etCustomIsha = view.findViewById<EditText>(R.id.etCustomIsha)
        val btnSave = view.findViewById<AppCompatButton>(R.id.btnSavePrayerMethod)

        val currentMethodId = sessionManager.getPrayerMethodId()

        PrayerCalculationMethods.PRESETS.forEach { method ->
            val radioButton = RadioButton(this).apply {
                id = method.id
                text = "${method.displayName}\n${method.subtitle}"
                textSize = 14f
                setTextColor(android.graphics.Color.BLACK)
                setPadding(0, 16, 0, 16)
                isChecked = method.id == currentMethodId
            }
            radioGroup.addView(radioButton)
        }

        etCustomFajr.setText(sessionManager.getCustomFajrAngle().toString())
        etCustomIsha.setText(sessionManager.getCustomIshaAngle().toString())
        layoutCustomAngle.visibility = if (currentMethodId == PrayerCalculationMethods.CUSTOM_ID) View.VISIBLE else View.GONE

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            layoutCustomAngle.visibility =
                if (checkedId == PrayerCalculationMethods.CUSTOM_ID) View.VISIBLE else View.GONE
        }

        btnSave.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            if (selectedId == -1) {
                Toast.makeText(this, "Pilih salah satu metode terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedId == PrayerCalculationMethods.CUSTOM_ID) {
                val fajrAngle = etCustomFajr.text.toString().toDoubleOrNull()
                val ishaAngle = etCustomIsha.text.toString().toDoubleOrNull()
                if (fajrAngle == null || ishaAngle == null) {
                    Toast.makeText(this, "Isi sudut Fajr & Isya dengan angka yang valid", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                sessionManager.setCustomPrayerAngles(fajrAngle, ishaAngle)
            }

            sessionManager.setPrayerMethodId(selectedId)
            updateCurrentMethodLabel()
            Toast.makeText(this, "Metode perhitungan disimpan", Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }
}
