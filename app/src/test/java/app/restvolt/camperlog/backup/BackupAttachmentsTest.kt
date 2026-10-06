package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Currency

/** Vehicle-Dokumente und Anhänge im JSON-Teil einer Sicherung: Rundgang und Validierung (siehe `BackupTest` für den Rest des Formats). */
class BackupAttachmentsTest {

    private val nok = Currency.getInstance("NOK")
    private val vehicleUuid = "2b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val documentUuid = "6b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val photoFileName = "a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1.jpg"
    private val zipPath = "Documents/Bluebird/Fahrzeugschein.jpg"

    private fun vehicle() = Vehicle(uuid = vehicleUuid, name = "Bluebird", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun document() = VehicleDocument(
        uuid = documentUuid,
        vehicleId = 0,
        kind = DocumentKind.REGISTRATION,
        title = "Fahrzeugschein",
        expiryDate = LocalDate.of(2030, 1, 1),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun attachment(fileName: String = photoFileName) = Attachment(
        uuid = "c3c3c3c3-c3c3-4c3c-8c3c-c3c3c3c3c3c3",
        ownerType = AttachmentOwnerType.VEHICLE_DOCUMENT,
        ownerId = 0,
        fileName = fileName,
        mimeType = "image/jpeg",
        sizeBytes = 2048,
        width = 800,
        height = 600,
        latitude = 47.5,
        longitude = 11.0,
        takenAt = LocalDateTime.of(2026, 5, 3, 14, 22, 1),
        caption = "Vorderseite",
        createdAt = Instant.EPOCH,
    )

    private fun tour() = Tour(
        uuid = "0b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60",
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 1),
        destination = "Ostsee",
        tourType = TourType.DAY_TRIP,
        travelDays = 1,
        overnightStays = 0,
        distanceKm = 50,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun backup(attachments: List<Attachment> = listOf(attachment())) = Backup(
        exportedAt = Instant.parse("2026-10-06T12:00:00Z"),
        mainCurrency = nok,
        rates = emptyList(),
        tours = listOf(tour()),
        tourVehicleUuid = mapOf(tour().uuid to vehicleUuid),
        vehicles = listOf(BackupVehicle(vehicle(), emptyList(), emptyList())),
        documents = listOf(BackupVehicleDocument(document(), vehicleUuid)),
        attachments = attachments.mapIndexed { index, attachment ->
            BackupAttachment(attachment, documentUuid, if (index == 0) zipPath else "Documents/Bluebird/Fahrzeugschein $index.jpg")
        },
    )

    private fun success(text: String) = (decodeBackup(text) as BackupReadResult.Success).backup
    private fun failure(text: String) = decodeBackup(text) as? BackupReadResult.Failure

    @Test
    fun roundTrip_keepsDocumentAndAttachment() {
        val decoded = success(encodeBackup(backup()))

        val document = decoded.documents.single()
        assertEquals("Fahrzeugschein", document.document.title)
        assertEquals(DocumentKind.REGISTRATION, document.document.kind)
        assertEquals(LocalDate.of(2030, 1, 1), document.document.expiryDate)
        assertEquals(vehicleUuid, document.vehicleUuid)

        val attachment = decoded.attachments.single()
        assertEquals(AttachmentOwnerType.VEHICLE_DOCUMENT, attachment.attachment.ownerType)
        assertEquals("Vorderseite", attachment.attachment.caption)
        assertEquals(documentUuid, attachment.ownerUuid)
        assertEquals(47.5, attachment.attachment.latitude)
        assertEquals(11.0, attachment.attachment.longitude)
        assertEquals(LocalDateTime.of(2026, 5, 3, 14, 22, 1), attachment.attachment.takenAt)
        assertEquals(zipPath, attachment.zipPath)
    }

    @Test
    fun attachmentWithOnlyLatitude_isRejected() {
        val broken = backup(listOf(attachment().copy(longitude = null)))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun attachmentWithOutOfRangeLatitude_isRejected() {
        val broken = backup(listOf(attachment().copy(latitude = 95.0)))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun attachmentWithoutCoordinatesOrTakenAt_isAccepted() {
        val decoded = success(encodeBackup(backup(listOf(attachment().copy(latitude = null, longitude = null, takenAt = null)))))

        val attachment = decoded.attachments.single().attachment
        assertNull(attachment.latitude)
        assertNull(attachment.takenAt)
    }

    @Test
    fun attachmentWithZipPathEqualToBackupJson_isRejected() {
        val broken = backup().copy(attachments = listOf(BackupAttachment(attachment(), documentUuid, BACKUP_ZIP_JSON_ENTRY)))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun attachmentWithPathTraversalZipPath_isRejected() {
        val broken = backup().copy(attachments = listOf(BackupAttachment(attachment(), documentUuid, "Documents/../../etc/passwd")))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun duplicateZipPath_isRejected() {
        val broken = backup().copy(
            attachments = listOf(
                BackupAttachment(attachment(), documentUuid, zipPath),
                BackupAttachment(attachment().copy(uuid = "e5e5e5e5-e5e5-4e5e-8e5e-e5e5e5e5e5e5"), documentUuid, zipPath),
            ),
        )

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = 2), failure(encodeBackup(broken)))
    }

    @Test
    fun zipPathDifferingOnlyInCase_isRejectedAsACollision() {
        val broken = backup().copy(
            attachments = listOf(
                BackupAttachment(attachment(), documentUuid, zipPath),
                BackupAttachment(
                    attachment().copy(uuid = "e5e5e5e5-e5e5-4e5e-8e5e-e5e5e5e5e5e5"),
                    documentUuid,
                    zipPath.uppercase(),
                ),
            ),
        )

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = 2), failure(encodeBackup(broken)))
    }

    @Test
    fun missingDocumentsAndAttachments_decodeAsEmpty_forOlderBackups() {
        // Ein Backup vor Formatversion 6 kennt die beiden Felder gar nicht.
        val withoutFields = Json.parseToJsonElement(encodeBackup(backup(emptyList()).copy(documents = emptyList())))
            .jsonObject
            .filterKeys { it != "vehicleDocuments" && it != "attachments" }
        val text = JsonObject(withoutFields).toString()

        val decoded = success(text)

        assertEquals(emptyList<BackupVehicleDocument>(), decoded.documents)
        assertEquals(emptyList<BackupAttachment>(), decoded.attachments)
    }

    @Test
    fun documentWithUnknownVehicleUuid_isRejected() {
        val broken = backup().copy(documents = listOf(BackupVehicleDocument(document(), "ffffffff-ffff-4fff-8fff-ffffffffffff")))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, documentNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun duplicateDocumentUuid_isRejected() {
        val broken = backup().copy(documents = listOf(BackupVehicleDocument(document(), vehicleUuid), BackupVehicleDocument(document(), vehicleUuid)))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, documentNumber = 2), failure(encodeBackup(broken)))
    }

    @Test
    fun attachmentWithUnknownOwnerUuid_isRejected() {
        val broken = backup().copy(attachments = listOf(BackupAttachment(attachment(), "ffffffff-ffff-4fff-8fff-ffffffffffff", zipPath)))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun duplicateAttachmentUuid_isRejected() {
        val broken = backup(listOf(attachment(), attachment()))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = 2), failure(encodeBackup(broken)))
    }

    @Test
    fun attachmentFileNameNotMatchingMimeTypeExtension_isRejected() {
        val broken = backup(listOf(attachment(fileName = photoFileName.removeSuffix(".jpg") + ".pdf")))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun attachmentWithUnsupportedMimeType_isRejected() {
        val broken = backup(listOf(attachment().copy(mimeType = "application/zip")))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun documentAttachmentWithoutDimensions_isRejected() {
        val broken = backup(listOf(attachment().copy(width = null, height = null)))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, attachmentNumber = 1), failure(encodeBackup(broken)))
    }
}
