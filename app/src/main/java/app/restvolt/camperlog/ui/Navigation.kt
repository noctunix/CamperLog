package app.restvolt.camperlog.ui

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.LogRepository
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.StationRepository
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.VehicleRepository
import app.restvolt.camperlog.domain.dueReminders
import app.restvolt.camperlog.domain.shouldShowKeepAndroidOpen
import app.restvolt.camperlog.ui.about.AboutScreen
import app.restvolt.camperlog.ui.about.KeepAndroidOpenDialog
import app.restvolt.camperlog.ui.about.KeepAndroidOpenSettings
import app.restvolt.camperlog.ui.data.AndroidDataFiles
import app.restvolt.camperlog.ui.data.DataScreen
import app.restvolt.camperlog.ui.data.DataViewModel
import app.restvolt.camperlog.ui.detail.TourDetailScreen
import app.restvolt.camperlog.ui.detail.TourDetailViewModel
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
import app.restvolt.camperlog.ui.onboarding.IntroductionSettings
import app.restvolt.camperlog.ui.onboarding.IntroductionTourScreen
import app.restvolt.camperlog.ui.overview.OverviewScreen
import app.restvolt.camperlog.ui.overview.OverviewViewModel
import app.restvolt.camperlog.ui.rates.RateEditScreen
import app.restvolt.camperlog.ui.rates.RateEditViewModel
import app.restvolt.camperlog.ui.rates.RatesScreen
import app.restvolt.camperlog.ui.rates.RatesViewModel
import app.restvolt.camperlog.ui.settings.SettingsScreen
import app.restvolt.camperlog.ui.theme.ReminderSettings
import app.restvolt.camperlog.ui.theme.ThemeMode
import app.restvolt.camperlog.ui.tours.ToursFilterSettings
import app.restvolt.camperlog.ui.tours.ToursScreen
import app.restvolt.camperlog.ui.tours.ToursViewModel
import app.restvolt.camperlog.ui.vehicle.VehicleScreen
import app.restvolt.camperlog.ui.vehicle.VehicleViewModel
import app.restvolt.camperlog.ui.vehicles.VehiclesScreen
import app.restvolt.camperlog.ui.vehicles.VehiclesViewModel
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

@Serializable
internal object ToursRoute

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

@Serializable
internal data class EditRoute(val tourId: Long = 0)

@Serializable
internal data class DetailRoute(val tourId: Long)

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
    backupImporter: BackupImporter,
    themeMode: ThemeMode,
    canShowStartDialogs: Boolean = true,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val context = LocalContext.current
    val reminderSettings = remember { ReminderSettings(context) }
    val reminderPreferences by reminderSettings.values.collectAsStateWithLifecycle()
    val currentVehicleFlow = remember(vehicles) { vehicles.observeCurrentVehicle() }
    val currentVehicle by currentVehicleFlow.collectAsStateWithLifecycle(initialValue = null)
    val reminderCount = currentVehicle?.let { vehicle ->
        dueReminders(vehicle, LocalDate.now(), reminderPreferences.leadDays, reminderPreferences.oilChangeIntervalMonths).size
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
            onFinished = {
                introductionSettings.seen = true
                showIntroductionTour = false
            },
        )
        return
    }

    NavHost(navController, startDestination = ToursRoute) {
        composable<ToursRoute> {
            val context = LocalContext.current
            ToursScreen(
                viewModel = viewModel { ToursViewModel(repository, vehicles, ToursFilterSettings(context)) },
                onAddTour = { navController.navigate(EditRoute()) },
                onOpenOverview = { vehicleId -> navController.navigate(OverviewRoute(vehicleId)) },
                onOpenData = { navController.navigate(DataRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenTour = { navController.navigate(DetailRoute(it)) },
                onOpenVehicles = { navController.navigate(VehiclesRoute) },
                bottomBar = bottomBar,
            )
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
                viewModel = viewModel { LogHistoryViewModel(logbook, route.vehicleId, route.type) },
                onBack = { navController.popFrom(entry) },
            )
        }
        composable<VehicleRoute> {
            VehicleScreen(
                viewModel = viewModel { VehicleViewModel(vehicles) },
                reminderSettings = reminderSettings,
                onOpenData = { navController.navigate(DataRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenVehicles = { navController.navigate(VehiclesRoute) },
                onEditVehicle = { id -> navController.navigate(VehicleEditRoute(id)) },
                onAddRepair = { vehicleId -> navController.navigate(RepairEditRoute(vehicleId)) },
                onOpenRepair = { vehicleId, repairId -> navController.navigate(RepairEditRoute(vehicleId, repairId)) },
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
            val vehicleViewModel = navController.vehicleViewModel(entry, vehicles)
            RepairEditScreen(
                viewModel = viewModel {
                    RepairEditViewModel(vehicles, exchangeRates, route.vehicleId, route.repairId, createSavedStateHandle())
                },
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
        composable<EditRoute> { entry ->
            val tourId = entry.toRoute<EditRoute>().tourId
            val toursViewModel = navController.toursViewModel(entry, repository, vehicles)
            EditTourScreen(
                viewModel = viewModel { EditTourViewModel(repository, vehicles, tourId, createSavedStateHandle()) },
                onDone = { navController.popFrom(entry) },
                onSaved = {
                    if (tourId == 0L) toursViewModel.onTourCreated()
                    navController.popFrom(entry)
                },
            )
        }
        composable<DetailRoute> { entry ->
            val tourId = entry.toRoute<DetailRoute>().tourId
            val toursViewModel = navController.toursViewModel(entry, repository, vehicles)
            TourDetailScreen(
                viewModel = viewModel { TourDetailViewModel(repository, vehicles, tourId) },
                onBack = { navController.popFrom(entry) },
                onEdit = { navController.navigate(EditRoute(tourId)) },
                onDelete = { tour ->
                    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        toursViewModel.delete(tour)
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<OverviewRoute> { entry ->
            val vehicleId = entry.toRoute<OverviewRoute>().vehicleId
            OverviewScreen(
                viewModel = viewModel { OverviewViewModel(repository, exchangeRates, vehicleId) },
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
                    DataViewModel(repository, exchangeRates, vehicles, logbook, stations, backupImporter, AndroidDataFiles(context))
                },
                onBack = { navController.popFrom(entry) },
            )
        }
        composable<SettingsRoute> { entry ->
            SettingsScreen(
                viewModel = viewModel { RatesViewModel(exchangeRates, repository) },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                reminderSettings = reminderSettings,
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

/** Untere Navigationsleiste der drei Hauptreiter; erneutes Tippen kehrt zur Wurzel des Reiters zurück. */
@Composable
private fun CamperLogBottomBar(navController: NavController, current: NavDestination?, reminderCount: Int) {
    NavigationBar {
        NavigationBarItem(
            selected = current.isOnTab<ToursRoute>(),
            onClick = { navController.navigateToTab(ToursRoute) },
            icon = { Icon(painterResource(R.drawable.ic_map), contentDescription = null) },
            label = { Text(stringResource(R.string.nav_tours)) },
        )
        NavigationBarItem(
            selected = current.isOnTab<LogbookRoute>(),
            onClick = { navController.navigateToTab(LogbookRoute) },
            icon = { Icon(painterResource(R.drawable.ic_book), contentDescription = null) },
            label = { Text(stringResource(R.string.nav_logbook)) },
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
            label = { Text(vehicleLabel) },
            modifier = vehicleItemModifier,
        )
    }
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
): ToursViewModel {
    val toursEntry = remember(entry) { getBackStackEntry<ToursRoute>() }
    val context = LocalContext.current
    return viewModel(viewModelStoreOwner = toursEntry) { ToursViewModel(repository, vehicles, ToursFilterSettings(context)) }
}

/**
 * Das [VehicleViewModel] des Fahrzeug-Reiters, damit das Reparaturformular dort die Löschmeldung
 * auslöst. [RepairEditRoute] liegt immer über [VehicleRoute] im Stapel, da nur von dort erreichbar.
 */
@Composable
private fun NavController.vehicleViewModel(entry: NavBackStackEntry, vehicles: VehicleRepository): VehicleViewModel {
    val vehicleEntry = remember(entry) { getBackStackEntry<VehicleRoute>() }
    return viewModel(viewModelStoreOwner = vehicleEntry) { VehicleViewModel(vehicles) }
}

/** Verlässt [entry] nur, solange er sichtbar ist; verhindert doppeltes Zurück bei schnellem Tippen. */
private fun NavController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) popBackStack()
}
