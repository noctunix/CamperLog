package app.restvolt.camperlog.domain

import java.io.InputStream

/**
 * Größte Anhangsdatei in Bytes: Grenze für importierte Dokumente und für Anhänge aus einer Sicherung
 * (Fotos werden beim Import ohnehin herunterskaliert und bleiben darunter).
 */
const val MAX_DOCUMENT_BYTES = 20L * 1024 * 1024

/** Anzahl der Bytes vom Dateianfang, die [sniffMimeType] braucht. */
const val MIME_SNIFF_HEADER_SIZE = 16

private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val WEBP_RIFF_MAGIC = "RIFF".toByteArray(Charsets.US_ASCII)
private val WEBP_TAG_MAGIC = "WEBP".toByteArray(Charsets.US_ASCII)
private val PDF_MAGIC = "%PDF".toByteArray(Charsets.US_ASCII)

private fun ByteArray.startsWith(magic: ByteArray, offset: Int = 0): Boolean =
    size >= offset + magic.size && magic.indices.all { this[offset + it] == magic[it] }

/** Liest bis zu [MIME_SNIFF_HEADER_SIZE] Bytes vom Anfang von [input]; kürzer nur bei kürzeren Dateien. */
fun readSniffHeader(input: InputStream): ByteArray {
    val buffer = ByteArray(MIME_SNIFF_HEADER_SIZE)
    var total = 0
    while (total < buffer.size) {
        val read = input.read(buffer, total, buffer.size - total)
        if (read < 0) break
        total += read
    }
    return buffer.copyOf(total)
}

/**
 * Erkennt den MIME-Typ einer Datei an ihren ersten Bytes statt an Dateiendung oder einem mitgelieferten
 * MIME-Typ, die beide leicht irreführend gesetzt werden können. `null`, wenn keines der unterstützten
 * Formate (JPEG, PNG, WebP, PDF) erkannt wird.
 */
fun sniffMimeType(header: ByteArray): String? = when {
    header.startsWith(JPEG_MAGIC) -> "image/jpeg"
    header.startsWith(PNG_MAGIC) -> "image/png"
    header.startsWith(WEBP_RIFF_MAGIC) && header.startsWith(WEBP_TAG_MAGIC, offset = 8) -> "image/webp"
    header.startsWith(PDF_MAGIC) -> "application/pdf"
    else -> null
}

/** Dateiendung für einen von [sniffMimeType] erkannten MIME-Typ. */
fun extensionFor(mimeType: String): String = when (mimeType) {
    "image/jpeg" -> ".jpg"
    "image/png" -> ".png"
    "image/webp" -> ".webp"
    "application/pdf" -> ".pdf"
    else -> ""
}
