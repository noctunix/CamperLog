package app.restvolt.camperlog.ui

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.AndroidLocationPermissionGate
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_ID
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_VERSION
import app.restvolt.camperlog.domain.guide.INTRODUCTION_TOUR_ID
import app.restvolt.camperlog.domain.guide.INTRODUCTION_TOUR_VERSION
import app.restvolt.camperlog.domain.guide.createFirstTourTour
import app.restvolt.camperlog.domain.guide.introductionTour
import app.restvolt.camperlog.ui.about.AboutScreen
import app.restvolt.camperlog.ui.about.GuidedTourEntry
import app.restvolt.camperlog.ui.data.AndroidDataFiles
import app.restvolt.camperlog.ui.data.DataScreen
import app.restvolt.camperlog.ui.data.DataViewModel
import app.restvolt.camperlog.ui.rates.RateEditScreen
import app.restvolt.camperlog.ui.rates.RateEditViewModel
import app.restvolt.camperlog.ui.rates.RatesScreen
import app.restvolt.camperlog.ui.rates.RatesViewModel
import app.restvolt.camperlog.ui.search.SearchScreen
import app.restvolt.camperlog.ui.search.SearchViewModel
import app.restvolt.camperlog.ui.settings.HomeLocationViewModel
import app.restvolt.camperlog.ui.settings.SettingsScreen
import app.restvolt.camperlog.ui.theme.AccentColor
import app.restvolt.camperlog.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Navigationsziele: Suche, Kurse, Daten, Einstellungen und Über. */
internal fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    deps: NavDependencies,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accentColor: AccentColor,
    onAccentColorChange: (AccentColor) -> Unit,
) = with(deps) {
    /**
     * Die Einträge der Liste "Tutorials", sowohl direkt in den Einstellungen als auch (als
     * Kurzlink) in "Über CamperLog". Startet [guideController] für die Pilot-Tour erst, nachdem
     * die Navigation zur Tourenliste tatsächlich abgeschlossen ist - sonst zeigt die Karte keinen
     * echten Anker, weil die Zielseite noch gar nicht komponiert ist.
     */
    fun guidedTourEntries(scope: CoroutineScope) = listOf(
        GuidedTourEntry(
            titleRes = R.string.guide_tour_introduction_title,
            completed = guideProgressStore.isCompleted(INTRODUCTION_TOUR_ID, INTRODUCTION_TOUR_VERSION),
            onStart = { guideController.start(introductionTour()) },
        ),
        GuidedTourEntry(
            titleRes = R.string.guide_tour_create_first_title,
            completed = guideProgressStore.isCompleted(CREATE_FIRST_TOUR_ID, CREATE_FIRST_TOUR_VERSION),
            onStart = {
                scope.launch {
                    navController.navigateToToursTabRootAndAwait()
                    guideController.start(createFirstTourTour())
                }
            },
        ),
    )
    composable<SearchRoute> { entry ->
        val resources = LocalResources.current
        val scope = rememberCoroutineScope()
        SearchScreen(
            viewModel = viewModel {
                SearchViewModel(
                    repository, vehicles, stations, diaryEntries, logbook, documents, checklists, checklistTemplates,
                    logTypeLabel = { type -> resources.getString(type.labelRes) },
                )
            },
            onBack = { navController.popFrom(entry) },
            onOpenTour = { tourId -> navController.navigate(DetailRoute(tourId)) },
            onOpenStop = { stationId -> navController.navigate(StationDetailRoute(stationId, fromStationsTab = true)) },
            onOpenDiaryEntry = { tourId, entryId ->
                navController.navigate(DetailRoute(tourId))
                navController.navigate(DiaryEditRoute(tourId = tourId, entryId = entryId))
            },
            onOpenLogbookHistory = { vehicleId, type -> navController.navigate(LogHistoryRoute(vehicleId, type.name)) },
            onOpenRepair = { vehicleId, repairId ->
                scope.launch {
                    vehicles.setCurrentVehicle(vehicleId)
                    navController.navigate(VehicleRoute)
                    navController.navigate(RepairEditRoute(vehicleId, repairId))
                }
            },
            onOpenChecklist = { checklistId, vehicleId, tourId ->
                if (tourId != null) {
                    navController.navigate(DetailRoute(tourId))
                    navController.navigate(ChecklistRoute(checklistId = checklistId, tourId = tourId))
                } else {
                    navController.navigate(VehicleChecklistsRoute(vehicleId))
                    navController.navigate(ChecklistRoute(checklistId = checklistId, vehicleId = vehicleId))
                }
            },
            onOpenChecklistTemplate = { templateId ->
                navController.navigate(ChecklistTemplatesRoute)
                navController.navigate(ChecklistTemplateEditRoute(templateId))
            },
            onOpenVehicleDocument = { vehicleId, documentId -> navController.navigate(DocumentDetailRoute(vehicleId, documentId)) },
            onOpenVehicle = { vehicleId ->
                scope.launch {
                    vehicles.setCurrentVehicle(vehicleId)
                    navController.navigate(VehicleRoute)
                }
            },
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
                    checklistTemplates,
                    checklists,
                    tracks,
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
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        SettingsScreen(
            viewModel = viewModel { RatesViewModel(exchangeRates, repository) },
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            accentColor = accentColor,
            onAccentColorChange = onAccentColorChange,
            reminderSettings = reminderSettings,
            notificationSettings = notificationSettings,
            locationSettings = locationSettings,
            weatherSettings = weatherSettings,
            trackSettings = trackSettings,
            homeLocationSettings = homeLocationSettings,
            homeLocationViewModel = viewModel {
                HomeLocationViewModel(homeLocationSettings, locationProvider, AndroidLocationPermissionGate(context))
            },
            guidedTours = guidedTourEntries(scope),
            onBack = { navController.popFrom(entry) },
            onOpenRates = { navController.navigate(RatesRoute) },
            onOpenAbout = { navController.navigate(AboutRoute) },
        )
    }
    composable<AboutRoute> { entry ->
        val scope = rememberCoroutineScope()
        AboutScreen(
            onBack = { navController.popFrom(entry) },
            guidedTours = guidedTourEntries(scope),
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
