package app.restvolt.camperlog.ui.tours

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.displayTitle
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.period
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.FinishTourDialog
import app.restvolt.camperlog.ui.TabTopBar
import app.restvolt.camperlog.ui.VehicleSwitcherTitle
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.vehicleDisplayName

/** Startseite: Tourenliste mit Suche, Jahresfilter und Einstieg in Eingabe, Übersicht und Datenverwaltung. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToursScreen(
    viewModel: ToursViewModel,
    onAddTour: () -> Unit,
    onOpenOverview: (Long?) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTour: (Long) -> Unit,
    onOpenVehicles: () -> Unit,
    activeRecordingTourId: Long? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val finishingTourId by viewModel.finishingTourId.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var finishTourId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ToursTopBar(
                scrollBehavior = scrollBehavior,
                vehicles = state.vehicles,
                currentVehicleId = state.currentVehicleId,
                showAllVehicles = state.showAllVehicles,
                onSelectVehicle = viewModel::onSelectVehicle,
                onSelectAllVehicles = viewModel::onSelectAllVehicles,
                onOpenVehicles = onOpenVehicles,
                onOpenOverview = { onOpenOverview(if (state.showAllVehicles) null else state.currentVehicleId) },
                onOpenSearch = onOpenSearch,
                onOpenData = onOpenData,
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            // Content-Überladung statt text/icon: Letztere blendet den Text per
            // clearAndSetSemantics aus, dann hätte der FAB für TalkBack keinen Namen.
            ExtendedFloatingActionButton(onClick = onAddTour) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.tours_new))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                // Platz unter der letzten Karte, damit der FAB sie nicht verdeckt.
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.hasAnyTour) {
                item {
                    SearchField(state.query, viewModel::onQueryChange)
                }
                item {
                    YearFilter(state.years, state.selectedYear, viewModel::onYearSelected)
                }
            }
            when {
                state.isLoading -> Unit
                !state.hasAnyTour -> item { EmptyHint(stringResource(R.string.tours_empty)) }
                state.tours.isEmpty() -> item { EmptyHint(stringResource(R.string.tours_no_match)) }
                else -> items(state.tours, key = Tour::id) { tour ->
                    val vehicleName = if (state.showAllVehicles) {
                        vehicleDisplayName(state.vehicles.firstOrNull { it.id == tour.vehicleId })
                    } else {
                        null
                    }
                    TourCard(
                        tour = tour,
                        vehicleName = vehicleName,
                        stationCount = state.stationCounts[tour.id] ?: 0,
                        recording = activeRecordingTourId == tour.id,
                        finishing = finishingTourId == tour.id,
                        onClick = { onOpenTour(tour.id) },
                        onFinish = { finishTourId = tour.id },
                    )
                }
            }
        }
    }

    // Die Meldung gilt erst nach vollständiger Anzeige als erledigt; wer die Liste währenddessen
    // verlässt, sieht sie bei der Rückkehr erneut und kann das Löschen noch rückgängig machen.
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is ToursMessage.Deleted -> {
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.tours_deleted, current.tour.displayTitle(resources.getString(R.string.detail_fallback_title))),
                    actionLabel = resources.getString(R.string.tours_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(current)
            }
            ToursMessage.Saved -> snackbar.showSnackbar(resources.getString(R.string.tours_saved))
            ToursMessage.Finished -> snackbar.showSnackbar(resources.getString(R.string.tour_finished))
            is ToursMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onMessageShown(current)
    }

    val finishTour = state.tours.firstOrNull { it.id == finishTourId && it.endDate == null }
    if (finishTour != null) {
        FinishTourDialog(
            tourStart = finishTour.startDate,
            today = java.time.LocalDate.now(),
            onConfirm = {
                finishTourId = null
                viewModel.finish(finishTour, it)
            },
            onDismiss = { finishTourId = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToursTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    vehicles: List<Vehicle>,
    currentVehicleId: Long,
    showAllVehicles: Boolean,
    onSelectVehicle: (Long) -> Unit,
    onSelectAllVehicles: () -> Unit,
    onOpenVehicles: () -> Unit,
    onOpenOverview: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    TabTopBar(
        titleContent = {
            VehicleSwitcherTitle(
                vehicles = vehicles,
                currentVehicleId = currentVehicleId,
                title = stringResource(R.string.tours_title),
                showAllVehiclesOption = true,
                allVehiclesSelected = showAllVehicles,
                onSelectVehicle = onSelectVehicle,
                onSelectAllVehicles = onSelectAllVehicles,
                onManageVehicles = onOpenVehicles,
            )
        },
        onOpenOverview = onOpenOverview,
        onOpenSearch = onOpenSearch,
        onOpenData = onOpenData,
        onOpenSettings = onOpenSettings,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.tours_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun YearFilter(years: List<Int>, selected: Int?, onSelect: (Int?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.tours_all_years)) })
        }
        items(years) { year ->
            FilterChip(
                selected = selected == year,
                onClick = { onSelect(if (selected == year) null else year) },
                label = { Text(year.toString()) },
            )
        }
    }
}

@Composable
private fun TourCard(
    tour: Tour,
    vehicleName: String?,
    stationCount: Int,
    recording: Boolean,
    finishing: Boolean,
    onClick: () -> Unit,
    onFinish: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.tours_open_details), onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tour.displayTitle(stringResource(R.string.detail_fallback_title)), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (tour.endDate == null) {
                        stringResource(R.string.tours_running_since, formatDate(tour.startDate, currentLocale()))
                    } else {
                        tour.period(currentLocale())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recording) {
                    Text(
                        stringResource(R.string.tours_track_recording),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val meta = if (vehicleName != null) {
                    stringResource(R.string.tours_row_meta_vehicle, tour.year, stringResource(tour.tourType.labelRes), vehicleName)
                } else {
                    stringResource(R.string.tours_row_meta, tour.year, stringResource(tour.tourType.labelRes))
                }
                // Stationsanzahl nur bei > 0 anfügen.
                val metaWithStations = if (stationCount > 0) {
                    "$meta · ${pluralStringResource(R.plurals.tours_row_station_count, stationCount, stationCount)}"
                } else {
                    meta
                }
                Text(metaWithStations, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tour.endDate == null) {
                TextButton(onClick = onFinish, enabled = !finishing) {
                    Text(stringResource(R.string.tour_finish))
                }
            }
        }
    }
}
