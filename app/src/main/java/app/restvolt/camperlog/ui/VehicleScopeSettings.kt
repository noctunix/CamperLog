package app.restvolt.camperlog.ui

import android.content.Context
import androidx.core.content.edit

/**
 * Persistiert die Ansichtseinstellung "Alle Fahrzeuge", die sich Touren- und Stationen-Reiter
 * teilen (2.4); das aktuelle Fahrzeug selbst liegt in Room.
 */
class VehicleScopeSettings(context: Context) {
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
