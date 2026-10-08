package app.restvolt.camperlog.data

import android.content.Context
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.domain.MAX_DOCUMENT_BYTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.imageio.ImageIO
import kotlinx.coroutines.test.runTest

/**
 * Prüft den Import-Pfad von [AndroidAttachmentFileStore] mit echten Bild- und PDF-Bytes: Herunterskalieren,
 * EXIF-Drehung und -Entfernung, Größenbegrenzung von Dokumenten und Erkennung über die ersten Bytes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentFileStoreTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val store by lazy { AndroidAttachmentFileStore(context) }
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "attachment-file-store-test").apply { mkdirs() }

    @Test
    fun importPhoto_downscalesAboveTheLimit() = runTest {
        val source = writeJpeg(tempDir, "big.jpg", width = 3000, height = 1500)

        val result = store.importPhoto(source.toUri()) as AttachmentImportResult.Success
        val photo = result.attachment as ImportedAttachment.Photo

        assertTrue("width ${photo.width} should be at most $MAX_PHOTO_DIMENSION", photo.width <= MAX_PHOTO_DIMENSION)
        assertTrue("height ${photo.height} should be at most $MAX_PHOTO_DIMENSION", photo.height <= MAX_PHOTO_DIMENSION)
        assertEquals(MAX_PHOTO_DIMENSION, photo.width)
        assertEquals("image/jpeg", photo.mimeType)
        assertTrue(store.file(photo.fileName).exists())
        assertEquals(store.file(photo.fileName).length(), photo.sizeBytes)
    }

    @Test
    fun importPhoto_appliesExifOrientationAndStripsExifFromResult() = runTest {
        // Orientation 6 = 90° im Uhrzeigersinn drehen: aus 400x200 (breiter als hoch) wird 200x400 (höher als breit).
        val source = writeJpeg(tempDir, "rotated.jpg", width = 400, height = 200, exifOrientation = 6)

        val result = store.importPhoto(source.toUri()) as AttachmentImportResult.Success
        val photo = result.attachment as ImportedAttachment.Photo

        assertEquals(200, photo.width)
        assertEquals(400, photo.height)
        // Die ursprüngliche Orientation 6 wurde auf die Pixel angewendet; die Ausgabedatei trägt
        // explizit "normal", nicht die ursprüngliche Drehung.
        val exif = ExifInterface(store.file(photo.fileName))
        assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
    }

    @Test
    fun importPhoto_keepsGpsAndCaptureTimeFromTheOriginal() = runTest {
        val source = writeJpeg(
            tempDir,
            "with-gps.jpg",
            width = 400,
            height = 200,
            latitude = 47.5,
            longitude = 11.0,
            takenAt = "2026:05:03 14:22:01",
        )

        val result = store.importPhoto(source.toUri()) as AttachmentImportResult.Success
        val photo = result.attachment as ImportedAttachment.Photo

        assertEquals(47.5, photo.latitude!!, 1e-6)
        assertEquals(11.0, photo.longitude!!, 1e-6)
        assertEquals(LocalDateTime.of(2026, 5, 3, 14, 22, 1), photo.takenAt)
        val exif = ExifInterface(store.file(photo.fileName))
        val latLong = exif.latLong
        assertTrue("GPS tags should survive the re-encode", latLong != null)
        assertEquals(47.5, latLong!![0], 1e-6)
        assertEquals(11.0, latLong[1], 1e-6)
        assertEquals("2026:05:03 14:22:01", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    }

    @Test
    fun importPhoto_withoutGps_leavesCoordinatesNull() = runTest {
        val source = writeJpeg(tempDir, "no-gps.jpg", width = 100, height = 100)

        val result = store.importPhoto(source.toUri()) as AttachmentImportResult.Success
        val photo = result.attachment as ImportedAttachment.Photo

        assertNull(photo.latitude)
        assertNull(photo.longitude)
        assertNull(photo.takenAt)
    }

    @Test
    fun importPhoto_smallImageIsNotUpscaled() = runTest {
        val source = writeJpeg(tempDir, "small.jpg", width = 300, height = 200)

        val result = store.importPhoto(source.toUri()) as AttachmentImportResult.Success
        val photo = result.attachment as ImportedAttachment.Photo

        assertEquals(300, photo.width)
        assertEquals(200, photo.height)
    }

    @Test
    fun importPhoto_unsupportedContent_isRejected() = runTest {
        val source = File(tempDir, "fake.jpg").apply { writeText("this is not an image") }

        val result = store.importPhoto(source.toUri())

        assertEquals(AttachmentImportResult.Failure(AttachmentImportError.UNSUPPORTED_TYPE), result)
    }

    @Test
    fun importPhoto_jpegHeaderButCorruptedData_failsToDecode() = runTest {
        val source = File(tempDir, "corrupted.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3, 4, 5))
        }

        val result = store.importPhoto(source.toUri())

        assertEquals(AttachmentImportResult.Failure(AttachmentImportError.DECODE_FAILED), result)
    }

    @Test
    fun importDocument_pdfIsCopiedAsIsWithoutReencoding() = runTest {
        val bytes = "%PDF-1.4\n%fake pdf content for the test\n%%EOF".toByteArray()
        val source = File(tempDir, "doc.pdf").apply { writeBytes(bytes) }

        val result = store.importDocument(source.toUri()) as AttachmentImportResult.Success
        val document = result.attachment as ImportedAttachment.Document

        assertEquals("application/pdf", document.mimeType)
        assertEquals(bytes.size.toLong(), document.sizeBytes)
        assertTrue(document.fileName.endsWith(".pdf"))
        assertEquals(bytes.toList(), store.file(document.fileName).readBytes().toList())
    }

    @Test
    fun importDocument_imageIsCopiedAsIsWithoutDownscaling() = runTest {
        val source = writeJpeg(tempDir, "scan.jpg", width = 3000, height = 1500)
        val originalSize = source.length()

        val result = store.importDocument(source.toUri()) as AttachmentImportResult.Success
        val document = result.attachment as ImportedAttachment.Document

        assertEquals("image/jpeg", document.mimeType)
        assertEquals(originalSize, document.sizeBytes)
        assertEquals(source.readBytes().toList(), store.file(document.fileName).readBytes().toList())
    }

    @Test
    fun importDocument_aboveTheSizeLimit_isRejectedAndFileIsCleanedUp() = runTest {
        val source = File(tempDir, "huge.pdf").apply {
            outputStream().use { out ->
                out.write("%PDF-1.4\n".toByteArray())
                val chunk = ByteArray(1024 * 1024)
                repeat(21) { out.write(chunk) }
            }
        }

        val result = store.importDocument(source.toUri())

        assertEquals(AttachmentImportResult.Failure(AttachmentImportError.TOO_LARGE), result)
        val attachmentsDir = File(context.filesDir, ATTACHMENTS_DIR)
        assertTrue(attachmentsDir.listFiles()?.none { it.length() > MAX_DOCUMENT_BYTES } != false)
    }

    @Test
    fun importDocument_unsupportedContent_isRejected() = runTest {
        val source = File(tempDir, "notes.txt").apply { writeText("just some text, not a supported format") }

        val result = store.importDocument(source.toUri())

        assertEquals(AttachmentImportResult.Failure(AttachmentImportError.UNSUPPORTED_TYPE), result)
    }

    @Test
    fun writeLocation_setsGpsExifOnAnExistingFile() = runTest {
        val source = writeJpeg(tempDir, "no-gps.jpg", width = 100, height = 100)
        val fileName = (store.importPhoto(source.toUri()) as AttachmentImportResult.Success).attachment.fileName

        store.writeLocation(fileName, 47.5, 11.0)

        val latLong = ExifInterface(store.file(fileName)).latLong
        assertTrue(latLong != null)
        assertEquals(47.5, latLong!![0], 1e-6)
        assertEquals(11.0, latLong[1], 1e-6)
    }

    @Test
    fun writeLocation_forAMissingFileIsANoOp() = runTest {
        store.writeLocation("does-not-exist.jpg", 47.5, 11.0)

        assertFalse(store.file("does-not-exist.jpg").exists())
    }

    @Test
    fun delete_removesTheFile() = runTest {
        val source = writeJpeg(tempDir, "to-delete.jpg", width = 100, height = 100)
        val fileName = (store.importPhoto(source.toUri()) as AttachmentImportResult.Success).attachment.fileName
        assertTrue(store.file(fileName).exists())

        store.delete(fileName)

        assertFalse(store.file(fileName).exists())
    }

    @Test
    fun sweepOrphanFiles_deletesOnlyOldFilesWithoutARow() = runTest {
        val now = Instant.parse("2026-10-06T12:00:00Z")
        val attachmentsDir = File(context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }
        val known = File(attachmentsDir, "known.jpg").apply { writeBytes(ByteArray(1)) }
        val oldOrphan = File(attachmentsDir, "old-orphan.jpg").apply { writeBytes(ByteArray(1)) }
        val recentOrphan = File(attachmentsDir, "recent-orphan.jpg").apply { writeBytes(ByteArray(1)) }
        val oldTime = now.minus(2, ChronoUnit.DAYS).toEpochMilli()
        val recentTime = now.minusSeconds(60).toEpochMilli()
        known.setLastModified(oldTime)
        oldOrphan.setLastModified(oldTime)
        recentOrphan.setLastModified(recentTime)

        store.sweepOrphanFiles(knownFileNames = setOf("known.jpg"), now = now)

        assertTrue(known.exists())
        assertFalse(oldOrphan.exists())
        assertTrue("a recent orphan must survive so an undo within the day still finds its file", recentOrphan.exists())
    }

    @Test
    fun commitStagedAttachmentFiles_countsUnmovableFilesAndKeepsTheRest() = runTest {
        val stagingDir = File(tempDir, "staging").apply { mkdirs() }
        val good = File(stagingDir, "good.jpg").apply { writeBytes(ByteArray(3)) }
        val missing = File(stagingDir, "missing.jpg").apply { delete() }

        val failed = commitStagedAttachmentFiles(store, mapOf("missing.jpg" to missing, "good.jpg" to good))

        assertEquals(1, failed)
        assertEquals(3L, store.file("good.jpg").length())
        assertFalse(store.file("missing.jpg").exists())
    }

    /**
     * Erzeugt eine echte JPEG-Datei mit [width]x[height] Pixeln, optional mit gesetztem
     * EXIF-`Orientation`-Tag, GPS-Koordinaten ([latitude]/[longitude]) und Aufnahmezeit [takenAt]
     * (EXIF-Format `yyyy:MM:dd HH:mm:ss`).
     */
    private fun writeJpeg(
        dir: File,
        name: String,
        width: Int,
        height: Int,
        exifOrientation: Int? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        takenAt: String? = null,
    ): File {
        val file = File(dir, name)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.BLUE
        graphics.fillRect(0, 0, width, height)
        graphics.color = Color.RED
        graphics.fillRect(0, 0, width / 4, height / 4)
        graphics.dispose()
        file.outputStream().use { out -> ImageIO.write(image, "jpg", out) }
        if (exifOrientation != null || latitude != null || takenAt != null) {
            val exif = ExifInterface(file)
            if (exifOrientation != null) exif.setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
            if (latitude != null && longitude != null) exif.setLatLong(latitude, longitude)
            if (takenAt != null) exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, takenAt)
            exif.saveAttributes()
        }
        return file
    }
}
