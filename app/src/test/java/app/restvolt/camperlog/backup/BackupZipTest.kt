package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.Attachment
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.DocumentKind
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ZIP-Verpackung einer Sicherung (siehe `BackupZip.kt`): Hin- und Rückweg über [writeBackupZip]
 * und [readBackupZip] mit den menschenlesbaren Pfaden aus [buildAttachmentZipPaths], sowie die
 * Angriffsfälle, die eine von Hand gebaute ZIP-Datei abdecken muss - jeder davon ohne jede
 * Seitenwirkung (keine Dateien bleiben in der Zwischenablage liegen).
 */
class BackupZipTest {

    private val nok = Currency.getInstance("NOK")
    private val vehicleUuid = "2b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val documentUuid = "6b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val photoFileName = "a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1.jpg"
    private val docFileName = "b2b2b2b2-b2b2-4b2b-8b2b-b2b2b2b2b2b2.pdf"
    private val photoBytes = jpegBytes()
    private val pdfBytes = "%PDF-1.4\nfake pdf content\n%%EOF".toByteArray()

    private fun jpegBytes(): ByteArray {
        // Reicht für den Inhaltssniff (JPEG-Magic FF D8 FF); die Bilddaten selbst werden hier nie decodiert.
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(64) { it.toByte() }
    }

    private fun vehicle(name: String = "Bluebird") = Vehicle(uuid = vehicleUuid, name = name, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun document(title: String = "Fahrzeugschein") = VehicleDocument(
        uuid = documentUuid,
        vehicleId = 0,
        kind = DocumentKind.REGISTRATION,
        title = title,
        expiryDate = LocalDate.of(2030, 1, 1),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun photoAttachment(fileName: String = photoFileName, sizeBytes: Long = photoBytes.size.toLong()) = Attachment(
        uuid = "c3c3c3c3-c3c3-4c3c-8c3c-c3c3c3c3c3c3",
        ownerType = AttachmentOwnerType.VEHICLE_DOCUMENT,
        ownerId = 0,
        fileName = fileName,
        mimeType = "image/jpeg",
        sizeBytes = sizeBytes,
        width = 800,
        height = 600,
        createdAt = Instant.EPOCH,
    )

    private fun pdfAttachment() = Attachment(
        uuid = "d4d4d4d4-d4d4-4d4d-8d4d-d4d4d4d4d4d4",
        ownerType = AttachmentOwnerType.VEHICLE_DOCUMENT,
        ownerId = 0,
        fileName = docFileName,
        mimeType = "application/pdf",
        sizeBytes = pdfBytes.size.toLong(),
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

    /** Baut eine [Backup] wie `buildBackup` es täte: Anhänge mit echten, über [buildAttachmentZipPaths] berechneten ZIP-Pfaden. */
    private fun backup(attachments: List<Attachment>, vehicleName: String = "Bluebird", title: String = "Fahrzeugschein"): Backup {
        val draft = Backup(
            exportedAt = Instant.parse("2026-10-06T12:00:00Z"),
            mainCurrency = nok,
            rates = emptyList(),
            tours = listOf(tour()),
            tourVehicleUuid = mapOf(tour().uuid to vehicleUuid),
            vehicles = listOf(BackupVehicle(vehicle(vehicleName), emptyList(), emptyList())),
            documents = listOf(BackupVehicleDocument(document(title), vehicleUuid)),
            attachments = attachments.map { BackupAttachment(it, documentUuid) },
        )
        val zipPaths = buildAttachmentZipPaths(draft)
        return draft.copy(attachments = draft.attachments.map { it.copy(zipPath = zipPaths.getValue(it.attachment.uuid)) })
    }

    private fun stagingDir(): File = createTempStagingDir()

    @Test
    fun roundTrip_withFiles_decodesBackupAndStagesMatchingFiles() {
        val source = backup(listOf(photoAttachment(), pdfAttachment()))
        val json = encodeBackup(source)
        val zipBytes = ByteArrayOutputStream().apply {
            writeBackupZip(this, json, source.attachments, includeFiles = true) { fileName ->
                when (fileName) {
                    photoFileName -> ByteArrayInputStream(photoBytes)
                    docFileName -> ByteArrayInputStream(pdfBytes)
                    else -> null
                }
            }
        }.toByteArray()
        val dir = stagingDir()

        val result = readBackupZip(ByteArrayInputStream(zipBytes), dir) as BackupZipReadResult.Success

        assertEquals(listOf("Fahrzeugschein"), result.backup.documents.map { it.document.title })
        assertEquals(2, result.backup.attachments.size)
        assertEquals(setOf(photoFileName, docFileName), result.stagedFiles.keys)
        assertEquals(photoBytes.toList(), result.stagedFiles.getValue(photoFileName).readBytes().toList())
        assertEquals(pdfBytes.toList(), result.stagedFiles.getValue(docFileName).readBytes().toList())
    }

    @Test
    fun roundTrip_withUnicodeVehicleAndDocumentNames_sanitizesAndStagesTheFile() {
        val source = backup(listOf(photoAttachment()), vehicleName = "Käfer äöü", title = "Zulassungsbescheinigung Teil II")
        val json = encodeBackup(source)
        val zipBytes = ByteArrayOutputStream().apply {
            writeBackupZip(this, json, source.attachments, includeFiles = true) { ByteArrayInputStream(photoBytes) }
        }.toByteArray()

        val result = readBackupZip(ByteArrayInputStream(zipBytes), stagingDir()) as BackupZipReadResult.Success

        assertEquals("Documents/Käfer äöü/Zulassungsbescheinigung Teil II.jpg", source.attachments.single().zipPath)
        assertEquals(photoBytes.toList(), result.stagedFiles.getValue(photoFileName).readBytes().toList())
    }

    @Test
    fun roundTrip_withMaximumLengthNames_staysImportable() {
        val longName = "X".repeat(500)
        val source = backup(listOf(photoAttachment()), vehicleName = longName, title = longName)
        val json = encodeBackup(source)
        val zipBytes = ByteArrayOutputStream().apply {
            writeBackupZip(this, json, source.attachments, includeFiles = true) { ByteArrayInputStream(photoBytes) }
        }.toByteArray()

        val result = readBackupZip(ByteArrayInputStream(zipBytes), stagingDir()) as BackupZipReadResult.Success

        assertEquals(longName, result.backup.documents.single().document.title)
        assertEquals(photoBytes.toList(), result.stagedFiles.getValue(photoFileName).readBytes().toList())
    }

    @Test
    fun twoAttachmentsOfTheSameDocument_getUniqueZipPathsWithinTheirFolder() {
        val first = photoAttachment(fileName = photoFileName)
        val second = photoAttachment(fileName = docFileName).copy(uuid = "e5e5e5e5-e5e5-4e5e-8e5e-e5e5e5e5e5e5")
        // Vorder- und Rückseite desselben Dokuments landen im selben Ordner unter demselben Titel.
        val source = backup(listOf(first, second))

        val paths = source.attachments.map { it.zipPath }

        assertEquals(2, paths.distinct().size)
        assertTrue(paths.any { it.endsWith("Fahrzeugschein.jpg") })
        assertTrue(paths.any { it.endsWith("Fahrzeugschein 2.jpg") })
    }

    @Test
    fun roundTrip_withoutFiles_decodesBackupWithEmptyStagedFiles() {
        val source = backup(listOf(photoAttachment()))
        val json = encodeBackup(source)
        val zipBytes = ByteArrayOutputStream().apply {
            writeBackupZip(this, json, source.attachments, includeFiles = false) { error("must not be called") }
        }.toByteArray()

        val result = readBackupZip(ByteArrayInputStream(zipBytes), stagingDir()) as BackupZipReadResult.Success

        assertEquals(1, result.backup.attachments.size)
        assertTrue(result.stagedFiles.isEmpty())
    }

    @Test
    fun pathTraversalEntryName_isRejectedWithoutStagingAnything() {
        val dir = stagingDir()
        val zipBytes = handCraftedZip("Documents/../../../etc/passwd" to ByteArray(10))

        val result = readBackupZip(ByteArrayInputStream(zipBytes), dir)

        assertTrue(result is BackupZipReadResult.Failure)
        assertEmptyAfterFailure(dir)
    }

    @Test
    fun absolutePathEntryName_isRejected() {
        val dir = stagingDir()
        val zipBytes = handCraftedZip("/etc/passwd" to ByteArray(10))

        val result = readBackupZip(ByteArrayInputStream(zipBytes), dir)

        assertTrue(result is BackupZipReadResult.Failure)
        assertEmptyAfterFailure(dir)
    }

    @Test
    fun unknownEntryName_isRejected() {
        val dir = stagingDir()
        val zipBytes = handCraftedZip("readme.txt" to "hello".toByteArray())

        val result = readBackupZip(ByteArrayInputStream(zipBytes), dir)

        assertTrue(result is BackupZipReadResult.Failure)
        assertEmptyAfterFailure(dir)
    }

    @Test
    fun fileNotReferencedByAnyAttachment_isRejected() {
        val source = backup(emptyList())
        val json = encodeBackup(source)
        val dir = stagingDir()
        val zipBytes = handCraftedZip(BACKUP_ZIP_JSON_ENTRY to json.toByteArray(), "Documents/Bluebird/Fahrzeugschein.jpg" to photoBytes)

        val result = readBackupZip(ByteArrayInputStream(zipBytes), dir)

        assertTrue(result is BackupZipReadResult.Failure)
        assertEmptyAfterFailure(dir)
    }

    @Test
    fun zipPathListedTwiceInBackupJson_isRejected() {
        val source = backup(listOf(photoAttachment(), pdfAttachment()))
        val samePath = source.attachments.first().zipPath
        val broken = source.copy(attachments = source.attachments.map { it.copy(zipPath = samePath) })
        val json = encodeBackup(broken)
        val dir = stagingDir()
        val zipBytes = handCraftedZip(BACKUP_ZIP_JSON_ENTRY to json.toByteArray(), samePath to photoBytes)

        val result = readBackupZip(ByteArrayInputStream(zipBytes), dir)

        assertTrue(result is BackupZipReadResult.Failure)
        assertEmptyAfterFailure(dir)
    }

    @Test
    fun zipEntryNameDifferingOnlyInCaseFromTheListedPath_isRejectedAsUnlisted() {
        val source = backup(listOf(photoAttachment()))
        val listedPath = source.attachments.single().zipPath
        val json = encodeBackup(source)
        val dir = stagingDir()
        // Der Eintrag heißt anders als die in backup.json gelistete Groß-/Kleinschreibung - exakter
        // Abgleich lehnt ihn ab, statt ihn stillschweigend derselben Datei zuzuordnen.
        val zipBytes = handCraftedZip(BACKUP_ZIP_JSON_ENTRY to json.toByteArray(), listedPath.uppercase() to photoBytes)

        val result = readBackupZip(ByteArrayInputStream(zipBytes), dir)

        assertTrue(result is BackupZipReadResult.Failure)
        assertEmptyAfterFailure(dir)
    }

    @Test
    fun fileLargerThanDeclaredMetadata_isRejected() {
        val source = backup(listOf(photoAttachment(sizeBytes = 3)))
        val zipPath = source.attachments.single().zipPath
        val json = encodeBackup(source)
        val dir = stagingDir()
        val zipBytes = handCraftedZip(BACKUP_ZIP_JSON_ENTRY to json.toByteArray(), zipPath to photoBytes)

        val result = readBackupZip(ByteArrayInputStream(zipBytes), dir)

        assertTrue(result is BackupZipReadResult.Failure)
        assertEmptyAfterFailure(dir)
    }

    @Test
    fun fileContentNotMatchingDeclaredMimeType_isRejected() {
        val source = backup(listOf(photoAttachment()))
        val zipPath = source.attachments.single().zipPath
        val json = encodeBackup(source)
        val dir = stagingDir()
        // Gibt sich per Metadaten als JPEG aus, der tatsächliche Inhalt ist aber keines.
        val fakePhoto = "not actually a jpeg".toByteArray()
        val zipBytes = handCraftedZip(BACKUP_ZIP_JSON_ENTRY to json.toByteArray(), zipPath to fakePhoto)

        val result = readBackupZip(ByteArrayInputStream(zipBytes), dir)

        assertTrue(result is BackupZipReadResult.Failure)
        assertEmptyAfterFailure(dir)
    }

    @Test
    fun oversizeFile_isRejectedEvenWhenItCompressesSmall() {
        val source = backup(listOf(pdfAttachment()))
        val zipPath = source.attachments.single().zipPath
        val json = encodeBackup(source)
        val dir = stagingDir()
        // 25 MB Nullen komprimieren winzig, zählen beim Entpacken aber als tatsächlich gelesene Bytes -
        // genau das, was eine Zip-Bombe ausnutzen würde.
        val huge = ByteArray(25 * 1024 * 1024)
        val zipBytes = handCraftedZip(BACKUP_ZIP_JSON_ENTRY to json.toByteArray(), zipPath to huge)

        val result = readBackupZip(ByteArrayInputStream(zipBytes), dir)

        assertTrue(result is BackupZipReadResult.Failure)
        assertEmptyAfterFailure(dir)
    }

    @Test
    fun corruptedZip_isRejectedWithoutThrowing() {
        val dir = stagingDir()
        val garbage = ByteArray(200) { (it * 7).toByte() }

        val result = readBackupZip(ByteArrayInputStream(garbage), dir)

        assertTrue(result is BackupZipReadResult.Failure)
        assertEmptyAfterFailure(dir)
    }

    @Test
    fun missingBackupJsonEntry_isRejected() {
        val dir = stagingDir()
        val zipBytes = handCraftedZip("Documents/Bluebird/Fahrzeugschein.jpg" to photoBytes)

        val result = readBackupZip(ByteArrayInputStream(zipBytes), dir)

        assertTrue(result is BackupZipReadResult.Failure)
        assertEmptyAfterFailure(dir)
    }

    @Test
    fun backupZipSizeEstimate_sumsJsonAndDistinctAttachmentSizes() {
        val source = backup(listOf(photoAttachment(), pdfAttachment()))
        val json = encodeBackup(source)

        val estimate = backupZipSizeEstimate(json, source.attachments)

        assertEquals(json.toByteArray(Charsets.UTF_8).size.toLong() + photoBytes.size + pdfBytes.size, estimate)
    }

    private fun assertEmptyAfterFailure(dir: File) {
        assertNull("nothing should remain staged after a rejected import", dir.listFiles()?.firstOrNull())
    }

    private fun createTempStagingDir(): File =
        java.nio.file.Files.createTempDirectory("camperlog-backup-zip-test").toFile()

    private fun handCraftedZip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                for ((name, content) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
}
