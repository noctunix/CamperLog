package app.restvolt.camperlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.restvolt.camperlog.data.AndroidLocationPermissionGate
import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_ID
import app.restvolt.camperlog.domain.guide.CREATE_FIRST_TOUR_VEHICLE_ACTION
import app.restvolt.camperlog.ui.checklists.ChecklistScreen
import app.restvolt.camperlog.ui.checklists.ChecklistTemplateEditScreen
import app.restvolt.camperlog.ui.checklists.ChecklistTemplateEditViewModel
import app.restvolt.camperlog.ui.checklists.ChecklistTemplatesScreen
import app.restvolt.camperlog.ui.checklists.ChecklistTemplatesViewModel
import app.restvolt.camperlog.ui.checklists.ChecklistViewModel
import app.restvolt.camperlog.ui.checklists.VehicleChecklistsScreen
import app.restvolt.camperlog.ui.checklists.VehicleChecklistsViewModel
import app.restvolt.camperlog.ui.edit.EditVehicleScreen
import app.restvolt.camperlog.ui.edit.EditVehicleViewModel
import app.restvolt.camperlog.ui.edit.RepairEditScreen
import app.restvolt.camperlog.ui.edit.RepairEditViewModel
import app.restvolt.camperlog.ui.logbook.LogHistoryScreen
import app.restvolt.camperlog.ui.logbook.LogHistoryViewModel
import app.restvolt.camperlog.ui.logbook.LogbookScreen
import app.restvolt.camperlog.ui.logbook.LogbookViewModel
import app.restvolt.camperlog.ui.vehicle.VehicleDocumentDetailScreen
import app.restvolt.camperlog.ui.vehicle.VehicleDocumentDetailViewModel
import app.restvolt.camperlog.ui.vehicle.VehicleDocumentEditScreen
import app.restvolt.camperlog.ui.vehicle.VehicleDocumentEditViewModel
import app.restvolt.camperlog.ui.vehicle.VehicleScreen
import app.restvolt.camperlog.ui.vehicle.VehicleViewModel
import app.restvolt.camperlog.ui.vehicle.WhereAmIViewModel
import app.restvolt.camperlog.ui.vehicles.VehiclesScreen
import app.restvolt.camperlog.ui.vehicles.VehiclesViewModel

/** Navigationsziele: Logbuch- und Fahrzeug-Reiter mit Reparaturen, Dokumenten und Checklisten. */
internal fun NavGraphBuilder.vehicleGraph(
    navController: NavController,
    deps: NavDependencies,
    bottomBar: @Composable () -> Unit,
) = with(deps) {
    composable<LogbookRoute> {
        LogbookScreen(
            viewModel = viewModel { LogbookViewModel(logbook, vehicles) },
            onOpenHistory = { vehicleId, type -> navController.navigate(LogHistoryRoute(vehicleId, type.name)) },
            onOpenSearch = { navController.navigate(SearchRoute) },
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
            viewModel = viewModel { VehicleViewModel(vehicles, documents, checklists) },
            whereAmIViewModel = viewModel { WhereAmIViewModel(locationProvider, AndroidLocationPermissionGate(vehicleContext)) },
            locationEnabled = locationEnabled,
            reminderSettings = reminderSettings,
            onOpenSearch = { navController.navigate(SearchRoute) },
            onOpenData = { navController.navigate(DataRoute) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
            onOpenVehicles = { navController.navigate(VehiclesRoute) },
            onEditVehicle = { id -> navController.navigate(VehicleEditRoute(id)) },
            onAddRepair = { vehicleId -> navController.navigate(RepairEditRoute(vehicleId)) },
            onOpenRepair = { vehicleId, repairId -> navController.navigate(RepairEditRoute(vehicleId, repairId)) },
            onAddDocument = { vehicleId -> navController.navigate(DocumentEditRoute(vehicleId)) },
            onOpenDocument = { vehicleId, documentId -> navController.navigate(DocumentDetailRoute(vehicleId, documentId)) },
            onOpenChecklists = { vehicleId -> navController.navigate(VehicleChecklistsRoute(vehicleId)) },
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
            onSaved = {
                // Inline aus der Pilot-Tour "Erste Tour anlegen" angelegt: schaltet ihren
                // Fahrzeug-Schritt frei und setzt sie fort - das Tourformular greift das neue
                // Fahrzeug über seine eigene, dauerhaft laufende Vorbelegung auf.
                if (guideController.state.value.tour?.id == CREATE_FIRST_TOUR_ID) {
                    guideController.completeAction(CREATE_FIRST_TOUR_VEHICLE_ACTION)
                    guideController.resume()
                }
                navController.popFrom(entry)
            },
        )
    }
    composable<RepairEditRoute> { entry ->
        val route = entry.toRoute<RepairEditRoute>()
        val vehicleViewModel = navController.vehicleViewModel(entry, vehicles, documents, checklists)
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
    composable<ChecklistTemplatesRoute> { entry ->
        ChecklistTemplatesScreen(
            viewModel = viewModel { ChecklistTemplatesViewModel(checklistTemplates) },
            onBack = { navController.popFrom(entry) },
            onAdd = { navController.navigate(ChecklistTemplateEditRoute()) },
            onEdit = { templateId -> navController.navigate(ChecklistTemplateEditRoute(templateId)) },
        )
    }
    composable<ChecklistTemplateEditRoute> { entry ->
        val route = entry.toRoute<ChecklistTemplateEditRoute>()
        val templatesViewModel = navController.checklistTemplatesViewModel(entry, checklistTemplates)
        ChecklistTemplateEditScreen(
            viewModel = viewModel {
                ChecklistTemplateEditViewModel(checklistTemplates, route.templateId, createSavedStateHandle())
            },
            onDone = { navController.popFrom(entry) },
            onSaved = { navController.popFrom(entry) },
            onDelete = { template ->
                if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    templatesViewModel.deleteTemplate(template)
                    navController.popBackStack()
                }
            },
        )
    }
    composable<VehicleChecklistsRoute> { entry ->
        val route = entry.toRoute<VehicleChecklistsRoute>()
        VehicleChecklistsScreen(
            viewModel = viewModel { VehicleChecklistsViewModel(checklists, checklistTemplates, route.vehicleId) },
            onBack = { navController.popFrom(entry) },
            onOpenTemplates = { navController.navigate(ChecklistTemplatesRoute) },
            onOpenChecklist = { checklistId -> navController.navigate(ChecklistRoute(checklistId = checklistId, vehicleId = route.vehicleId)) },
        )
    }
    composable<ChecklistRoute> { entry ->
        val route = entry.toRoute<ChecklistRoute>()
        val deleteChecklist: (Checklist) -> Unit = if (route.tourId != null) {
            navController.tourDetailViewModel(entry, repository, vehicles, stations, diaryEntries, checklists, exchangeRates, countryLookup, attachments, attachmentFileStore, tracks)::deleteChecklist
        } else {
            navController.vehicleChecklistsViewModel(entry, checklists, checklistTemplates, route.vehicleId)::deleteChecklist
        }
        ChecklistScreen(
            viewModel = viewModel { ChecklistViewModel(checklists, route.checklistId) },
            onBack = { navController.popFrom(entry) },
            onDelete = { checklist ->
                if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    deleteChecklist(checklist)
                    navController.popBackStack()
                }
            },
        )
    }
}
