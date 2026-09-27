package site.elahady.alkaukaba.viewmodel.waktusholat

import site.elahady.alkaukaba.repo.PrayerRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PrayerMonthlyScheduleViewModelFactory(private val repository: PrayerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PrayerMonthlyScheduleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PrayerMonthlyScheduleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
