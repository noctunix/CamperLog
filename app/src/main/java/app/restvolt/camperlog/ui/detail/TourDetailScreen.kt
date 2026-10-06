package app.restvolt.camperlog.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.Vehicle
import app.restvolt.camperlog.domain.formatAmounts
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.domain.FUEL_SERVICES
import app.restvolt.camperlog.domain.SUPPLY_SERVICES
import app.restvolt.camperlog.share.openInMaps
import app.restvolt.camperlog.share.shareTour
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.LabeledValue
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.StationTypePickerSheet
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.iconRes
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.stationSavedText
import app.restvolt.camperlog.ui.vehicleDisplayName
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Schreibgeschützte Ansicht einer Tour mit Zeitleiste der Stationen (6.2). [onDelete] löscht die
 * Tour ohne Rückfrage; die Liste bietet anschließend „Rückgängig" an.
 */
@Composable
fun TourDetailScreen(
    viewModel: TourDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (Tour) -> Unit,
    onAddStation: (Long, StationType) -> Unit,
    onOpenStation: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val tour = (state as? DetailUiState.Loaded)?.tour
    var overflowExpanded by remember { mutableStateOf(false) }
    var showTypePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopBar(title = tour?.destination ?: stringResource(R.string.detail_fallback_title), onBack = onBack) {
                if (tour != null) {
                    IconButton(onClick = onEdit) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.detail_edit))
                    }
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_share)) },
                            onClick = {
                                overflowExpanded = false
                                if (!context.shareTour(tour)) {
                                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.no_share_app)) }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_open_maps)) },
                            onClick = {
                                overflowExpanded = false
                                if (!context.openInMaps(tour)) {
                                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.detail_no_maps_app)) }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_delete)) },
                            onClick = {
                                overflowExpanded = false
                                onDelete(tour)
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (tour != null) {
                // Content-Überladung statt text/icon, siehe ToursScreen: sonst hätte der FAB für TalkBack keinen Namen.
                ExtendedFloatingActionButton(onClick = { showTypePicker = true }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.station_fab_add))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val current = state) {
            DetailUiState.Loading -> Unit
            DetailUiState.NotFound -> EmptyHint(stringResource(R.string.tour_not_found), Modifier.padding(padding))
            is DetailUiState.Loaded -> TourDetails(
                tour = current.tour,
                vehicle = current.vehicle,
                stations = current.stations,
                modifier = Modifier.fillMaxSize(),
                padding = padding,
                onOpenStation = onOpenStation,
                onAddStop = { showTypePicker = true },
            )
        }
    }

    if (showTypePicker && tour != null) {
        StationTypePickerSheet(
            onSelect = { type ->
                showTypePicker = false
                onAddStation(tour.id, type)
            },
            onDismiss = { showTypePicker = false },
        )
    }

    // Die Meldung gilt erst nach vollständiger Anzeige als erledigt, siehe ToursScreen.
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is StationMessage.Deleted -> {
                val label = current.station.name.ifBlank { resources.getString(current.station.type.labelRes) }
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.station_deleted, label),
                    actionLabel = resources.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteStation(current)
            }
            is StationMessage.Saved -> snackbar.showSnackbar(stationSavedText(resources, current.loggedServices))
            is StationMessage.Failed -> snackbar.showSnackbar(resources.getString(current.text), withDismissAction = true)
        }
        viewModel.onMessageShown(current)
    }
}

@Composable
private fun TourDetails(
    tour: Tour,
    vehicle: Vehicle?,
    stations: List<Station>,
    modifier: Modifier,
    padding: PaddingValues,
    onOpenStation: (Long) -> Unit,
    onAddStop: () -> Unit,
) {
    val locale = currentLocale()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            // Der Scaffold reserviert keinen Platz für den FAB; siehe ToursScreen.
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard {
                if (vehicle != null) {
                    LabeledValue(stringResource(R.string.field_vehicle), vehicleDisplayName(vehicle))
                }
                LabeledValue(stringResource(R.string.field_start_date), formatDate(tour.startDate, locale))
                LabeledValue(stringResource(R.string.field_end_date), formatDate(tour.endDate, locale))
                LabeledValue(stringResource(R.string.field_destination), tour.destination)
                LabeledValue(stringResource(R.string.field_tour_type), stringResource(tour.tourType.labelRes))
                LabeledValue(stringResource(R.string.field_travel_days), tour.travelDays.toString())
                LabeledValue(stringResource(R.string.field_overnight_stays), tour.overnightStays.toString())
                LabeledValue(stringResource(R.string.label_distance), stringResource(R.string.distance_km, tour.distanceKm))
                LabeledValue(stringResource(R.string.field_cost), formatAmounts(tour.costs, locale))
            }
        }
        item {
            Text(
                stringResource(R.string.stations_section_title, stations.size),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (stations.isEmpty()) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.stations_timeline_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = onAddStop) {
                        Text(stringResource(R.string.stations_add_stop))
                    }
                }
            }
        } else {
            items(stations, key = Station::id) { station ->
                StationTimelineRow(
                    station = station,
                    locale = locale,
                    isLast = station.id == stations.last().id,
                    onClick = { onOpenStation(station.id) },
                )
            }
        }
        if (tour.notes.isNotBlank() || tour.mapLink != null) {
            item {
                SectionCard {
                    if (tour.notes.isNotBlank()) {
                        Text(
                            stringResource(R.string.field_notes),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(tour.notes, style = MaterialTheme.typography.bodyLarge)
                    }
                    tour.mapLink?.let {
                        Text(
                            stringResource(R.string.field_map_link),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StationTimelineRow(station: Station, locale: Locale, isLast: Boolean, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.tours_open_details)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(station.type.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .size(width = 2.dp, height = 24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 8.dp else 20.dp),
        ) {
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
            Text(
                stationSupportingText(station, locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun stationSupportingText(station: Station, locale: Locale): String {
    val date = formatDate(station.date, locale)
    val dateTime = station.time?.let { "$date, %02d:%02d".format(it.hour, it.minute) } ?: date
    val detail = when (station.type) {
        StationType.OVERNIGHT -> station.nights?.let { pluralStringResource(R.plurals.station_nights, it, it) }
        StationType.SUPPLY -> servicesLabel(SUPPLY_SERVICES, station.services)
        StationType.FUEL -> servicesLabel(FUEL_SERVICES + SUPPLY_SERVICES, station.services)
        StationType.SIGHT, StationType.FOOD, StationType.FERRY, StationType.OTHER -> null
    }
    return listOfNotNull(dateTime, detail).joinToString(" · ")
}

@Composable
private fun servicesLabel(allowed: Set<StationService>, selected: Set<StationService>): String? {
    val present = allowed.filter { it in selected }
    if (present.isEmpty()) return null
    return present.map { stringResource(it.labelRes) }.joinToString(", ")
}
