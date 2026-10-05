package app.restvolt.camperlog.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderSettingsTest {

    @Test
    fun defaultsAndChangesSurviveNewSettingsInstance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            assertEquals(30, ReminderSettings(context).reminderLeadDays)
            assertEquals(12, ReminderSettings(context).oilChangeIntervalMonths)

            ReminderSettings(context).reminderLeadDays = 14
            ReminderSettings(context).oilChangeIntervalMonths = 6

            assertEquals(14, ReminderSettings(context).reminderLeadDays)
            assertEquals(6, ReminderSettings(context).oilChangeIntervalMonths)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
