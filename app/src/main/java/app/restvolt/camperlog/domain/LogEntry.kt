package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Art des Bordbuch-Eintrags. */
enum class LogType { CASSETTE_EMPTIED, GREY_WATER_EMPTIED, DIESEL_HEATER_RUN, GAS_HEATER_RUN, GAS_BOTTLE_SWAPPED }

/**
 * Ein Bordbuch-Eintrag. Nach dem Anlegen unveränderlich, mit einer Ausnahme: [stationId] folgt der
 * Verknüpfung mit einer Station (4), also dem Setzen/Lösen des Häkchens, einem Verschieben von Datum
 * oder Fahrzeug (dort als Löschen und Neuanlegen mit derselben uuid) und dem Fremdschlüssel
 * `ON DELETE SET NULL` beim Löschen der Station.
 */
data class LogEntry(
    val id: Long = 0,
    val uuid: String = "",
    val vehicleId: Long,
    val type: LogType,
    val date: LocalDate,
    val createdAt: Instant,
    val stationId: Long? = null,
)

/** Zugriff auf das Bordbuch eines Fahrzeugs. */
interface LogRepository {

    /** Liefert je [LogType] das jüngste Datum eines Fahrzeugs; fehlende Arten sind nicht enthalten. */
    fun observeLatest(vehicleId: Long): Flow<Map<LogType, LocalDate>>

    /** Liefert die Einträge eines Fahrzeugs und einer Art, neueste zuerst. */
    fun observeEntries(vehicleId: Long, type: LogType): Flow<List<LogEntry>>

    /** Liefert alle Einträge aller Fahrzeuge für den Sicherungs-Export. */
    suspend fun allEntries(): List<LogEntry>

    /** Ob mindestens ein Bordbuch-Eintrag gespeichert ist. */
    suspend fun hasEntries(): Boolean

    /** Legt einen neuen Eintrag an. */
    suspend fun add(vehicleId: Long, type: LogType, date: LocalDate): LogEntry

    /** Löscht den Eintrag mit [id]. */
    suspend fun delete(id: Long)

    /** Legt einen zuvor gelöschten [entry] mit seiner bisherigen id wieder an. */
    suspend fun restore(entry: LogEntry)

    /** Liefert den mit [stationId] und [type] verknüpften Eintrag, oder `null` (4). */
    suspend fun linkedEntry(stationId: Long, type: LogType): LogEntry?

    /** Liefert einen noch unverknüpften Eintrag von [vehicleId]/[type]/[date], für die Dublettenprüfung (4.1). */
    suspend fun findUnlinked(vehicleId: Long, type: LogType, date: LocalDate): LogEntry?

    /** Verknüpft den Eintrag [entryId] mit [stationId] (4.1, 8.3). */
    suspend fun link(entryId: Long, stationId: Long)

    /** Legt einen neuen, mit [stationId] verknüpften Eintrag an; [uuid] stammt von einer verschobenen Station, falls gesetzt (4.3). */
    suspend fun addLinked(vehicleId: Long, type: LogType, date: LocalDate, stationId: Long, uuid: String? = null): LogEntry
}
