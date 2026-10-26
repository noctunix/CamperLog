package app.restvolt.camperlog.ui.settings

import android.content.Context
import androidx.core.content.edit
import app.restvolt.camperlog.domain.LevelAudioScheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistiert, ob die akustische Rückmeldung der Wasserwaage aktiv ist (Default aus) und welche
 * Richtungslogik ([LevelAudioScheme]) dabei verwendet wird (Default [LevelAudioScheme.DOMINANT_AXIS]).
 */
class LevelAudioSettings(context: Context) {
    private val preferences = context.getSharedPreferences("level_audio", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))
    val values: StateFlow<Boolean> = state.asStateFlow()
    var enabled: Boolean
        get() = state.value
        set(value) { preferences.edit { putBoolean(KEY_ENABLED, value) }; state.value = value }

    private val schemeState = MutableStateFlow(
        preferences.getString(KEY_SCHEME, null)?.let { runCatching { LevelAudioScheme.valueOf(it) }.getOrNull() }
            ?: LevelAudioScheme.DOMINANT_AXIS,
    )
    val schemeValues: StateFlow<LevelAudioScheme> = schemeState.asStateFlow()
    var scheme: LevelAudioScheme
        get() = schemeState.value
        set(value) { preferences.edit { putString(KEY_SCHEME, value.name) }; schemeState.value = value }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_SCHEME = "scheme"
    }
}
