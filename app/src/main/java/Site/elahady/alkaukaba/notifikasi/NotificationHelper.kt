package site.elahady.alkaukaba.notifikasi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import site.elahady.alkaukaba.MainActivity
import site.elahady.alkaukaba.R

/** Bikin channel notifikasi adzan (sekali, di [site.elahady.alkaukaba.AlKaukabaApplication])
 *  dan post notifikasi untuk mode Beep/Senyap. Mode Adzan Penuh lewat [AdzanPlaybackService]. */
object NotificationHelper {

    const val CHANNEL_ADZAN_PLAYBACK = "adzan_playback"
    const val CHANNEL_BEEP = "adzan_beep"
    const val CHANNEL_SILENT = "adzan_silent"
    const val CHANNEL_PRE_ADZAN_REMINDER = "pre_adzan_reminder"

    private const val NOTIF_ID_BEEP = 2001
    private const val NOTIF_ID_SILENT = 2002
    private const val NOTIF_ID_PRE_ADZAN_REMINDER = 2003

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val playbackChannel = NotificationChannel(
            CHANNEL_ADZAN_PLAYBACK, "Adzan (Suara Penuh)", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifikasi ongoing saat adzan penuh sedang diputar"
            setSound(null, null) // Suara diputar manual lewat MediaPlayer di AdzanPlaybackService.
        }

        val sonificationAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val beepChannel = NotificationChannel(
            CHANNEL_BEEP, "Adzan (Beep Pelan)", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifikasi waktu sholat dengan nada singkat"
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, sonificationAttrs)
        }

        val silentChannel = NotificationChannel(
            CHANNEL_SILENT, "Adzan (Senyap)", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifikasi waktu sholat tanpa suara"
            setSound(null, null)
        }

        val preAdzanReminderChannel = NotificationChannel(
            CHANNEL_PRE_ADZAN_REMINDER, "Pengingat Pra-Adzan", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Pengingat beberapa menit sebelum waktu sholat tiba, tanpa suara adzan"
            setSound(null, null)
            enableVibration(true)
        }

        manager.createNotificationChannels(
            listOf(playbackChannel, beepChannel, silentChannel, preAdzanReminderChannel)
        )
    }

    fun postBeepNotification(context: Context, prayerName: String) {
        post(context, CHANNEL_BEEP, "Waktu $prayerName telah tiba", "Saatnya menunaikan sholat $prayerName", NOTIF_ID_BEEP)
    }

    fun postSilentNotification(context: Context, prayerName: String) {
        post(context, CHANNEL_SILENT, "Waktu $prayerName telah tiba", "Saatnya menunaikan sholat $prayerName", NOTIF_ID_SILENT)
    }

    /** Dipanggil [PreAdzanReminderReceiver] beberapa menit sebelum waktu sholat tiba. */
    fun postPreAdzanReminderNotification(context: Context, prayerName: String, minutes: Int) {
        post(
            context, CHANNEL_PRE_ADZAN_REMINDER,
            "$minutes menit lagi waktu $prayerName",
            "Bersiap-siap untuk menunaikan sholat $prayerName",
            NOTIF_ID_PRE_ADZAN_REMINDER
        )
    }

    private fun post(context: Context, channelId: String, title: String, text: String, notificationId: Int) {
        val contentIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Izin POST_NOTIFICATIONS belum diberikan user - lewati saja, tidak perlu crash.
        }
    }
}
