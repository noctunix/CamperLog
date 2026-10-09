package app.restvolt.camperlog.ui

import app.restvolt.camperlog.CamperLogApp
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.tracking.TrackRecordingSettings
import app.restvolt.camperlog.tracking.TrackRecordingService
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.restvolt.camperlog.R
import app.restvolt.camperlog.BuildConfig
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.data.AndroidBackupFolderWriter
import app.restvolt.camperlog.data.AndroidLocationProvider
import app.restvolt.camperlog.data.AndroidTileLoader
import app.restvolt.camperlog.data.AndroidPlaceSearchProvider
import app.restvolt.camperlog.data.AndroidWeatherProvider
import app.restvolt.camperlog.data.AssetCountryLookupRepository
import app.restvolt.camperlog.data.AttachmentFileStore
import app.restvolt.camperlog.domain.AttachmentRepository
import app.restvolt.camperlog.domain.ChecklistRepository
import app.restvolt.camperlog.domain.ChecklistTemplateRepository
import app.restvolt.camperlog.domain.CountryLookupRepository
import app.restvolt.camperlog.domain.DiaryEntryRepository
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.GeoIntentLocation
import app.restvolt.camperlog.domain.LocationProvider
import app.restvolt.camperlog.domain.guide.GuideController
import app.restvolt.camperlog.domain.guide.GuidePhase
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
import app.restvolt.camperlog.domain.PlaceSearchProvider
import app.restvolt.camperlog.domain.WeatherProvider
import app.restvolt.camperlog.domain.camperLogUserAgent
import app.restvolt.camperlog.domain.documentReminders
import app.restvolt.camperlog.domain.dueReminders
import app.restvolt.camperlog.domain.isMapAvailable
import app.restvolt.camperlog.domain.shouldShowKeepAndroidOpen
import app.restvolt.camperlog.ui.about.KeepAndroidOpenDialog
import app.restvolt.camperlog.ui.about.KeepAndroidOpenSettings
import app.restvolt.camperlog.ui.attachments.AndroidAttachmentPickers
import app.restvolt.camperlog.ui.attachments.AttachmentPickers
import app.restvolt.camperlog.ui.checklists.ChecklistTemplatesViewModel
import app.restvolt.camperlog.ui.checklists.VehicleChecklistsViewModel
import app.restvolt.camperlog.ui.settings.HomeLocationSettings
import app.restvolt.camperlog.ui.settings.LocationSettings
import app.restvolt.camperlog.ui.settings.NotificationSettings
import app.restvolt.camperlog.ui.settings.WeatherSettings
import app.restvolt.camperlog.ui.data.BackupSettings
import app.restvolt.camperlog.ui.detail.StationDetailViewModel
import app.restvolt.camperlog.ui.detail.AndroidTourExportFiles
import app.restvolt.camperlog.ui.detail.TourDetailViewModel
import app.restvolt.camperlog.ui.guide.GuideHost
import app.restvolt.camperlog.ui.guide.GuideProgressStore
import app.restvolt.camperlog.ui.onboarding.IntroductionSettings
import app.restvolt.camperlog.ui.onboarding.IntroductionTourScreen
import app.restvolt.camperlog.ui.stations.StationsViewModel
import app.restvolt.camperlog.ui.stations.StationsWhatsNewSettings
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.ReminderSettings
import app.restvolt.camperlog.ui.theme.ThemeMode
import app.restvolt.camperlog.ui.tours.ToursViewModel
import app.restvolt.camperlog.ui.vehicle.VehicleViewModel
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

@Serializable
internal object ToursRoute

/** Volltextsuche, von der Kopfzeile jedes Hauptreiters aus erreichbar. */
@Serializable
internal object SearchRoute

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

/** Tagebuchformular; [entryId] 0 legt einen neuen Eintrag an. */
@Serializable
internal data class DiaryEditRoute(val tourId: Long, val entryId: Long = 0)

/** Checklisten-Vorlagenliste. */
@Serializable
internal object ChecklistTemplatesRoute

/** Formular einer Checklisten-Vorlage; [templateId] 0 legt eine neue Vorlage an. */
@Serializable
internal data class ChecklistTemplateEditRoute(val templateId: Long = 0)

/** Checklisten eines Fahrzeugs ohne Tourbezug (z. B. Einwintern). */
@Serializable
internal data class VehicleChecklistsRoute(val vehicleId: Long)

/**
 * Eine gestartete Checkliste. [tourId] ist gesetzt, wenn sie zu einer Tour gehört (dann trägt
 * [DetailRoute] den Löschkanal), sonst [vehicleId] ihr Fahrzeug (dann [VehicleChecklistsRoute]).
 */
@Serializable
internal data class ChecklistRoute(val checklistId: Long, val vehicleId: Long = 0, val tourId: Long? = null)

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

/** Gemeinsame Abhängigkeiten der Navigationsziele, einmal in [CamperLogNavHost] gebündelt. */
internal class NavDependencies(
    val repository: TourRepository,
    val vehicles: VehicleRepository,
    val logbook: LogRepository,
    val stations: StationRepository,
    val exchangeRates: ExchangeRateRepository,
    val documents: VehicleDocumentRepository,
    val diaryEntries: DiaryEntryRepository,
    val checklists: ChecklistRepository,
    val checklistTemplates: ChecklistTemplateRepository,
    val attachments: AttachmentRepository,
    val attachmentFileStore: AttachmentFileStore,
    val backupImporter: BackupImporter,
    val locationProvider: LocationProvider,
    val weatherProvider: WeatherProvider,
    val placeSearchProvider: PlaceSearchProvider,
    val tileLoader: TileLoader,
    val countryLookup: CountryLookupRepository,
    val attachmentPickers: AttachmentPickers,
    val tracks: TrackRepository,
    val reminderSettings: ReminderSettings,
    val locationSettings: LocationSettings,
    val trackSettings: TrackRecordingSettings,
    val weatherSettings: WeatherSettings,
    val notificationSettings: NotificationSettings,
    val backupSettings: BackupSettings,
    val backupFolderWriter: AndroidBackupFolderWriter,
    val homeLocationSettings: HomeLocationSettings,
    val guideController: GuideController,
    val guideProgressStore: GuideProgressStore,
)

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
    checklists: ChecklistRepository,
    checklistTemplates: ChecklistTemplateRepository,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    backupImporter: BackupImporter,
    themeMode: ThemeMode,
    accentColor: AccentColor,
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
    /** Ortssuche für "Ort suchen" im Stationsformular; in Tests ein Fake. */
    placeSearchProvider: PlaceSearchProvider = AndroidPlaceSearchProvider(userAgent = camperLogUserAgent(BuildConfig.VERSION_NAME)),
    /** Kachellader der Karte; in Tests ein Fake. */
    tileLoader: TileLoader = AndroidTileLoader(userAgent = camperLogUserAgent(BuildConfig.VERSION_NAME)),
    /** Offline-Ländererkennung für Tourdetail und Übersicht; in Tests ein Fake. */
    countryLookup: CountryLookupRepository = AssetCountryLookupRepository(LocalContext.current),
    /** Kamera-/Galerie-/Dokument-Auswahl der Foto-Streifen; in Tests ein Fake ohne echten System-Dialog. */
    attachmentPickers: AttachmentPickers = AndroidAttachmentPickers,
    /** Aufgezeichnete Trackpunkte; in Tests ein Fake. */
    tracks: TrackRepository = (LocalContext.current.applicationContext as CamperLogApp).tracks,
    onAccentColorChange: (AccentColor) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val context = LocalContext.current
    val reminderSettings = remember { ReminderSettings(context) }
    val reminderPreferences by reminderSettings.values.collectAsStateWithLifecycle()
    val locationSettings = remember { LocationSettings(context) }
    val trackSettings = remember { TrackRecordingSettings.get(context) }
    val weatherSettings = remember { WeatherSettings(context) }
    val notificationSettings = remember { NotificationSettings(context) }
    val backupSettings = remember { BackupSettings(context) }
    val backupFolderWriter = remember { AndroidBackupFolderWriter(context) }
    val homeLocationSettings = remember { HomeLocationSettings(context) }
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
    val bottomBar: @Composable () -> Unit = {
        Column {
            val trackedTourId by trackSettings.tracked.collectAsStateWithLifecycle()
            trackedTourId?.let { tourId ->
                TrackRecordingBar(tourId, repository, tracks, trackSettings) { navController.navigate(DetailRoute(it)) }
            }
            CamperLogBottomBar(navController, currentDestination, reminderCount)
        }
    }

    val introductionSettings = remember { IntroductionSettings(context) }
    var showIntroductionTour by rememberSaveable { mutableStateOf(false) }

    // Geteilter Controller für künftige geführte Touren; Lebensdauer wie die übrigen hier
    // erzeugten Einstellungen-Objekte, also solange diese Komposition bestehen bleibt.
    val guideScope = rememberCoroutineScope()
    val guideController = remember { GuideController(guideScope) }
    val guideProgressStore = remember { GuideProgressStore(context) }
    val guideState by guideController.state.collectAsStateWithLifecycle()
    LaunchedEffect(guideState.phase, guideState.tour?.id) {
        val tour = guideState.tour
        if (guideState.phase == GuidePhase.ENDED && tour != null) {
            guideProgressStore.markCompleted(tour.id, tour.version)
        }
    }

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

    val deps = NavDependencies(
        repository, vehicles, logbook, stations, exchangeRates, documents, diaryEntries, checklists, checklistTemplates,
        attachments, attachmentFileStore, backupImporter, locationProvider, weatherProvider, placeSearchProvider, tileLoader,
        countryLookup, attachmentPickers, tracks, reminderSettings, locationSettings, trackSettings, weatherSettings,
        notificationSettings, backupSettings, backupFolderWriter, homeLocationSettings, guideController, guideProgressStore,
    )
    NavHost(navController, startDestination = ToursRoute) {
        toursGraph(navController, deps, bottomBar)
        vehicleGraph(navController, deps, bottomBar)
        stationsGraph(navController, deps, bottomBar)
        settingsGraph(
            navController,
            deps,
            themeMode,
            onThemeModeChange,
            accentColor,
            onAccentColorChange,
            onShowIntroductionAgain = { showIntroductionTour = true },
        )
    }
    GuideHost(guideController, modifier = Modifier.fillMaxSize())

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
internal fun NavController.toursViewModel(
    entry: NavBackStackEntry,
    repository: TourRepository,
    vehicles: VehicleRepository,
    stations: StationRepository,
    tracks: TrackRepository,
): ToursViewModel {
    val toursEntry = remember(entry) { getBackStackEntry<ToursRoute>() }
    val context = LocalContext.current
    val trackSettings = remember { TrackRecordingSettings.get(context) }
    return viewModel(viewModelStoreOwner = toursEntry) {
        ToursViewModel(
            repository,
            vehicles,
            stations,
            tracks,
            VehicleScopeSettings(context),
            onTourFinished = { tourId ->
                if (trackSettings.trackedTourId == tourId || trackSettings.activeRecording?.tourId == tourId) TrackRecordingService.stop(context)
            },
            onTourDeleted = { tourId ->
                if (trackSettings.trackedTourId == tourId || trackSettings.activeRecording?.tourId == tourId) TrackRecordingService.stop(context)
            },
        )
    }
}

/**
 * Das [VehicleViewModel] des Fahrzeug-Reiters, damit das Reparaturformular dort die Löschmeldung
 * auslöst. [RepairEditRoute] liegt immer über [VehicleRoute] im Stapel, da nur von dort erreichbar.
 */
@Composable
internal fun NavController.vehicleViewModel(
    entry: NavBackStackEntry,
    vehicles: VehicleRepository,
    documents: VehicleDocumentRepository,
    checklists: ChecklistRepository,
): VehicleViewModel {
    val vehicleEntry = remember(entry) { getBackStackEntry<VehicleRoute>() }
    return viewModel(viewModelStoreOwner = vehicleEntry) { VehicleViewModel(vehicles, documents, checklists) }
}

/**
 * Das [TourDetailViewModel] der Tourdetailseite, damit das Stationsdetail dort die Löschmeldung
 * auslöst. [StationDetailRoute] liegt immer über [DetailRoute] im Stapel, da nur von dort erreichbar.
 */
@Composable
internal fun NavController.tourDetailViewModel(
    entry: NavBackStackEntry,
    tours: TourRepository,
    vehicles: VehicleRepository,
    stations: StationRepository,
    diaryEntries: DiaryEntryRepository,
    checklists: ChecklistRepository,
    exchangeRates: ExchangeRateRepository,
    countryLookup: CountryLookupRepository,
    attachments: AttachmentRepository,
    attachmentFileStore: AttachmentFileStore,
    tracks: TrackRepository,
): TourDetailViewModel {
    val detailEntry = remember(entry) { getBackStackEntry<DetailRoute>() }
    val tourId = detailEntry.toRoute<DetailRoute>().tourId
    val context = LocalContext.current
    return viewModel(viewModelStoreOwner = detailEntry) {
        TourDetailViewModel(
            tours, vehicles, stations, diaryEntries, checklists, exchangeRates, countryLookup, attachments, attachmentFileStore,
            AndroidTourExportFiles(context), tracks, tourId,
        )
    }
}

/**
 * Das [ChecklistTemplatesViewModel] der Vorlagenliste, damit das Vorlagenformular dort die
 * Löschmeldung auslöst. [ChecklistTemplateEditRoute] liegt immer über [ChecklistTemplatesRoute] im Stapel.
 */
@Composable
internal fun NavController.checklistTemplatesViewModel(
    entry: NavBackStackEntry,
    templates: ChecklistTemplateRepository,
): ChecklistTemplatesViewModel {
    val templatesEntry = remember(entry) { getBackStackEntry<ChecklistTemplatesRoute>() }
    return viewModel(viewModelStoreOwner = templatesEntry) { ChecklistTemplatesViewModel(templates) }
}

/**
 * Das [VehicleChecklistsViewModel] der fahrzeugbezogenen Checklistenliste, damit eine geöffnete
 * Checkliste ohne Tourbezug dort die Löschmeldung auslöst. [ChecklistRoute] liegt in diesem Fall
 * immer über [VehicleChecklistsRoute] im Stapel.
 */
@Composable
internal fun NavController.vehicleChecklistsViewModel(
    entry: NavBackStackEntry,
    checklists: ChecklistRepository,
    templates: ChecklistTemplateRepository,
    vehicleId: Long,
): VehicleChecklistsViewModel {
    val listEntry = remember(entry) { getBackStackEntry<VehicleChecklistsRoute>() }
    return viewModel(viewModelStoreOwner = listEntry) { VehicleChecklistsViewModel(checklists, templates, vehicleId) }
}

/**
 * Das [StationsViewModel] des Stationen-Reiters, damit das Stationsdetail dort die Löschmeldung
 * auslöst, wenn es vom Stationen-Reiter aus geöffnet wurde ([StationDetailRoute.fromStationsTab]).
 */
@Composable
internal fun NavController.stationsViewModel(
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
internal fun NavController.stationDetailViewModel(
    entry: NavBackStackEntry,
    stations: StationRepository,
    tours: TourRepository,
    stationId: Long,
): StationDetailViewModel {
    val detailEntry = remember(entry) { getBackStackEntry<StationDetailRoute>() }
    return viewModel(viewModelStoreOwner = detailEntry) { StationDetailViewModel(stations, tours, stationId) }
}

/** Ob [T] irgendwo im aktuellen Stapel liegt; ein direkter Aufruf von [getBackStackEntry] würde sonst werfen. */
internal inline fun <reified T : Any> NavController.hasRoute(): Boolean =
    runCatching { getBackStackEntry<T>() }.isSuccess

/** Verlässt [entry] nur, solange er sichtbar ist; verhindert doppeltes Zurück bei schnellem Tippen. */
internal fun NavController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) popBackStack()
}
