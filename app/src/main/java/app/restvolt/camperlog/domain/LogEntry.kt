package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Art des Bordbuch-Eintrags. */
enum class LogType { CASSETTE_EMPTIED, GREY_WATER_EMPTIED, DIESEL_HEATER_RUN, GAS_HEATER_RUN }

/** Ein Bordbuch-Eintrag; unveränderlich nach dem Anlegen. */
data class LogEntry(
    val id: Long = 0,
    val uuid: String = "",
    val vehicleId: Long,
    val type: LogType,
    val date: LocalDate,
    val createdAt: Instant,
)

/** Zugriff auf das Bordbuch eines Fahrzeugs. */
interface LogRepository {

    /** Liefert je [LogType] das jüngste Datum eines Fahrzeugs; fehlende Arten sind nicht enthalten. */
    fun observeLatest(vehicleId: Long): Flow<Map<LogType, LocalDate>>

    /** Liefert die Einträge eines Fahrzeugs und einer Art, neueste zuerst. */
    fun observeEntries(vehicleId: Long, type: LogType): Flow<List<LogEntry>>

    /** Liefert alle Einträge aller Fahrzeuge für den Sicherungs-Export. */
    suspend fun allEntries(): List<LogEntry>

    /** Legt einen neuen Eintrag an. */
    suspend fun add(vehicleId: Long, type: LogType, date: LocalDate): LogEntry

    /** Löscht den Eintrag mit [id]. */
    suspend fun delete(id: Long)

    /** Legt einen zuvor gelöschten [entry] mit seiner bisherigen id wieder an. */
    suspend fun restore(entry: LogEntry)
}
