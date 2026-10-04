package app.restvolt.camperlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.restvolt.camperlog.backup.BackupImporter
import app.restvolt.camperlog.domain.ExchangeRateRepository
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.ui.data.AndroidDataFiles
import app.restvolt.camperlog.ui.data.DataScreen
import app.restvolt.camperlog.ui.data.DataViewModel
import app.restvolt.camperlog.ui.detail.TourDetailScreen
import app.restvolt.camperlog.ui.detail.TourDetailViewModel
import app.restvolt.camperlog.ui.edit.EditTourScreen
import app.restvolt.camperlog.ui.edit.EditTourViewModel
import app.restvolt.camperlog.ui.overview.OverviewScreen
import app.restvolt.camperlog.ui.overview.OverviewViewModel
import app.restvolt.camperlog.ui.rates.RateEditScreen
import app.restvolt.camperlog.ui.rates.RateEditViewModel
import app.restvolt.camperlog.ui.rates.RatesScreen
import app.restvolt.camperlog.ui.rates.RatesViewModel
import app.restvolt.camperlog.ui.settings.SettingsScreen
import app.restvolt.camperlog.ui.theme.ThemeMode
import app.restvolt.camperlog.ui.tours.ToursScreen
import app.restvolt.camperlog.ui.tours.ToursViewModel
import kotlinx.serialization.Serializable

@Serializable
internal object ToursRoute

@Serializable
internal data class EditRoute(val tourId: Long = 0)

@Serializable
internal data class DetailRoute(val tourId: Long)

@Serializable
internal object OverviewRoute

@Serializable
internal object RatesRoute

@Serializable
internal object SettingsRoute

@Serializable
internal object DataRoute

/** Kursformular; ohne [currencyCode] wird ein neuer Kurs mit frei wählbarer Währung angelegt. */
@Serializable
internal data class RateEditRoute(val currencyCode: String? = null)

/** Navigationsgraph der App mit Start auf der Tourenliste. */
@Composable
fun CamperLogNavHost(
    repository: TourRepository,
    exchangeRates: ExchangeRateRepository,
    backupImporter: BackupImporter,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = ToursRoute) {
        composable<ToursRoute> {
            ToursScreen(
                viewModel = viewModel { ToursViewModel(repository) },
                onAddTour = { navController.navigate(EditRoute()) },
                onOpenOverview = { navController.navigate(OverviewRoute) },
                onOpenData = { navController.navigate(DataRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenTour = { navController.navigate(DetailRoute(it)) },
            )
        }
        composable<EditRoute> { entry ->
            val tourId = entry.toRoute<EditRoute>().tourId
            val toursViewModel = navController.toursViewModel(entry, repository)
            EditTourScreen(
                viewModel = viewModel { EditTourViewModel(repository, tourId, createSavedStateHandle()) },
                onDone = { navController.popFrom(entry) },
                onSaved = {
                    if (tourId == 0L) toursViewModel.onTourCreated()
                    navController.popFrom(entry)
                },
            )
        }
        composable<DetailRoute> { entry ->
            val tourId = entry.toRoute<DetailRoute>().tourId
            val toursViewModel = navController.toursViewModel(entry, repository)
            TourDetailScreen(
                viewModel = viewModel { TourDetailViewModel(repository, tourId) },
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
            OverviewScreen(
                viewModel = viewModel { OverviewViewModel(repository, exchangeRates) },
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
                viewModel = viewModel { DataViewModel(repository, exchangeRates, backupImporter, AndroidDataFiles(context)) },
                onBack = { navController.popFrom(entry) },
            )
        }
        composable<SettingsRoute> { entry ->
            SettingsScreen(
                viewModel = viewModel { RatesViewModel(exchangeRates, repository) },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onBack = { navController.popFrom(entry) },
                onOpenRates = { navController.navigate(RatesRoute) },
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
}

/**
 * Das [ToursViewModel] der Startseite, damit Detail und Formular dort Snackbar-Meldungen auslösen.
 * Die Startseite liegt als Startziel immer unten im Back Stack.
 */
@Composable
private fun NavController.toursViewModel(entry: NavBackStackEntry, repository: TourRepository): ToursViewModel {
    val toursEntry = remember(entry) { getBackStackEntry<ToursRoute>() }
    return viewModel(viewModelStoreOwner = toursEntry) { ToursViewModel(repository) }
}

/** Verlässt [entry] nur, solange er sichtbar ist; verhindert doppeltes Zurück bei schnellem Tippen. */
private fun NavController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) popBackStack()
}
