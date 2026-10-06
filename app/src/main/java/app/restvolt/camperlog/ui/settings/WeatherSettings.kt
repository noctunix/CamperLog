package app.restvolt.camperlog.ui.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Schalter "Wetter & Karte (Internet)" (6.11), aus bis der Nutzer ihn einschaltet (13). Anders als
 * [LocationSettings] gibt es beim Ausschalten nichts zurückzunehmen: die Internet-Berechtigung ist
 * eine Install-Time-Berechtigung, die der Schalter nur in der Nutzung einschränkt, nicht entzieht (9).
 */
class WeatherSettings(context: Context) {
    private val preferences = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))

    /** Aktueller Wert des Schalters; Änderungen über [enabled] werden sofort sichtbar. */
    val values: StateFlow<Boolean> = state.asStateFlow()

    var enabled: Boolean
        get() = state.value
        set(value) {
            preferences.edit { putBoolean(KEY_ENABLED, value) }
            state.value = value
        }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
