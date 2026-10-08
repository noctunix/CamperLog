package app.restvolt.camperlog.ui.detail

import android.content.res.Resources
import android.database.SQLException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.domain.AttachmentOwnerType
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistRepository
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.Conversion
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.CountryLookupRepository
import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.DiaryEntryRepository
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourMetrics
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.autoDetectedCountries
import app.restvolt.camperlog.domain.convert
import app.restvolt.camperlog.domain.costsByCategory
import app.restvolt.camperlog.domain.stationCostTotals
import app.restvolt.camperlog.domain.totalCosts
import app.restvolt.camperlog.domain.derivedMetrics
import app.restvolt.camperlog.share.CsvVocabulary
import app.restvolt.camperlog.share.tourExportBaseName
import app.restvolt.camperlog.share.writeTourExportZip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.util.Locale

/**
 * Zustand der Detailansicht. [Loaded.vehicle] ist nur gesetzt, wenn es mehr als ein Fahrzeug gibt.
 * [Loaded.stations] ist die Zeitleiste, aufsteigend nach `(date, time NULLS LAST, createdAt)`.
 * [Loaded.stopCosts] ist die Summe der Stationskosten je Währung, [Loaded.totalCosts] die
 * Gesamtsumme (manuelle Tourkosten plus Stationskosten) je Währung, [Loaded.categoryCosts] deren
 * Aufschlüsselung nach Kategorie. [Loaded.conversion] ist `null`, wenn alle Kosten bereits in der
 * Hauptwährung vorliegen und eine Umrechnung nichts Neues zeigen würde.
 */
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Loaded(
        val tour: Tour,
        val vehicle: Vehicle? = null,
        val stations: List<Station> = emptyList(),
        val stopCosts: List<Money> = emptyList(),
        val totalCosts: List<Money> = emptyList(),
        val categoryCosts: Map<CostCategory, List<Money>> = emptyMap(),
        val conversion: Conversion? = null,
        /** Aus Stationskoordinaten und Vignetten erkannte Länder, ohne [Tour.manualCountriesAdded]/[Tour.manualCountriesRemoved]. */
        val autoDetectedCountries: Set<String> = emptySet(),
        /** Tagebucheinträge der Tour, aufsteigend nach Datum. */
        val diaryEntries: List<DiaryEntry> = emptyList(),
        /** Checklisten der Tour, neueste zuerst. */
        val checklists: List<Checklist> = emptyList(),
        /** Kennzahlen für laufende Touren live abgeleitet, für abgeschlossene aus den gespeicherten Werten. */
        val metrics: TourMetrics = TourMetrics(tour.travelDays, tour.overnightStays, tour.distanceKm),
        /** Aktuelle Trackpunkte, benötigt zum Festschreiben der Kennzahlen beim Beenden. */
        val trackPoints: List<TrackPoint> = emptyList(),
    ) : DetailUiState
}

private data class TourContent(
    val stations: List<Station>,
    val diaryEntries: List<DiaryEntry>,
    val checklists: List<Checklist>,
    val trackPoints: List<TrackPoint>,
)

sealed interface FinishMessage {
    data object Finished : FinishMessage
    data object Failed : FinishMessage
}

/** Rückmeldung zu einem Tagebucheintrag; die Detailseite zeigt sie als Snackbar. */
sealed interface DiaryMessage {
    data class Deleted(val entry: DiaryEntry) : DiaryMessage
    data class Failed(@StringRes val text: Int) : DiaryMessage
}

/** Rückmeldung zu einer Checkliste der Tour; die Detailseite zeigt sie als Snackbar. */
sealed interface ChecklistMessage {
    data class Deleted(val checklist: Checklist) : ChecklistMessage
    data class Failed(@StringRes val text: Int) : ChecklistMessage
}

/** Rückmeldung zu einer Station aus der Zeitleiste oder zum Tour-Export; die Detailseite zeigt sie als Snackbar. */
sealed interface StationMessage {
    /** [linkedEntryIds] sind die Bordbuch-Einträge, die vor dem Löschen mit der Station verknüpft waren. */
    data class Deleted(val station: Station, val linkedEntryIds: List<Long> = emptyList()) : StationMessage
    data class Saved(val loggedServices: Set<StationService>) : StationMessage
    data class Failed(@StringRes val text: Int) : StationMessage
}

/** Zu teilende ZIP-Datei eines Tour-Exports; [destination] füllt den Betreff des Sharesheets. */
data class TourExportRequest(val uri: String, val destination: String)

/** Beobachtet eine Tour mit ihrer Stationen-Zeitleiste, damit Änderungen aus Formular und Löschen sofort sichtbar sind. */
class TourDetailViewModel(
    private val repository: TourRepository,
    private val vehicles: VehicleRepository,
    private val stations: StationRepository,
    private val diaryEntries: DiaryEntryRepository,
    private val checklists: ChecklistRepository,
    exchangeRates: ExchangeRateRepository,
    private val countryLookup: CountryLookupRepository,
    private val attachments: AttachmentRepository,
    private val attachmentFileStore: AttachmentFileStore,
    private val exportFiles: TourExportFiles,
    private val tracks: TrackRepository,
    tourId: Long,
    /** Vokabular von `Stops.csv` im Tour-Export, nach der App-Sprache des Geräts. */
    private val csvVocabulary: () -> CsvVocabulary = { CsvVocabulary.fromLocale(Locale.getDefault()) },
    private val today: () -> LocalDate = LocalDate::now,
    private val onTourFinished: () -> Unit = {},
) : ViewModel() {

    val uiState: StateFlow<DetailUiState> = combine(
        repository.observeTour(tourId),
        vehicles.observeVehicles(),
        combine(
            stations.observeForTour(tourId),
            diaryEntries.observeForTour(tourId),
            checklists.observeForTour(tourId),
            tracks.observeForTour(tourId),
        ) { stationList, diaryList, checklistList, trackPoints ->
            TourContent(stationList, diaryList, checklistList, trackPoints)
        },
        combine(exchangeRates.observeMainCurrency(), exchangeRates.observeRates()) { main, rates -> main to rates },
    ) { tour, vehicleList, content, (main, rates) ->
        if (tour == null) {
            DetailUiState.NotFound
        } else {
            val stationList = content.stations
            val totalCosts = tour.totalCosts(stationList)
            val metrics = if (tour.endDate == null) {
                tour.derivedMetrics(stationList, content.trackPoints, today())
            } else {
                TourMetrics(tour.travelDays, tour.overnightStays, tour.distanceKm)
            }
            DetailUiState.Loaded(
                tour = tour,
                vehicle = vehicleList.firstOrNull { it.id == tour.vehicleId }.takeIf { vehicleList.size > 1 },
                stations = stationList,
                stopCosts = stationList.stationCostTotals(),
                totalCosts = totalCosts,
                categoryCosts = stationList.costsByCategory(),
                conversion = if (totalCosts.all { it.currency == main }) null else convert(totalCosts, main, rates),
                autoDetectedCountries = autoDetectedCountries(stationList, countryLookup),
                diaryEntries = content.diaryEntries,
                checklists = content.checklists,
                metrics = metrics,
                trackPoints = content.trackPoints,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    private val _finishing = MutableStateFlow(false)
    val finishing: StateFlow<Boolean> = _finishing.asStateFlow()

    private val _finishMessage = MutableStateFlow<FinishMessage?>(null)
    val finishMessage: StateFlow<FinishMessage?> = _finishMessage.asStateFlow()

    /** Schließt die laufende Tour am gewählten Tag und schreibt ihre aktuell abgeleiteten Kennzahlen fest. */
    fun finish(endDate: LocalDate) {
        val loaded = uiState.value as? DetailUiState.Loaded ?: return
        val tour = loaded.tour
        if (tour.endDate != null || endDate < tour.startDate || endDate > today() || _finishing.value) return
        _finishing.value = true
        viewModelScope.launch {
            try {
                val completed = tour.copy(endDate = endDate)
                val metrics = completed.derivedMetrics(loaded.stations, loaded.trackPoints, endDate)
                repository.save(
                    completed.copy(
                        travelDays = metrics.travelDays,
                        overnightStays = metrics.overnightStays,
                        distanceKm = metrics.distanceKm,
                    ),
                )
                onTourFinished()
                _finishMessage.value = FinishMessage.Finished
            } catch (_: SQLException) {
                _finishMessage.value = FinishMessage.Failed
            } finally {
                _finishing.value = false
            }
        }
    }

    fun onFinishMessageShown(shown: FinishMessage) {
        _finishMessage.compareAndSet(shown, null)
    }

    private val _message = MutableStateFlow<StationMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar der Detailseite; nach der Anzeige [onMessageShown] aufrufen. */
    val message: StateFlow<StationMessage?> = _message.asStateFlow()

    /** Löscht [station] und bietet über [StationMessage.Deleted] das Rückgängigmachen an. */
    fun deleteStation(station: Station) {
        viewModelScope.launch {
            _message.value = try {
                val linkedEntryIds = stations.linkedLogEntries(station.id).map { it.id }
                stations.delete(station.id)
                StationMessage.Deleted(station, linkedEntryIds)
            } catch (_: SQLException) {
                StationMessage.Failed(R.string.station_delete_failed)
            }
        }
    }

    /** Stellt eine über [deleteStation] entfernte Station wieder her und verknüpft ihre Bordbuch-Einträge erneut. */
    fun undoDeleteStation(message: StationMessage.Deleted) {
        viewModelScope.launch {
            try {
                stations.restore(message.station)
                stations.relinkLogEntries(message.linkedEntryIds, message.station.id)
            } catch (_: SQLException) {
                _message.value = StationMessage.Failed(R.string.station_restore_failed)
            }
        }
    }

    /** Speichert die manuellen Länderanpassungen der Tour (siehe [app.restvolt.camperlog.domain.tourCountries]). */
    fun saveCountries(tour: Tour, manuallyAdded: Set<String>, manuallyRemoved: Set<String>) {
        viewModelScope.launch {
            repository.save(tour.copy(manualCountriesAdded = manuallyAdded, manualCountriesRemoved = manuallyRemoved))
        }
    }

    /** Meldet, dass eine neue Station gespeichert wurde, für die Snackbar der Detailseite. */
    fun onStationSaved(loggedServices: Set<StationService>) {
        _message.value = StationMessage.Saved(loggedServices)
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onMessageShown(shown: StationMessage) {
        _message.compareAndSet(shown, null)
    }

    private val _diaryMessage = MutableStateFlow<DiaryMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar zum Tagebuch; nach der Anzeige [onDiaryMessageShown] aufrufen. */
    val diaryMessage: StateFlow<DiaryMessage?> = _diaryMessage.asStateFlow()

    /** Löscht [entry] und bietet über [DiaryMessage.Deleted] das Rückgängigmachen an. */
    fun deleteDiaryEntry(entry: DiaryEntry) {
        viewModelScope.launch {
            _diaryMessage.value = try {
                diaryEntries.delete(entry.id)
                DiaryMessage.Deleted(entry)
            } catch (_: SQLException) {
                DiaryMessage.Failed(R.string.diary_delete_failed)
            }
        }
    }

    /** Stellt einen über [deleteDiaryEntry] entfernten Eintrag unverändert wieder her. */
    fun undoDeleteDiaryEntry(entry: DiaryEntry) {
        viewModelScope.launch {
            try {
                diaryEntries.restore(entry)
            } catch (_: SQLException) {
                _diaryMessage.value = DiaryMessage.Failed(R.string.diary_restore_failed)
            }
        }
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onDiaryMessageShown(shown: DiaryMessage) {
        _diaryMessage.compareAndSet(shown, null)
    }

    private val _checklistMessage = MutableStateFlow<ChecklistMessage?>(null)

    /** Einmalige Rückmeldung für die Snackbar zu Checklisten; nach der Anzeige [onChecklistMessageShown] aufrufen. */
    val checklistMessage: StateFlow<ChecklistMessage?> = _checklistMessage.asStateFlow()

    private val _startedChecklistId = MutableStateFlow<Long?>(null)

    /** id der gerade gestarteten Checkliste, bis [onChecklistStartHandled] aufgerufen wird; löst das Öffnen aus. */
    val startedChecklistId: StateFlow<Long?> = _startedChecklistId.asStateFlow()

    /** Startet [template] als neue Checkliste dieser Tour und ihres Fahrzeugs. */
    fun startChecklist(template: ChecklistTemplate) {
        val tour = (uiState.value as? DetailUiState.Loaded)?.tour ?: return
        viewModelScope.launch {
            val checklist = app.restvolt.camperlog.domain.startChecklist(template, tour.vehicleId, tour.id)
            _startedChecklistId.value = checklists.save(checklist)
        }
    }

    /** [startedChecklistId] wurde übernommen und soll nicht erneut ausgelöst werden. */
    fun onChecklistStartHandled() {
        _startedChecklistId.value = null
    }

    /** Löscht [checklist] und bietet über [ChecklistMessage.Deleted] das Rückgängigmachen an. */
    fun deleteChecklist(checklist: Checklist) {
        viewModelScope.launch {
            _checklistMessage.value = try {
                checklists.delete(checklist.id)
                ChecklistMessage.Deleted(checklist)
            } catch (_: SQLException) {
                ChecklistMessage.Failed(R.string.checklist_delete_failed)
            }
        }
    }

    /** Stellt eine über [deleteChecklist] entfernte Checkliste unverändert wieder her. */
    fun undoDeleteChecklist(checklist: Checklist) {
        viewModelScope.launch {
            try {
                checklists.restore(checklist)
            } catch (_: SQLException) {
                _checklistMessage.value = ChecklistMessage.Failed(R.string.checklist_restore_failed)
            }
        }
    }

    /** Verwirft [shown], sofern inzwischen keine neuere Meldung vorliegt. */
    fun onChecklistMessageShown(shown: ChecklistMessage) {
        _checklistMessage.compareAndSet(shown, null)
    }

    private val _exporting = MutableStateFlow(false)

    /** Läuft gerade ein Tour-Export? Für eine Fortschrittsanzeige und zum Sperren der Export-Aktion. */
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _exportRequest = MutableStateFlow<TourExportRequest?>(null)

    /** Zu teilende Export-ZIP, bis [exportRequestHandled] aufgerufen wird. */
    val exportRequest: StateFlow<TourExportRequest?> = _exportRequest.asStateFlow()

    /**
     * Exportiert [tour] mit [stations] und ihren bereinigten [countries] sowie [diary] als ZIP
     * (`Tour.html`, `Tour.md`, `Tour.gpx`, `Stops.csv`, Fotos) und fordert danach über [exportRequest]
     * das Teilen an; [res] liefert die Textbausteine in der App-Sprache. Läuft bereits ein Export, ruft
     * ein weiterer Aufruf nichts auf.
     */
    fun exportTour(res: Resources, tour: Tour, stations: List<Station>, countries: Set<String>, diary: List<DiaryEntry>) {
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                val stationIds = stations.mapTo(HashSet()) { it.id }
                val photosByStation = attachments.allAttachments()
                    .filter { it.ownerType == AttachmentOwnerType.STATION && it.ownerId in stationIds }
                    .groupBy { it.ownerId }
                val vehicleNames = vehicles.allVehicles().associate { it.id to it.name }
                val defaultVehicleName = res.getString(R.string.vehicle_default_name)
                val track = tracks.observeForTour(tour.id).first()
                val baseName = tourExportBaseName(tour.destination, tour.startDate)
                val uri = exportFiles.writeTourExportZip(baseName) { output ->
                    writeTourExportZip(
                        output = output,
                        res = res,
                        tour = tour,
                        stations = stations,
                        countries = countries,
                        diaryEntries = diary,
                        photosByStation = photosByStation,
                        tourNames = mapOf(tour.id to tour.destination),
                        vehicleNames = vehicleNames,
                        defaultVehicleName = defaultVehicleName,
                        vocabulary = csvVocabulary(),
                        photoContent = { fileName -> attachmentFileStore.file(fileName).takeIf { it.exists() }?.inputStream() },
                        track = track,
                    )
                }
                _exportRequest.value = TourExportRequest(uri, tour.destination)
            } catch (_: IOException) {
                _message.value = StationMessage.Failed(R.string.export_tour_failed)
            } catch (_: SQLException) {
                _message.value = StationMessage.Failed(R.string.export_tour_failed)
            } finally {
                _exporting.value = false
            }
        }
    }

    /** Quittiert [exportRequest]; ohne passende App ([started] = false) folgt ein Hinweis. */
    fun exportRequestHandled(started: Boolean) {
        _exportRequest.value = null
        if (!started) _message.value = StationMessage.Failed(R.string.no_share_app)
    }
}
