package app.restvolt.camperlog.ui

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.restvolt.camperlog.R
import app.restvolt.camperlog.BuildConfig
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.data.AndroidBackupFolderWriter
import app.restvolt.camperlog.data.AndroidLocationPermissionGate
import app.restvolt.camperlog.data.AndroidLocationProvider
import app.restvolt.camperlog.data.AndroidTileLoader
import app.restvolt.camperlog.data.AndroidWeatherProvider
import app.restvolt.camperlog.data.AssetCountryLookupRepository
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.data.BackupFolderWriter
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.CountryLookupRepository
import app.restvolt.camperlog.domain.DiaryEntryRepository
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.GeoIntentLocation
import app.restvolt.camperlog.domain.LocationProvider
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TileLoader
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.VehicleDocumentRepository
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.WeatherProvider
import app.restvolt.camperlog.domain.camperLogUserAgent
import app.restvolt.camperlog.domain.documentReminders
import app.restvolt.camperlog.domain.dueReminders
import app.restvolt.camperlog.domain.isMapAvailable
import app.restvolt.camperlog.domain.shouldShowKeepAndroidOpen
import app.restvolt.camperlog.ui.about.AboutScreen
import app.restvolt.camperlog.ui.about.KeepAndroidOpenDialog
import app.restvolt.camperlog.ui.about.KeepAndroidOpenSettings
import app.restvolt.camperlog.ui.attachments.AndroidAttachmentPickers
import app.restvolt.camperlog.ui.attachments.AttachmentPickers
import app.restvolt.camperlog.ui.settings.LocationSettings
import app.restvolt.camperlog.ui.settings.NotificationSettings
import app.restvolt.camperlog.ui.settings.WeatherSettings
import app.restvolt.camperlog.ui.data.AndroidDataFiles
import app.restvolt.camperlog.ui.data.BackupSettings
import app.restvolt.camperlog.ui.data.DataScreen
import app.restvolt.camperlog.ui.data.DataViewModel
import app.restvolt.camperlog.ui.detail.DetailUiState
import app.restvolt.camperlog.ui.detail.StationDetailScreen
import app.restvolt.camperlog.ui.detail.StationDetailViewModel
import app.restvolt.camperlog.ui.detail.AndroidTourExportFiles
import app.restvolt.camperlog.ui.detail.TourDetailScreen
import app.restvolt.camperlog.ui.detail.TourDetailViewModel
import app.restvolt.camperlog.ui.edit.EditStationScreen
import app.restvolt.camperlog.ui.edit.EditStationViewModel
import app.restvolt.camperlog.ui.edit.EditTourScreen
import app.restvolt.camperlog.ui.edit.EditTourViewModel
import app.restvolt.camperlog.ui.edit.EditVehicleScreen
import app.restvolt.camperlog.ui.edit.EditVehicleViewModel
import app.restvolt.camperlog.ui.edit.RepairEditScreen
import app.restvolt.camperlog.ui.edit.RepairEditViewModel
import app.restvolt.camperlog.ui.logbook.LogHistoryScreen
import app.restvolt.camperlog.ui.logbook.LogHistoryViewModel
import app.restvolt.camperlog.ui.logbook.LogbookScreen
import app.restvolt.camperlog.ui.logbook.LogbookViewModel
import app.restvolt.camperlog.ui.map.MapScreen
import app.restvolt.camperlog.ui.map.MapViewModel
import app.restvolt.camperlog.ui.onboarding.IntroductionSettings
import app.restvolt.camperlog.ui.onboarding.IntroductionTourScreen
import app.restvolt.camperlog.ui.overview.OverviewScreen
import app.restvolt.camperlog.ui.overview.OverviewViewModel
import app.restvolt.camperlog.ui.rates.RateEditScreen
import app.restvolt.camperlog.ui.rates.RateEditViewModel
import app.restvolt.camperlog.ui.rates.RatesScreen
import app.restvolt.camperlog.ui.rates.RatesViewModel
import app.restvolt.camperlog.ui.settings.SettingsScreen
import app.restvolt.camperlog.ui.stations.StationsScreen
import app.restvolt.camperlog.ui.stations.StationsViewModel
import app.restvolt.camperlog.ui.stations.StationsWhatsNewSettings
import app.restvolt.camperlog.ui.theme.ReminderSettings
import app.restvolt.camperlog.ui.theme.ThemeMode
import app.restvolt.camperlog.ui.tours.ToursScreen
import app.restvolt.camperlog.ui.tours.ToursViewModel
import app.restvolt.camperlog.ui.vehicle.VehicleDocumentDetailScreen
import app.restvolt.camperlog.ui.vehicle.VehicleDocumentDetailViewModel
import app.restvolt.camperlog.ui.vehicle.VehicleDocumentEditScreen
import app.restvolt.camperlog.ui.vehicle.VehicleDocumentEditViewModel
import app.restvolt.camperlog.ui.vehicle.VehicleScreen
import app.restvolt.camperlog.ui.vehicle.VehicleViewModel
import app.restvolt.camperlog.ui.vehicle.WhereAmIViewModel
import app.restvolt.camperlog.ui.vehicles.VehiclesScreen
import app.restvolt.camperlog.ui.vehicles.VehiclesViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

@Serializable
internal object ToursRoute

/** Stationen-Reiter: fahrzeugübergreifende Liste mit Suche, Filtern und FAB. */
@Serializable
internal object StationsRoute

@Serializable
internal object LogbookRoute

/** Verlauf einer Bordbuch-Art eines Fahrzeugs. */
@Serializable
internal data class LogHistoryRoute(val vehicleId: Long, val typeName: String) {
    /** Enums als Routenargument bräuchten Keep-Regeln gegen R8; daher wird der Name übergeben. */
    val type: LogType get() = LogType.valueOf(typeName)
}

@Serializable
internal object VehicleRoute

/** Fahrzeugverwaltung: Liste, Anlegen, Bearbeiten und Löschen. */
@Serializable
internal object VehiclesRoute

/** Fahrzeugformular; [vehicleId] 0 legt ein neues Fahrzeug an. */
@Serializable
internal data class VehicleEditRoute(val vehicleId: Long = 0)

/** Reparaturformular eines Fahrzeugs; [repairId] 0 legt eine neue Reparatur an. */
@Serializable
internal data class RepairEditRoute(val vehicleId: Long, val repairId: Long = 0)

/** Formular eines Fahrzeugdokuments; [documentId] 0 legt ein neues Dokument an. */
@Serializable
internal data class DocumentEditRoute(val vehicleId: Long, val documentId: Long = 0)

/** Detailseite eines Fahrzeugdokuments. */
@Serializable
internal data class DocumentDetailRoute(val vehicleId: Long, val documentId: Long)

@Serializable
internal data class EditRoute(val tourId: Long = 0)

@Serializable
internal data class DetailRoute(val tourId: Long)

/** Karte der Stationen einer Tour; nur erreichbar, wenn [isMapAvailable] zutrifft. */
@Serializable
internal data class TourMapRoute(val tourId: Long)

/** Karte des aktuellen Filters des Stationen-Reiters. */
@Serializable
internal object StationsMapRoute

/**
 * Stationsformular; [stationId] 0 legt eine neue Station an. [initialType] (Name von [StationType])
 * und die Vorbelegung aus Koordinaten/Ort gelten nur dafür.
 */
@Serializable
internal data class StationEditRoute(
    val stationId: Long = 0,
    val tourId: Long? = null,
    val initialType: String? = null,
    val prefillLatitude: Double? = null,
    val prefillLongitude: Double? = null,
    val prefillPlace: String? = null,
)

/**
 * [fromStationsTab] unterscheidet, ob die Station vom Stationen-Reiter aus geöffnet wurde (dann hat
 * [StationsRoute] den Löschkanal) oder von der Tourdetailseite aus (dann [DetailRoute]). [justSavedLoggedServices]
 * ist gesetzt, wenn diese Seite direkt nach dem ersten Anlegen der Station erreicht wird (Namen von
 * [StationService]), damit die Speicher-Snackbar hier statt auf der Herkunftsseite erscheint.
 */
@Serializable
internal data class StationDetailRoute(
    val stationId: Long,
    val fromStationsTab: Boolean = false,
    val justSavedLoggedServices: List<String>? = null,
)

/** [vehicleId] `null` zeigt die Kennzahlen aller Fahrzeuge, sonst nur die von [vehicleId]. */
@Serializable
internal data class OverviewRoute(val vehicleId: Long? = null)

@Serializable
internal object RatesRoute

@Serializable
internal object SettingsRoute

@Serializable
internal object AboutRoute

@Serializable
internal object DataRoute

/** Kursformular; ohne [currencyCode] wird ein neuer Kurs mit frei wählbarer Währung angelegt. */
@Serializable
internal data class RateEditRoute(val currencyCode: String? = null)

/** Navigationsgraph der App mit Start auf der Tourenliste. */
@Composable
fun CamperLogNavHost(
    repository: TourRepository,
    vehicles: VehicleRepository,
    logbook: LogRepository,
    stations: StationRepository,
    exchangeRates: ExchangeRateRepository,
    documents: VehicleDocumentRepository,
    diaryEntries: DiaryEntryRepository,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    backupImporter: BackupImporter,
    themeMode: ThemeMode,
    canShowStartDialogs: Boolean = true,
    /** Aus einem eingehenden `geo:`-Link gelesener Ort; `null` außerhalb dieses Starts. */
    pendingGeoIntent: GeoIntentLocation? = null,
    /** [pendingGeoIntent] wurde übernommen und soll nicht erneut ausgelöst werden, z. B. bei einer Drehung. */
    onGeoIntentHandled: () -> Unit = {},
    /** Fahrzeug-id aus einer getippten Wartungs-Benachrichtigung; `null` außerhalb dieses Starts. */
    pendingVehicleId: Long? = null,
    /** [pendingVehicleId] wurde übernommen und soll nicht erneut ausgelöst werden. */
    onVehicleIntentHandled: () -> Unit = {},
    /** Fahrzeugdokument-id aus einer getippten Ablauf-Erinnerung; `null` außerhalb dieses Starts. */
    pendingDocumentId: Long? = null,
    /** [pendingDocumentId] wurde übernommen und soll nicht erneut ausgelöst werden. */
    onDocumentIntentHandled: () -> Unit = {},
    /** Direkt der Daten-Screen soll geöffnet werden, aus einer getippten Sicherungs-Erinnerung. */
    pendingOpenData: Boolean = false,
    /** [pendingOpenData] wurde übernommen und soll nicht erneut ausgelöst werden. */
    onOpenDataHandled: () -> Unit = {},
    /** Standorthardware für das Stationsformular und "Wo bin ich?"; in Tests ein Fake. */
    locationProvider: LocationProvider = AndroidLocationProvider(LocalContext.current),
    /** Wetterabfrage für die "Wetter"-Karte im Stationsformular; in Tests ein Fake. */
    weatherProvider: WeatherProvider = AndroidWeatherProvider(userAgent = camperLogUserAgent(BuildConfig.VERSION_NAME)),
    /** Kachellader der Karte; in Tests ein Fake. */
    tileLoader: TileLoader = AndroidTileLoader(userAgent = camperLogUserAgent(BuildConfig.VERSION_NAME)),
    /** Offline-Ländererkennung für Tourdetail und Übersicht; in Tests ein Fake. */
    countryLookup: CountryLookupRepository = AssetCountryLookupRepository(LocalContext.current),
    /** Kamera-/Galerie-/Dokument-Auswahl der Foto-Streifen; in Tests ein Fake ohne echten System-Dialog. */
    attachmentPickers: AttachmentPickers = AndroidAttachmentPickers,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val context = LocalContext.current
    val reminderSettings = remember { ReminderSettings(context) }
    val reminderPreferences by reminderSettings.values.collectAsStateWithLifecycle()
    val locationSettings = remember { LocationSettings(context) }
    val weatherSettings = remember { WeatherSettings(context) }
    val notificationSettings = remember { NotificationSettings(context) }
    val backupSettings = remember { BackupSettings(context) }
    val backupFolderWriter = remember { AndroidBackupFolderWriter(context) }
    val currentVehicleFlow = remember(vehicles) { vehicles.observeCurrentVehicle() }
    val currentVehicle by currentVehicleFlow.collectAsStateWithLifecycle(initialValue = null)
    val currentVehicleDocumentsFlow = remember(documents, currentVehicle?.id) {
        currentVehicle?.id?.let { documents.observeForVehicle(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val currentVehicleDocuments by currentVehicleDocumentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val reminderCount = currentVehicle?.let { vehicle ->
        dueReminders(vehicle, LocalDate.now(), reminderPreferences.leadDays, reminderPreferences.oilChangeIntervalMonths).size +
            documentReminders(currentVehicleDocuments, LocalDate.now(), reminderPreferences.leadDays).size
    } ?: 0
    val bottomBar: @Composable () -> Unit = { CamperLogBottomBar(navController, currentDestination, reminderCount) }

    val introductionSettings = remember { IntroductionSettings(context) }
    var showIntroductionTour by rememberSaveable { mutableStateOf(false) }

    val keepAndroidOpenSettings = remember { KeepAndroidOpenSettings(context) }
    var showStartupKeepAndroidOpen by rememberSaveable { mutableStateOf(false) }
    if (canShowStartDialogs) {
        LaunchedEffect(Unit) {
            keepAndroidOpenSettings.recordFirstLaunchIfNeeded()
            if (!introductionSettings.seen) {
                // Die Einführungstour hat Vorrang: der Hinweis erscheint nie zusammen mit ihr.
                showIntroductionTour = true
            } else {
                val hasData = repository.hasTours() || logbook.hasEntries()
                showStartupKeepAndroidOpen = shouldShowKeepAndroidOpen(keepAndroidOpenSettings.state, hasData, Instant.now())
            }
        }
    }

    if (showIntroductionTour) {
        IntroductionTourScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            reminderSettings = reminderSettings,
            locationSettings = locationSettings,
            weatherSettings = weatherSettings,
            onFinished = {
                introductionSettings.seen = true
                showIntroductionTour = false
            },
        )
        return
    }

    // Ein geo:-Link startet die Stationsaufnahme mit Typauswahl, ausgelöst nach der Einführungstour,
    // nie zusammen mit ihr.
    var geoLocationForPicker by remember { mutableStateOf<GeoIntentLocation?>(null) }
    LaunchedEffect(pendingGeoIntent) {
        if (pendingGeoIntent != null) {
            geoLocationForPicker = pendingGeoIntent
            onGeoIntentHandled()
        }
    }

    // Eine getippte Wartungs-Benachrichtigung wählt das Fahrzeug aus und öffnet den Reiter.
    LaunchedEffect(pendingVehicleId) {
        val vehicleId = pendingVehicleId
        if (vehicleId != null) {
            vehicles.setCurrentVehicle(vehicleId)
            navController.navigateToTab(VehicleRoute)
            onVehicleIntentHandled()
        }
    }

    // Eine getippte Ablauf-Erinnerung wählt das Fahrzeug des Dokuments aus und öffnet dessen Detailseite.
    LaunchedEffect(pendingDocumentId) {
        val documentId = pendingDocumentId
        if (documentId != null) {
            val document = documents.allDocuments().firstOrNull { it.id == documentId }
            if (document != null) {
                vehicles.setCurrentVehicle(document.vehicleId)
                navController.navigateToTab(VehicleRoute)
                navController.navigate(DocumentDetailRoute(document.vehicleId, documentId))
            }
            onDocumentIntentHandled()
        }
    }

    // Eine getippte Sicherungs-Erinnerung öffnet direkt den Daten-Screen.
    LaunchedEffect(pendingOpenData) {
        if (pendingOpenData) {
            navController.navigate(DataRoute)
            onOpenDataHandled()
        }
    }

    NavHost(navController, startDestination = ToursRoute) {
        composable<ToursRoute> {
            val context = LocalContext.current
            ToursScreen(
                viewModel = viewModel { ToursViewModel(repository, vehicles, stations, VehicleScopeSettings(context)) },
                onAddTour = { navController.navigate(EditRoute()) },
                onOpenOverview = { vehicleId -> navController.navigate(OverviewRoute(vehicleId)) },
                onOpenData = { navController.navigate(DataRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenTour = { navController.navigate(DetailRoute(it)) },
                onOpenVehicles = { navController.navigate(VehiclesRoute) },
                bottomBar = bottomBar,
            )
        }
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
        composable<LogbookRoute> {
            LogbookScreen(
                viewModel = viewModel { LogbookViewModel(logbook, vehicles) },
                onOpenHistory = { vehicleId, type -> navController.navigate(LogHistoryRoute(vehicleId, type.name)) },
                onOpenData = { navController.navigate(DataRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenVehicles = { navController.navigate(VehiclesRoute) },
                bottomBar = bottomBar,
            )
        }
        composable<LogHistoryRoute> { entry ->
            val route = entry.toRoute<LogHistoryRoute>()
            LogHistoryScreen(
                viewModel = viewModel { LogHistoryViewModel(logbook, stations, route.vehicleId, route.type) },
                attachments = attachments,
                attachmentFileStore = attachmentFileStore,
                attachmentPickers = attachmentPickers,
                onBack = { navController.popFrom(entry) },
            )
        }
        composable<VehicleRoute> {
            val vehicleContext = LocalContext.current
            val locationEnabled by locationSettings.values.collectAsStateWithLifecycle()
            VehicleScreen(
                viewModel = viewModel { VehicleViewModel(vehicles, documents) },
                whereAmIViewModel = viewModel { WhereAmIViewModel(locationProvider, AndroidLocationPermissionGate(vehicleContext)) },
                locationEnabled = locationEnabled,
                reminderSettings = reminderSettings,
                onOpenData = { navController.navigate(DataRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenVehicles = { navController.navigate(VehiclesRoute) },
                onEditVehicle = { id -> navController.navigate(VehicleEditRoute(id)) },
                onAddRepair = { vehicleId -> navController.navigate(RepairEditRoute(vehicleId)) },
                onOpenRepair = { vehicleId, repairId -> navController.navigate(RepairEditRoute(vehicleId, repairId)) },
                onAddDocument = { vehicleId -> navController.navigate(DocumentEditRoute(vehicleId)) },
                onOpenDocument = { vehicleId, documentId -> navController.navigate(DocumentDetailRoute(vehicleId, documentId)) },
                bottomBar = bottomBar,
            )
        }
        composable<VehiclesRoute> { entry ->
            VehiclesScreen(
                viewModel = viewModel { VehiclesViewModel(vehicles) },
                onBack = { navController.popFrom(entry) },
                onAdd = { navController.navigate(VehicleEditRoute()) },
                onEdit = { id -> navController.navigate(VehicleEditRoute(id)) },
            )
        }
        composable<VehicleEditRoute> { entry ->
            val vehicleId = entry.toRoute<VehicleEditRoute>().vehicleId
            EditVehicleScreen(
                viewModel = viewModel { EditVehicleViewModel(vehicles, vehicleId, createSavedStateHandle()) },
                onDone = { navController.popFrom(entry) },
                onSaved = { navController.popFrom(entry) },
            )
        }
        composable<RepairEditRoute> { entry ->
            val route = entry.toRoute<RepairEditRoute>()
            val vehicleViewModel = navController.vehicleViewModel(entry, vehicles, documents)
            RepairEditScreen(
                viewModel = viewModel {
                    RepairEditViewModel(vehicles, exchangeRates, route.vehicleId, route.repairId, createSavedStateHandle())
                },
                attachments = attachments,
                attachmentFileStore = attachmentFileStore,
                attachmentPickers = attachmentPickers,
                onDone = { navController.popFrom(entry) },
                onSaved = { navController.popFrom(entry) },
                onDelete = { repair ->
                    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        vehicleViewModel.deleteRepair(repair)
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<DocumentEditRoute> { entry ->
            val route = entry.toRoute<DocumentEditRoute>()
            val documentEditViewModel = viewModel {
                VehicleDocumentEditViewModel(documents, route.vehicleId, route.documentId, createSavedStateHandle())
            }
            VehicleDocumentEditScreen(
                viewModel = documentEditViewModel,
                attachments = attachments,
                attachmentFileStore = attachmentFileStore,
                attachmentPickers = attachmentPickers,
                onDone = { navController.popFrom(entry) },
                onSaved = {
                    if (route.documentId == 0L) {
                        val savedId = documentEditViewModel.uiState.value.savedDocumentId
                        navController.popBackStack()
                        navController.navigate(DocumentDetailRoute(route.vehicleId, savedId))
                    } else {
                        navController.popFrom(entry)
                    }
                },
            )
        }
        composable<DocumentDetailRoute> { entry ->
            val route = entry.toRoute<DocumentDetailRoute>()
            val detailViewModel = viewModel { VehicleDocumentDetailViewModel(documents, route.vehicleId, route.documentId) }
            VehicleDocumentDetailScreen(
                viewModel = detailViewModel,
                attachments = attachments,
                attachmentFileStore = attachmentFileStore,
                attachmentPickers = attachmentPickers,
                onBack = { navController.popFrom(entry) },
                onEdit = { navController.navigate(DocumentEditRoute(route.vehicleId, route.documentId)) },
                onDelete = { document ->
                    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        detailViewModel.delete(document)
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<EditRoute> { entry ->
            val tourId = entry.toRoute<EditRoute>().tourId
            val toursViewModel = navController.toursViewModel(entry, repository, vehicles, stations)
            EditTourScreen(
                viewModel = viewModel { EditTourViewModel(repository, vehicles, stations, tourId, createSavedStateHandle()) },
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
            TourDetailScreen(
                viewModel = viewModel {
                    TourDetailViewModel(
                        repository,
                        vehicles,
                        stations,
                        exchangeRates,
                        countryLookup,
                        attachments,
                        attachmentFileStore,
                        AndroidTourExportFiles(context),
                        tourId,
                    )
                },
                weatherMapEnabled = weatherMapEnabled,
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
            )
        }
        composable<TourMapRoute> { entry ->
            val route = entry.toRoute<TourMapRoute>()
            val tourDetailViewModel =
                navController.tourDetailViewModel(entry, repository, vehicles, stations, exchangeRates, countryLookup, attachments, attachmentFileStore)
            val detailState by tourDetailViewModel.uiState.collectAsStateWithLifecycle()
            val loaded = detailState as? DetailUiState.Loaded
            MapScreen(
                stations = loaded?.stations ?: emptyList(),
                title = loaded?.tour?.destination ?: stringResource(R.string.detail_fallback_title),
                viewModel = viewModel(key = "map_tour_${route.tourId}") { MapViewModel(tileLoader) },
                onBack = { navController.popFrom(entry) },
                onOpenStation = { stationId -> navController.navigate(StationDetailRoute(stationId)) },
            )
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
                    stationId = route.stationId,
                    initialTourId = route.tourId,
                    initialType = route.initialType?.let(StationType::valueOf),
                    prefillLatitude = route.prefillLatitude,
                    prefillLongitude = route.prefillLongitude,
                    prefillPlace = route.prefillPlace,
                    locationProvider = locationProvider,
                    locationPermissionGate = AndroidLocationPermissionGate(stationEditContext),
                    weatherProvider = weatherProvider,
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
                    navController.tourDetailViewModel(entry, repository, vehicles, stations, exchangeRates, countryLookup, attachments, attachmentFileStore)::deleteStation
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
        composable<OverviewRoute> { entry ->
            val vehicleId = entry.toRoute<OverviewRoute>().vehicleId
            OverviewScreen(
                viewModel = viewModel { OverviewViewModel(repository, exchangeRates, stations, countryLookup, vehicleId) },
                onBack = { navController.popFrom(entry) },
                onOpenRates = { navController.navigate(RatesRoute) },
            )
        }
        composable<RatesRoute> { entry ->
            RatesScreen(
                viewModel = viewModel { RatesViewModel(exchangeRates, repository) },
                onBack = { navController.popFrom(entry) },
                onEditRate = { navController.navigate(RateEditRoute(it)) },
            )
        }
        composable<DataRoute> { entry ->
            val context = LocalContext.current
            DataScreen(
                viewModel = viewModel {
                    DataViewModel(
                        repository,
                        exchangeRates,
                        vehicles,
                        logbook,
                        stations,
                        documents,
                        diaryEntries,
                        attachments,
                        attachmentFileStore,
                        backupImporter,
                        AndroidDataFiles(context),
                        backupFolderWriter,
                        onBackupSaved = backupSettings::recordBackupMade,
                    )
                },
                backupSettings = backupSettings,
                notificationSettings = notificationSettings,
                folderWriter = backupFolderWriter,
                onBack = { navController.popFrom(entry) },
            )
        }
        composable<SettingsRoute> { entry ->
            SettingsScreen(
                viewModel = viewModel { RatesViewModel(exchangeRates, repository) },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                reminderSettings = reminderSettings,
                notificationSettings = notificationSettings,
                locationSettings = locationSettings,
                weatherSettings = weatherSettings,
                onBack = { navController.popFrom(entry) },
                onOpenRates = { navController.navigate(RatesRoute) },
                onOpenAbout = { navController.navigate(AboutRoute) },
            )
        }
        composable<AboutRoute> { entry ->
            AboutScreen(
                onBack = { navController.popFrom(entry) },
                onShowIntroductionAgain = { showIntroductionTour = true },
            )
        }
        composable<RateEditRoute> { entry ->
            val currencyCode = entry.toRoute<RateEditRoute>().currencyCode
            RateEditScreen(
                viewModel = viewModel { RateEditViewModel(exchangeRates, currencyCode, createSavedStateHandle()) },
                onDone = { navController.popFrom(entry) },
            )
        }
    }

    geoLocationForPicker?.let { location ->
        StationTypePickerSheet(
            onSelect = { type ->
                geoLocationForPicker = null
                navController.navigate(
                    StationEditRoute(
                        initialType = type.name,
                        prefillLatitude = location.latitude,
                        prefillLongitude = location.longitude,
                        prefillPlace = location.label,
                    ),
                )
            },
            onDismiss = { geoLocationForPicker = null },
        )
    }

    if (showStartupKeepAndroidOpen) {
        KeepAndroidOpenDialog(
            onSupported = {
                keepAndroidOpenSettings.markShown()
                keepAndroidOpenSettings.markSupported()
                showStartupKeepAndroidOpen = false
            },
            onDismiss = {
                keepAndroidOpenSettings.markShown()
                showStartupKeepAndroidOpen = false
            },
        )
    }
}

/** Untere Navigationsleiste der vier Hauptreiter; erneutes Tippen kehrt zur Wurzel des Reiters zurück. */
@Composable
private fun CamperLogBottomBar(navController: NavController, current: NavDestination?, reminderCount: Int) {
    NavigationBar {
        NavigationBarItem(
            selected = current.isOnTab<ToursRoute>(),
            onClick = { navController.navigateToTab(ToursRoute) },
            icon = { Icon(painterResource(R.drawable.ic_route), contentDescription = null) },
            label = { NavLabel(stringResource(R.string.nav_tours)) },
        )
        NavigationBarItem(
            selected = current.isOnTab<StationsRoute>(),
            onClick = { navController.navigateToTab(StationsRoute) },
            icon = { Icon(painterResource(R.drawable.ic_location_on), contentDescription = null) },
            label = { NavLabel(stringResource(R.string.nav_stops)) },
        )
        NavigationBarItem(
            selected = current.isOnTab<LogbookRoute>(),
            onClick = { navController.navigateToTab(LogbookRoute) },
            icon = { Icon(painterResource(R.drawable.ic_book), contentDescription = null) },
            label = { NavLabel(stringResource(R.string.nav_logbook)) },
        )
        val vehicleLabel = stringResource(R.string.nav_vehicle)
        val vehicleItemModifier = if (reminderCount > 0) {
            val description = pluralStringResource(R.plurals.nav_vehicle_reminders, reminderCount, vehicleLabel, reminderCount)
            Modifier.semantics(mergeDescendants = true) { contentDescription = description }
        } else {
            Modifier
        }
        NavigationBarItem(
            selected = current.isOnTab<VehicleRoute>(),
            onClick = { navController.navigateToTab(VehicleRoute) },
            icon = {
                BadgedBox(badge = { if (reminderCount > 0) Badge() }) {
                    Icon(painterResource(R.drawable.ic_directions_car), contentDescription = null)
                }
            },
            label = { NavLabel(vehicleLabel) },
            modifier = vehicleItemModifier,
        )
    }
}

/**
 * Beschriftung eines Hauptreiters, einzeilig und bei Bedarf verkleinert: mit 4 Reitern bricht
 * "Stationen"/"Bordbuch" sonst schon bei 1.5x mittendrin ab, weil Compose nicht trennt.
 */
@Composable
private fun NavLabel(text: String) {
    // maxFontSize muss auf die reguläre Labelgröße gedeckelt werden: ohne das wächst autoSize
    // auf seinen Standard von 112.sp, weil NavigationBarItem dem Label keine feste Breite vorgibt.
    val labelSize = MaterialTheme.typography.labelMedium.fontSize
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = labelSize),
    )
}

private inline fun <reified T : Any> NavDestination?.isOnTab(): Boolean = this?.hierarchy?.any { it.hasRoute<T>() } == true

/** Navigiert zu einem Hauptreiter nach dem üblichen Material-Muster für Bottom-Navigation. */
private inline fun <reified T : Any> NavController.navigateToTab(route: T) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Das [ToursViewModel] der Startseite, damit Detail und Formular dort Snackbar-Meldungen auslösen.
 * Die Startseite liegt als Startziel immer unten im Back Stack.
 */
@Composable
private fun NavController.toursViewModel(
    entry: NavBackStackEntry,
    repository: TourRepository,
    vehicles: VehicleRepository,
    stations: StationRepository,
): ToursViewModel {
    val toursEntry = remember(entry) { getBackStackEntry<ToursRoute>() }
    val context = LocalContext.current
    return viewModel(viewModelStoreOwner = toursEntry) { ToursViewModel(repository, vehicles, stations, VehicleScopeSettings(context)) }
}

/**
 * Das [VehicleViewModel] des Fahrzeug-Reiters, damit das Reparaturformular dort die Löschmeldung
 * auslöst. [RepairEditRoute] liegt immer über [VehicleRoute] im Stapel, da nur von dort erreichbar.
 */
@Composable
private fun NavController.vehicleViewModel(entry: NavBackStackEntry, vehicles: VehicleRepository, documents: VehicleDocumentRepository): VehicleViewModel {
    val vehicleEntry = remember(entry) { getBackStackEntry<VehicleRoute>() }
    return viewModel(viewModelStoreOwner = vehicleEntry) { VehicleViewModel(vehicles, documents) }
}

/**
 * Das [TourDetailViewModel] der Tourdetailseite, damit das Stationsdetail dort die Löschmeldung
 * auslöst. [StationDetailRoute] liegt immer über [DetailRoute] im Stapel, da nur von dort erreichbar.
 */
@Composable
private fun NavController.tourDetailViewModel(
    entry: NavBackStackEntry,
    tours: TourRepository,
    vehicles: VehicleRepository,
    stations: StationRepository,
    exchangeRates: ExchangeRateRepository,
    countryLookup: CountryLookupRepository,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
): TourDetailViewModel {
    val detailEntry = remember(entry) { getBackStackEntry<DetailRoute>() }
    val tourId = detailEntry.toRoute<DetailRoute>().tourId
    val context = LocalContext.current
    return viewModel(viewModelStoreOwner = detailEntry) {
        TourDetailViewModel(tours, vehicles, stations, exchangeRates, countryLookup, attachments, attachmentFileStore, AndroidTourExportFiles(context), tourId)
    }
}

/**
 * Das [StationsViewModel] des Stationen-Reiters, damit das Stationsdetail dort die Löschmeldung
 * auslöst, wenn es vom Stationen-Reiter aus geöffnet wurde ([StationDetailRoute.fromStationsTab]).
 */
@Composable
private fun NavController.stationsViewModel(
    entry: NavBackStackEntry,
    stations: StationRepository,
    tours: TourRepository,
    vehicles: VehicleRepository,
): StationsViewModel {
    val stationsEntry = remember(entry) { getBackStackEntry<StationsRoute>() }
    val context = LocalContext.current
    return viewModel(viewModelStoreOwner = stationsEntry) {
        StationsViewModel(stations, tours, vehicles, VehicleScopeSettings(context), StationsWhatsNewSettings(context))
    }
}

/**
 * Das [StationDetailViewModel] der Stationsdetailseite, damit das Bearbeiten dort die Speichermeldung
 * auslöst. [StationEditRoute] liegt in diesem Fall immer über [StationDetailRoute] im Stapel.
 */
@Composable
private fun NavController.stationDetailViewModel(
    entry: NavBackStackEntry,
    stations: StationRepository,
    tours: TourRepository,
    stationId: Long,
): StationDetailViewModel {
    val detailEntry = remember(entry) { getBackStackEntry<StationDetailRoute>() }
    return viewModel(viewModelStoreOwner = detailEntry) { StationDetailViewModel(stations, tours, stationId) }
}

/** Ob [T] irgendwo im aktuellen Stapel liegt; ein direkter Aufruf von [getBackStackEntry] würde sonst werfen. */
private inline fun <reified T : Any> NavController.hasRoute(): Boolean =
    runCatching { getBackStackEntry<T>() }.isSuccess

/** Verlässt [entry] nur, solange er sichtbar ist; verhindert doppeltes Zurück bei schnellem Tippen. */
private fun NavController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) popBackStack()
}
