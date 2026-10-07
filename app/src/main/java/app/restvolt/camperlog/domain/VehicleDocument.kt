package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Art eines Fahrzeugdokuments. */
enum class DocumentKind { REGISTRATION, INSURANCE, WARRANTY, INSPECTION_REPORT, MANUAL, OTHER }

/**
 * Ein Dokument eines Fahrzeugs (Fahrzeugschein, Versicherungspolice, Garantie, Prüfbericht, Anleitung, …).
 * Seine Dateien (z. B. Vorder- und Rückseite eines Scans) sind [Attachment]s mit
 * `ownerType == VEHICLE_DOCUMENT` und `ownerId == id`, siehe `AttachmentRepository`.
 */
data class VehicleDocument(
    val id: Long = 0,
    val uuid: String = "",
    val vehicleId: Long,
    val kind: DocumentKind,
    val title: String,
    val expiryDate: LocalDate? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Zugriff auf die Dokumente eines Fahrzeugs; das Löschen eines Fahrzeugs löscht seine Dokumente über den Fremdschlüssel mit. */
interface VehicleDocumentRepository {
    /** Dokumente eines Fahrzeugs, nächster Ablauf zuerst, Dokumente ohne Ablaufdatum zuletzt. */
    fun observeForVehicle(vehicleId: Long): Flow<List<VehicleDocument>>

    /** Alle Dokumente aller Fahrzeuge, live aktualisiert, für die Volltextsuche. */
    fun observeAllDocuments(): Flow<List<VehicleDocument>>

    /** Alle Dokumente aller Fahrzeuge, für den Sicherungs-Export und den täglichen Erinnerungs-Check. */
    suspend fun allDocuments(): List<VehicleDocument>

    /** Legt [document] an, wenn seine id 0 ist, sonst wird es aktualisiert. */
    suspend fun save(document: VehicleDocument): Long

    /** Löscht das Dokument mit [id]; seine Anhänge werden mitgelöscht (siehe `AttachmentRepository`). */
    suspend fun delete(id: Long)

    /** Legt ein zuvor gelöschtes [document] mit seiner bisherigen id wieder an. */
    suspend fun restore(document: VehicleDocument)
}
