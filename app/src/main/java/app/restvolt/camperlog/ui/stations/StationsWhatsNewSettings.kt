package app.restvolt.camperlog.ui.stations

import android.content.Context
import androidx.core.content.edit

/** Persistiert, ob der Hinweiskarte "Neu: Stationen" auf dem Stationen-Reiter bereits geschlossen wurde (6.3). */
class StationsWhatsNewSettings(context: Context) {
    private val preferences = context.getSharedPreferences("stations_whats_new", Context.MODE_PRIVATE)

    var dismissed: Boolean
        get() = preferences.getBoolean(KEY_DISMISSED, false)
        set(value) {
            preferences.edit { putBoolean(KEY_DISMISSED, value) }
        }

    private companion object {
        const val KEY_DISMISSED = "dismissed"
    }
}
