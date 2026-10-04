package app.restvolt.camperlog.ui

import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.ImportResult
import kotlinx.coroutines.CompletableDeferred

/**
 * Merkt sich Importaufrufe und meldet alle Touren als neu. Ein gesetztes [gate] hält den Import an,
 * bis es abgeschlossen wird; [failure] wird stattdessen geworfen.
 */
class FakeBackupImporter : BackupImporter {
    val calls = mutableListOf<Pair<Backup, ImportMode>>()
    var gate: CompletableDeferred<Unit>? = null
    var failure: Exception? = null

    override suspend fun import(backup: Backup, mode: ImportMode): ImportResult {
        calls += backup to mode
        gate?.await()
        failure?.let { throw it }
        return ImportResult(backup.tours.size, 0, 0, backup.rates.size)
    }
}
