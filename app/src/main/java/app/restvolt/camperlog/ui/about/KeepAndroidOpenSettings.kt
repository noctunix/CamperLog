package app.restvolt.camperlog.ui.about

import android.content.Context
import androidx.core.content.edit
import app.restvolt.camperlog.domain.KeepAndroidOpenState
import java.time.Clock
import java.time.Instant

/** Persist the "Keep Android Open" hint's state; [clock] is injectable for tests. */
class KeepAndroidOpenSettings(context: Context, private val clock: Clock = Clock.systemUTC()) {
    private val preferences = context.getSharedPreferences("keep_android_open", Context.MODE_PRIVATE)

    /** Aktueller Stand für [app.restvolt.camperlog.domain.shouldShowKeepAndroidOpen]. */
    val state: KeepAndroidOpenState
        get() = KeepAndroidOpenState(
            firstLaunchAt = preferences.getLong(KEY_FIRST_LAUNCH_AT, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
            lastShownAt = preferences.getLong(KEY_LAST_SHOWN_AT, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
            supported = preferences.getBoolean(KEY_SUPPORTED, false),
        )

    /** Setzt [KeepAndroidOpenState.firstLaunchAt] einmalig; spätere Aufrufe lassen ihn unverändert. */
    fun recordFirstLaunchIfNeeded() {
        if (!preferences.contains(KEY_FIRST_LAUNCH_AT)) {
            preferences.edit { putLong(KEY_FIRST_LAUNCH_AT, clock.millis()) }
        }
    }

    /** Merkt sich, dass der Hinweis gerade angezeigt wurde, unabhängig vom Ausgang. */
    fun markShown() {
        preferences.edit { putLong(KEY_LAST_SHOWN_AT, clock.millis()) }
    }

    /** Merkt sich, dass die Petition unterstützt wurde; der Hinweis erscheint danach nie wieder. */
    fun markSupported() {
        preferences.edit { putBoolean(KEY_SUPPORTED, true) }
    }

    private companion object {
        const val KEY_FIRST_LAUNCH_AT = "first_launch_at"
        const val KEY_LAST_SHOWN_AT = "last_shown_at"
        const val KEY_SUPPORTED = "supported"
    }
}
