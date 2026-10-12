package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.Instant

/** Herkunft eines Diagnoseprotokoll-Eintrags. */
enum class DiagnosticsLogSource { CRASH, TRACK_RECORDING }

/**
 * Ein Eintrag im lokalen, rotierenden Diagnoseprotokoll ("Fehlerprotokoll" in den Einstellungen).
 * [message] und [stackTrace] sind schon vor dem Entstehen redigiert ([DiagnosticsLogRedactor.redact])
 * und längenbegrenzt - [capture] ist der einzige Weg, einen Eintrag aus rohem Text zu erzeugen,
 * [source]/[fatal]/[tag] werden nicht redigiert (keine Nutzerdaten).
 */
@Serializable
data class DiagnosticsLogEntry(
    @Serializable(with = InstantSerializer::class) val timestamp: Instant,
    val source: DiagnosticsLogSource,
    val fatal: Boolean,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
) {
    companion object {
        private const val MAX_MESSAGE_CHARS = 2000
        private const val MAX_STACK_CHARS = 4000

        fun capture(
            timestamp: Instant,
            source: DiagnosticsLogSource,
            fatal: Boolean,
            tag: String,
            rawMessage: String,
            rawStack: String? = null,
        ): DiagnosticsLogEntry = DiagnosticsLogEntry(
            timestamp = timestamp,
            source = source,
            fatal = fatal,
            tag = tag,
            // Redigieren vor dem Kürzen: ein fixer Schnitt könnte sonst mitten in einem sensiblen
            // Token (z. B. den Nachkommastellen einer Koordinate) landen und es unter die für ein
            // Redaktionsmuster nötige Länge verkürzen.
            message = truncate(DiagnosticsLogRedactor.redact(rawMessage), MAX_MESSAGE_CHARS),
            stackTrace = rawStack?.let { truncate(DiagnosticsLogRedactor.redact(it), MAX_STACK_CHARS) },
        )

        private fun truncate(text: String, max: Int): String =
            if (text.length <= max) text else "${text.take(max)}…[gekürzt]"
    }
}

/** Zugriff auf das lokale Diagnoseprotokoll. */
interface DiagnosticsLogRepository {
    /** Alle Einträge, älteste zuerst (Schreibreihenfolge). */
    fun observeAll(): Flow<List<DiagnosticsLogEntry>>

    /** Schreibt [entry] und rotiert danach (älteste Einträge fallen zuerst weg). Schlägt niemals sichtbar fehl. */
    suspend fun append(entry: DiagnosticsLogEntry)

    /** Löscht das gesamte Protokoll. */
    suspend fun clear()
}
