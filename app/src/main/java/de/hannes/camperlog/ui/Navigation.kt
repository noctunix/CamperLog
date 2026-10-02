package de.hannes.camperlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.hannes.camperlog.domain.TourRepository
import de.hannes.camperlog.ui.detail.TourDetailScreen
import de.hannes.camperlog.ui.detail.TourDetailViewModel
import de.hannes.camperlog.ui.edit.EditTourScreen
import de.hannes.camperlog.ui.edit.EditTourViewModel
import de.hannes.camperlog.ui.overview.OverviewScreen
import de.hannes.camperlog.ui.overview.OverviewViewModel
import de.hannes.camperlog.ui.tours.ToursScreen
import de.hannes.camperlog.ui.tours.ToursViewModel
import kotlinx.serialization.Serializable

@Serializable
internal object ToursRoute

@Serializable
internal data class EditRoute(val tourId: Long = 0)

@Serializable
internal data class DetailRoute(val tourId: Long)

@Serializable
internal object OverviewRoute

/** Navigationsgraph der App mit Start auf der Tourenliste. */
@Composable
fun CamperLogNavHost(repository: TourRepository) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = ToursRoute) {
        composable<ToursRoute> {
            ToursScreen(
                viewModel = viewModel { ToursViewModel(repository) },
                onAddTour = { navController.navigate(EditRoute()) },
                onOpenOverview = { navController.navigate(OverviewRoute) },
                onOpenTour = { navController.navigate(DetailRoute(it)) },
            )
        }
        composable<EditRoute> { entry ->
            val tourId = entry.toRoute<EditRoute>().tourId
            val toursViewModel = navController.toursViewModel(entry, repository)
            EditTourScreen(
                viewModel = viewModel { EditTourViewModel(repository, tourId) },
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
                viewModel = viewModel { OverviewViewModel(repository) },
                onBack = { navController.popFrom(entry) },
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
