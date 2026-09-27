package site.elahady.alkaukaba.ui.pilihkota

import site.elahady.alkaukaba.adapter.MarkazRadioAdapter
import site.elahady.alkaukaba.databinding.ActivityPilihKotaBinding
import site.elahady.alkaukaba.utils.HisabNasionalCalculator
import site.elahady.alkaukaba.utils.SessionManager
import site.elahady.alkaukaba.utils.applySystemBarInsetsPadding
import site.elahady.alkaukaba.utils.applyTopSystemBarInsetAsMargin
import site.elahady.alkaukaba.utils.applyStatusBarIconsForTheme
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager

/**
 * Picker kota single-select, dipakai bareng oleh Jadwal Imsakiyah dan Waktu Sholat (masing-masing
 * punya setting kota sendiri di SessionManager, lihat [EXTRA_TARGET]). Beda dari
 * `PilihMarkazActivity` (checklist multi-select buat Hisab Nasional): di sini tap satu baris
 * langsung menyimpan pilihan lalu `finish()` - caller re-read SessionManager di `onResume()`,
 * pola yang sama dipakai `HisabNasionalActivity`.
 */
class PilihKotaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET = "extra_target"
        const val EXTRA_TITLE = "extra_title"
        const val TARGET_IMSAKIYAH = "imsakiyah"
        const val TARGET_WAKTU_SHOLAT = "waktu_sholat"
    }

    private lateinit var binding: ActivityPilihKotaBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPilihKotaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        applyStatusBarIconsForTheme()
        binding.includeToolbar.toolbarDefault.applyTopSystemBarInsetAsMargin()
        binding.root.applySystemBarInsetsPadding(applyBottom = true)

        sessionManager = SessionManager(this)

        val target = intent.getStringExtra(EXTRA_TARGET) ?: TARGET_IMSAKIYAH
        binding.includeToolbar.tvToolbarTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: "Pilih Kota"
        binding.includeToolbar.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val currentId = getSelectedId(target)
        val items = listOf(null) + HisabNasionalCalculator.allMarkaz

        binding.rvMarkazRadio.layoutManager = LinearLayoutManager(this)
        binding.rvMarkazRadio.adapter = MarkazRadioAdapter(items, currentId) { picked ->
            setSelectedId(target, picked?.id)
            finish()
        }
    }

    private fun getSelectedId(target: String): String? = when (target) {
        TARGET_WAKTU_SHOLAT -> sessionManager.getSelectedMarkazWaktuSholatId()
        else -> sessionManager.getSelectedMarkazImsakiyahId()
    }

    private fun setSelectedId(target: String, id: String?) {
        when (target) {
            TARGET_WAKTU_SHOLAT -> sessionManager.setSelectedMarkazWaktuSholatId(id)
            else -> sessionManager.setSelectedMarkazImsakiyahId(id)
        }
    }
}
