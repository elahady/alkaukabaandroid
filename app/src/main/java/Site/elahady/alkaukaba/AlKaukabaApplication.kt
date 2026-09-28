package site.elahady.alkaukaba

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import site.elahady.alkaukaba.notifikasi.AdzanRefreshWorker
import site.elahady.alkaukaba.notifikasi.NotificationHelper
import site.elahady.alkaukaba.utils.ThemePrefs
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AlKaukabaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Harus dipanggil sebelum activity manapun dibuat, supaya tidak ada kedipan tema
        // salah sesaat sebelum preferensi ter-apply.
        ThemePrefs.applySavedMode(this)
        NotificationHelper.createChannels(this)

        val workManager = WorkManager.getInstance(this)

        // Jadwalkan sisa waktu sholat hari ini segera (mis. baru install/update app).
        workManager.enqueueUniqueWork(
            AdzanRefreshWorker.UNIQUE_WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AdzanRefreshWorker>().build()
        )

        // Refresh harian jam 00:05 untuk menjadwalkan waktu sholat besok.
        val periodicRequest = PeriodicWorkRequestBuilder<AdzanRefreshWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNextRefresh(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            AdzanRefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    private fun millisUntilNextRefresh(): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
