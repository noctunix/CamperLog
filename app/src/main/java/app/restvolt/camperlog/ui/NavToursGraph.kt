package app.restvolt.camperlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.restvolt.camperlog.ui.checklists.suggestedChecklistTemplates
import app.restvolt.camperlog.ui.detail.AndroidTourExportFiles
import app.restvolt.camperlog.ui.detail.DetailUiState
import app.restvolt.camperlog.ui.detail.DiaryEditScreen
import app.restvolt.camperlog.ui.detail.DiaryEditViewModel
import app.restvolt.camperlog.ui.detail.TourDetailScreen
import app.restvolt.camperlog.ui.detail.TourDetailViewModel
import app.restvolt.camperlog.ui.detail.TrackRecordingCard
import app.restvolt.camperlog.ui.edit.EditTourScreen
import app.restvolt.camperlog.ui.edit.EditTourViewModel
import app.restvolt.camperlog.ui.map.MapScreen
import app.restvolt.camperlog.ui.map.MapViewModel
import app.restvolt.camperlog.ui.overview.OverviewScreen
import app.restvolt.camperlog.ui.overview.OverviewViewModel
import app.restvolt.camperlog.ui.tours.ToursScreen
import app.restvolt.camperlog.ui.tours.ToursViewModel
import kotlinx.coroutines.launch

/** Navigationsziele: Touren-Reiter mit Tourdetail, Tagebuch, Tourkarte und Übersicht. */
internal fun NavGraphBuilder.toursGraph(
    navController: NavController,
    deps: NavDependencies,
    bottomBar: @Composable () -> Unit,
) = with(deps) {
    composable<ToursRoute> {
        val context = LocalContext.current
        ToursScreen(
            viewModel = viewModel { ToursViewModel(repository, vehicles, stations, VehicleScopeSettings(context)) },
            onAddTour = { navController.navigate(EditRoute()) },
            onOpenOverview = { vehicleId -> navController.navigate(OverviewRoute(vehicleId)) },
            onOpenSearch = { navController.navigate(SearchRoute) },
            onOpenData = { navController.navigate(DataRoute) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
            onOpenTour = { navController.navigate(DetailRoute(it)) },
            onOpenVehicles = { navController.navigate(VehiclesRoute) },
            bottomBar = bottomBar,
        )
    }
    composable<EditRoute> { entry ->
        val tourId = entry.toRoute<EditRoute>().tourId
        val toursViewModel = navController.toursViewModel(entry, repository, vehicles, stations)
        EditTourScreen(
            viewModel = viewModel { EditTourViewModel(repository, vehicles, stations, checklists, tourId, createSavedStateHandle()) },
            onDone = { navController.popFrom(entry) },
            onSaved = {
                if (tourId == 0L) toursViewModel.onTourCreated()
                navController.popFrom(entry)
            },
        )
    }
    composable<DetailRoute> { entry ->
        val tourId = entry.toRoute<DetailRoute>().tourId
        val toursViewModel = navController.toursViewModel(entry, repository, vehicles, stations)
        val weatherMapEnabled by weatherSettings.values.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val resources = LocalResources.current
        val checklistTemplateList by remember(checklistTemplates) { checklistTemplates.observeAll() }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val scope = rememberCoroutineScope()
        val trackPreferences by trackSettings.values.collectAsStateWithLifecycle()
        TourDetailScreen(
            viewModel = viewModel {
                TourDetailViewModel(
                    repository,
                    vehicles,
                    stations,
                    diaryEntries,
                    checklists,
                    exchangeRates,
                    countryLookup,
                    attachments,
                    attachmentFileStore,
                    AndroidTourExportFiles(context),
                    tracks,
                    tourId,
                    onTourFinished = {
                        if (trackSettings.activeRecording?.tourId == tourId) {
                            app.restvolt.camperlog.tracking.TrackRecordingService.stop(context)
                        }
                    },
                )
            },
            weatherMapEnabled = weatherMapEnabled,
            checklistTemplates = checklistTemplateList,
            trackCard = if (trackPreferences.enabled) {
                { TrackRecordingCard(tourId, tracks, trackSettings) }
            } else {
                null
            },
            onBack = { navController.popFrom(entry) },
            onEdit = { navController.navigate(EditRoute(tourId)) },
            onDelete = { tour ->
                if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    toursViewModel.delete(tour)
                    navController.popBackStack()
                }
            },
            onAddStation = { targetTourId, type ->
                navController.navigate(StationEditRoute(tourId = targetTourId, initialType = type.name))
            },
            onOpenStation = { stationId -> navController.navigate(StationDetailRoute(stationId)) },
            onOpenMap = { navController.navigate(TourMapRoute(tourId)) },
            onAddDiaryEntry = { targetTourId -> navController.navigate(DiaryEditRoute(tourId = targetTourId)) },
            onOpenDiaryEntry = { entryId -> navController.navigate(DiaryEditRoute(tourId = tourId, entryId = entryId)) },
            onAddSuggestedChecklistTemplates = {
                scope.launch { suggestedChecklistTemplates(resources).forEach { checklistTemplates.save(it) } }
            },
            onOpenChecklist = { checklistId -> navController.navigate(ChecklistRoute(checklistId = checklistId, tourId = tourId)) },
        )
    }
    composable<DiaryEditRoute> { entry ->
        val route = entry.toRoute<DiaryEditRoute>()
        val tourDetailViewModel =
            navController.tourDetailViewModel(entry, repository, vehicles, stations, diaryEntries, checklists, exchangeRates, countryLookup, attachments, attachmentFileStore, tracks)
        DiaryEditScreen(
            viewModel = viewModel {
                DiaryEditViewModel(diaryEntries, repository, route.tourId, route.entryId, createSavedStateHandle())
            },
            onDone = { navController.popFrom(entry) },
            onSaved = { navController.popFrom(entry) },
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
            navController.tourDetailViewModel(entry, repository, vehicles, stations, diaryEntries, checklists, exchangeRates, countryLookup, attachments, attachmentFileStore, tracks)
        val detailState by tourDetailViewModel.uiState.collectAsStateWithLifecycle()
        val loaded = detailState as? DetailUiState.Loaded
        val track by remember(route.tourId) { tracks.observeForTour(route.tourId) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        MapScreen(
            stations = loaded?.stations ?: emptyList(),
            track = track,
            title = loaded?.tour?.destination ?: stringResource(R.string.detail_fallback_title),
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
