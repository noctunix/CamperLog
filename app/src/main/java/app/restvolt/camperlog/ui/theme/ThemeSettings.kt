package app.restvolt.camperlog.ui.theme

import android.content.Context
import androidx.core.content.edit

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** Persist only appearance; tours and currency settings remain in Room. */
class ThemeSettings(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    var mode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == preferences.getString("theme_mode", null) } ?: ThemeMode.SYSTEM
        set(value) {
            preferences.edit { putString("theme_mode", value.name) }
        }
}
