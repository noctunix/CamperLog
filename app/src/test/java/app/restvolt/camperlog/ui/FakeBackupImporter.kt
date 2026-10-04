package app.restvolt.camperlog.ui

import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.ImportResult

/** Merkt sich Importaufrufe und meldet alle Touren als neu. */
class FakeBackupImporter : BackupImporter {
    val calls = mutableListOf<Pair<Backup, ImportMode>>()

    override suspend fun import(backup: Backup, mode: ImportMode): ImportResult {
        calls += backup to mode
        return ImportResult(backup.tours.size, 0, 0, backup.rates.size)
    }
}
