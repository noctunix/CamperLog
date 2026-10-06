package app.restvolt.camperlog.ui.tours

import android.content.Context
import androidx.core.content.edit

/** Persist the tours-only "all vehicles" view filter; the current vehicle itself lives in Room. */
class ToursFilterSettings(context: Context) {
    private val preferences = context.getSharedPreferences("tours_filter", Context.MODE_PRIVATE)

    var allVehicles: Boolean
        get() = preferences.getBoolean(KEY_ALL_VEHICLES, false)
        set(value) {
            preferences.edit { putBoolean(KEY_ALL_VEHICLES, value) }
        }

    private companion object {
        const val KEY_ALL_VEHICLES = "all_vehicles"
    }
}
