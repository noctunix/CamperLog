package app.restvolt.camperlog.ui.onboarding

import android.content.Context
import androidx.core.content.edit

/** Persist whether the first-run setup has already been completed. */
class IntroductionSettings(context: Context) {
    private val preferences = context.getSharedPreferences("introduction", Context.MODE_PRIVATE)

    var seen: Boolean
        get() = preferences.getBoolean(KEY_SEEN, false)
        set(value) {
            preferences.edit { putBoolean(KEY_SEEN, value) }
        }

    private companion object {
        const val KEY_SEEN = "seen"
    }
}
