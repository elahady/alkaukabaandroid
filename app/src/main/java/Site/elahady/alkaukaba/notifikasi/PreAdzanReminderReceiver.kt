package site.elahady.alkaukaba.notifikasi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import site.elahady.alkaukaba.utils.SessionManager

/** Diterima beberapa menit sebelum waktu sholat tiba (dipasang oleh [AdzanScheduler]).
 *  Preferensi enabled/durasi dibaca di sini, bukan saat menjadwalkan, supaya user yang
 *  mematikan fitur ini setelah alarm terpasang tidak tetap kebagian notifikasi. */
class PreAdzanReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(AdzanScheduler.EXTRA_PRAYER_NAME) ?: return
        val sessionManager = SessionManager(context)
        if (!sessionManager.isPreAdzanReminderEnabled()) return

        NotificationHelper.postPreAdzanReminderNotification(
            context, prayerName, sessionManager.getPreAdzanReminderMinutes()
        )
    }
}
