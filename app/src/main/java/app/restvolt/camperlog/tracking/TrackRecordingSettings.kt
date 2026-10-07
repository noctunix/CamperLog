package app.restvolt.camperlog.tracking

import android.content.Context
import androidx.core.content.edit
import app.restvolt.camperlog.data.AndroidLocationPermissionRevoker
import app.restvolt.camperlog.data.LocationPermissionRevoker
import app.restvolt.camperlog.domain.TrackInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Einstellungen der Trackaufzeichnung. */
data class TrackRecordingPreferences(
    val enabled: Boolean = false,
    val interval: TrackInterval = TrackInterval.DEFAULT,
    val fasterWhileCharging: Boolean = false,
)

/** Laufende Aufzeichnung: Tour und Segment, in das neue Punkte geschrieben werden. */
data class ActiveRecording(val tourId: Long, val segment: Int)

/**
 * Schalter "Trackaufzeichnung" (aus bis der Nutzer ihn einschaltet), Intervall, Ladeoption und die
 * gerade laufende Aufzeichnung. Die laufende Aufzeichnung wird gespeichert, damit der Dienst nach
 * einem Neustart der App weiß, wohin er schreibt.
 *
 * Beim Ausschalten entzieht [revoker] die Standortberechtigung, aber nur wenn auch der Schalter
 * "Standort" aus ist ([locationEnabled]).
 */
class TrackRecordingSettings(
    context: Context,
    private val revoker: LocationPermissionRevoker = AndroidLocationPermissionRevoker(context),
    private val locationEnabled: () -> Boolean = { isLocationSwitchOn(context) },
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())
    private val activeState = MutableStateFlow(readActive())

    val values: StateFlow<TrackRecordingPreferences> = state.asStateFlow()

    /** Gerade laufende Aufzeichnung oder `null`. */
    val active: StateFlow<ActiveRecording?> = activeState.asStateFlow()

    var enabled: Boolean
        get() = state.value.enabled
        set(value) {
            preferences.edit { putBoolean(KEY_ENABLED, value) }
            state.value = state.value.copy(enabled = value)
            if (!value) {
                activeRecording = null
                if (!locationEnabled()) revoker.revokeOnKill()
            }
        }

    var interval: TrackInterval
        get() = state.value.interval
        set(value) {
            preferences.edit { putString(KEY_INTERVAL, value.storageValue) }
            state.value = state.value.copy(interval = value)
        }

    var fasterWhileCharging: Boolean
        get() = state.value.fasterWhileCharging
        set(value) {
            preferences.edit { putBoolean(KEY_FASTER_WHILE_CHARGING, value) }
            state.value = state.value.copy(fasterWhileCharging = value)
        }

    /** Ob der einmalige Hinweis zur Akkuoptimierung schon gezeigt wurde. */
    var batteryHintShown: Boolean
        get() = preferences.getBoolean(KEY_BATTERY_HINT_SHOWN, false)
        set(value) {
            preferences.edit { putBoolean(KEY_BATTERY_HINT_SHOWN, value) }
        }

    var activeRecording: ActiveRecording?
        get() = activeState.value
        set(value) {
            preferences.edit {
                if (value == null) {
                    remove(KEY_ACTIVE_TOUR)
                    remove(KEY_ACTIVE_SEGMENT)
                } else {
                    putLong(KEY_ACTIVE_TOUR, value.tourId)
                    putInt(KEY_ACTIVE_SEGMENT, value.segment)
                }
            }
            activeState.value = value
        }

    /** Liest die gespeicherten Werte neu, z. B. wenn eine andere Instanz sie geändert hat. */
    fun reload() {
        state.value = read()
        activeState.value = readActive()
    }

    private fun read() = TrackRecordingPreferences(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        interval = TrackInterval.parse(preferences.getString(KEY_INTERVAL, null)),
        fasterWhileCharging = preferences.getBoolean(KEY_FASTER_WHILE_CHARGING, false),
    )

    private fun readActive(): ActiveRecording? {
        if (!preferences.contains(KEY_ACTIVE_TOUR)) return null
        return ActiveRecording(preferences.getLong(KEY_ACTIVE_TOUR, 0), preferences.getInt(KEY_ACTIVE_SEGMENT, 1))
    }

    companion object {
        const val PREFERENCES_NAME = "track_recording"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_FASTER_WHILE_CHARGING = "faster_while_charging"
        private const val KEY_BATTERY_HINT_SHOWN = "battery_hint_shown"
        private const val KEY_ACTIVE_TOUR = "active_tour_id"
        private const val KEY_ACTIVE_SEGMENT = "active_segment"

        @Volatile
        private var shared: TrackRecordingSettings? = null

        /** Gemeinsame Instanz für Oberfläche und Aufzeichnungsdienst, damit beide denselben Zustand sehen. */
        fun get(context: Context): TrackRecordingSettings =
            shared ?: synchronized(this) { shared ?: TrackRecordingSettings(context.applicationContext).also { shared = it } }

        /** Ob der Schalter "Trackaufzeichnung" an ist, ohne eine Instanz anzulegen. */
        fun isSwitchOn(context: Context): Boolean =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        private fun isLocationSwitchOn(context: Context): Boolean =
            context.getSharedPreferences("location", Context.MODE_PRIVATE).getBoolean("enabled", false)
    }
}
