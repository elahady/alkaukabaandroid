package site.elahady.alkaukaba

import android.app.Application
import site.elahady.alkaukaba.utils.ThemePrefs

class AlKaukabaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Harus dipanggil sebelum activity manapun dibuat, supaya tidak ada kedipan tema
        // salah sesaat sebelum preferensi ter-apply.
        ThemePrefs.applySavedMode(this)
    }
}
