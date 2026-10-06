package app.restvolt.camperlog.ui.settings

import android.content.Context
import androidx.core.content.edit
import app.restvolt.camperlog.data.AndroidLocationPermissionRevoker
import app.restvolt.camperlog.data.LocationPermissionRevoker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Schalter "Standort" (6.11), aus bis der Nutzer ihn einschaltet (13). Beim Ausschalten entzieht
 * [revoker] die Berechtigung (6.7, 9); [approximateHintShown] merkt den einmaligen Hinweis zu einem
 * nur ungefähren Standort (6.7).
 */
class LocationSettings(context: Context, private val revoker: LocationPermissionRevoker = AndroidLocationPermissionRevoker(context)) {
    private val preferences = context.getSharedPreferences("location", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))

    /** Aktueller Wert des Schalters; Änderungen über [enabled] werden sofort sichtbar. */
    val values: StateFlow<Boolean> = state.asStateFlow()

    var enabled: Boolean
        get() = state.value
        set(value) {
            preferences.edit { putBoolean(KEY_ENABLED, value) }
            state.value = value
            if (!value) revoker.revokeOnKill()
        }

    /** Ob der einmalige Hinweis zu einem nur ungefähren Standort schon gezeigt wurde. */
    var approximateHintShown: Boolean
        get() = preferences.getBoolean(KEY_APPROXIMATE_HINT_SHOWN, false)
        set(value) {
            preferences.edit { putBoolean(KEY_APPROXIMATE_HINT_SHOWN, value) }
        }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_APPROXIMATE_HINT_SHOWN = "approximate_hint_shown"
    }
}
