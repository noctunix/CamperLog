package app.restvolt.camperlog.ui.settings

import android.content.Context
import androidx.core.content.edit
import app.restvolt.camperlog.data.TileHttpCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Schalter "Wetter & Karte (Internet)", aus bis der Nutzer ihn einschaltet. Die
 * Internet-Berechtigung selbst ist eine Install-Time-Berechtigung, die der Schalter nur in der
 * Nutzung einschränkt, nicht entzieht; beim Ausschalten wird aber der Kartenkachel-Cache
 * gelöscht, damit besuchte Gegenden nicht auf dem Gerät liegen bleiben.
 */
class WeatherSettings(context: Context, private val clearTileCache: () -> Unit = { TileHttpCache.clear(context) }) {
    private val preferences = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))

    /** Aktueller Wert des Schalters; Änderungen über [enabled] werden sofort sichtbar. */
    val values: StateFlow<Boolean> = state.asStateFlow()

    var enabled: Boolean
        get() = state.value
        set(value) {
            preferences.edit { putBoolean(KEY_ENABLED, value) }
            state.value = value
            if (!value) clearTileCache()
        }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
