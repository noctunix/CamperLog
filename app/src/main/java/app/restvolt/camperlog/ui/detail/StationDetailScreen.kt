package app.restvolt.camperlog.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.allowedServices
import app.restvolt.camperlog.domain.formatCoordinates
import app.restvolt.camperlog.domain.formatDate
import app.restvolt.camperlog.share.openInMaps
import app.restvolt.camperlog.ui.BackTopBar
import app.restvolt.camperlog.ui.EmptyHint
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.WeatherSummary
import app.restvolt.camperlog.ui.coordinatesContentDescription
import app.restvolt.camperlog.ui.currentLocale
import app.restvolt.camperlog.ui.iconRes
import app.restvolt.camperlog.ui.labelRes
import app.restvolt.camperlog.ui.stationSavedText
import app.restvolt.camperlog.ui.yesNoRes
import kotlinx.coroutines.launch

/**
 * Schreibgeschützte Ansicht einer Station. [onDelete] löscht ohne Rückfrage; die Tourdetailseite
 * bietet danach „Rückgängig" an. [onOpenTour] navigiert zur zugehörigen Tour, sofern vorhanden.
 */
@Composable
fun StationDetailScreen(
    viewModel: StationDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (Station) -> Unit,
    onOpenTour: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val station = (state as? StationDetailUiState.Loaded)?.station
    var overflowExpanded by remember { mutableStateOf(false) }
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbar.showSnackbar(stationSavedText(resources, current.loggedServices))
        viewModel.onMessageShown(current)
    }

    Scaffold(
        topBar = {
            val title = station?.name?.ifBlank { station.let { stringResource(it.type.labelRes) } }
                ?: stringResource(R.string.station_fallback_title)
            BackTopBar(title = title, onBack = onBack) {
                if (station != null) {
                    IconButton(onClick = onEdit) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.station_detail_edit))
                    }
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.station_detail_delete)) },
                            onClick = {
                                overflowExpanded = false
                                onDelete(station)
                            },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val current = state) {
            StationDetailUiState.Loading -> Unit
            StationDetailUiState.NotFound -> EmptyHint(stringResource(R.string.station_not_found), Modifier.padding(padding))
            is StationDetailUiState.Loaded -> StationDetails(
                station = current.station,
                tourDestination = current.tour?.destination,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                onOpenTour = { current.station.tourId?.let(onOpenTour) },
                onOpenMaps = {
                    if (!context.openInMaps(current.station)) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.station_detail_no_maps_app)) }
                    }
                },
            )
        }
    }
}

@Composable
private fun StationDetails(
    station: Station,
    tourDestination: String?,
    modifier: Modifier,
    onOpenTour: () -> Unit,
    onOpenMaps: () -> Unit,
) {
    val locale = currentLocale()
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard {
            val typeLabel = stringResource(station.type.labelRes)
            val rowText = if (tourDestination != null) stringResource(R.string.station_open_tour, typeLabel, tourDestination) else typeLabel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (tourDestination != null) it.clickable(onClickLabel = stringResource(R.string.tours_open_details), onClick = onOpenTour) else it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(station.type.iconRes), contentDescription = null, modifier = Modifier.padding(end = 12.dp))
                Text(rowText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }

            val dateText = station.time?.let { "${formatDate(station.date, locale)}, %02d:%02d".format(it.hour, it.minute) }
                ?: formatDate(station.date, locale)
            val nightsText = station.nights?.let { pluralStringResource(R.plurals.station_nights, it, it) }
            Text(listOfNotNull(dateText, nightsText).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)

            if (station.place.isNotBlank()) Text(station.place, style = MaterialTheme.typography.bodyLarge)

            if (station.latitude != null && station.longitude != null) {
                val description = coordinatesContentDescription(station.latitude, station.longitude, station.accuracyM, locale)
                Text(
                    formatCoordinates(station.latitude, station.longitude, locale),
                    modifier = Modifier.semantics { contentDescription = description },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (hasMapTarget(station)) {
                OutlinedButton(onClick = onOpenMaps) { Text(stringResource(R.string.station_detail_open_maps)) }
            }

            pitchDetailsText(station)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            servicesText(station)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            station.weather?.let { WeatherSummary(it) }
        }
        if (station.notes.isNotBlank()) {
            SectionCard {
                Text(stringResource(R.string.field_notes), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                Text(station.notes, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun hasMapTarget(station: Station): Boolean =
    (station.latitude != null && station.longitude != null) || station.mapLink != null || station.name.isNotBlank() || station.place.isNotBlank()

@Composable
private fun pitchDetailsText(station: Station): String? {
    val parts = listOfNotNull(
        station.siteKind?.let { stringResource(it.labelRes) },
        station.pitchAssigned?.let {
            stringResource(R.string.station_summary_field, stringResource(R.string.field_pitch_assigned), stringResource(yesNoRes(it)))
        },
        station.electricityFlatRate?.let {
            stringResource(R.string.station_summary_field, stringResource(R.string.field_electricity), stringResource(it.labelRes))
        },
        station.lteQuality?.let { stringResource(R.string.station_summary_field, stringResource(R.string.field_lte), stringResource(it.labelRes)) },
        station.pitchSlope?.let { stringResource(it.labelRes) },
        station.levelingBlocksUsed?.let {
            stringResource(R.string.station_summary_field, stringResource(R.string.field_leveling_blocks), stringResource(yesNoRes(it)))
        },
    )
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

@Composable
private fun servicesText(station: Station): String? {
    if (station.services.isEmpty()) return null
    val label = stringResource(R.string.station_type_supply)
    val values = station.type.allowedServices.filter { it in station.services }.map { stringResource(it.labelRes) }.joinToString(", ")
    return stringResource(R.string.station_summary_field, label, values)
}
