package app.restvolt.camperlog.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Obergrenze für JSON-Antworten (Wetter, Ortssuche); echte Antworten liegen weit darunter. */
const val MAX_JSON_RESPONSE_BYTES = 1024 * 1024

/** Obergrenze für eine Kartenkachel (256 × 256 PNG); echte Kacheln liegen weit darunter. */
const val MAX_TILE_RESPONSE_BYTES = 512 * 1024

/** Die Antwort ist größer als erlaubt; wird wie jeder andere Lesefehler behandelt. */
class ResponseTooLargeException(limit: Int) : IOException("Antwort größer als $limit Bytes")

/**
 * Liest den Strom vollständig, bricht aber mit [ResponseTooLargeException] ab, sobald mehr als
 * [maxBytes] ankommen. Schützt vor unbegrenztem Speicherverbrauch durch fehlerhafte Gegenstellen.
 */
fun InputStream.readBytesAtMost(maxBytes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(chunk)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > maxBytes) throw ResponseTooLargeException(maxBytes)
        out.write(chunk, 0, read)
    }
}

/** Wie [readBytesAtMost], dekodiert als UTF-8. */
fun InputStream.readTextAtMost(maxBytes: Int): String = readBytesAtMost(maxBytes).toString(Charsets.UTF_8)
