package app.restvolt.camperlog.backup

/** Wie eine Sicherung mit den vorhandenen Daten verrechnet wird. */
enum class ImportMode {
    /**
     * Touren, Fahrzeuge und Reparaturen werden über ihre UUID abgeglichen: Neue werden angelegt,
     * vorhandene nur ersetzt, wenn die Sicherung eine neuere Fassung enthält. Bordbuch-Einträge
     * sind unveränderlich und werden nur angelegt, wenn ihre UUID noch unbekannt ist. Kurse werden
     * nach Kursdatum übernommen. Die Hauptwährung und das aktuelle Fahrzeug bleiben.
     */
    MERGE,

    /**
     * Alle Touren, Fahrzeuge, Reparaturen, Bordbuch-Einträge und Kurse werden gelöscht und durch
     * die Sicherung samt Hauptwährung und aktuellem Fahrzeug ersetzt.
     */
    REPLACE,
}

/**
 * Ergebnis eines Imports; [unchangedTours] zählt Touren, deren gespeicherte Fassung neuer oder
 * gleich alt war. Bordbuch-Einträge sind unveränderlich, daher gibt es für sie keine Zählung
 * aktualisierter oder unveränderter Einträge. [addedStations] zählt auch die aus alten
 * Stellplatz-Feldern abgeleiteten Übernachtungs-Stationen. Anhänge sind wie Bordbuch-Einträge
 * unveränderlich (nur [addedAttachments], keine Aktualisierung).
 */
data class ImportResult(
    val addedTours: Int,
    val updatedTours: Int,
    val unchangedTours: Int,
    val importedRates: Int,
    val addedVehicles: Int = 0,
    val updatedVehicles: Int = 0,
    val addedRepairs: Int = 0,
    val updatedRepairs: Int = 0,
    val addedLogEntries: Int = 0,
    val addedStations: Int = 0,
    val updatedStations: Int = 0,
    val addedDocuments: Int = 0,
    val updatedDocuments: Int = 0,
    val addedDiaryEntries: Int = 0,
    val updatedDiaryEntries: Int = 0,
    val addedChecklistTemplates: Int = 0,
    val updatedChecklistTemplates: Int = 0,
    val addedChecklists: Int = 0,
    val updatedChecklists: Int = 0,
    val addedAttachments: Int = 0,
)

/** Spielt eine geprüfte Sicherung ein, vollständig oder gar nicht. */
fun interface BackupImporter {
    suspend fun import(backup: Backup, mode: ImportMode): ImportResult
}
