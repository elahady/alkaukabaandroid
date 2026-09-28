package site.elahady.alkaukaba.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("AppSession", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PRAYER_METHOD_ID = "PRAYER_METHOD_ID"
        private const val KEY_PRAYER_CUSTOM_FAJR = "PRAYER_CUSTOM_FAJR"
        private const val KEY_PRAYER_CUSTOM_ISHA = "PRAYER_CUSTOM_ISHA"
        const val DEFAULT_PRAYER_METHOD_ID = PrayerCalculationMethods.EPHEMERIS_ID
        const val DEFAULT_CUSTOM_FAJR_ANGLE = 20.0
        const val DEFAULT_CUSTOM_ISHA_ANGLE = 18.0

        private const val KEY_LOCATION_MODE = "LOCATION_MODE"
        private const val KEY_MANUAL_LAT = "MANUAL_LAT"
        private const val KEY_MANUAL_LNG = "MANUAL_LNG"
        const val LOCATION_MODE_AUTO = "AUTO"
        const val LOCATION_MODE_MANUAL = "MANUAL"

        private const val KEY_QIBLA_SOURCE = "QIBLA_SOURCE"
        const val QIBLA_SOURCE_ALADHAN = "ALADHAN"
        const val QIBLA_SOURCE_MANUAL = "MANUAL_FORMULA"

        private const val KEY_ADZAN_SOUND_MODE = "ADZAN_SOUND_MODE"
        const val ADZAN_SOUND_MODE_ADZAN = "ADZAN"
        const val ADZAN_SOUND_MODE_BEEP = "BEEP"
        const val ADZAN_SOUND_MODE_SILENT = "SILENT"

        private const val KEY_HISAB_AWAL_BULAN_METHOD = "HISAB_AWAL_BULAN_METHOD"
        const val HISAB_AWAL_BULAN_ASTRONOMY_ENGINE = "ASTRONOMY_ENGINE"
        const val HISAB_AWAL_BULAN_DURRUL_ANIQ = "DURRUL_ANIQ"

        private const val KEY_HISAB_NASIONAL_MARKAZ_IDS = "HISAB_NASIONAL_MARKAZ_IDS"

        private const val KEY_SELECTED_MARKAZ_IMSAKIYAH = "SELECTED_MARKAZ_IMSAKIYAH"
        private const val KEY_SELECTED_MARKAZ_WAKTU_SHOLAT = "SELECTED_MARKAZ_WAKTU_SHOLAT"

        private const val KEY_LAST_HOME_LAT = "LAST_HOME_LAT"
        private const val KEY_LAST_HOME_LNG = "LAST_HOME_LNG"

        private const val KEY_PRE_ADZAN_REMINDER_ENABLED = "PRE_ADZAN_REMINDER_ENABLED"
        private const val KEY_PRE_ADZAN_REMINDER_MINUTES = "PRE_ADZAN_REMINDER_MINUTES"
        const val DEFAULT_PRE_ADZAN_REMINDER_MINUTES = 10
    }

    /**
     * Setting lokasi global — dipakai semua fitur yang butuh lat/lon (Waktu Sholat, Kiblat,
     * Kalender, Bulan Hijriyah), bukan cuma layar Konfigurasi. Lihat docs di folder
     * docs/features untuk fitur mana yang sudah/belum menghormati setting ini.
     */
    fun setLocationMode(mode: String) {
        prefs.edit().putString(KEY_LOCATION_MODE, mode).apply()
    }

    fun getLocationMode(): String = prefs.getString(KEY_LOCATION_MODE, LOCATION_MODE_AUTO) ?: LOCATION_MODE_AUTO

    fun isManualLocationMode(): Boolean = getLocationMode() == LOCATION_MODE_MANUAL && hasManualLocation()

    fun setManualLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(KEY_MANUAL_LAT, lat.toFloat())
            .putFloat(KEY_MANUAL_LNG, lng.toFloat())
            .apply()
    }

    fun getManualLat(): Double = prefs.getFloat(KEY_MANUAL_LAT, 0f).toDouble()

    fun getManualLng(): Double = prefs.getFloat(KEY_MANUAL_LNG, 0f).toDouble()

    fun hasManualLocation(): Boolean = prefs.contains(KEY_MANUAL_LAT) && prefs.contains(KEY_MANUAL_LNG)

    fun setQiblaSource(source: String) {
        prefs.edit().putString(KEY_QIBLA_SOURCE, source).apply()
    }

    fun getQiblaSource(): String = prefs.getString(KEY_QIBLA_SOURCE, QIBLA_SOURCE_ALADHAN) ?: QIBLA_SOURCE_ALADHAN

    fun setPrayerMethodId(methodId: Int) {
        prefs.edit().putInt(KEY_PRAYER_METHOD_ID, methodId).apply()
    }

    fun getPrayerMethodId(): Int = prefs.getInt(KEY_PRAYER_METHOD_ID, DEFAULT_PRAYER_METHOD_ID)

    fun setCustomPrayerAngles(fajrAngle: Double, ishaAngle: Double) {
        prefs.edit()
            .putFloat(KEY_PRAYER_CUSTOM_FAJR, fajrAngle.toFloat())
            .putFloat(KEY_PRAYER_CUSTOM_ISHA, ishaAngle.toFloat())
            .apply()
    }

    fun getCustomFajrAngle(): Double = prefs.getFloat(KEY_PRAYER_CUSTOM_FAJR, DEFAULT_CUSTOM_FAJR_ANGLE.toFloat()).toDouble()

    fun getCustomIshaAngle(): Double = prefs.getFloat(KEY_PRAYER_CUSTOM_ISHA, DEFAULT_CUSTOM_ISHA_ANGLE.toFloat()).toDouble()

    /** Format query `methodSettings` Aladhan API ("fajrAngle,maghribOffset,ishaAngle"), hanya relevan saat method Custom (99). */
    fun getMethodSettingsQuery(): String? {
        if (getPrayerMethodId() != PrayerCalculationMethods.CUSTOM_ID) return null
        return "${getCustomFajrAngle()},0,${getCustomIshaAngle()}"
    }

    fun setAdzanSoundMode(mode: String) {
        prefs.edit().putString(KEY_ADZAN_SOUND_MODE, mode).apply()
    }

    fun getAdzanSoundMode(): String =
        prefs.getString(KEY_ADZAN_SOUND_MODE, ADZAN_SOUND_MODE_ADZAN) ?: ADZAN_SOUND_MODE_ADZAN

    /** Metode hisab awal bulan Hijriyah (fitur "Bulan Hijriyah") — Astronomy Engine (default) atau Ad-Durrul Aniq. */
    fun setHisabAwalBulanMethod(method: String) {
        prefs.edit().putString(KEY_HISAB_AWAL_BULAN_METHOD, method).apply()
    }

    fun getHisabAwalBulanMethod(): String =
        prefs.getString(KEY_HISAB_AWAL_BULAN_METHOD, HISAB_AWAL_BULAN_ASTRONOMY_ENGINE) ?: HISAB_AWAL_BULAN_ASTRONOMY_ENGINE

    /** Markaz (ibu kota provinsi) yang dicentang user untuk fitur "Hisab Awal Bulan Nasional".
     *  `null` = user belum pernah mengatur, caller pakai `HisabNasionalCalculator.defaultMarkazIds`. */
    fun setSelectedMarkazNasionalIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_HISAB_NASIONAL_MARKAZ_IDS, ids).apply()
    }

    fun getSelectedMarkazNasionalIds(): Set<String>? = prefs.getStringSet(KEY_HISAB_NASIONAL_MARKAZ_IDS, null)

    /**
     * Kota (markaz) yang dipilih user untuk fitur "Jadwal Imsakiyah" (menu ⚙️ di toolbar,
     * lihat `PilihKotaActivity`). `null` = belum pernah diatur / user pilih "Gunakan GPS
     * Otomatis" -> jatuh ke alur lokasi lama (GPS -> lokasi manual global -> fallback Jakarta).
     * Sengaja terpisah dari [KEY_SELECTED_MARKAZ_WAKTU_SHOLAT] - tiap fitur punya pilihan
     * kotanya sendiri, tidak dibagi.
     */
    fun setSelectedMarkazImsakiyahId(id: String?) {
        prefs.edit().putString(KEY_SELECTED_MARKAZ_IMSAKIYAH, id).apply()
    }

    fun getSelectedMarkazImsakiyahId(): String? = prefs.getString(KEY_SELECTED_MARKAZ_IMSAKIYAH, null)

    /** Sama seperti [setSelectedMarkazImsakiyahId]/[getSelectedMarkazImsakiyahId], untuk fitur "Waktu Sholat". */
    fun setSelectedMarkazWaktuSholatId(id: String?) {
        prefs.edit().putString(KEY_SELECTED_MARKAZ_WAKTU_SHOLAT, id).apply()
    }

    fun getSelectedMarkazWaktuSholatId(): String? = prefs.getString(KEY_SELECTED_MARKAZ_WAKTU_SHOLAT, null)

    /**
     * Koordinat terakhir yang dipakai Beranda (MainActivity) untuk jadwal sholat — dari GPS
     * maupun lokasi manual. Dipakai `AdzanRefreshWorker` sebagai cadangan kalau GPS tidak
     * bisa dibaca di background, supaya alarm adzan tetap mengacu ke lokasi yang tampil di
     * Beranda (bukan langsung jatuh ke Jakarta). `null` = Beranda belum pernah dapat lokasi.
     */
    fun setLastHomeLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(KEY_LAST_HOME_LAT, lat.toFloat())
            .putFloat(KEY_LAST_HOME_LNG, lng.toFloat())
            .apply()
    }

    fun getLastHomeLocation(): Pair<Double, Double>? {
        if (!prefs.contains(KEY_LAST_HOME_LAT) || !prefs.contains(KEY_LAST_HOME_LNG)) return null
        return prefs.getFloat(KEY_LAST_HOME_LAT, 0f).toDouble() to prefs.getFloat(KEY_LAST_HOME_LNG, 0f).toDouble()
    }

    /** Pengingat pra-adzan (mis. "10 menit lagi Dzuhur") — nonaktif by default (opt-in),
     *  berlaku untuk semua 5 waktu sholat wajib sekaligus (bukan per-waktu). */
    fun setPreAdzanReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRE_ADZAN_REMINDER_ENABLED, enabled).apply()
    }

    fun isPreAdzanReminderEnabled(): Boolean = prefs.getBoolean(KEY_PRE_ADZAN_REMINDER_ENABLED, false)

    fun setPreAdzanReminderMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_PRE_ADZAN_REMINDER_MINUTES, minutes).apply()
    }

    fun getPreAdzanReminderMinutes(): Int =
        prefs.getInt(KEY_PRE_ADZAN_REMINDER_MINUTES, DEFAULT_PRE_ADZAN_REMINDER_MINUTES)

    fun setLogin(isLoggedIn: Boolean) {
        val editor = prefs.edit()
        editor.putBoolean("IS_LOGGED_IN", isLoggedIn)
        editor.apply()
    }

    fun setUserName(username : String) {
        val editor = prefs.edit()
        editor.putString("USERNAME",username)
        editor.apply()
    }


    fun setEmail(email : String) {
        val editor = prefs.edit()
        editor.putString("EMAIL",email)
        editor.apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean("IS_LOGGED_IN", false)
    }

    fun getUserName(): String? = prefs.getString("USERNAME", null)

    fun getEmail(): String? = prefs.getString("EMAIL", null)

    fun setUserId(id: Int) {
        prefs.edit().putInt("USER_ID", id).apply()
    }

    fun getUserId(): Int = prefs.getInt("USER_ID", -1)

    /** Sanctum bearer token (lihat AuthController::userResponse di alkaukabaweb) - dipakai untuk
     * update_profile/change_password/delete_account, action publik (login/register/google_login)
     * tidak butuh ini. */
    fun setAuthToken(token: String) {
        prefs.edit().putString("AUTH_TOKEN", token).apply()
    }

    fun getAuthToken(): String? = prefs.getString("AUTH_TOKEN", null)

    fun setAvatarUrl(url: String?) {
        prefs.edit().putString("AVATAR_URL", url).apply()
    }

    fun getAvatarUrl(): String? = prefs.getString("AVATAR_URL", null)

    fun clearUserData() {
        prefs.edit()
            .remove("IS_LOGGED_IN")
            .remove("USERNAME")
            .remove("EMAIL")
            .remove("USER_ID")
            .remove("AUTH_TOKEN")
            .remove("AVATAR_URL")
            .apply()
    }
}