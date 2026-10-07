package app.restvolt.camperlog.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistItem
import app.restvolt.camperlog.domain.ChecklistRepository
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.ChecklistTemplateRepository
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.DiaryEntryRepository
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.SEARCH_MIN_QUERY_LENGTH
import app.restvolt.camperlog.domain.SearchSnippet
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleDocument
import app.restvolt.camperlog.domain.VehicleDocumentRepository
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.buildSearchSnippet
import app.restvolt.camperlog.domain.matchesSearch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val SEARCH_DEBOUNCE_MILLIS = 200L

/** Zustand der Suche: Hinweis bei zu kurzer Eingabe (siehe [SEARCH_MIN_QUERY_LENGTH]), sonst die Ergebnisgruppen (leer = kein Treffer). */
data class SearchUiState(
    val query: String = "",
    val isQueryTooShort: Boolean = true,
    val groups: List<SearchResultGroup> = emptyList(),
)

/** Alle für die Suche beobachteten Daten, gebündelt vor der Auswertung gegen die Sucheingabe. */
private data class SearchDataFirstHalf(
    val vehicles: List<Vehicle>,
    val tours: List<Tour>,
    val stations: List<Station>,
    val diaryEntries: List<DiaryEntry>,
    val logEntries: List<LogEntry>,
)

private data class SearchDataSecondHalf(
    val documents: List<VehicleDocument>,
    val checklists: List<Checklist>,
    val templates: List<ChecklistTemplate>,
    val repairs: List<Repair>,
)

private data class SearchData(val first: SearchDataFirstHalf, val second: SearchDataSecondHalf)

/**
 * Durchsucht alle Touren, Stationen, Tagebucheinträge, Bordbuch-Einträge, Reparaturen, Checklisten,
 * Checklisten-Vorlagen, Fahrzeugdokumente und Fahrzeuge aller Fahrzeuge gegen [onQueryChange]. Die
 * Auswertung läuft entprellt und abseits des Hauptthreads, siehe [uiState]. [logTypeLabel] liefert die
 * sprachabhängige Bezeichnung einer Bordbuch-Art, da [LogEntry] selbst kein Freitextfeld hat.
 */
class SearchViewModel(
    tours: TourRepository,
    vehicles: VehicleRepository,
    stations: StationRepository,
    diaryEntries: DiaryEntryRepository,
    logbook: LogRepository,
    documents: VehicleDocumentRepository,
    checklists: ChecklistRepository,
    checklistTemplates: ChecklistTemplateRepository,
    private val logTypeLabel: (LogType) -> String = { it.name },
    /** Dispatcher der Auswertung abseits des Hauptthreads; in Tests ein steuerbarer Test-Dispatcher. */
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val data: Flow<SearchData> = combine(
        combine(
            vehicles.observeVehicles(),
            tours.observeTours(),
            stations.observeForVehicle(null),
            diaryEntries.observeAllEntries(),
            logbook.observeAllEntries(),
            ::SearchDataFirstHalf,
        ),
        combine(
            documents.observeAllDocuments(),
            checklists.observeAll(),
            checklistTemplates.observeAll(),
            vehicles.observeAllRepairs(),
            ::SearchDataSecondHalf,
        ),
        ::SearchData,
    )

    /**
     * Gruppen der aktuellen Sucheingabe, [SEARCH_DEBOUNCE_MILLIS] ms nach der letzten Änderung berechnet.
     * Getrennt von [uiState]s `query`/`isQueryTooShort`, die sofort mit der Eingabe mitlaufen, damit das
     * Suchfeld nie der Eingabe hinterherhängt.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val groups: StateFlow<List<SearchResultGroup>> = combine(query.debounce(SEARCH_DEBOUNCE_MILLIS), data) { q, d -> q.trim() to d }
        .mapLatest { (trimmedQuery, data) ->
            if (trimmedQuery.length < SEARCH_MIN_QUERY_LENGTH) {
                emptyList()
            } else {
                withContext(computationDispatcher) { buildGroups(trimmedQuery, data) }
            }
        }
        // StateFlow statt Flow: eine frische Suche zeigt sofort "query"/"isQueryTooShort" in [uiState],
        // ohne auf die erste entprellte Berechnung warten zu müssen (combine bräuchte sonst von jeder
        // Quelle mindestens einen Wert).
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Zustand der Suche; `query` und `isQueryTooShort` folgen der Eingabe sofort, `groups` entprellt. */
    val uiState: StateFlow<SearchUiState> = combine(query, groups) { rawQuery, groups ->
        SearchUiState(query = rawQuery, isQueryTooShort = rawQuery.trim().length < SEARCH_MIN_QUERY_LENGTH, groups = groups)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    private fun buildGroups(query: String, data: SearchData): List<SearchResultGroup> {
        val (vehicles, tours, stations, diaryEntries, logEntries) = data.first
        val (documents, checklists, templates, repairs) = data.second

        val showVehicleNames = vehicles.size > 1
        val vehicleNameById = vehicles.associate { it.id to it.name }
        fun vehicleNameOf(vehicleId: Long): String? = if (showVehicleNames) vehicleNameById[vehicleId].orEmpty() else null
        val tourById = tours.associateBy { it.id }

        val tourResults = tours.mapNotNull { tour ->
            val fields = listOf(tour.destination, tour.notes)
            if (!matchesSearch(query, fields)) return@mapNotNull null
            SearchResult.TourResult(
                tourId = tour.id,
                destination = tour.destination,
                date = tour.startDate,
                snippet = firstSnippet(query, fields),
                vehicleName = vehicleNameOf(tour.vehicleId),
            )
        }

        val stopResults = stations.mapNotNull { station ->
            val fields = listOf(station.name, station.place, station.notes, station.ferryBookingReference)
            if (!matchesSearch(query, fields)) return@mapNotNull null
            SearchResult.StopResult(
                stationId = station.id,
                name = station.name,
                stationType = station.type,
                date = station.date,
                snippet = firstSnippet(query, fields),
                vehicleName = vehicleNameOf(station.vehicleId),
            )
        }

        val diaryResults = diaryEntries.mapNotNull { entry ->
            val fields = listOf(entry.text)
            if (!matchesSearch(query, fields)) return@mapNotNull null
            val tour = tourById[entry.tourId]
            SearchResult.DiaryResult(
                tourId = entry.tourId,
                entryId = entry.id,
                tourDestination = tour?.destination.orEmpty(),
                date = entry.date,
                snippet = firstSnippet(query, fields),
                vehicleName = tour?.let { vehicleNameOf(it.vehicleId) },
            )
        }

        val logbookResults = logEntries.mapNotNull { entry ->
            val label = logTypeLabel(entry.type)
            val fields = listOf(label)
            if (!matchesSearch(query, fields)) return@mapNotNull null
            SearchResult.LogbookResult(
                entryId = entry.id,
                vehicleId = entry.vehicleId,
                logType = entry.type,
                label = label,
                date = entry.date,
                snippet = firstSnippet(query, fields),
                vehicleName = vehicleNameOf(entry.vehicleId),
            )
        }

        val repairResults = repairs.mapNotNull { repair ->
            val fields = listOf(repair.description)
            if (!matchesSearch(query, fields)) return@mapNotNull null
            SearchResult.RepairResult(
                vehicleId = repair.vehicleId,
                repairId = repair.id,
                description = repair.description,
                date = repair.date,
                snippet = firstSnippet(query, fields),
                vehicleName = vehicleNameOf(repair.vehicleId),
            )
        }

        val checklistResults = checklists.mapNotNull { checklist ->
            val fields = listOf(checklist.title) + checklist.items.map(ChecklistItem::text)
            if (!matchesSearch(query, fields)) return@mapNotNull null
            SearchResult.ChecklistResult(
                checklistId = checklist.id,
                vehicleId = checklist.vehicleId,
                tourId = checklist.tourId,
                title = checklist.title,
                date = checklist.createdAt.toLocalDate(),
                snippet = firstSnippet(query, fields),
                vehicleName = vehicleNameOf(checklist.vehicleId),
            )
        }

        val templateResults = templates.mapNotNull { template ->
            val fields = listOf(template.name) + template.items
            if (!matchesSearch(query, fields)) return@mapNotNull null
            SearchResult.ChecklistTemplateResult(
                templateId = template.id,
                title = template.name,
                date = template.createdAt.toLocalDate(),
                snippet = firstSnippet(query, fields),
            )
        }

        val documentResults = documents.mapNotNull { document ->
            val fields = listOf(document.title)
            if (!matchesSearch(query, fields)) return@mapNotNull null
            SearchResult.VehicleDocumentResult(
                vehicleId = document.vehicleId,
                documentId = document.id,
                title = document.title,
                date = document.expiryDate ?: document.createdAt.toLocalDate(),
                snippet = firstSnippet(query, fields),
                vehicleName = vehicleNameOf(document.vehicleId),
            )
        }

        val vehicleResults = vehicles.mapNotNull { vehicle ->
            val fields = listOf(vehicle.name, vehicle.notes)
            if (!matchesSearch(query, fields)) return@mapNotNull null
            SearchResult.VehicleResult(
                vehicleId = vehicle.id,
                title = vehicle.name,
                date = vehicle.createdAt.toLocalDate(),
                snippet = firstSnippet(query, fields),
            )
        }

        return listOf(
            SearchResultGroup(SearchResultType.TOUR, tourResults.sortedByDescending { it.date }),
            SearchResultGroup(SearchResultType.STOP, stopResults.sortedByDescending { it.date }),
            SearchResultGroup(SearchResultType.DIARY, diaryResults.sortedByDescending { it.date }),
            SearchResultGroup(SearchResultType.LOGBOOK, logbookResults.sortedByDescending { it.date }),
            SearchResultGroup(SearchResultType.REPAIR, repairResults.sortedByDescending { it.date }),
            SearchResultGroup(SearchResultType.CHECKLIST, checklistResults.sortedByDescending { it.date }),
            SearchResultGroup(SearchResultType.CHECKLIST_TEMPLATE, templateResults.sortedByDescending { it.date }),
            SearchResultGroup(SearchResultType.VEHICLE_DOCUMENT, documentResults.sortedByDescending { it.date }),
            SearchResultGroup(SearchResultType.VEHICLE, vehicleResults.sortedByDescending { it.date }),
        ).filter { it.results.isNotEmpty() }
    }

    private fun firstSnippet(query: String, fields: List<String>): SearchSnippet? =
        fields.firstNotNullOfOrNull { field -> buildSearchSnippet(query, field) }

    private fun Instant.toLocalDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()
}
