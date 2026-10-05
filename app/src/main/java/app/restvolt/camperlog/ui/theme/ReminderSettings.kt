package app.restvolt.camperlog.ui.theme

import android.content.Context
import androidx.core.content.edit

/** Persist reminder preferences; vehicles and logbook entries remain in Room. */
class ReminderSettings(context: Context) {
    private val preferences = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    var reminderLeadDays: Int
        get() = preferences.getInt(KEY_LEAD_DAYS, DEFAULT_LEAD_DAYS)
        set(value) {
            preferences.edit { putInt(KEY_LEAD_DAYS, value) }
        }

    var oilChangeIntervalMonths: Int
        get() = preferences.getInt(KEY_OIL_INTERVAL_MONTHS, DEFAULT_OIL_INTERVAL_MONTHS)
        set(value) {
            preferences.edit { putInt(KEY_OIL_INTERVAL_MONTHS, value) }
        }

    private companion object {
        const val KEY_LEAD_DAYS = "lead_days"
        const val KEY_OIL_INTERVAL_MONTHS = "oil_interval_months"
        const val DEFAULT_LEAD_DAYS = 30
        const val DEFAULT_OIL_INTERVAL_MONTHS = 12
    }
}
