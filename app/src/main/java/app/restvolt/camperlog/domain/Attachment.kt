package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Woran ein Anhang hängt. */
enum class AttachmentOwnerType { STATION, REPAIR, LOG_ENTRY, VEHICLE_DOCUMENT }

/**
 * Eine Datei (Foto oder Dokument) zu einem Eintrag, referenziert über [ownerType] und [ownerId] statt
 * eines Fremdschlüssels je Zieltabelle - [ownerId] bedeutet je nach [ownerType] eine Stations-, Reparatur-,
 * Bordbuch- oder Fahrzeugdokument-id. [fileName] ist `<uuid>.<ext>` relativ zum Anhangs-Ordner
 * (`AttachmentFileStore.ATTACHMENTS_DIR`). Fotos sind nach dem Import auf höchstens 2048 px
 * herunterskalierte, EXIF-freie JPEGs mit gesetztem [width]/[height]; Dokumente werden unverändert mit
 * ihrem ursprünglichen [mimeType] übernommen und haben keine Abmessungen.
 */
data class Attachment(
    val id: Long = 0,
    val uuid: String = "",
    val ownerType: AttachmentOwnerType,
    val ownerId: Long,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val caption: String = "",
    val createdAt: Instant,
)

/**
 * Zugriff auf Anhänge. Da [Attachment.ownerId] je nach [AttachmentOwnerType] in eine andere Tabelle
 * zeigt, gibt es dafür keinen Datenbank-Fremdschlüssel; das Löschen eines Eintrags muss seine Anhänge
 * daher selbst mitnehmen (siehe die `delete`-Methoden von `RoomStationRepository`,
 * `RoomVehicleRepository`, `RoomLogRepository` und `RoomVehicleDocumentRepository`).
 *
 * [delete] entfernt nur die Datenbankzeile, nicht die Datei: Das erlaubt dasselbe
 * Rückgängigmachen-per-Snackbar-Muster wie bei Reparaturen und Stationen ([restore]). Die Datei selbst
 * räumt erst `AttachmentFileStore.sweepOrphanFiles` auf, und zwar erst, wenn sie seit mindestens einem
 * Tag zu keiner Zeile mehr gehört - das gibt einem späten Rückgängigmachen genug Zeit.
 */
interface AttachmentRepository {
    /** Anhänge eines Eintrags, älteste zuerst (Reihenfolge der Aufnahme). */
    fun observeForOwner(ownerType: AttachmentOwnerType, ownerId: Long): Flow<List<Attachment>>

    /** Wie [observeForOwner], aber einmalig; für den Sicherungs-Export. */
    suspend fun forOwner(ownerType: AttachmentOwnerType, ownerId: Long): List<Attachment>

    /** Alle Anhänge aller Einträge für den Sicherungs-Export. */
    suspend fun allAttachments(): List<Attachment>

    /** Legt [attachment] an und liefert seine neue id; die Datei muss bereits importiert sein. */
    suspend fun add(attachment: Attachment): Long

    /** Löscht die Datenbankzeile mit [id]; die Datei bleibt bis zum nächsten Aufräumlauf liegen. */
    suspend fun delete(id: Long)

    /** Legt eine zuvor gelöschte [attachment] mit ihrer bisherigen id wieder an. */
    suspend fun restore(attachment: Attachment)

    /** Löscht alle Anhänge eines Eintrags, z. B. weil der Eintrag selbst gelöscht wird. */
    suspend fun deleteForOwner(ownerType: AttachmentOwnerType, ownerId: Long)

    /** Wie [deleteForOwner], aber für mehrere Einträge derselben Art, z. B. alle Stationen einer Tour. */
    suspend fun deleteForOwners(ownerType: AttachmentOwnerType, ownerIds: Collection<Long>)

    /** Alle [Attachment.fileName] aller gespeicherten Anhänge, für den Aufräumlauf ohne Datenbankzugriff je Datei. */
    suspend fun allFileNames(): Set<String>
}
