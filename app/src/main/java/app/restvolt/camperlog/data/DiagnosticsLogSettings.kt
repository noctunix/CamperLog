package app.restvolt.camperlog.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Schalter "Diagnose-Protokoll" (aus bis der Nutzer ihn einschaltet): zeichnet zusätzliche,
 * ausführliche Diagnose-Einträge auf (aktuell nur Trackaufzeichnung), die sich in den
 * Einstellungen ansehen und teilen lassen. Absturzerfassung läuft davon unabhängig immer.
 */
class DiagnosticsLogSettings(context: Context) {
    private val preferences = context.getSharedPreferences("diagnostics_log", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))

    val enabled: StateFlow<Boolean> = state.asStateFlow()

    fun setEnabled(value: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, value) }
        state.value = value
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
