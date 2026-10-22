package app.restvolt.camperlog.domain

/** [StationService] → [LogType]: welche Versorgungs-Häkchen einen Bordbuch-Eintrag der Station nach sich ziehen. */
val SYNCED_SERVICE_LOG_TYPES: Map<StationService, LogType> = mapOf(
    StationService.CASSETTE to LogType.CASSETTE_EMPTIED,
    StationService.GREY_WATER to LogType.GREY_WATER_EMPTIED,
    StationService.GAS to LogType.GAS_BOTTLE_SWAPPED,
)

/** [StationType] → [LogType]: welche Stationstypen allein durch ihr Dasein einen Bordbuch-Eintrag nach sich ziehen. */
val SYNCED_STATION_TYPE_LOG_TYPES: Map<StationType, LogType> = mapOf(
    StationType.FUEL to LogType.FUELED,
)

/** Alle mit Stationen synchronisierten [LogType]s: über Dienste und über den Stationstyp. */
val SYNCED_LOG_TYPES: Set<LogType> = SYNCED_SERVICE_LOG_TYPES.values.toSet() + SYNCED_STATION_TYPE_LOG_TYPES.values

/** Die [LogType]s, die [station] in ihrem jetzigen Zustand im Bordbuch verlangt. */
fun requiredLogTypes(station: Station): Set<LogType> = buildSet {
    SYNCED_SERVICE_LOG_TYPES.forEach { (service, type) -> if (service in station.services) add(type) }
    SYNCED_STATION_TYPE_LOG_TYPES[station.type]?.let(::add)
}

/** Eine Angleichung des Bordbuchs an eine gespeicherte Station. */
sealed interface LogSyncAction {
    /** Verknüpft den vorhandenen Eintrag [entryId] mit [stationId], um eine Dublette zu vermeiden. */
    data class Link(val entryId: Long, val stationId: Long) : LogSyncAction

    /** Löscht den verknüpften Eintrag [entryId], wenn das Häkchen entfernt wurde oder die Station sich verschoben hat. */
    data class Delete(val entryId: Long) : LogSyncAction

    /** Legt einen neuen, mit [stationId] verknüpften Eintrag an; [uuid] stammt von einer verschobenen Station, falls gesetzt. */
    data class Create(val type: LogType, val vehicleId: Long, val date: java.time.LocalDate, val stationId: Long, val uuid: String?) : LogSyncAction
}

/**
 * Berechnet die Bordbuch-Angleichung für eine gespeicherte Station.
 * [old] ist der Stand vor dem Speichern oder `null` bei einer neuen Station; [new] ist bereits
 * gespeichert, ihre id ist also bekannt. Welche Typen verlangt sind, steht in [requiredLogTypes].
 * [linkedEntry] liefert den aktuell mit `(old.id, type)` verknüpften Eintrag, [unlinkedEntry] einen noch unverknüpften Eintrag von `(new.vehicleId, type,
 * new.date)` zur Dublettenprüfung. Reine Entscheidung ohne eigene Datenbankzugriffe; die beiden
 * Parameter sind die einzigen Lesevorgänge.
 */
suspend fun syncStationLogEntries(
    old: Station?,
    new: Station,
    linkedEntry: suspend (type: LogType) -> LogEntry?,
    unlinkedEntry: suspend (type: LogType) -> LogEntry?,
): List<LogSyncAction> = buildList {
    val oldRequired = old?.let(::requiredLogTypes).orEmpty()
    val newRequired = requiredLogTypes(new)
    for (type in SYNCED_LOG_TYPES) {
        val oldActive = type in oldRequired
        val newActive = type in newRequired
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
