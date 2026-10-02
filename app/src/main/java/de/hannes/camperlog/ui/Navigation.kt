package de.hannes.camperlog.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
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
private object ToursRoute

@Serializable
private data class EditRoute(val tourId: Long = 0)

@Serializable
private data class DetailRoute(val tourId: Long)

@Serializable
private object OverviewRoute

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
            EditTourScreen(
                viewModel = viewModel { EditTourViewModel(repository, tourId) },
                onDone = { navController.popFrom(entry) },
            )
        }
        composable<DetailRoute> { entry ->
            val tourId = entry.toRoute<DetailRoute>().tourId
            TourDetailScreen(
                viewModel = viewModel { TourDetailViewModel(repository, tourId) },
                onBack = { navController.popFrom(entry) },
                onEdit = { navController.navigate(EditRoute(tourId)) },
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

/** Verlässt [entry] nur, solange er sichtbar ist; verhindert doppeltes Zurück bei schnellem Tippen. */
private fun NavController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) popBackStack()
}
