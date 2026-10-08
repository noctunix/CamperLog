package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/**
 * Baut die menschenlesbaren ZIP-Pfade einzeln (siehe `ZipPath.kt`), ohne den Umweg über JSON-Kodierung -
 * der Rundgang durch eine echte ZIP-Datei ist Sache von `BackupZipTest`.
 */
class ZipPathTest {

    private val nok = Currency.getInstance("NOK")
    private val vehicleUuid = "11111111-1111-4111-8111-111111111111"

    private fun vehicle(name: String = "Bluebird") = Vehicle(uuid = vehicleUuid, name = name, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun tour(uuid: String = "22222222-2222-4222-8222-222222222222", destination: String = "Lofoten") = Tour(
        uuid = uuid,
        startDate = LocalDate.of(2026, 5, 10),
        endDate = LocalDate.of(2026, 5, 20),
        destination = destination,
        tourType = TourType.VACATION,
        travelDays = 10,
        overnightStays = 9,
        distanceKm = 2000,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun station(
        uuid: String,
        name: String = "Platz am See",
        date: LocalDate = LocalDate.of(2026, 5, 12),
    ) = Station(uuid = uuid, vehicleId = 1, type = StationType.OVERNIGHT, date = date, name = name, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun repair(uuid: String, description: String = "Bremsen erneuert") = Repair(
        uuid = uuid,
        vehicleId = 1,
        date = LocalDate.of(2026, 3, 1),
        description = description,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun logEntry(uuid: String, type: LogType = LogType.CASSETTE_EMPTIED) =
        LogEntry(uuid = uuid, vehicleId = 1, type = type, date = LocalDate.of(2026, 5, 15), createdAt = Instant.EPOCH)

    private fun document(uuid: String, title: String = "Fahrzeugschein") = VehicleDocument(
        uuid = uuid,
        vehicleId = 1,
        kind = DocumentKind.REGISTRATION,
        title = title,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun photoAttachment(uuid: String, ownerType: AttachmentOwnerType) = Attachment(
        uuid = uuid,
        ownerType = ownerType,
        ownerId = 0,
        fileName = "$uuid.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 100,
        width = 10,
        height = 10,
        createdAt = Instant.EPOCH,
    )

    private fun documentAttachment(uuid: String, mimeType: String = "application/pdf") = Attachment(
        uuid = uuid,
        ownerType = AttachmentOwnerType.VEHICLE_DOCUMENT,
        ownerId = 0,
        fileName = if (mimeType == "application/pdf") "$uuid.pdf" else "$uuid.jpg",
        mimeType = mimeType,
        sizeBytes = 100,
        width = if (mimeType == "application/pdf") null else 10,
        height = if (mimeType == "application/pdf") null else 10,
        createdAt = Instant.EPOCH,
    )

    /** Baut eine [Backup] aus den gegebenen Teilen; [zipPath] jedes Anhangs ist noch leer, [buildAttachmentZipPaths] berechnet ihn. */
    private fun backup(
        stations: List<Station> = emptyList(),
        stationTourUuid: Map<String, String> = emptyMap(),
        vehicles: List<BackupVehicle> = listOf(BackupVehicle(vehicle(), emptyList(), emptyList())),
        documents: List<BackupVehicleDocument> = emptyList(),
        attachments: List<Pair<Attachment, String>> = emptyList(),
        tours: List<Tour> = emptyList(),
    ) = Backup(
        exportedAt = Instant.EPOCH,
        mainCurrency = nok,
        rates = emptyList(),
        tours = tours,
        vehicles = vehicles,
        stations = stations,
        stationTourUuid = stationTourUuid,
        documents = documents,
        attachments = attachments.map { (attachment, ownerUuid) -> BackupAttachment(attachment, ownerUuid) },
    )

    @Test
    fun stationWithTour_buildsPhotosTourPath() {
        val stationUuid = "33333333-3333-4333-8333-333333333333"
        val attachment = photoAttachment("44444444-4444-4444-8444-444444444444", AttachmentOwnerType.STATION)
        val backup = backup(
            tours = listOf(tour()),
            stations = listOf(station(stationUuid)),
            stationTourUuid = mapOf(stationUuid to tour().uuid),
            attachments = listOf(attachment to stationUuid),
        )

        val path = buildAttachmentZipPaths(backup).getValue(attachment.uuid)

        assertEquals("Photos/2026-05 Lofoten/2026-05-12 Platz am See 1.jpg", path)
    }

    @Test
    fun stationWithoutTour_usesNoTourFolder() {
        val stationUuid = "33333333-3333-4333-8333-333333333333"
        val attachment = photoAttachment("44444444-4444-4444-8444-444444444444", AttachmentOwnerType.STATION)
        val backup = backup(stations = listOf(station(stationUuid)), attachments = listOf(attachment to stationUuid))

        val path = buildAttachmentZipPaths(backup).getValue(attachment.uuid)

        assertEquals("Photos/No tour/2026-05-12 Platz am See 1.jpg", path)
    }

    @Test
    fun multiplePhotosOfTheSameStation_getSequentialNumbers() {
        val stationUuid = "33333333-3333-4333-8333-333333333333"
        val first = photoAttachment("44444444-4444-4444-8444-444444444444", AttachmentOwnerType.STATION)
        val second = photoAttachment("55555555-5555-4555-8555-555555555555", AttachmentOwnerType.STATION)
        val backup = backup(stations = listOf(station(stationUuid)), attachments = listOf(first to stationUuid, second to stationUuid))

        val paths = buildAttachmentZipPaths(backup)

        assertEquals("Photos/No tour/2026-05-12 Platz am See 1.jpg", paths.getValue(first.uuid))
        assertEquals("Photos/No tour/2026-05-12 Platz am See 2.jpg", paths.getValue(second.uuid))
    }

    @Test
    fun twoDifferentStationsWithTheSameNameAndDate_getUniqueZipPaths() {
        val firstStationUuid = "33333333-3333-4333-8333-333333333333"
        val secondStationUuid = "66666666-6666-4666-8666-666666666666"
        val firstPhoto = photoAttachment("44444444-4444-4444-8444-444444444444", AttachmentOwnerType.STATION)
        val secondPhoto = photoAttachment("55555555-5555-4555-8555-555555555555", AttachmentOwnerType.STATION)
        val backup = backup(
            stations = listOf(station(firstStationUuid, name = "Wiese"), station(secondStationUuid, name = "Wiese")),
            attachments = listOf(firstPhoto to firstStationUuid, secondPhoto to secondStationUuid),
        )

        val paths = buildAttachmentZipPaths(backup)

        assertEquals("Photos/No tour/2026-05-12 Wiese 1.jpg", paths.getValue(firstPhoto.uuid))
        assertEquals("Photos/No tour/2026-05-12 Wiese 1 2.jpg", paths.getValue(secondPhoto.uuid))
    }

    @Test
    fun repairPhoto_buildsPhotosRepairsVehiclePath() {
        val repairUuid = "33333333-3333-4333-8333-333333333333"
        val attachment = photoAttachment("44444444-4444-4444-8444-444444444444", AttachmentOwnerType.REPAIR)
        val backup = backup(
            vehicles = listOf(BackupVehicle(vehicle("Bluebird"), listOf(repair(repairUuid)), emptyList())),
            attachments = listOf(attachment to repairUuid),
        )

        val path = buildAttachmentZipPaths(backup).getValue(attachment.uuid)

        assertEquals("Photos/Repairs/Bluebird/2026-03-01 Bremsen erneuert 1.jpg", path)
    }

    @Test
    fun logEntryPhoto_buildsPhotosLogbookPath() {
        val entryUuid = "33333333-3333-4333-8333-333333333333"
        val attachment = photoAttachment("44444444-4444-4444-8444-444444444444", AttachmentOwnerType.LOG_ENTRY)
        val backup = backup(
            vehicles = listOf(BackupVehicle(vehicle(), emptyList(), listOf(logEntry(entryUuid)))),
            attachments = listOf(attachment to entryUuid),
        )

        val path = buildAttachmentZipPaths(backup).getValue(attachment.uuid)

        assertEquals("Photos/Logbook/2026-05-15 Cassette emptied 1.jpg", path)
    }

    @Test
    fun vehicleDocument_buildsDocumentsVehiclePath() {
        val documentUuid = "33333333-3333-4333-8333-333333333333"
        val attachment = documentAttachment("44444444-4444-4444-8444-444444444444")
        val backup = backup(
            documents = listOf(BackupVehicleDocument(document(documentUuid), vehicleUuid)),
            attachments = listOf(attachment to documentUuid),
        )

        val path = buildAttachmentZipPaths(backup).getValue(attachment.uuid)

        assertEquals("Documents/Bluebird/Fahrzeugschein.pdf", path)
    }

    @Test
    fun unicodeNamesAreKeptAsEntered() {
        val documentUuid = "33333333-3333-4333-8333-333333333333"
        val attachment = documentAttachment("44444444-4444-4444-8444-444444444444", mimeType = "image/jpeg")
        val backup = backup(
            documents = listOf(BackupVehicleDocument(document(documentUuid, title = "Zulassungsbescheinigung Teil II äöüß"), vehicleUuid)),
            vehicles = listOf(BackupVehicle(vehicle("Käfer 🚐"), emptyList(), emptyList())),
            attachments = listOf(attachment to documentUuid),
        )

        val path = buildAttachmentZipPaths(backup).getValue(attachment.uuid)

        assertEquals("Documents/Käfer 🚐/Zulassungsbescheinigung Teil II äöüß.jpg", path)
    }

    @Test
    fun forbiddenCharactersAreStrippedFromUserEnteredNames() {
        val documentUuid = "33333333-3333-4333-8333-333333333333"
        val attachment = documentAttachment("44444444-4444-4444-8444-444444444444", mimeType = "image/jpeg")
        val backup = backup(
            documents = listOf(BackupVehicleDocument(document(documentUuid, title = "Schein? Original <Kopie>"), vehicleUuid)),
            attachments = listOf(attachment to documentUuid),
        )

        val path = buildAttachmentZipPaths(backup).getValue(attachment.uuid)

        assertEquals("Documents/Bluebird/Schein Original Kopie.jpg", path)
    }

    @Test
    fun blankUserEnteredNameFallsBackToAGenericLabel() {
        val documentUuid = "33333333-3333-4333-8333-333333333333"
        val attachment = documentAttachment("44444444-4444-4444-8444-444444444444", mimeType = "image/jpeg")
        val backup = backup(
            documents = listOf(BackupVehicleDocument(document(documentUuid, title = "   "), vehicleUuid)),
            attachments = listOf(attachment to documentUuid),
        )

        val path = buildAttachmentZipPaths(backup).getValue(attachment.uuid)

        assertEquals("Documents/Bluebird/Document.jpg", path)
    }

    @Test
    fun longUserEnteredNameIsCappedPerSegmentIncludingExtensionAndSuffix() {
        val firstDocumentUuid = "33333333-3333-4333-8333-333333333333"
        val secondDocumentUuid = "66666666-6666-4666-8666-666666666666"
        val firstAttachment = documentAttachment("44444444-4444-4444-8444-444444444444", mimeType = "image/jpeg")
        val secondAttachment = documentAttachment("55555555-5555-4555-8555-555555555555", mimeType = "application/pdf")
        val longTitle = "A".repeat(500)
        val backup = backup(
            documents = listOf(
                BackupVehicleDocument(document(firstDocumentUuid, title = longTitle), vehicleUuid),
                BackupVehicleDocument(document(secondDocumentUuid, title = longTitle), vehicleUuid),
            ),
            attachments = listOf(firstAttachment to firstDocumentUuid, secondAttachment to secondDocumentUuid),
        )

        val paths = buildAttachmentZipPaths(backup)
        val first = paths.getValue(firstAttachment.uuid).substringAfterLast('/')
        val second = paths.getValue(secondAttachment.uuid).substringAfterLast('/')

        assertEquals("A".repeat(MAX_ZIP_PATH_SEGMENT_LENGTH - 4) + ".jpg", first)
        assertEquals("A".repeat(MAX_ZIP_PATH_SEGMENT_LENGTH - 4) + ".pdf", second)
        paths.values.forEach { assertTrue(it, isValidZipPath(it)) }
    }

    @Test
    fun cappedNamesThatCollideKeepTheirSuffixWithinTheSegmentLimit() {
        val longTitle = "B".repeat(500)
        val uuids = listOf("33333333-3333-4333-8333-333333333333", "66666666-6666-4666-8666-666666666666")
        val attachments = listOf(
            documentAttachment("44444444-4444-4444-8444-444444444444", mimeType = "image/jpeg"),
            documentAttachment("55555555-5555-4555-8555-555555555555", mimeType = "image/jpeg"),
        )
        val backup = backup(
            documents = uuids.map { BackupVehicleDocument(document(it, title = longTitle), vehicleUuid) },
            attachments = attachments.zip(uuids),
        )

        val paths = buildAttachmentZipPaths(backup)

        assertEquals(
            "B".repeat(MAX_ZIP_PATH_SEGMENT_LENGTH - 6) + " 2.jpg",
            paths.getValue(attachments[1].uuid).substringAfterLast('/'),
        )
        paths.values.forEach { assertTrue(it, isValidZipPath(it)) }
    }

    @Test
    fun namesDifferingOnlyInCaseCollideAndGetSuffixed() {
        val firstDocumentUuid = "33333333-3333-4333-8333-333333333333"
        val secondDocumentUuid = "66666666-6666-4666-8666-666666666666"
        val firstAttachment = documentAttachment("44444444-4444-4444-8444-444444444444", mimeType = "image/jpeg")
        val secondAttachment = documentAttachment("55555555-5555-4555-8555-555555555555", mimeType = "image/jpeg")
        val backup = backup(
            documents = listOf(
                BackupVehicleDocument(document(firstDocumentUuid, title = "Schein"), vehicleUuid),
                BackupVehicleDocument(document(secondDocumentUuid, title = "SCHEIN"), vehicleUuid),
            ),
            attachments = listOf(firstAttachment to firstDocumentUuid, secondAttachment to secondDocumentUuid),
        )

        val paths = buildAttachmentZipPaths(backup)

        assertEquals("Documents/Bluebird/Schein.jpg", paths.getValue(firstAttachment.uuid))
        assertEquals("Documents/Bluebird/SCHEIN 2.jpg", paths.getValue(secondAttachment.uuid))
    }

    @Test
    fun isValidZipPath_acceptsOrdinaryRelativePaths() {
        assertTrue(isValidZipPath("backup.json"))
        assertTrue(isValidZipPath("Photos/No tour/2026-05-12 Platz am See 1.jpg"))
        assertTrue(isValidZipPath("Documents/Käfer 🚐/Schein.pdf"))
    }

    @Test
    fun isValidZipPath_rejectsUnsafePaths() {
        assertFalse(isValidZipPath(""))
        assertFalse(isValidZipPath("/etc/passwd"))
        assertFalse(isValidZipPath("Photos/../../etc/passwd"))
        assertFalse(isValidZipPath("Photos/.."))
        assertFalse(isValidZipPath("Photos/."))
        assertFalse(isValidZipPath("Documents\\Vehicle\\Schein.pdf"))
        assertFalse(isValidZipPath("Photos/Sch:ein.jpg"))
        assertFalse(isValidZipPath("a".repeat(90)))
        assertFalse(isValidZipPath((1..11).joinToString("/") { "segment$it" }))
    }

    @Test
    fun sanitizeZipName_trimsAndFallsBackWhenEmpty() {
        assertEquals("Hello World", sanitizeZipName("  Hello World  ", "fallback"))
        assertEquals("fallback", sanitizeZipName("   ", "fallback"))
        assertEquals("fallback", sanitizeZipName("***???", "fallback"))
        assertEquals("Schein", sanitizeZipName("Sch/e\\in:*?\"<>|", "fallback"))
    }
}
