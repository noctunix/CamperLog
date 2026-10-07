package app.restvolt.camperlog.ui.stations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.formatMonthYear
import app.restvolt.camperlog.domain.isMapAvailable
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.TabTopBar
import app.restvolt.camperlog.ui.VehicleSwitcherTitle
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.iconRes
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.stationSavedText
import java.util.Locale

/** Stationen-Reiter: fahrzeugübergreifende Liste mit Suche, Filtern, laufender Tour und Hinweiskarte. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StationsScreen(
    viewModel: StationsViewModel,
    weatherMapEnabled: Boolean,
    onAddStop: () -> Unit,
    onOpenTour: (Long) -> Unit,
    onOpenStation: (Long) -> Unit,
    onOpenMap: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVehicles: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val locale = currentLocale()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TabTopBar(
                titleContent = {
                    VehicleSwitcherTitle(
                        vehicles = state.vehicles,
                        currentVehicleId = state.currentVehicleId,
                        title = stringResource(R.string.stations_title),
                        showAllVehiclesOption = true,
                        allVehiclesSelected = state.showAllVehicles,
                        onSelectVehicle = viewModel::onSelectVehicle,
                        onSelectAllVehicles = viewModel::onSelectAllVehicles,
                        onManageVehicles = onOpenVehicles,
                    )
                },
                extraActions = {
                    if (isMapAvailable(weatherMapEnabled, state.stations)) {
                        IconButton(onClick = onOpenMap) {
                            Icon(painterResource(R.drawable.ic_map), contentDescription = stringResource(R.string.action_map))
                        }
                    }
                },
                onOpenSearch = onOpenSearch,
                onOpenData = onOpenData,
                onOpenSettings = onOpenSettings,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            // Content-Überladung statt text/icon, siehe ToursScreen: sonst hätte der FAB für TalkBack keinen Namen.
            ExtendedFloatingActionButton(onClick = onAddStop) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.station_fab_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val groups = state.stations.groupBy { formatMonthYear(it.date, locale) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.showWhatsNew) {
                item { WhatsNewCard(onOpenSettings = onOpenSettings, onClose = viewModel::dismissWhatsNew) }
            }
            state.runningTour?.let { running ->
                item { RunningTourCard(running, onClick = { onOpenTour(running.tourId) }) }
            }
            if (state.hasAnyStation) {
                item { SearchField(state.query, viewModel::onQueryChange) }
                item { TypeFilter(state.selectedType, viewModel::onTypeSelected) }
                item { FavoriteFilterChip(state.favoriteOnly, viewModel::onFavoriteOnlyChange) }
            }
            when {
                state.isLoading -> Unit
                !state.hasAnyStation -> item { EmptyHint(stringResource(R.string.stations_empty)) }
                state.stations.isEmpty() -> item { EmptyHint(stringResource(R.string.stations_no_match)) }
                else -> groups.forEach { (month, stationsOfMonth) ->
                    stickyHeader(key = month) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Text(
                                month,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    items(stationsOfMonth, key = Station::id) { station ->
                        StationRow(
                            station = station,
                            tourName = station.tourId?.let(state.tourNames::get),
                            locale = locale,
                            onClick = { onOpenStation(station.id) },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is StationsMessage.Deleted -> {
                val label = current.station.name.ifBlank { resources.getString(current.station.type.labelRes) }
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.station_deleted, label),
                    actionLabel = resources.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteStation(current)
            }
            is StationsMessage.Saved -> snackbar.showSnackbar(stationSavedText(resources, current.loggedServices))
            is StationsMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onMessageShown(current)
    }
}

@Composable
private fun WhatsNewCard(onOpenSettings: () -> Unit, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stations_whats_new_text), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}

@Composable
private fun RunningTourCard(runningTour: RunningTour, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.tours_open_details)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = openLabel, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.stations_running_tour_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.stations_running_tour_meta, runningTour.destination, runningTour.dayNumber, runningTour.totalDays),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.stations_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun TypeFilter(selected: StationType?, onSelect: (StationType?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.stations_filter_all)) })
        }
        items(StationType.entries) { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(if (selected == type) null else type) },
                label = { Text(stringResource(type.labelRes)) },
                leadingIcon = { Icon(painterResource(type.iconRes), contentDescription = null) },
            )
        }
    }
}

@Composable
private fun FavoriteFilterChip(selected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        FilterChip(
            selected = selected,
            onClick = { onSelectedChange(!selected) },
            label = { Text(stringResource(R.string.station_would_return)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_favorite), contentDescription = null) },
        )
    }
}

@Composable
private fun StationRow(station: Station, tourName: String?, locale: Locale, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.tours_open_details)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = openLabel, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(station.type.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column(Modifier.weight(1f)) {
            val headline = station.name.ifBlank { stringResource(station.type.labelRes) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    headline,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (station.favorite) {
                    Icon(
                        painterResource(R.drawable.ic_favorite),
                        contentDescription = stringResource(R.string.station_would_return),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val date = station.time?.let { "${formatDate(station.date, locale)}, %02d:%02d".format(it.hour, it.minute) }
                ?: formatDate(station.date, locale)
            Text(
                listOfNotNull(date, tourName).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
