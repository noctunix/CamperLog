package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Ein Punkt einer gestarteten Checkliste: Text, als Kopie der Vorlage zum Startzeitpunkt, und sein Haken. */
data class ChecklistItem(val text: String, val checked: Boolean = false)

/**
 * Eine gestartete Checkliste. [tourId] `null` bedeutet, dass sie zu keiner Tour gehört (z. B.
 * Einwintern); sonst ist [vehicleId] das Fahrzeug der Tour. [items] ist beim Start eine Kopie der
 * Vorlage, siehe [startChecklist].
 */
data class Checklist(
    val id: Long = 0,
    val uuid: String = "",
    val vehicleId: Long,
    val tourId: Long? = null,
    val title: String,
    val items: List<ChecklistItem> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Anzahl erledigter Punkte. */
val Checklist.checkedCount: Int get() = items.count(ChecklistItem::checked)

/** Ob alle Punkte erledigt sind; eine Checkliste ohne Punkte gilt nicht als erledigt. */
val Checklist.isComplete: Boolean get() = items.isNotEmpty() && items.all(ChecklistItem::checked)

/**
 * Zugriff auf gestartete Checklisten. Das Löschen eines Fahrzeugs löscht seine Checklisten über den
 * Fremdschlüssel mit, das Löschen einer Tour ebenso ihre.
 */
interface ChecklistRepository {
    /** Checklisten einer Tour, neueste zuerst. */
    fun observeForTour(tourId: Long): Flow<List<Checklist>>

    /** Checklisten eines Fahrzeugs ohne Tourbezug, neueste zuerst. */
    fun observeForVehicleWithoutTour(vehicleId: Long): Flow<List<Checklist>>

    fun observeChecklist(id: Long): Flow<Checklist?>

    /** Alle Checklisten, live aktualisiert, für die Volltextsuche. */
    fun observeAll(): Flow<List<Checklist>>

    /** Alle Checklisten, für den Sicherungs-Export. */
    suspend fun allChecklists(): List<Checklist>

    /** Legt [checklist] an, wenn seine id 0 ist, sonst wird sie aktualisiert. */
    suspend fun save(checklist: Checklist): Long

    /** Löscht die Checkliste mit [id]. */
    suspend fun delete(id: Long)

    /** Legt eine zuvor gelöschte [checklist] mit ihrer bisherigen id und ihren Zeitstempeln wieder an. */
    suspend fun restore(checklist: Checklist)

    /** Ordnet alle Checklisten von [tourId] dem Fahrzeug [vehicleId] zu, z. B. beim Wechsel des Tourfahrzeugs. */
    suspend fun moveTourToVehicle(tourId: Long, vehicleId: Long)
}

/** Startet [template] als neue Checkliste für [vehicleId] (und optional [tourId]); ihre Punkte sind eine Kopie der Vorlage. */
fun startChecklist(template: ChecklistTemplate, vehicleId: Long, tourId: Long? = null): Checklist = Checklist(
    vehicleId = vehicleId,
    tourId = tourId,
    title = template.name,
    items = template.items.map { ChecklistItem(text = it) },
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
