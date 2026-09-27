package site.elahady.alkaukaba.viewmodel.jadwalimsakiyah

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.cosinekitty.astronomy.Observer
import kotlinx.coroutines.launch
import site.elahady.alkaukaba.api.PrayerData
import site.elahady.alkaukaba.repo.PrayerRepository
import site.elahady.alkaukaba.utils.HijriCalendarEngine
import site.elahady.alkaukaba.utils.PrayerScheduleFormatter
import site.elahady.alkaukaba.viewmodel.shared.PrayerScheduleTableRow
import site.elahady.alkaukaba.viewmodel.shared.PrayerScheduleTableUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Tabel jadwal imsakiyah bulan Ramadhan: kolom hari + semua PrayerKind yang ada di menu Waktu
// Sholat (lihat PrayerScheduleFormatter). Data diambil sekali/dua kali panggil lewat
// PrayerRepository.getIslamicHolidays() (endpoint /v1/calendar, 1 bulan Masehi per panggilan),
// BUKAN loop per-hari - 1 bulan Hijriyah membentang 1-2 bulan Masehi.
//
// Fitur ini dikunci ke Ramadhan saja (bulan lain tidak perlu, sesuai keputusan produk) -
// tidak ada lagi navigasi bebas ke bulan Hijriyah manapun seperti versi awal.
class JadwalImsakiyahViewModel(private val repository: PrayerRepository) : ViewModel() {

    companion object {
        // Batas aman pencarian Ramadhan terdekat ke depan - 1 tahun Hijriyah = 12 bulan,
        // jadi 12 iterasi cukup untuk menjamin ketemu tanpa risiko infinite loop.
        private const val MAX_MONTH_SEARCH = 12
        private const val RAMADHAN_MONTH_NAME = "Ramadhan"
    }

    private val _uiState = MutableLiveData<PrayerScheduleTableUiState>()
    val uiState: LiveData<PrayerScheduleTableUiState> = _uiState

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /** Cari Ramadhan terdekat ke depan (bulan berjalan kalau kebetulan sedang Ramadhan, kalau
     *  tidak Ramadhan tahun ini/depan) dari lokasi [lat]/[lng], lalu muat jadwal 1 bulan penuh. */
    fun loadRamadhan(lat: Double, lng: Double) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val observer = Observer(lat, lng, 0.0)
                var range = HijriCalendarEngine.monthRangeForOffset(observer, Calendar.getInstance(), 0)
                var offset = 0
                while (range.monthName != RAMADHAN_MONTH_NAME && offset < MAX_MONTH_SEARCH) {
                    offset++
                    range = HijriCalendarEngine.monthRangeForOffset(observer, Calendar.getInstance(), offset)
                }

                val dayCalendars = (0 until range.dayCount).map { dayIndex ->
                    (range.startDate.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, dayIndex) }
                }
                val monthYearPairs = dayCalendars
                    .map { (it.get(Calendar.MONTH) + 1) to it.get(Calendar.YEAR) }
                    .distinct()

                val apiData = mutableListOf<PrayerData>()
                for ((month, year) in monthYearPairs) {
                    val response = repository.getIslamicHolidays(lat, lng, month, year)
                    if (response.isSuccessful) {
                        response.body()?.data?.let { apiData.addAll(it) }
                    }
                }

                val apiDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                val localDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val labelDateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

                val rows = dayCalendars.mapIndexed { index, dayCal ->
                    val dateStr = localDateFormat.format(dayCal.time)
                    val match = apiData.find { data ->
                        try {
                            val apiDate = apiDateFormat.parse(data.date.readable)
                            apiDate != null && localDateFormat.format(apiDate) == dateStr
                        } catch (e: Exception) {
                            false
                        }
                    }
                    PrayerScheduleTableRow(
                        dayLabel = index + 1,
                        gregorianLabel = labelDateFormat.format(dayCal.time),
                        times = PrayerScheduleFormatter.buildTimes(match?.timings)
                    )
                }

                _uiState.value = PrayerScheduleTableUiState(
                    monthLabel = "${range.monthName} ${range.year} H",
                    columnLabels = PrayerScheduleFormatter.COLUMN_LABELS,
                    rows = rows
                )
            } catch (e: Exception) {
                _errorMessage.value = "Gagal memuat jadwal imsakiyah: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
