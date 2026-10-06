package app.restvolt.camperlog.domain

/** [StationService] → [LogType]: welche Versorgungs-Häkchen den Bordbuch-Eintrag einer Station sind (4, 13.5 Nr. 5). */
val SYNCED_SERVICE_LOG_TYPES: Map<StationService, LogType> = mapOf(
    StationService.CASSETTE to LogType.CASSETTE_EMPTIED,
    StationService.GREY_WATER to LogType.GREY_WATER_EMPTIED,
    StationService.GAS to LogType.GAS_BOTTLE_SWAPPED,
)

/** Eine Angleichung des Bordbuchs an eine gespeicherte Station, aus den Regeln in Abschnitt 4 abgeleitet. */
sealed interface LogSyncAction {
    /** Verknüpft den vorhandenen Eintrag [entryId] mit [stationId] (Regel 1: Dubletten vermeiden). */
    data class Link(val entryId: Long, val stationId: Long) : LogSyncAction

    /** Löscht den verknüpften Eintrag [entryId] (Regel 2: Häkchen entfernt, Regel 3: Station verschoben). */
    data class Delete(val entryId: Long) : LogSyncAction

    /** Legt einen neuen, mit [stationId] verknüpften Eintrag an; [uuid] stammt von einer verschobenen Station, falls gesetzt (Regel 3). */
    data class Create(val type: LogType, val vehicleId: Long, val date: java.time.LocalDate, val stationId: Long, val uuid: String?) : LogSyncAction
}

/**
 * Berechnet die Bordbuch-Angleichung für eine gespeicherte Station nach den Regeln in Abschnitt 4.
 * [old] ist der Stand vor dem Speichern oder `null` bei einer neuen Station; [new] ist bereits
 * gespeichert, ihre id ist also bekannt. [linkedEntry] liefert den aktuell mit `(old.id, type)`
 * verknüpften Eintrag, [unlinkedEntry] einen noch unverknüpften Eintrag von `(new.vehicleId, type,
 * new.date)` (Regel 1, Dublettenprüfung). Reine Entscheidung ohne eigene Datenbankzugriffe; die
 * beiden Parameter sind die einzigen Lesevorgänge.
 */
suspend fun syncStationLogEntries(
    old: Station?,
    new: Station,
    linkedEntry: suspend (type: LogType) -> LogEntry?,
    unlinkedEntry: suspend (type: LogType) -> LogEntry?,
): List<LogSyncAction> = buildList {
    for ((service, type) in SYNCED_SERVICE_LOG_TYPES) {
        val oldActive = old != null && service in old.services
        val newActive = service in new.services
        val moved = old != null && (old.date != new.date || old.vehicleId != new.vehicleId)
        val existingLink = if (oldActive) linkedEntry(type) else null

        when {
            !newActive && !oldActive -> Unit
            !newActive && oldActive -> existingLink?.let { add(LogSyncAction.Delete(it.id)) }
            newActive && oldActive && !moved -> Unit
            else -> {
                val preservedUuid = existingLink?.uuid.takeIf { newActive && oldActive && moved }
                if (newActive && oldActive && moved) existingLink?.let { add(LogSyncAction.Delete(it.id)) }
                val dedupe = unlinkedEntry(type)
                if (dedupe != null) {
                    add(LogSyncAction.Link(dedupe.id, new.id))
                } else {
                    add(LogSyncAction.Create(type, new.vehicleId, new.date, new.id, preservedUuid))
                }
            }
        }
    }
}
