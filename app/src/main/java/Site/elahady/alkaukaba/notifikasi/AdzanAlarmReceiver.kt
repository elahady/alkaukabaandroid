package site.elahady.alkaukaba.notifikasi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import site.elahady.alkaukaba.utils.SessionManager

/** Diterima tepat saat waktu sholat tiba (dipasang oleh [AdzanScheduler]). Preferensi suara
 *  dibaca di sini, bukan saat menjadwalkan, supaya perubahan setting terbaru selalu kepakai. */
class AdzanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(AdzanScheduler.EXTRA_PRAYER_NAME) ?: return
        val sessionManager = SessionManager(context)

        when (sessionManager.getAdzanSoundMode()) {
            SessionManager.ADZAN_SOUND_MODE_BEEP -> NotificationHelper.postBeepNotification(context, prayerName)
            SessionManager.ADZAN_SOUND_MODE_SILENT -> NotificationHelper.postSilentNotification(context, prayerName)
            else -> AdzanPlaybackService.start(context, prayerName)
        }

        // Alarm ini sekali pakai - pasang yang berikutnya sekarang (waktu yang baru lewat dijadwalkan
        // ke besok), tanpa bergantung pada job harian 00:05.
        AdzanRefreshWorker.enqueueImmediate(context.applicationContext)
    }
}
