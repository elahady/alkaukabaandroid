package site.elahady.alkaukaba.notifikasi

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import site.elahady.alkaukaba.MainActivity
import site.elahady.alkaukaba.R

/** Foreground service yang memutar rekaman adzan penuh (res/raw) untuk mode
 *  [site.elahady.alkaukaba.utils.SessionManager.ADZAN_SOUND_MODE_ADZAN]. */
class AdzanPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val EXTRA_PRAYER_NAME = "prayer_name"
        private const val ACTION_STOP = "site.elahady.alkaukaba.ACTION_STOP_ADZAN"
        private const val NOTIF_ID = 2100

        fun start(context: Context, prayerName: String) {
            val intent = Intent(context, AdzanPlaybackService::class.java)
                .putExtra(EXTRA_PRAYER_NAME, prayerName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlaybackAndSelf()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: "Sholat"
        startForeground(NOTIF_ID, buildNotification(prayerName))
        playAdzan(prayerName)
        return START_NOT_STICKY
    }

    private fun buildNotification(prayerName: String): Notification {
        val stopIntent = Intent(this, AdzanPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ADZAN_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Waktu $prayerName telah tiba")
            .setContentText("Adzan sedang diputar")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun playAdzan(prayerName: String) {
        val rawRes = AdzanSound.forPrayer(prayerName)
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val afd = resources.openRawResourceFd(rawRes)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            setOnCompletionListener { stopPlaybackAndSelf() }
            prepare()
            start()
        }
    }

    private fun stopPlaybackAndSelf() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
