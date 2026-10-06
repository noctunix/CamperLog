package app.restvolt.camperlog.domain

/** Ab dieser Gesamtgröße schließt die automatische Sicherung in den Sicherungsordner Fotos und Dokumente nicht mehr ein. */
const val AUTO_BACKUP_FILES_SIZE_LIMIT_BYTES = 200L * 1024 * 1024

/**
 * Ob eine automatische Sicherung in den Sicherungsordner Fotos und Dokumente mit einschließen soll:
 * ja, solange [totalSizeBytes] unter [AUTO_BACKUP_FILES_SIZE_LIMIT_BYTES] liegt - darüber würde jede
 * nächtliche Sicherung spürbar Zeit und Speicherplatz im gewählten Ordner kosten.
 */
fun shouldIncludeFilesInAutoBackup(totalSizeBytes: Long): Boolean = totalSizeBytes < AUTO_BACKUP_FILES_SIZE_LIMIT_BYTES
