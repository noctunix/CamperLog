package app.restvolt.camperlog.backup

/** Wie eine Sicherung mit den vorhandenen Daten verrechnet wird. */
enum class ImportMode {
    /**
     * Touren werden über ihre UUID abgeglichen: Neue werden angelegt, vorhandene nur ersetzt, wenn
     * die Sicherung eine neuere Fassung enthält. Kurse ebenso nach Kursdatum. Die Hauptwährung bleibt.
     */
    MERGE,

    /** Alle Touren und Kurse werden gelöscht und durch die Sicherung samt Hauptwährung ersetzt. */
    REPLACE,
}

/** Ergebnis eines Imports; [unchangedTours] zählt Touren, deren gespeicherte Fassung neuer oder gleich alt war. */
data class ImportResult(
    val addedTours: Int,
    val updatedTours: Int,
    val unchangedTours: Int,
    val importedRates: Int,
)

/** Spielt eine geprüfte Sicherung ein, vollständig oder gar nicht. */
fun interface BackupImporter {
    suspend fun import(backup: Backup, mode: ImportMode): ImportResult
}
