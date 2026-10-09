package app.restvolt.camperlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import app.restvolt.camperlog.data.AndroidLocationPermissionGate
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.ui.detail.StationDetailScreen
import app.restvolt.camperlog.ui.detail.StationDetailViewModel
import app.restvolt.camperlog.ui.edit.EditStationScreen
import app.restvolt.camperlog.ui.edit.EditStationViewModel
import app.restvolt.camperlog.ui.map.MapScreen
import app.restvolt.camperlog.ui.map.MapViewModel
import app.restvolt.camperlog.ui.stations.StationsScreen
import app.restvolt.camperlog.ui.stations.StationsViewModel
import app.restvolt.camperlog.ui.stations.StationsWhatsNewSettings
import kotlinx.coroutines.launch

/** Navigationsziele: Stationen-Reiter mit Karte, Stationsformular und Stationsdetail. */
internal fun NavGraphBuilder.stationsGraph(
    navController: NavController,
    deps: NavDependencies,
    bottomBar: @Composable () -> Unit,
) = with(deps) {
    composable<StationsRoute> {
        val context = LocalContext.current
        var stationsTypePicker by rememberSaveable { mutableStateOf(false) }
        val weatherMapEnabled by weatherSettings.values.collectAsStateWithLifecycle()
        StationsScreen(
            viewModel = viewModel {
                StationsViewModel(stations, repository, vehicles, VehicleScopeSettings(context), StationsWhatsNewSettings(context))
            },
            weatherMapEnabled = weatherMapEnabled,
            onAddStop = { stationsTypePicker = true },
            onOpenTour = { tourId -> navController.navigate(DetailRoute(tourId)) },
            onOpenStation = { stationId -> navController.navigate(StationDetailRoute(stationId, fromStationsTab = true)) },
            onOpenMap = { navController.navigate(StationsMapRoute) },
            onOpenSearch = { navController.navigate(SearchRoute) },
            onOpenData = { navController.navigate(DataRoute) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
            onOpenVehicles = { navController.navigate(VehiclesRoute) },
            bottomBar = bottomBar,
        )
        if (stationsTypePicker) {
            StationTypePickerSheet(
                onSelect = { type ->
                    stationsTypePicker = false
                    navController.navigate(StationEditRoute(initialType = type.name))
                },
                onDismiss = { stationsTypePicker = false },
            )
        }
    }
    composable<StationsMapRoute> { entry ->
        val stationsViewModel = navController.stationsViewModel(entry, stations, repository, vehicles)
        val stationsState by stationsViewModel.uiState.collectAsStateWithLifecycle()
        MapScreen(
            stations = stationsState.stations,
            title = stringResource(R.string.stations_title),
            viewModel = viewModel(key = "map_stations") { MapViewModel(tileLoader) },
            onBack = { navController.popFrom(entry) },
            onOpenStation = { stationId -> navController.navigate(StationDetailRoute(stationId, fromStationsTab = true)) },
        )
    }
    composable<StationEditRoute> { entry ->
        val route = entry.toRoute<StationEditRoute>()
        val stationEditContext = LocalContext.current
        // Wohin die Speichermeldung beim Bearbeiten einer bestehenden Station geht: die kam immer
        // vom Stationsdetail, das Speichern führt dort auch wieder hin.
        val onStationSaved: (Set<StationService>) -> Unit = when {
            route.stationId != 0L && navController.hasRoute<StationDetailRoute>() ->
                navController.stationDetailViewModel(entry, stations, repository, route.stationId)::onStationSaved
            else -> { _ -> }
        }
        val stationEditViewModel = viewModel {
            EditStationViewModel(
                repository = stations,
                tours = repository,
                vehicles = vehicles,
                attachments = attachments,
                fileStore = attachmentFileStore,
                stationId = route.stationId,
                initialTourId = route.tourId,
                initialType = route.initialType?.let(StationType::valueOf),
                prefillLatitude = route.prefillLatitude,
                prefillLongitude = route.prefillLongitude,
                prefillPlace = route.prefillPlace,
                locationProvider = locationProvider,
                locationPermissionGate = AndroidLocationPermissionGate(stationEditContext),
                weatherProvider = weatherProvider,
                placeSearchProvider = placeSearchProvider,
                savedStateHandle = createSavedStateHandle(),
            )
        }
        EditStationScreen(
            viewModel = stationEditViewModel,
            locationSettings = locationSettings,
            weatherSettings = weatherSettings,
            attachments = attachments,
            attachmentFileStore = attachmentFileStore,
            attachmentPickers = attachmentPickers,
            onDone = { navController.popFrom(entry) },
            onSaved = { loggedServices ->
                if (route.stationId == 0L) {
                    // Eine neue Station landet auf ihrer Detailseite, damit sofort Fotos angehängt
                    // werden können; Zurück führt dann zur Herkunft des Formulars (Tourdetail,
                    // Stationen-Reiter oder, bei einem `geo:`-Link ohne einen der beiden im Stapel,
                    // zu dessen eigener Herkunft).
                    val savedId = stationEditViewModel.uiState.value.savedStationId
                    val fromStationsTab = !(route.tourId != null && navController.hasRoute<DetailRoute>())
                    navController.popBackStack()
                    navController.navigate(
                        StationDetailRoute(savedId, fromStationsTab = fromStationsTab, justSavedLoggedServices = loggedServices.map { it.name }),
                    )
                } else {
                    onStationSaved(loggedServices)
                    navController.popFrom(entry)
                }
            },
        )
    }
    composable<StationDetailRoute> { entry ->
        val route = entry.toRoute<StationDetailRoute>()
        // Je nach Herkunft trägt entweder der Stationen-Reiter oder die Tourdetailseite den Löschkanal;
        // eine frisch angelegte Station kann ohne einen der beiden hier landen (z. B. über einen
        // `geo:`-Link ohne Herkunft im Stapel), dann löscht sie direkt ohne "Rückgängig".
        val fallbackDeleteScope = rememberCoroutineScope()
        val deleteStation: (Station) -> Unit = when {
            route.fromStationsTab && navController.hasRoute<StationsRoute>() ->
                navController.stationsViewModel(entry, stations, repository, vehicles)::deleteStation
            navController.hasRoute<DetailRoute>() ->
                navController.tourDetailViewModel(entry, repository, vehicles, stations, diaryEntries, checklists, exchangeRates, countryLookup, attachments, attachmentFileStore, tracks)::deleteStation
            else -> { station -> fallbackDeleteScope.launch { stations.delete(station.id) } }
        }
        StationDetailScreen(
            viewModel = viewModel {
                StationDetailViewModel(stations, repository, route.stationId, route.justSavedLoggedServices?.map(StationService::valueOf)?.toSet())
            },
            attachments = attachments,
            attachmentFileStore = attachmentFileStore,
            attachmentPickers = attachmentPickers,
            onBack = { navController.popFrom(entry) },
            onEdit = { navController.navigate(StationEditRoute(stationId = route.stationId)) },
            onOpenTour = if (route.fromStationsTab) {
                { tourId -> navController.navigate(DetailRoute(tourId)) }
            } else {
                { navController.popFrom(entry) }
            },
            onDelete = { station ->
                if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    deleteStation(station)
                    navController.popBackStack()
                }
            },
        )
    }
}
