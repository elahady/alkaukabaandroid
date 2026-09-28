package site.elahady.alkaukaba.notifikasi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/** Alarm exact hilang setelah reboot - jadwalkan ulang sisa waktu sholat hari ini begitu device
 *  menyala lagi (atau app diupdate), tanpa menunggu refresh harian berikutnya. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        WorkManager.getInstance(context).enqueueUniqueWork(
            AdzanRefreshWorker.UNIQUE_WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AdzanRefreshWorker>().build()
        )
    }
}
