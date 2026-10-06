package app.restvolt.camperlog.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.reminders.ReminderWorkScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeReminderWorkScheduler : ReminderWorkScheduler {
    var enqueueCalls = 0
    var cancelCalls = 0

    override fun enqueue() {
        enqueueCalls++
    }

    override fun cancel() {
        cancelCalls++
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationSettingsTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun clear() {
        context.getSharedPreferences("notifications", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultsToOffAndPersistsAcrossInstances() {
        clear()
        try {
            assertFalse(NotificationSettings(context, FakeReminderWorkScheduler()).enabled)

            NotificationSettings(context, FakeReminderWorkScheduler()).enabled = true

            assertTrue(NotificationSettings(context, FakeReminderWorkScheduler()).enabled)
        } finally {
            clear()
        }
    }

    @Test
    fun settingToOn_enqueuesTheWork() {
        clear()
        try {
            val scheduler = FakeReminderWorkScheduler()
            val settings = NotificationSettings(context, scheduler)

            settings.enabled = true

            assertEquals(1, scheduler.enqueueCalls)
            assertEquals(0, scheduler.cancelCalls)
            assertTrue(settings.values.value)
        } finally {
            clear()
        }
    }

    @Test
    fun settingToOff_cancelsTheWork() {
        clear()
        try {
            val scheduler = FakeReminderWorkScheduler()
            val settings = NotificationSettings(context, scheduler)
            settings.enabled = true

            settings.enabled = false

            assertEquals(1, scheduler.cancelCalls)
            assertFalse(settings.values.value)
        } finally {
            clear()
        }
    }

    @Test
    fun construction_whileEnabled_reEnqueuesTheWork() {
        clear()
        try {
            NotificationSettings(context, FakeReminderWorkScheduler()).enabled = true

            val scheduler = FakeReminderWorkScheduler()
            NotificationSettings(context, scheduler)

            assertEquals(1, scheduler.enqueueCalls)
        } finally {
            clear()
        }
    }

    @Test
    fun construction_whileDisabled_doesNotEnqueue() {
        clear()
        try {
            val scheduler = FakeReminderWorkScheduler()
            NotificationSettings(context, scheduler)

            assertEquals(0, scheduler.enqueueCalls)
        } finally {
            clear()
        }
    }
}
