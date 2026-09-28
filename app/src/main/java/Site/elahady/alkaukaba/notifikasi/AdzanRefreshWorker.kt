package site.elahady.alkaukaba.notifikasi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import site.elahady.alkaukaba.api.RetrofitClient
import site.elahady.alkaukaba.repo.PrayerRepository
import site.elahady.alkaukaba.utils.SessionManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/** Ambil jadwal sholat hari ini via [PrayerRepository] lalu jadwalkan alarm lewat [AdzanScheduler]. */
class AdzanRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "adzan_refresh_periodic"
        const val UNIQUE_WORK_NAME_IMMEDIATE = "adzan_refresh_immediate"

        // Default Jakarta - sama dengan fallback yang sudah dipakai MainActivity saat izin lokasi ditolak.
        private const val DEFAULT_LAT = -6.2088
        private const val DEFAULT_LNG = 106.8456

        // Selisih koordinat (derajat, ~5 km) minimal dari yang tersimpan sebelum jadwal adzan
        // dihitung ulang - supaya GPS yang bergeser sedikit tidak memicu fetch API tiap Beranda dibuka.
        private const val LOCATION_CHANGE_THRESHOLD_DEG = 0.05

        /** Jadwalkan ulang alarm adzan sekarang juga (sekali jalan, tanpa menunggu job harian). */
        fun enqueueImmediate(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AdzanRefreshWorker>().build()
            )
        }

        /**
         * Dipanggil Beranda tiap kali lokasi jadwal sholatnya ketemu (GPS atau manual). Simpan sebagai
         * acuan adzan, dan kalau lokasinya berpindah signifikan (atau baru pertama kali) langsung
         * jadwalkan ulang alarm - mis. user pindah kota atau ganti lokasi manual di Konfigurasi.
         */
        fun onHomeLocationResolved(context: Context, lat: Double, lng: Double) {
            val sessionManager = SessionManager(context)
            val previous = sessionManager.getLastHomeLocation()
            sessionManager.setLastHomeLocation(lat, lng)
            val moved = previous == null ||
                kotlin.math.abs(previous.first - lat) > LOCATION_CHANGE_THRESHOLD_DEG ||
                kotlin.math.abs(previous.second - lng) > LOCATION_CHANGE_THRESHOLD_DEG
            if (moved) enqueueImmediate(context.applicationContext)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val (lat, lng) = resolveLocation()
            val repository = PrayerRepository(RetrofitClient.instance, applicationContext)
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val response = repository.getTimingPrayers(lat, lng, dateFormat.format(Date()))
            val timings = response.body()?.data?.timings

            if (response.isSuccessful && timings != null) {
                // Jadwal besok dipakai untuk waktu sholat yang hari ini sudah lewat (mis. Subuh besok
                // setelah Isya lewat), supaya tidak bergantung pada job harian 00:05 yang bisa
                // tertunda Doze. Gagal ambil jadwal besok tidak menggagalkan jadwal hari ini.
                val tomorrowTimings = try {
                    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time
                    repository.getTimingPrayers(lat, lng, dateFormat.format(tomorrow)).body()?.data?.timings
                } catch (e: Exception) {
                    Log.w("AdzanRefreshWorker", "Gagal ambil jadwal besok, hanya jadwal hari ini yang dipasang", e)
                    null
                }
                AdzanScheduler.scheduleFromTimings(applicationContext, timings, tomorrowTimings)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("AdzanRefreshWorker", "Gagal refresh jadwal adzan", e)
            Result.retry()
        }
    }

    private suspend fun resolveLocation(): Pair<Double, Double> {
        val sessionManager = SessionManager(applicationContext)
        if (sessionManager.isManualLocationMode()) {
            return sessionManager.getManualLat() to sessionManager.getManualLng()
        }
        val location = getLastLocationOrNull()
        if (location != null) return location.latitude to location.longitude
        // GPS sering tidak bisa dibaca dari background (tanpa izin lokasi latar belakang) -
        // pakai koordinat terakhir yang tampil di Beranda supaya adzan tetap mengacu ke situ.
        return sessionManager.getLastHomeLocation() ?: (DEFAULT_LAT to DEFAULT_LNG)
    }

    private suspend fun getLastLocationOrNull(): Location? {
        val hasFine = ActivityCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        return suspendCancellableCoroutine { cont ->
            try {
                LocationServices.getFusedLocationProviderClient(applicationContext).lastLocation
                    .addOnSuccessListener { location -> cont.resume(location) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (e: SecurityException) {
                cont.resume(null)
            }
        }
    }
}
