package app.restvolt.camperlog.ui.stations

import android.content.Context
import androidx.core.content.edit

/** Ob die Hinweiskarte „Neu: Stationen“ ansteht: nur nach einem Update, das bestehende Touren umgebaut hat, bis sie geschlossen wird. */
class StationsWhatsNewSettings(context: Context) {
    private val preferences = context.getSharedPreferences("stations_whats_new", Context.MODE_PRIVATE)

    var pending: Boolean
        get() = preferences.getBoolean(KEY_PENDING, false)
        set(value) {
            preferences.edit { putBoolean(KEY_PENDING, value) }
        }

    private companion object {
        const val KEY_PENDING = "pending"
    }
}
