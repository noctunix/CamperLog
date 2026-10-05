package app.restvolt.camperlog.ui.theme

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Persist reminder preferences; vehicles and logbook entries remain in Room. */
class ReminderSettings(context: Context) {
    private val preferences = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(
        ReminderPreferences(
            leadDays = preferences.getInt(KEY_LEAD_DAYS, DEFAULT_LEAD_DAYS),
            oilChangeIntervalMonths = preferences.getInt(KEY_OIL_INTERVAL_MONTHS, DEFAULT_OIL_INTERVAL_MONTHS),
        ),
    )

    /** Aktuelle Werte; Änderungen über die Setter dieser Instanz werden sofort sichtbar. */
    val values: StateFlow<ReminderPreferences> = state.asStateFlow()

    var reminderLeadDays: Int
        get() = state.value.leadDays
        set(value) {
            preferences.edit { putInt(KEY_LEAD_DAYS, value) }
            state.update { it.copy(leadDays = value) }
        }

    var oilChangeIntervalMonths: Int
        get() = state.value.oilChangeIntervalMonths
        set(value) {
            preferences.edit { putInt(KEY_OIL_INTERVAL_MONTHS, value) }
            state.update { it.copy(oilChangeIntervalMonths = value) }
        }

    private companion object {
        const val KEY_LEAD_DAYS = "lead_days"
        const val KEY_OIL_INTERVAL_MONTHS = "oil_interval_months"
        const val DEFAULT_LEAD_DAYS = 30
        const val DEFAULT_OIL_INTERVAL_MONTHS = 12
    }
}

/** Vorlauf in Tagen für fällige Termine und Intervall für den Ölwechsel in Monaten. */
data class ReminderPreferences(val leadDays: Int, val oilChangeIntervalMonths: Int)
