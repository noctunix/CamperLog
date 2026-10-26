package app.restvolt.camperlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.restvolt.camperlog.data.AndroidLevelSensor
import app.restvolt.camperlog.ui.logbook.LogbookScreen
import app.restvolt.camperlog.ui.logbook.LogbookViewModel
import app.restvolt.camperlog.ui.settings.LevelAudioSettings
import app.restvolt.camperlog.ui.tools.LevelScreen
import app.restvolt.camperlog.ui.tools.LevelViewModel
import app.restvolt.camperlog.ui.tools.ToolsScreen

/**
 * Navigationsziele: Tools-Reiter mit Bordbuch und Wasserwaage. [logbookViewModel] ist
 * Activity-gebunden, damit die Schnellzugriffsreihe des Touren-Reiters denselben Zustand sieht.
 */
internal fun NavGraphBuilder.toolsGraph(
    navController: NavController,
    deps: NavDependencies,
    bottomBar: @Composable () -> Unit,
    logbookViewModel: LogbookViewModel,
) = with(deps) {
    composable<ToolsRoute> {
        ToolsScreen(
            bottomBar = bottomBar,
            onOpenLogbook = { navController.navigate(LogbookRoute) },
            onOpenLevel = { navController.navigate(LevelRoute) },
        )
    }
    composable<LogbookRoute> {
        LogbookScreen(
            viewModel = logbookViewModel,
            onOpenHistory = { vehicleId, type -> navController.navigate(LogHistoryRoute(vehicleId, type.name)) },
            onOpenSearch = { navController.navigate(SearchRoute) },
            onOpenData = { navController.navigate(DataRoute()) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
            onOpenVehicles = { navController.navigate(VehiclesRoute) },
            bottomBar = bottomBar,
        )
    }
    composable<LevelRoute> { entry ->
        val context = LocalContext.current
        LevelScreen(
            viewModel = viewModel { LevelViewModel(AndroidLevelSensor(context), LevelAudioSettings(context)) },
            onBack = { navController.popFrom(entry) },
        )
    }
}
