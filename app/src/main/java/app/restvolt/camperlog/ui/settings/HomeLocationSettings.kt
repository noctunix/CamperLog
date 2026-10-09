package app.restvolt.camperlog.ui.settings

import android.content.Context
import androidx.core.content.edit
import app.restvolt.camperlog.domain.CoordinateSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Zuhause-Ort für die automatische Start-/Ende-Station neuer Touren. [latitude]/[longitude] `null`
 * bedeutet "kein Zuhause gesetzt"; ein leerer [name] verwendet beim Anlegen der Station einen Standardnamen.
 */
data class HomeLocationPreferences(
    val name: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coordinateSource: CoordinateSource? = null,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

/** Persistiert [HomeLocationPreferences]; siehe [app.restvolt.camperlog.ui.data.BackupSettings] für das gleiche Muster. */
class HomeLocationSettings(context: Context) {
    private val preferences = context.getSharedPreferences("home_location", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(load())

    /** Aktuelle Werte; Änderungen über die Setter dieser Instanz werden sofort sichtbar. */
    val values: StateFlow<HomeLocationPreferences> = state.asStateFlow()

    var name: String
        get() = state.value.name
        set(value) {
            preferences.edit { putString(KEY_NAME, value) }
            state.update { it.copy(name = value) }
        }

    /** Setzt Koordinate und Quelle zusammen; `null` für alle drei löscht das Zuhause wieder. */
    fun setLocation(latitude: Double?, longitude: Double?, source: CoordinateSource?) {
        preferences.edit {
            if (latitude != null) putString(KEY_LATITUDE, latitude.toString()) else remove(KEY_LATITUDE)
            if (longitude != null) putString(KEY_LONGITUDE, longitude.toString()) else remove(KEY_LONGITUDE)
            if (source != null) putString(KEY_SOURCE, source.name) else remove(KEY_SOURCE)
        }
        state.update { it.copy(latitude = latitude, longitude = longitude, coordinateSource = source) }
    }

    private fun load(): HomeLocationPreferences = HomeLocationPreferences(
        name = preferences.getString(KEY_NAME, "").orEmpty(),
        latitude = preferences.getString(KEY_LATITUDE, null)?.toDoubleOrNull(),
        longitude = preferences.getString(KEY_LONGITUDE, null)?.toDoubleOrNull(),
        coordinateSource = preferences.getString(KEY_SOURCE, null)?.let { raw -> CoordinateSource.entries.firstOrNull { it.name == raw } },
    )

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_SOURCE = "source"
    }
}
