package site.elahady.alkaukaba.viewmodel.waktusholat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import site.elahady.alkaukaba.repo.PrayerRepository
import site.elahady.alkaukaba.utils.HijriDateUtil
import site.elahady.alkaukaba.utils.PrayerScheduleFormatter
import site.elahady.alkaukaba.viewmodel.shared.PrayerScheduleTableRow
import site.elahady.alkaukaba.viewmodel.shared.PrayerScheduleTableUiState
import java.text.SimpleDateFormat
import java.util.Locale

// Jadwal sholat 1 bulan Masehi penuh (bulan/tahun bebas pilihan user, lihat dialog_pilih_bulan),
// dipakai untuk cetak PDF di WaktuSholatActivity. Beda dari PrayerTimesViewModel (hari ini saja),
// dan beda dari JadwalImsakiyahViewModel (bulan Hijriyah Ramadhan) - tapi format per-baris waktu
// sama persis lewat PrayerScheduleFormatter supaya konsisten.
class PrayerMonthlyScheduleViewModel(private val repository: PrayerRepository) : ViewModel() {

    private val _uiState = MutableLiveData<PrayerScheduleTableUiState>()
    val uiState: LiveData<PrayerScheduleTableUiState> = _uiState

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadMonth(lat: Double, lng: Double, gregorianMonth: Int, year: Int) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val response = repository.getIslamicHolidays(lat, lng, gregorianMonth, year)
                if (!response.isSuccessful) {
                    _errorMessage.value = "Gagal mengambil data: ${response.code()}"
                    return@launch
                }
                val apiDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                val labelDateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                val days = response.body()?.data ?: emptyList()
                val rows = days.mapIndexed { index, day ->
                    val label = try {
                        apiDateFormat.parse(day.date.readable)?.let { labelDateFormat.format(it) } ?: day.date.readable
                    } catch (e: Exception) {
                        day.date.readable
                    }
                    PrayerScheduleTableRow(
                        dayLabel = index + 1,
                        gregorianLabel = label,
                        times = PrayerScheduleFormatter.buildTimes(day.timings)
                    )
                }
                _uiState.value = PrayerScheduleTableUiState(
                    monthLabel = "${HijriDateUtil.gregorianMonthNames[gregorianMonth - 1]} $year",
                    columnLabels = PrayerScheduleFormatter.COLUMN_LABELS,
                    rows = rows
                )
            } catch (e: Exception) {
                _errorMessage.value = "Terjadi kesalahan koneksi: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
