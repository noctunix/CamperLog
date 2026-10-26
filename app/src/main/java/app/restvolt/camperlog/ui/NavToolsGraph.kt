package app.restvolt.camperlog.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.lifecycle.viewmodel.compose.viewModel
import app.restvolt.camperlog.ui.logbook.LogbookScreen
import app.restvolt.camperlog.ui.logbook.LogbookViewModel
import app.restvolt.camperlog.ui.tools.ToolsScreen

/** Navigationsziele: Tools-Reiter mit Bordbuch. */
internal fun NavGraphBuilder.toolsGraph(
    navController: NavController,
    deps: NavDependencies,
    bottomBar: @Composable () -> Unit,
) = with(deps) {
    composable<ToolsRoute> {
        ToolsScreen(bottomBar = bottomBar, onOpenLogbook = { navController.navigate(LogbookRoute) })
    }
    composable<LogbookRoute> {
        LogbookScreen(
            viewModel = viewModel { LogbookViewModel(logbook, vehicles, stations) },
            onOpenHistory = { vehicleId, type -> navController.navigate(LogHistoryRoute(vehicleId, type.name)) },
            onOpenSearch = { navController.navigate(SearchRoute) },
            onOpenData = { navController.navigate(DataRoute()) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
            onOpenVehicles = { navController.navigate(VehiclesRoute) },
            bottomBar = bottomBar,
        )
    }
}
