package app.restvolt.camperlog.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class AttachmentFormatTest {

    @Test
    fun supportedFormatsAreRecognizedByTheirFirstBytes() {
        assertEquals("image/jpeg", sniffMimeType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals("image/png", sniffMimeType(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals("image/webp", sniffMimeType("RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray(Charsets.US_ASCII)))
        assertEquals("application/pdf", sniffMimeType("%PDF-1.7".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun unknownOrTruncatedHeadersAreRejected() {
        assertNull(sniffMimeType("GIF89a".toByteArray(Charsets.US_ASCII)))
        assertNull(sniffMimeType("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray(Charsets.US_ASCII)))
        assertNull(sniffMimeType(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertNull(sniffMimeType(ByteArray(0)))
    }

    @Test
    fun readSniffHeaderFillsTheHeaderAcrossShortReads() {
        val bytes = ByteArray(40) { it.toByte() }
        val oneByteAtATime = object : InputStream() {
            private val source = ByteArrayInputStream(bytes)
            override fun read(): Int = source.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = source.read(b, off, minOf(len, 1))
        }

        assertArrayEquals(bytes.copyOf(MIME_SNIFF_HEADER_SIZE), readSniffHeader(oneByteAtATime))
        assertArrayEquals(bytes.copyOf(3), readSniffHeader(ByteArrayInputStream(bytes.copyOf(3))))
    }
}
