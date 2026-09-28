package site.elahady.alkaukaba.notifikasi

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import site.elahady.alkaukaba.api.TimingPrayers
import site.elahady.alkaukaba.utils.SessionManager
import java.util.Calendar

/** Pasang alarm exact untuk 5 waktu sholat wajib berdasarkan [TimingPrayers] hari ini. */
object AdzanScheduler {

    const val EXTRA_PRAYER_NAME = "prayer_name"
    const val PRAYER_SUBUH = "Subuh"

    // requestCode PendingIntent per waktu sholat, supaya alarm lama otomatis ter-replace
    // (bukan menumpuk) tiap kali dijadwalkan ulang.
    private val PRAYER_REQUEST_CODES = mapOf(
        PRAYER_SUBUH to 4101,
        "Dzuhur" to 4102,
        "Ashar" to 4103,
        "Maghrib" to 4104,
        "Isya" to 4105
    )

    // requestCode terpisah untuk alarm pengingat pra-adzan, supaya tidak bentrok dengan
    // PRAYER_REQUEST_CODES di atas (alarm adzan & reminder berjalan independen).
    private val PRE_ADZAN_REMINDER_REQUEST_CODES = mapOf(
        PRAYER_SUBUH to 5101,
        "Dzuhur" to 5102,
        "Ashar" to 5103,
        "Maghrib" to 5104,
        "Isya" to 5105
    )

    /**
     * Tiap waktu sholat dijadwalkan pada kemunculan berikutnya: hari ini kalau belum lewat,
     * kalau sudah lewat pakai [tomorrowTimings] (bila tersedia). Satu alarm per waktu sholat
     * (requestCode sama), jadi tidak ada penumpukan alarm hari ini + besok.
     */
    fun scheduleFromTimings(context: Context, timings: TimingPrayers, tomorrowTimings: TimingPrayers? = null) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()
        val sessionManager = SessionManager(context)
        val reminderEnabled = sessionManager.isPreAdzanReminderEnabled()
        val reminderMinutes = sessionManager.getPreAdzanReminderMinutes()

        val today = prayerHhmm(timings)
        val tomorrow = tomorrowTimings?.let { prayerHhmm(it) }

        today.forEach { (name, hhmmToday) ->
            var time = parseToCalendar(hhmmToday, dayOffset = 0) ?: return@forEach
            if (time.before(now)) {
                // Sudah lewat hari ini - pakai jadwal besok; tanpa itu, dilewati dan job berikutnya
                // (00:05 / sesudah adzan) yang menjadwalkan.
                val hhmmTomorrow = tomorrow?.get(name) ?: return@forEach
                time = parseToCalendar(hhmmTomorrow, dayOffset = 1) ?: return@forEach
            }
            scheduleAlarm(context, alarmManager, AdzanAlarmReceiver::class.java, PRAYER_REQUEST_CODES, name, time)

            if (reminderEnabled) {
                val reminderTime = (time.clone() as Calendar).apply { add(Calendar.MINUTE, -reminderMinutes) }
                // Reminder dilewati kalau titik waktunya sendiri sudah lewat, meski waktu
                // sholatnya sendiri belum (mis. reminder dijadwalkan setelah aplikasi dibuka
                // di antara jam reminder dan jam sholat).
                if (!reminderTime.before(now)) {
                    scheduleAlarm(
                        context, alarmManager, PreAdzanReminderReceiver::class.java,
                        PRE_ADZAN_REMINDER_REQUEST_CODES, name, reminderTime
                    )
                }
            }
        }
    }

    private fun prayerHhmm(timings: TimingPrayers) = linkedMapOf(
        PRAYER_SUBUH to timings.subuh,
        "Dzuhur" to timings.dzuhur,
        "Ashar" to timings.ashar,
        "Maghrib" to timings.maghrib,
        "Isya" to timings.isya
    )

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        receiverClass: Class<out android.content.BroadcastReceiver>,
        requestCodes: Map<String, Int>,
        prayerName: String,
        time: Calendar
    ) {
        val requestCode = requestCodes[prayerName] ?: return
        val intent = Intent(context, receiverClass).apply {
            putExtra(EXTRA_PRAYER_NAME, prayerName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Izin "Alarm & pengingat" belum diberikan user - fallback ke alarm tidak-exact
            // supaya notifikasi tetap muncul (mundur beberapa menit itu wajar untuk kasus ini).
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.timeInMillis, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.timeInMillis, pendingIntent)
    }

    /** Parse "HH:mm" (format Aladhan API) jadi Calendar di timezone device, [dayOffset] hari dari hari ini. */
    private fun parseToCalendar(hhmm: String, dayOffset: Int): Calendar? {
        val parts = hhmm.trim().split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
