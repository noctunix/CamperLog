package app.restvolt.camperlog.data

import android.content.Context
import app.restvolt.camperlog.domain.DiagnosticsLogEntry
import app.restvolt.camperlog.domain.DiagnosticsLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * [DiagnosticsLogRepository] als rotierende JSONL-Datei in `context.filesDir` (App-eigener,
 * nicht freigegebener Speicher). Read-modify-write statt echtem Anhängen, da die Rotation die
 * ganze Datei sowieso sehen muss - bei [MAX_BYTES] günstig genug. Schreibt in eine Temp-Datei
 * und benennt sie um, damit ein Prozessabbruch mitten im Schreiben die Datei nie beschädigt
 * zurücklässt; ein Schreibfehler (z. B. Speicher voll) wird verworfen statt geworfen, denn ein
 * Protokollierungsfehler darf selbst nie zu einem sichtbaren Fehler werden.
 */
class FileDiagnosticsLogRepository(context: Context) : DiagnosticsLogRepository {
    private val file = File(context.filesDir, "diagnostics_log.jsonl")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(readAll())

    override fun observeAll() = state.asStateFlow()

    override suspend fun append(entry: DiagnosticsLogEntry) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    var lines = readLines() + json.encodeToString(DiagnosticsLogEntry.serializer(), entry)
                    if (lines.size > MAX_ENTRIES) lines = lines.takeLast(MAX_ENTRIES)
                    while (lines.size > 1 && totalBytes(lines) > MAX_BYTES) lines = lines.drop(1)
                    writeLines(lines)
                    state.value = lines.mapNotNull(::tryParse)
                }
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching { file.delete() }
                state.value = emptyList()
            }
        }
    }

    private fun readAll(): List<DiagnosticsLogEntry> = readLines().mapNotNull(::tryParse)

    private fun readLines(): List<String> =
        if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()

    private fun writeLines(lines: List<String>) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n"))
        temp.renameTo(file)
    }

    private fun totalBytes(lines: List<String>): Int = lines.sumOf { it.toByteArray().size + 1 }

    private fun tryParse(line: String): DiagnosticsLogEntry? =
        runCatching { json.decodeFromString(DiagnosticsLogEntry.serializer(), line) }.getOrNull()

    companion object {
        const val MAX_ENTRIES = 200
        const val MAX_BYTES = 262_144 // 256 KiB
    }
}
