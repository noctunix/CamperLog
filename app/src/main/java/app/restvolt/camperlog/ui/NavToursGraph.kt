package app.restvolt.camperlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.LatLon
import app.restvolt.camperlog.domain.TrackSummary
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_FAB_ACTION
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_ID
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_SAVE_ACTION
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_STATION_FAB_ACTION
import app.restvolt.camperlog.domain.displayTitle
import app.restvolt.camperlog.ui.checklists.suggestedChecklistTemplates
import app.restvolt.camperlog.ui.detail.AndroidTourExportFiles
import app.restvolt.camperlog.ui.detail.DetailUiState
import app.restvolt.camperlog.ui.detail.DiaryEditScreen
import app.restvolt.camperlog.ui.detail.DiaryEditViewModel
import app.restvolt.camperlog.ui.detail.TourDetailScreen
import app.restvolt.camperlog.ui.detail.TourDetailViewModel
import app.restvolt.camperlog.ui.detail.TrackRecordingSection
import app.restvolt.camperlog.ui.detail.TrackRecordingStatusRow
import app.restvolt.camperlog.ui.edit.EditTourScreen
import app.restvolt.camperlog.ui.edit.EditTourViewModel
import app.restvolt.camperlog.ui.edit.isUnmodifiedNewTourDraft
import app.restvolt.camperlog.ui.map.MapScreen
import app.restvolt.camperlog.ui.map.MapViewModel
import app.restvolt.camperlog.ui.overview.OverviewScreen
import app.restvolt.camperlog.ui.overview.OverviewViewModel
import app.restvolt.camperlog.ui.tours.ToursScreen
import app.restvolt.camperlog.ui.tours.ToursViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Navigationsziele: Touren-Reiter mit Tourdetail, Tagebuch, Tourkarte und Übersicht. */
internal fun NavGraphBuilder.toursGraph(
    navController: NavController,
    deps: NavDependencies,
    bottomBar: @Composable () -> Unit,
) = with(deps) {
    composable<ToursRoute> {
        var showStationPicker by rememberSaveable { mutableStateOf(false) }
        val context = LocalContext.current
        ToursScreen(
            viewModel = viewModel {
                ToursViewModel(
                    repository,
                    vehicles,
                    stations,
                    tracks,
                    VehicleScopeSettings(context),
                    checklists,
                    onTourFinished = { tourId -> app.restvolt.camperlog.tracking.TrackRecordingService.stopIfTracking(context, tourId) },
                    onTourDeleted = { tourId -> app.restvolt.camperlog.tracking.TrackRecordingService.stopIfTracking(context, tourId) },
                )
            },
            onOpenChecklist = { checklistId, vehicleId -> navController.navigate(ChecklistRoute(checklistId = checklistId, vehicleId = vehicleId)) },
            onAddTour = {
                // Schaltet den zweiten Schritt der Pilot-Tour frei, aber nur, wenn sie gerade läuft:
                // normales Anlegen ohne aktive Tour soll nicht an sie koppeln.
                if (guideController.state.value.tour?.id == CREATE_FIRST_TOUR_ID) {
                    guideController.completeAction(CREATE_FIRST_TOUR_FAB_ACTION)
                }
                navController.navigate(EditRoute())
            },
            onAddStation = { showStationPicker = true },
            onImportBackup = { navController.navigate(DataRoute(importOnOpen = true)) },
            onOpenOverview = { vehicleId -> navController.navigate(OverviewRoute(vehicleId)) },
            onOpenSearch = { navController.navigate(SearchRoute) },
            onOpenData = { navController.navigate(DataRoute()) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
            onOpenTour = { navController.navigate(DetailRoute(it)) },
            onOpenVehicles = { navController.navigate(VehiclesRoute) },
            bottomBar = bottomBar,
        )
        if (showStationPicker) {
            StationTypePickerSheet(
                onSelect = { type ->
                    showStationPicker = false
                    navController.navigate(StationEditRoute(initialType = type.name))
                },
                onDismiss = { showStationPicker = false },
            )
        }
    }
    composable<EditRoute> { entry ->
        val tourId = entry.toRoute<EditRoute>().tourId
        val resources = LocalResources.current
        val toursViewModel = navController.toursViewModel(entry, repository, vehicles, stations, tracks, checklistTemplates, checklists)
        val guideState by guideController.state.collectAsStateWithLifecycle()
        val isCreateFirstTourGuide = guideState.tour?.id == CREATE_FIRST_TOUR_ID
        val editTourViewModel = viewModel {
            EditTourViewModel(
                repository,
                vehicles,
                stations,
                checklists,
                tourId,
                createSavedStateHandle(),
                tracks = tracks,
                homeLocations = homeLocations,
                defaultHomeStationName = { resources.getString(R.string.home_location_default_name) },
            )
        }
        EditTourScreen(
            viewModel = editTourViewModel,
            trackSettings = trackSettings,
            homeLocations = homeLocations,
            requestTrackPermissionEarly = isCreateFirstTourGuide,
            onBeforeSystemDialog = guideController::pause,
            onAfterSystemDialog = guideController::resume,
            onAddVehicle = {
                // Pausiert die Pilot-Tour für das ungeführte Fahrzeugformular, aber nur, wenn sie
                // gerade läuft: normales Anlegen ohne aktive Tour soll nicht an sie koppeln.
                if (isCreateFirstTourGuide) guideController.pause()
                navController.navigate(VehicleEditRoute())
            },
            onDone = {
                // Eine neue Tour speichert sich schon laufend selbst; beim Verlassen entscheidet sich nur
                // noch, ob der Entwurf gelöscht wird (ohne jede sinnvolle Eingabe oder explizit verworfen)
                // oder bleibt.
                val newTourKept = tourId == 0L &&
                    !editTourViewModel.wasDiscarded() &&
                    !editTourViewModel.uiState.value.isUnmodifiedNewTourDraft()
                if (newTourKept) toursViewModel.onTourCreated()
                // Schaltet den letzten Schritt der Pilot-Tour frei, aber nur, wenn sie gerade läuft und
                // die Tour wirklich bleibt: normales Verlassen ohne aktive Tour soll nicht an sie koppeln.
                if (isCreateFirstTourGuide && newTourKept) {
                    guideController.completeAction(CREATE_FIRST_TOUR_SAVE_ACTION)
                    guideController.next()
                }
                val keptTourId = editTourViewModel.uiState.value.tourId
                if (isCreateFirstTourGuide && newTourKept && keptTourId != null) {
                    // Die Pilot-Tour will als Nächstes den "Station hinzufügen"-FAB der
                    // Tourdetailseite zeigen; normales Verlassen führt sonst zur Tourenliste zurück.
                    navController.popBackStack()
                    navController.navigate(DetailRoute(keptTourId))
                } else {
                    navController.popFrom(entry)
                }
            },
        )
    }
    composable<DetailRoute> { entry ->
        val tourId = entry.toRoute<DetailRoute>().tourId
        val toursViewModel = navController.toursViewModel(entry, repository, vehicles, stations, tracks, checklistTemplates, checklists)
        val weatherMapEnabled by weatherSettings.values.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val resources = LocalResources.current
        val checklistTemplateList by remember(checklistTemplates) { checklistTemplates.observeAll() }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val scope = rememberCoroutineScope()
        val detailTour by remember(repository, tourId) { repository.observeTour(tourId) }
            .collectAsStateWithLifecycle(initialValue = null)
        // Eigene, schlanke Abfrage statt die Karte immer einzuhängen: eine beendete Tour ohne
        // aufgezeichnete Punkte soll in der Liste gar keinen (auch keinen leeren) Platz belegen.
        val trackSummary by remember(tracks, tourId) { tracks.observeSummary(tourId) }
            .collectAsStateWithLifecycle(initialValue = TrackSummary.EMPTY)
        val firstTrackPointLatLon by remember(tracks, tourId) {
            tracks.observeForTour(tourId).map { points -> points.minByOrNull { it.recordedAt }?.let { LatLon(it.latitude, it.longitude) } }
        }.collectAsStateWithLifecycle(initialValue = null)
        TourDetailScreen(
            viewModel = viewModel {
                TourDetailViewModel(
                    repository,
                    vehicles,
                    stations,
                    diaryEntries,
                    checklists,
                    logbook,
                    exchangeRates,
                    countryLookup,
                    attachments,
                    attachmentFileStore,
                    AndroidTourExportFiles(context),
                    tracks,
                    tourId,
                    onTourFinished = { app.restvolt.camperlog.tracking.TrackRecordingService.stopIfTracking(context, tourId) },
                )
            },
            weatherMapEnabled = weatherMapEnabled,
            hasTrack = trackSummary.points > 0,
            firstTrackPoint = firstTrackPointLatLon,
            checklistTemplates = checklistTemplateList,
            trackStatusContent = detailTour?.takeIf { it.endDate == null }?.let {
                { TrackRecordingStatusRow(tourId, trackSettings) }
            },
            trackSectionContent = detailTour?.let { tour ->
                if (tour.endDate != null && trackSummary.points == 0) {
                    null
                } else {
                    { TrackRecordingSection(tourId, tracks, trackSettings, hasEndDate = tour.endDate != null) }
                }
            },
            forceVisibleForTutorial = detailTour?.isDemo == true,
            onBack = { navController.popFrom(entry) },
            onEdit = { navController.navigate(EditRoute(tourId)) },
            onDelete = { tour ->
                if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    toursViewModel.delete(tour)
                    navController.popBackStack()
                }
            },
            onAddStation = { targetTourId, type ->
                // Schaltet den "Station hinzufügen"-Schritt der Pilot-Tour frei, aber nur, wenn sie
                // gerade läuft: normales Anlegen ohne aktive Tour soll nicht an sie koppeln.
                if (guideController.state.value.tour?.id == CREATE_FIRST_TOUR_ID) {
                    guideController.completeAction(CREATE_FIRST_TOUR_STATION_FAB_ACTION)
                }
                navController.navigate(StationEditRoute(tourId = targetTourId, initialType = type.name))
            },
            onOpenStation = { stationId -> navController.navigate(StationDetailRoute(stationId)) },
            onOpenMap = { navController.navigate(TourMapRoute(tourId)) },
            onAddDiaryEntry = { targetTourId -> navController.navigate(DiaryEditRoute(tourId = targetTourId)) },
            onOpenDiaryEntry = { entryId -> navController.navigate(DiaryEditRoute(tourId = tourId, entryId = entryId)) },
            onAddSuggestedChecklistTemplates = {
                scope.launch { suggestedChecklistTemplates(resources).forEach { checklistTemplates.save(it) } }
            },
            onManageChecklistTemplates = { navController.navigate(ChecklistTemplatesRoute) },
            onOpenChecklist = { checklistId -> navController.navigate(ChecklistRoute(checklistId = checklistId, tourId = tourId)) },
        )
    }
    composable<DiaryEditRoute> { entry ->
        val route = entry.toRoute<DiaryEditRoute>()
        val tourDetailViewModel =
            navController.tourDetailViewModel(entry, repository, vehicles, stations, diaryEntries, checklists, logbook, exchangeRates, countryLookup, attachments, attachmentFileStore, tracks)
        DiaryEditScreen(
            viewModel = viewModel {
                DiaryEditViewModel(diaryEntries, repository, route.tourId, route.entryId, createSavedStateHandle())
            },
            onDone = { navController.popFrom(entry) },
            onDelete = { diaryEntry ->
                if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    tourDetailViewModel.deleteDiaryEntry(diaryEntry)
                    navController.popBackStack()
                }
            },
        )
    }
    composable<TourMapRoute> { entry ->
        val route = entry.toRoute<TourMapRoute>()
        val tourDetailViewModel =
            navController.tourDetailViewModel(entry, repository, vehicles, stations, diaryEntries, checklists, logbook, exchangeRates, countryLookup, attachments, attachmentFileStore, tracks)
        val detailState by tourDetailViewModel.uiState.collectAsStateWithLifecycle()
        val loaded = detailState as? DetailUiState.Loaded
        val track by remember(route.tourId) { tracks.observeForTour(route.tourId) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val fallbackTitle = stringResource(R.string.detail_fallback_title)
        MapScreen(
            stations = loaded?.stations ?: emptyList(),
            track = track,
            title = loaded?.tour?.displayTitle(fallbackTitle) ?: fallbackTitle,
            viewModel = viewModel(key = "map_tour_${route.tourId}") { MapViewModel(tileLoader) },
            onBack = { navController.popFrom(entry) },
            onOpenStation = { stationId -> navController.navigate(StationDetailRoute(stationId)) },
        )
    }
    composable<OverviewRoute> { entry ->
        val vehicleId = entry.toRoute<OverviewRoute>().vehicleId
        OverviewScreen(
            viewModel = viewModel { OverviewViewModel(repository, exchangeRates, stations, countryLookup, vehicleId) },
            onBack = { navController.popFrom(entry) },
            onOpenRates = { navController.navigate(RatesRoute) },
        )
    }
}
