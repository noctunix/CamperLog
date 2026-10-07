package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Ein Tagebucheintrag zu einem Tag einer Tour, getrennt von den Notizen einzelner Stationen.
 * Je Tour und Tag gibt es höchstens einen Eintrag (siehe [DiaryEntryRepository]); [date] ist nicht
 * auf den Zeitraum der Tour beschränkt, da dieser nachträglich geändert werden kann.
 */
data class DiaryEntry(
    val id: Long = 0,
    val uuid: String = "",
    val tourId: Long,
    val date: LocalDate,
    val text: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Zugriff auf die Tagebucheinträge einer Tour; das Löschen einer Tour löscht ihre Einträge über den Fremdschlüssel mit. */
interface DiaryEntryRepository {
    /** Einträge einer Tour, aufsteigend nach Datum. */
    fun observeForTour(tourId: Long): Flow<List<DiaryEntry>>

    /** Alle Einträge aller Touren, live aktualisiert, für die Volltextsuche. */
    fun observeAllEntries(): Flow<List<DiaryEntry>>

    /** Alle Einträge aller Touren, für den Sicherungs-Export. */
    suspend fun allEntries(): List<DiaryEntry>

    /** Legt [entry] an, wenn seine id 0 ist, sonst wird er aktualisiert. */
    suspend fun save(entry: DiaryEntry): Long

    /** Löscht den Eintrag mit [id]. */
    suspend fun delete(id: Long)

    /** Legt einen zuvor gelöschten [entry] mit seiner bisherigen id und seinen Zeitstempeln wieder an. */
    suspend fun restore(entry: DiaryEntry)
}
