package app.restvolt.camperlog.ui.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/** Standard-Vorlauf der Sicherungs-Erinnerung in Wochen (ROADMAP.md 1.9.0); `0` bedeutet "aus". */
const val DEFAULT_BACKUP_REMINDER_WEEKS = 4

/**
 * Einstellungen und Zustand der Sicherungs-Erinnerung und des Sicherungsordners (1.9.0).
 * [lastNotifiedBackupBaseline] wird nur vom täglichen Hintergrund-Check gelesen/geschrieben, siehe
 * [app.restvolt.camperlog.domain.shouldNotifyBackupOverdue].
 */
data class BackupPreferences(
    val reminderWeeks: Int = DEFAULT_BACKUP_REMINDER_WEEKS,
    val lastBackupAt: Instant? = null,
    val lastNotifiedBackupBaseline: Instant? = null,
    val folderUri: String? = null,
    val autoBackupToFolder: Boolean = false,
)

/** Persistiert [BackupPreferences]; siehe [app.restvolt.camperlog.ui.theme.ReminderSettings] für das gleiche Muster. */
class BackupSettings(context: Context) {
    private val preferences = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(load())

    /** Aktuelle Werte; Änderungen über die Setter dieser Instanz werden sofort sichtbar. */
    val values: StateFlow<BackupPreferences> = state.asStateFlow()

    var reminderWeeks: Int
        get() = state.value.reminderWeeks
        set(value) {
            preferences.edit { putInt(KEY_REMINDER_WEEKS, value) }
            state.update { it.copy(reminderWeeks = value) }
        }

    var folderUri: String?
        get() = state.value.folderUri
        set(value) {
            preferences.edit { if (value != null) putString(KEY_FOLDER_URI, value) else remove(KEY_FOLDER_URI) }
            state.update { it.copy(folderUri = value) }
        }

    var autoBackupToFolder: Boolean
        get() = state.value.autoBackupToFolder
        set(value) {
            preferences.edit { putBoolean(KEY_AUTO_BACKUP, value) }
            state.update { it.copy(autoBackupToFolder = value) }
        }

    var lastBackupAt: Instant?
        get() = state.value.lastBackupAt
        set(value) {
            preferences.edit { if (value != null) putLong(KEY_LAST_BACKUP, value.toEpochMilli()) else remove(KEY_LAST_BACKUP) }
            state.update { it.copy(lastBackupAt = value) }
        }

    var lastNotifiedBackupBaseline: Instant?
        get() = state.value.lastNotifiedBackupBaseline
        set(value) {
            preferences.edit { if (value != null) putLong(KEY_LAST_NOTIFIED_BASELINE, value.toEpochMilli()) else remove(KEY_LAST_NOTIFIED_BASELINE) }
            state.update { it.copy(lastNotifiedBackupBaseline = value) }
        }

    /** Nach jeder erfolgreichen Sicherung aufgerufen (jeder Exportweg), um [lastBackupAt] zu setzen. */
    fun recordBackupMade(at: Instant) {
        lastBackupAt = at
    }

    private fun load(): BackupPreferences = BackupPreferences(
        reminderWeeks = preferences.getInt(KEY_REMINDER_WEEKS, DEFAULT_BACKUP_REMINDER_WEEKS),
        lastBackupAt = preferences.getLong(KEY_LAST_BACKUP, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
        lastNotifiedBackupBaseline = preferences.getLong(KEY_LAST_NOTIFIED_BASELINE, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
        folderUri = preferences.getString(KEY_FOLDER_URI, null),
        autoBackupToFolder = preferences.getBoolean(KEY_AUTO_BACKUP, false),
    )

    private companion object {
        const val KEY_REMINDER_WEEKS = "reminder_weeks"
        const val KEY_LAST_BACKUP = "last_backup_millis"
        const val KEY_LAST_NOTIFIED_BASELINE = "last_notified_baseline_millis"
        const val KEY_FOLDER_URI = "folder_uri"
        const val KEY_AUTO_BACKUP = "auto_backup_to_folder"
    }
}
